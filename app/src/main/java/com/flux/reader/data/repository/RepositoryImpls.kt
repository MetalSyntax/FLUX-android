package com.flux.reader.data.repository

import com.flux.reader.data.local.SettingsDataStore
import com.flux.reader.data.local.dao.FluxDao
import com.flux.reader.data.mapper.toDomain
import com.flux.reader.data.mapper.toEntity
import com.flux.reader.data.remote.CatalogCache
import com.flux.reader.data.remote.EpubMirrors
import com.flux.reader.data.remote.GutendexApi
import com.flux.reader.data.remote.OpdsFeed
import com.flux.reader.data.remote.OpenLibraryMapper
import com.flux.reader.data.remote.OpenLibraryResponseDto
import com.flux.reader.data.remote.RepoIndexParser
import com.flux.reader.data.remote.SourceStore
import com.flux.reader.domain.model.BookSource
import com.flux.reader.domain.model.SourceRepo
import com.flux.reader.domain.model.SourceType
import com.flux.reader.domain.model.SourcesState
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.StateFlow
import com.flux.reader.data.remote.OpdsParser
import com.flux.reader.domain.model.CatalogPage
import io.ktor.http.contentLength
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookCollection
import com.flux.reader.domain.model.Bookmark
import com.flux.reader.domain.model.DiscoverBook
import com.flux.reader.domain.model.ReadingSession
import com.flux.reader.domain.model.UserSettings
import com.flux.reader.domain.repository.BookRepository
import com.flux.reader.domain.repository.DiscoverRepository
import com.flux.reader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.flux.reader.data.files.LibraryFiles
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val dao: FluxDao,
    private val files: LibraryFiles
) : BookRepository {

    override fun observeLibrary(): Flow<List<Book>> =
        dao.observeAllBooks().map { list -> list.map { it.toDomain() } }

    override fun observeBook(bookId: String): Flow<Book?> =
        dao.observeBook(bookId).map { it?.toDomain() }

    override suspend fun upsertBook(book: Book) {
        dao.upsertBook(book.toEntity())
    }

    override suspend fun deleteBook(bookId: String) {
        dao.getBookById(bookId)?.let { files.deleteFiles(it.toDomain()) }
        dao.deleteBook(bookId)
        dao.deleteBookmarksOf(bookId)
        dao.allCollections().filter { bookId in it.toDomain().bookIds }.forEach {
            dao.upsertCollection(it.toDomain().let { c -> c.copy(bookIds = c.bookIds - bookId) }.toEntity())
        }
    }

    override suspend fun updateProgress(bookId: String, page: Int, totalPages: Int, cfi: String?) {
        dao.updateProgress(bookId, page, totalPages, cfi, System.currentTimeMillis())
    }

    override suspend fun toggleFavorite(bookId: String) {
        val current = dao.getBookById(bookId) ?: return
        dao.upsertBook(current.copy(isFavorite = !current.isFavorite))
    }

    override suspend fun toggleNsfw(bookId: String) {
        val current = dao.getBookById(bookId) ?: return
        dao.upsertBook(current.copy(isNsfw = !current.isNsfw))
    }

    override suspend fun renameBook(bookId: String, newTitle: String) {
        dao.renameBook(bookId, newTitle)
    }

    override fun observeBookmarks(bookId: String): Flow<List<Bookmark>> =
        dao.observeBookmarks(bookId).map { list -> list.map { it.toDomain() } }

    override suspend fun addBookmark(bookmark: Bookmark) {
        dao.insertBookmark(bookmark.toEntity())
    }

    override suspend fun deleteBookmark(bookmarkId: String) {
        dao.deleteBookmark(bookmarkId)
    }

    override fun observeCollections(): Flow<List<BookCollection>> =
        dao.observeCollections().map { list -> list.map { it.toDomain() } }

    override suspend fun upsertCollection(collection: BookCollection) {
        dao.upsertCollection(collection.toEntity())
    }

    override suspend fun deleteCollection(collectionId: String) {
        dao.deleteCollection(collectionId)
    }

    override fun observeSessions(): Flow<List<ReadingSession>> =
        dao.observeSessions().map { list -> list.map { it.toDomain() } }

    override suspend fun recordSession(session: ReadingSession) {
        dao.insertSession(session.toEntity())
    }
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: SettingsDataStore
) : SettingsRepository {
    override fun observeSettings(): Flow<UserSettings> = dataStore.settingsFlow
    override suspend fun updateSettings(transform: (UserSettings) -> UserSettings) {
        dataStore.update(transform)
    }
}

@Singleton
class DiscoverRepositoryImpl @Inject constructor(
    private val api: GutendexApi,
    private val bookRepository: BookRepository,
    private val files: LibraryFiles,
    private val client: HttpClient,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
) : DiscoverRepository {

    private val cache = CatalogCache(java.io.File(context.cacheDir, "catalog"))
    private val store = SourceStore(java.io.File(context.filesDir, "sources.json"))
    override val sources: StateFlow<SourcesState> = store.state

    override suspend fun addRepo(url: String): Result<SourceRepo> = runCatching {
        val u = url.trim()
        require(u.startsWith("https://") || u.startsWith("http://")) { "La dirección debe empezar por https://" }
        val body = client.get(u) { timeout { requestTimeoutMillis = 15_000 } }
            .also { check(it.status.isSuccess()) { "HTTP ${it.status.value}" } }.bodyAsText()
        RepoIndexParser.parse(u, body).also { withContext(Dispatchers.IO) { store.addRepo(it) } }
    }

    override fun removeRepo(url: String) = store.removeRepo(url)
    override fun installSource(source: BookSource) = store.install(source)
    override fun uninstallSource(id: String) = store.uninstall(id)

    /** Caché fresca responde sin red; si la red falla, sirve la vencida. */
    override suspend fun browse(source: BookSource, query: String?, genre: String?, page: Int): Result<CatalogPage> {
        val ck = "${source.id}|${source.url}|${query?.lowercase()}|${genre?.lowercase()}|$page"
        val hit = cache.get(ck)
        if (hit?.second == true) return Result.success(CatalogPage(hit.first, hit.first.size >= PAGE_SIZE))
        return runCatching {
            when (source.engine) {
                SourceType.GUTENDEX -> gutendex(source, query, genre, page)
                SourceType.OPEN_LIBRARY -> openLibrary(source, query, genre, page)
                SourceType.OPDS -> opds(source, query, page)
                null -> error("Tipo de fuente no compatible: ${source.type}")
            }.also { cache.put(ck, it.books) }
        }.recoverCatching { err ->
            if (err is kotlinx.coroutines.CancellationException) throw err
            hit?.let { CatalogPage(it.first, false) } ?: throw err
        }
    }

    /** Gutendex; si falla la búsqueda, el OPDS oficial de Gutenberg (1 página). */
    private suspend fun gutendex(source: BookSource, query: String?, genre: String?, page: Int): CatalogPage = runCatching {
        val dto = if (!query.isNullOrBlank()) api.searchBooks(source.url, query, page) else api.getBooksByTopic(source.url, genre ?: "Adventure", page)
        CatalogPage(dto.results.filter { "application/epub+zip" in it.formats }.map { it.toDomain(genre ?: "Clásicos", source.id) }, dto.next != null)
    }.recoverCatching { first ->
        if (first is kotlinx.coroutines.CancellationException || page > 1) throw first
        CatalogPage(OpdsParser.parse(api.opdsSearch(query ?: genre ?: "Adventure"), source.id).also { check(it.isNotEmpty()) { "OPDS vacío" } }, false)
    }.getOrThrow()

    private suspend fun openLibrary(source: BookSource, query: String?, genre: String?, page: Int): CatalogPage {
        val dto: OpenLibraryResponseDto = client.get(source.url.trimEnd('/') + "/search.json") {
            parameter("q", OpenLibraryMapper.query(query, if (query.isNullOrBlank()) genre ?: "fiction" else null))
            parameter("fields", OpenLibraryMapper.FIELDS)
            parameter("limit", OpenLibraryMapper.PAGE_SIZE)
            parameter("page", page)
            timeout { requestTimeoutMillis = 15_000 }
        }.also { check(it.status.isSuccess()) { "HTTP ${it.status.value}" } }.body()
        return CatalogPage(OpenLibraryMapper.toBooks(dto, source.id, genre ?: ""), page * OpenLibraryMapper.PAGE_SIZE < dto.numFound)
    }

    /** OPDS: la página siguiente se guarda por fuente/consulta porque el feed da la URL, no un número. */
    private val opdsNext = HashMap<String, String>()

    private suspend fun opds(source: BookSource, query: String?, page: Int): CatalogPage {
        val key = "${source.id}|$query"
        val url = when {
            page > 1 -> opdsNext[key] ?: return CatalogPage(emptyList(), false)
            !query.isNullOrBlank() -> (source.search ?: error("Esta fuente no permite buscar"))
                .replace("{q}", java.net.URLEncoder.encode(query, "UTF-8"))
            else -> source.url
        }
        val xml = client.get(url) { timeout { requestTimeoutMillis = 15_000 } }
            .also { check(it.status.isSuccess()) { "HTTP ${it.status.value}" } }.bodyAsText()
        val p = OpdsFeed.parse(xml, url, source.id)
        if (p.next != null) opdsNext[key] = p.next else opdsNext.remove(key)
        return CatalogPage(p.books, p.next != null)
    }

    /** Descarga el EPUB (probando mirrors en orden, con progreso) y lo importa a la biblioteca. */
    override suspend fun importFromDiscover(discoverBook: DiscoverBook, onProgress: (Float) -> Unit): Result<Book> = runCatching {
        if (discoverBook.epubDownloadUrl == null) error("Este libro no tiene EPUB disponible")
        val tmp = files.tempFile("dl")
        try {
            EpubMirrors.firstSuccess((listOfNotNull(discoverBook.epubDownloadUrl) + discoverBook.mirrors).distinct()) { url ->
                withContext(Dispatchers.IO) {
                    client.prepareGet(url) {
                        timeout { requestTimeoutMillis = 600_000; connectTimeoutMillis = 8_000; socketTimeoutMillis = 25_000 }
                    }.execute { resp ->
                        check(resp.status.isSuccess()) { "HTTP ${resp.status.value}" }
                        val total = resp.contentLength() ?: -1L
                        var done = 0L
                        resp.bodyAsChannel().toInputStream().use { i ->
                            tmp.outputStream().use { o ->
                                val buf = ByteArray(32 * 1024)
                                while (true) {
                                    val n = i.read(buf); if (n < 0) break
                                    o.write(buf, 0, n); done += n
                                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                        check(done > 1024) { "Archivo vacío" }
                    }
                }
            }
            var book = files.importFile(tmp, discoverBook.id.replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80) + ".epub", discoverBook.title, discoverBook.author).getOrThrow()
            if (book.coverUri.isBlank() && !discoverBook.coverUrl.isNullOrBlank()) {
                runCatching { client.get(discoverBook.coverUrl) { timeout { requestTimeoutMillis = 15_000 } }.body<ByteArray>() }.onSuccess { files.saveCoverBytes(book.id, it) }
                book = book.copy(coverUri = files.coverFile(book.id).takeIf { it.exists() }?.absolutePath.orEmpty())
            }
            bookRepository.upsertBook(book)
            book
        } finally { tmp.delete() }
    }

    private companion object { const val PAGE_SIZE = 32 }
}
