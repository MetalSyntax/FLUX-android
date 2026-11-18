package com.flux.reader.data.files

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Almacenamiento interno de libros y portadas + extracción de metadatos al importar. */
@Singleton
class LibraryFiles @Inject constructor(@ApplicationContext private val context: Context) {
    val booksDir = File(context.filesDir, "books").apply { mkdirs() }
    val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
    private val comicCache = File(context.cacheDir, "cbr")
    private val tmpDir = File(context.cacheDir, "import").apply { mkdirs() }

    fun bookFile(id: String, ext: String) = File(booksDir, "$id.$ext")
    fun coverFile(id: String) = File(coversDir, "$id.jpg")
    fun comicCacheDir(id: String) = File(comicCache, id)
    fun tempFile(prefix: String) = File.createTempFile(prefix, ".tmp", tmpDir)

    fun deleteFiles(book: Book) {
        File(book.fileUri).delete()
        coverFile(book.id).delete()
        comicCacheDir(book.id).deleteRecursively()
    }

    fun saveCover(id: String, bmp: Bitmap) {
        val scaled = if (bmp.width > 600) Bitmap.createScaledBitmap(bmp, 600, (bmp.height * 600f / bmp.width).toInt().coerceAtLeast(1), true) else bmp
        coverFile(id).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    fun saveCoverBytes(id: String, bytes: ByteArray) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { saveCover(id, it) }
    }

    fun displayName(uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "libro"

    /** Copia el documento elegido (SAF) a almacenamiento interno e inicia la importación. */
    suspend fun importUri(uri: Uri): Result<Book> = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = tempFile("saf")
            try {
                context.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().use { i.copyTo(it) } }
                importFile(tmp, displayName(uri)).getOrThrow()
            } finally { tmp.delete() }
        }
    }

    /** Importa [src] (se copia; el original no se toca). */
    suspend fun importFile(src: File, name: String, titleHint: String? = null, authorHint: String? = null): Result<Book> =
        withContext(Dispatchers.IO) {
            runCatching {
                val ext = name.substringAfterLast('.', "").lowercase()
                val type = when (ext) {
                    "epub" -> BookType.EPUB
                    "pdf" -> BookType.PDF
                    "cbz", "cbr", "zip", "rar" -> BookType.CBR
                    else -> throw IllegalArgumentException("Formato no soportado: .$ext")
                }
                val id = UUID.randomUUID().toString()
                val dest = bookFile(id, ext)
                src.copyTo(dest, overwrite = true)
                try {
                    var title = name.substringBeforeLast('.')
                    var author = if (type == BookType.CBR) "Manga / Comic" else "E-Book"
                    var pages = 1
                    when (type) {
                        BookType.EPUB -> EpubPackage(dest).use { epub ->
                            if (epub.spine.isEmpty()) error("EPUB vacío o dañado")
                            if (epub.title.isNotBlank()) title = epub.title
                            if (epub.author.isNotBlank()) author = epub.author
                            pages = 1000
                            epub.coverPath?.let { p -> epub.open(p)?.use { saveCoverBytes(id, it.readBytes()) } }
                        }
                        BookType.PDF -> PdfPageSource(dest).use { pdf ->
                            pages = pdf.pageCount.coerceAtLeast(1)
                            kotlinx.coroutines.runBlocking { pdf.render(0, 480) }?.let { saveCover(id, it) }
                        }
                        BookType.CBR -> PageSources.openComic(dest, comicCacheDir(id)).use { cs ->
                            if (cs.pageCount == 0) error("No se encontraron imágenes")
                            pages = cs.pageCount
                            kotlinx.coroutines.runBlocking { cs.render(0, 480) }?.let { saveCover(id, it) }
                        }
                    }
                    Book(
                        id = id, title = titleHint?.takeIf { it.isNotBlank() } ?: title,
                        author = authorHint?.takeIf { it.isNotBlank() } ?: author,
                        coverUri = coverFile(id).takeIf { it.exists() }?.absolutePath.orEmpty(),
                        fileUri = dest.absolutePath, type = type, currentPage = 1, totalPages = pages,
                        fileSizeBytes = dest.length(), fileName = name
                    )
                } catch (e: Throwable) {
                    dest.delete(); coverFile(id).delete(); comicCacheDir(id).deleteRecursively()
                    throw e
                }
            }
        }
}
