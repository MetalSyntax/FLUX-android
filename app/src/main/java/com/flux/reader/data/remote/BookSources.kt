package com.flux.reader.data.remote

import com.flux.reader.domain.model.BookSource
import com.flux.reader.domain.model.DiscoverBook
import com.flux.reader.domain.model.SourceRepo
import com.flux.reader.domain.model.SourcesState
import java.io.File
import java.net.URI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val lenientJson = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private fun String.isHttpUrl() = runCatching { URI(this).let { (it.scheme == "https" || it.scheme == "http") && !it.host.isNullOrBlank() } }.getOrDefault(false)

/** Valida el índice JSON de un repositorio: viene de internet, así que se descarta lo que no cumpla. */
object RepoIndexParser {
    private val idRx = Regex("[a-z0-9._-]{1,64}")

    fun parse(url: String, body: String): SourceRepo {
        val repo = lenientJson.decodeFromString(SourceRepo.serializer(), body)
        require(repo.name.isNotBlank()) { "El repositorio no tiene nombre" }
        val ok = repo.sources.filter { s ->
            s.engine != null && idRx.matches(s.id) && s.name.isNotBlank() && s.url.isHttpUrl() &&
                (s.search == null || ("{q}" in s.search && s.search.replace("{q}", "q").isHttpUrl()))
        }.distinctBy { it.id }.map { it.copy(name = it.name.take(80), description = it.description?.take(300)) }
        require(ok.isNotEmpty()) { "El repositorio no tiene fuentes compatibles" }
        return SourceRepo(url = url, name = repo.name.take(80), sources = ok)
    }
}

/** Repositorios agregados y fuentes instaladas, en un JSON en filesDir. Arranca vacío: nada viene preinstalado. */
class SourceStore(private val file: File) {
    private val _state = MutableStateFlow(load())
    val state: StateFlow<SourcesState> = _state.asStateFlow()

    private fun load(): SourcesState = runCatching {
        lenientJson.decodeFromString(SourcesState.serializer(), file.readText())
    }.getOrDefault(SourcesState())

    @Synchronized
    fun update(transform: (SourcesState) -> SourcesState) {
        val next = transform(_state.value)
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(lenientJson.encodeToString(SourcesState.serializer(), next))
        check(tmp.renameTo(file)) { "No se pudo guardar las fuentes" }
        _state.value = next
    }

    fun addRepo(repo: SourceRepo) = update { s ->
        // Al refrescar un repo, sus fuentes instaladas toman la definición nueva (o se quitan si ya no existen).
        val fresh = repo.sources.associateBy { it.id }
        val oldIds = s.repos.firstOrNull { it.url == repo.url }?.sources?.map { it.id }.orEmpty().toSet()
        s.copy(
            repos = s.repos.filterNot { it.url == repo.url } + repo,
            installed = s.installed.mapNotNull { i -> if (i.id in oldIds) fresh[i.id] else i }
        )
    }

    fun removeRepo(url: String) = update { s ->
        val ids = s.repos.firstOrNull { it.url == url }?.sources?.map { it.id }.orEmpty().toSet()
        s.copy(repos = s.repos.filterNot { it.url == url }, installed = s.installed.filterNot { it.id in ids })
    }

    fun install(source: BookSource) = update { s -> s.copy(installed = s.installed.filterNot { it.id == source.id } + source) }

    fun uninstall(id: String) = update { s -> s.copy(installed = s.installed.filterNot { it.id == id }) }
}

/** Parser OPDS 1.x genérico (feeds de adquisición): libros con enlace EPUB, portada y página siguiente. */
object OpdsFeed {
    private val entryRx = Regex("<entry[\\s>](.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val linkRx = Regex("<link\\b([^>]*)/?>")
    private val attrRx = Regex("([\\w:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
    private val idRx = Regex("<id>(.*?)</id>", RegexOption.DOT_MATCHES_ALL)
    private val titleRx = Regex("<title[^>]*>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val authorRx = Regex("<author>.*?<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
    private val categoryRx = Regex("<category[^>]*label=\"([^\"]*)\"")

    data class Page(val books: List<DiscoverBook>, val next: String?)

    private fun links(xml: String) = linkRx.findAll(xml).map { m ->
        attrRx.findAll(m.groupValues[1]).associate { it.groupValues[1] to unescape(it.groupValues[3].ifEmpty { it.groupValues[4] }) }
    }.toList()

    fun parse(xml: String, baseUrl: String, sourceId: String): Page {
        fun abs(href: String) = runCatching { URI(baseUrl).resolve(href).toString() }.getOrNull()
        val books = entryRx.findAll(xml).mapNotNull { m ->
            val e = m.groupValues[1]
            val ls = links(e)
            val epubs = ls.filter { it["rel"].orEmpty().startsWith("http://opds-spec.org/acquisition") && it["type"].orEmpty().startsWith("application/epub+zip") }
                .mapNotNull { it["href"]?.let(::abs) }
            if (epubs.isEmpty()) return@mapNotNull null
            val cover = (ls.firstOrNull { it["rel"] == "http://opds-spec.org/image/thumbnail" } ?: ls.firstOrNull { it["rel"] == "http://opds-spec.org/image" })
                ?.get("href")?.let(::abs)
            val title = titleRx.find(e)?.groupValues?.get(1)?.let(::unescape) ?: return@mapNotNull null
            DiscoverBook(
                id = "$sourceId:${idRx.find(e)?.groupValues?.get(1)?.trim() ?: epubs.first()}",
                title = title,
                author = authorRx.find(e)?.groupValues?.get(1)?.let(::unescape) ?: "Autor desconocido",
                genre = categoryRx.find(e)?.groupValues?.get(1)?.let(::unescape) ?: "",
                coverUrl = cover,
                epubDownloadUrl = epubs.first(),
                mirrors = epubs.drop(1)
            )
        }.toList()
        // El <link rel="next"> del feed está fuera de las <entry>.
        val feedLinks = links(entryRx.replace(xml, ""))
        return Page(books, feedLinks.firstOrNull { it["rel"] == "next" }?.get("href")?.let(::abs))
    }

    internal fun unescape(s: String) = s.trim().replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'").replace("&amp;", "&")
}

@Serializable
data class OpenLibraryResponseDto(@SerialName("num_found") val numFound: Int = 0, val docs: List<OpenLibraryDocDto> = emptyList())

@Serializable
data class OpenLibraryDocDto(
    val key: String = "",
    val title: String = "",
    @SerialName("author_name") val authorName: List<String> = emptyList(),
    @SerialName("cover_i") val coverId: Long? = null,
    val ia: List<String> = emptyList(),
    @SerialName("ebook_access") val ebookAccess: String? = null,
    val subject: List<String> = emptyList()
)

/** Open Library: solo obras de dominio público (ebook_access=public); el EPUB se baja de archive.org. */
object OpenLibraryMapper {
    const val PAGE_SIZE = 32
    const val FIELDS = "key,title,author_name,cover_i,ia,ebook_access,subject"

    fun query(text: String?, genre: String?): String = buildString {
        if (!text.isNullOrBlank()) append(text.trim()).append(' ')
        if (!genre.isNullOrBlank()) append("subject:\"").append(genre.lowercase().replace("\"", "")).append("\" ")
        append("ebook_access:public")
    }

    fun toBooks(dto: OpenLibraryResponseDto, sourceId: String, fallbackGenre: String): List<DiscoverBook> =
        dto.docs.filter { it.ebookAccess == "public" && it.ia.isNotEmpty() && it.title.isNotBlank() }.map { d ->
            val epubs = d.ia.distinct().take(4).map { "https://archive.org/download/$it/$it.epub" }
            DiscoverBook(
                id = "$sourceId:${d.key}",
                title = d.title,
                author = d.authorName.firstOrNull() ?: "Autor desconocido",
                genre = d.subject.firstOrNull() ?: fallbackGenre,
                coverUrl = d.coverId?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
                epubDownloadUrl = epubs.first(),
                mirrors = epubs.drop(1)
            )
        }
}
