package com.flux.reader.data.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.github.junrar.Archive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.zip.ZipFile

/** Fuente de páginas como bitmaps: PDF, CBZ (zip de imágenes) y CBR (RAR extraído a caché). */
interface PageSource : Closeable {
    val pageCount: Int
    suspend fun render(index: Int, targetWidth: Int): Bitmap?
}

private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp")
private fun isImage(name: String) = name.substringAfterLast('.', "").lowercase() in IMAGE_EXT && !name.contains("__MACOSX")

/** Orden "natural": 2.jpg antes que 10.jpg. */
val naturalOrder = Comparator<String> { a, b ->
    val ra = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
    val rb = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ra.size, rb.size)) {
        val x = ra[i]; val y = rb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) x.toBigInteger().compareTo(y.toBigInteger()) else x.compareTo(y)
        if (c != 0) return@Comparator c
    }
    ra.size - rb.size
}

private fun decodeSampled(bytes: ByteArray, targetWidth: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    var sample = 1
    while (opts.outWidth / (sample * 2) >= targetWidth) sample *= 2
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

class PdfPageSource(file: File) : PageSource {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val lock = Mutex() // PdfRenderer solo permite una página abierta a la vez
    override val pageCount = renderer.pageCount // se guarda: tras close() el renderer lanza excepción
    @Volatile private var closed = false

    override suspend fun render(index: Int, targetWidth: Int): Bitmap? = lock.withLock {
        if (closed) return@withLock null
        withContext(Dispatchers.IO) {
            runCatching { renderer.openPage(index.coerceIn(0, pageCount - 1)).use { page ->
                val scale = targetWidth.toFloat() / page.width
                val bmp = Bitmap.createBitmap(targetWidth, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } }.onFailure { android.util.Log.e("FLUX", "render pdf", it) }.getOrNull()
        }
    }

    override fun close() { closed = true; runCatching { renderer.close() }; runCatching { fd.close() } }
}

class ZipPageSource(file: File) : PageSource {
    private val zip = ZipFile(file)
    private val names = zip.entries().asSequence().filter { !it.isDirectory && isImage(it.name) }
        .map { it.name }.sortedWith(naturalOrder).toList()
    override val pageCount get() = names.size

    override suspend fun render(index: Int, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        val name = names.getOrNull(index) ?: return@withContext null
        zip.getInputStream(zip.getEntry(name)).use { decodeSampled(it.readBytes(), targetWidth) }
    }

    override fun close() { runCatching { zip.close() } }
}

class DirPageSource(dir: File) : PageSource {
    private val files = dir.listFiles().orEmpty().filter { isImage(it.name) }.sortedWith(compareBy(naturalOrder) { it.name })
    override val pageCount get() = files.size

    override suspend fun render(index: Int, targetWidth: Int): Bitmap? = withContext(Dispatchers.IO) {
        files.getOrNull(index)?.let { decodeSampled(it.readBytes(), targetWidth) }
    }

    override fun close() {}
}

object PageSources {
    fun isZip(file: File) = file.inputStream().use { s -> ByteArray(2).also { s.read(it) } }.let { it[0] == 'P'.code.toByte() && it[1] == 'K'.code.toByte() }

    /** CBR: muchos "CBR" son en realidad zip; si es RAR4 real se extrae a [cacheDir]/<id>. RAR5 no está soportado. */
    fun openComic(file: File, cacheDir: File): PageSource {
        if (isZip(file)) return ZipPageSource(file)
        if (!cacheDir.isDirectory || cacheDir.listFiles().isNullOrEmpty()) extractRar(file, cacheDir)
        return DirPageSource(cacheDir)
    }

    fun extractRar(file: File, outDir: File) {
        outDir.deleteRecursively(); outDir.mkdirs()
        try {
            Archive(file).use { arc ->
                var n = 0
                for (h in arc.fileHeaders) {
                    if (h.isDirectory || !isImage(h.fileName)) continue
                    val name = "%05d_%s".format(n++, h.fileName.substringAfterLast('\\').substringAfterLast('/'))
                    File(outDir, name).outputStream().use { arc.extractFile(h, it) }
                }
                require(n > 0) { "El archivo CBR no contiene imágenes" }
            }
        } catch (e: Exception) {
            outDir.deleteRecursively()
            throw IllegalStateException("No se pudo abrir este CBR (¿formato RAR5?). Conviértelo a CBZ.", e)
        }
    }

    fun coverBitmap(file: File, type: com.flux.reader.domain.model.BookType, tmp: File): Bitmap? = runCatching {
        val src = when (type) {
            com.flux.reader.domain.model.BookType.PDF -> PdfPageSource(file)
            else -> openComic(file, tmp)
        }
        src.use { s -> kotlinx.coroutines.runBlocking { s.render(0, 480) } }
    }.getOrNull()
}
