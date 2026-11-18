package com.flux.reader.data.backup

import android.content.Context
import android.net.Uri
import com.flux.reader.data.files.LibraryFiles
import com.flux.reader.data.local.dao.FluxDao
import com.flux.reader.data.mapper.toDomain
import com.flux.reader.data.mapper.toEntity
import com.flux.reader.domain.model.BookCollection
import com.flux.reader.domain.model.LibrarySort
import com.flux.reader.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class ImportSummary(val added: Int, val skipped: Int, val withoutFile: Int)

@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: FluxDao,
    private val settingsRepo: SettingsRepository,
    private val files: LibraryFiles
) {
    suspend fun export(target: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val books = dao.allBooks().map { it.toDomain() }
            val settings = settingsRepo.observeSettings().first()
            val entries = ArrayList<Pair<String, () -> java.io.InputStream?>>()
            val backupBooks = books.map { b ->
                val src = File(b.fileUri)
                val filePath = if (src.exists()) "books/${b.id}.${BackupMapper.extOf(b.fileName, b.type)}" else null
                val cover = File(b.coverUri).takeIf { b.coverUri.startsWith("/") && it.exists() }
                val coverPath = cover?.let { "covers/${b.id}.jpg" }
                if (filePath != null) entries += filePath to { src.inputStream() }
                if (coverPath != null) entries += coverPath to { cover.inputStream() }
                BackupMapper.toBackup(b, filePath, coverPath)
            }
            val manifest = BackupManifest(
                exportedAt = System.currentTimeMillis(), source = "android",
                settings = BackupMapper.toBackup(settings),
                metadata = JsonObject(mapOf(
                    "sort_pref" to JsonPrimitive(when (settings.librarySort) { LibrarySort.RECENT -> "date"; LibrarySort.TITLE -> "title"; LibrarySort.AUTHOR -> "author" }),
                    "lib_view" to JsonPrimitive(settings.libraryGrid)
                )),
                books = backupBooks,
                bookmarks = dao.allBookmarks().map { BackupMapper.toBackup(it.toDomain()) },
                sessions = dao.allSessions().map { BackupMapper.toBackup(it.toDomain()) },
                collections = dao.allCollections().map { BackupMapper.toBackup(it.toDomain()) }
            )
            context.contentResolver.openOutputStream(target, "wt")!!.buffered().use { BackupCodec.write(it, manifest, entries) }
            books.size
        }
    }

    /** Importa un zip de FLUX (web o Android) fusionando con lo existente, sin duplicar. */
    suspend fun import(source: Uri): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = files.tempFile("backup")
            try {
                context.contentResolver.openInputStream(source)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                BackupCodec.Reader(tmp).use { reader -> merge(reader) }
            } finally { tmp.delete() }
        }
    }

    private suspend fun merge(reader: BackupCodec.Reader): ImportSummary {
        val m = reader.manifest
        val existing = dao.allBooks().map { it.toDomain() }.toMutableList()
        val idMap = HashMap<String, String>() // id de la copia -> id local
        var added = 0; var skipped = 0; var noFile = 0

        for (b in m.books) {
            val size = b.file?.let { reader.size(it) } ?: -1L
            val dup = BackupMapper.findDuplicate(existing, b, size)
            if (dup != null) {
                idMap[b.id] = dup.id
                skipped++
                if (b.lastReadDate > dup.lastReadEpochMs) { // gana el progreso más reciente
                    val inc = BackupMapper.toBook(b, dup.fileUri, dup.coverUri, dup.fileSizeBytes)
                    dao.upsertBook(dup.copy(
                        currentPage = inc.currentPage, totalPages = if (dup.type == inc.type) inc.totalPages else dup.totalPages,
                        currentCfi = inc.currentCfi, lastReadEpochMs = inc.lastReadEpochMs, isFavorite = inc.isFavorite
                    ).toEntity())
                }
                continue
            }
            val input = b.file?.let { reader.open(it) }
            if (input == null) { noFile++; continue }
            val type = BackupMapper.typeOf(b.type)
            val dest = files.bookFile(b.id, BackupMapper.extOf(b.fileName ?: b.file, type))
            input.use { i -> dest.outputStream().use { i.copyTo(it) } }
            val coverFile = files.coverFile(b.id)
            b.cover?.let { c -> reader.open(c)?.use { i -> files.saveCoverBytes(b.id, i.readBytes()) } }
            val book = BackupMapper.toBook(b, dest.absolutePath, coverFile.takeIf { it.exists() }?.absolutePath.orEmpty(), dest.length())
            dao.upsertBook(book.toEntity())
            existing += book
            added++
        }
        fun map(id: String) = idMap[id] ?: id
        val knownIds = dao.allBooks().map { it.id }.toSet()

        val localCols = dao.allCollections().map { it.toDomain() }.associateBy { it.id }
        for (c in m.collections) {
            val ids = c.bookIds.map(::map).filter { it in knownIds }.toSet()
            val cur = localCols[c.id]
            dao.upsertCollection((cur?.copy(bookIds = cur.bookIds + ids)
                ?: BookCollection(c.id, c.name, BackupMapper.colorFromString(c.color), ids)).toEntity())
        }
        for (bm in m.bookmarks) if (map(bm.bookId) in knownIds) dao.insertBookmark(BackupMapper.toBookmark(bm, map(bm.bookId)).toEntity())
        for (s in m.sessions) if (map(s.bookId) in knownIds) dao.insertSession(BackupMapper.toSession(s, map(s.bookId)).toEntity())

        settingsRepo.updateSettings { cur ->
            var next = m.settings?.let { BackupMapper.applySettings(cur, it) } ?: cur
            m.metadata?.get("sort_pref")?.let { (it as? JsonPrimitive)?.contentOrNull }?.let { v ->
                next = next.copy(librarySort = when (v) { "title" -> LibrarySort.TITLE; "author" -> LibrarySort.AUTHOR; else -> LibrarySort.RECENT })
            }
            (m.metadata?.get("lib_view") as? JsonPrimitive)?.booleanOrNull?.let { next = next.copy(libraryGrid = it) }
            next
        }
        return ImportSummary(added, skipped, noFile)
    }
}
