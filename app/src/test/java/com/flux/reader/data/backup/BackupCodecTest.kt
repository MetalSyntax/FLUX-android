package com.flux.reader.data.backup

import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {
    private val epub = Book("b1", "Alicia", "Carroll", "", "/x", BookType.EPUB, 250, 1000, null, 5L, true, true, 4, "alicia.epub", listOf("clasico"))

    private fun tmpZip(bytes: ByteArray) = File.createTempFile("flux", ".zip").apply { writeBytes(bytes); deleteOnExit() }

    @Test fun roundTripKeepsManifestAndFileBytes() {
        val payload = ByteArray(5000) { (it % 251).toByte() }
        val manifest = BackupManifest(
            exportedAt = 123, books = listOf(BackupMapper.toBackup(epub, "books/b1.epub", "covers/b1.jpg")),
            collections = listOf(BackupMapper.toBackup(com.flux.reader.domain.model.BookCollection("c1", "Favs", 0xFF7C3AED, setOf("b1"))))
        )
        val out = ByteArrayOutputStream()
        BackupCodec.write(out, manifest, listOf("books/b1.epub" to { ByteArrayInputStream(payload) }, "covers/b1.jpg" to { null }))
        BackupCodec.Reader(tmpZip(out.toByteArray())).use { r ->
            assertEquals(manifest, r.manifest)
            assertEquals(payload.toList(), r.open("books/b1.epub")!!.readBytes().toList())
            assertEquals(5000L, r.size("books/b1.epub"))
            assertNull(r.open("covers/b1.jpg"))
        }
        assertEquals("var(--color-purple)", manifest.collections[0].color) // token de la web preservado
    }

    @Test fun readsWebStyleManifestWithUnknownAndMissingFields() {
        val json = """{"format":"flux-backup","version":1,"source":"web","extra":1,
          "settings":{"name":"Ana","theme":"black","language":"pt","showNsfw":true,"dailyGoal":15,"avatar":"data:image/png;base64,xx"},
          "metadata":{"sort_pref":"title","lib_view":false,"search_history":["a"]},
          "books":[{"id":"w1","title":"Manga","type":"CBR","progress":40,"currentPage":4,"totalPages":10,"tags":["NSFW"],
                    "lastReadDate":99,"file":"books/w1.cbz","fileName":"m.cbz","coverUrlIgnored":"x"}],
          "bookmarks":[{"id":"k","bookId":"w1","page":3,"label":"x","color":"var(--color-red)","createdAt":1}],
          "sessions":[{"id":"s","bookId":"w1","startTime":1,"endTime":2,"pagesRead":5}],
          "collections":[{"id":"c","name":"N","color":"var(--color-green)","bookIds":["w1"]}]}"""
        val z = ByteArrayOutputStream()
        ZipOutputStream(z).use { it.putNextEntry(ZipEntry("manifest.json")); it.write(json.toByteArray()); it.closeEntry() }
        BackupCodec.Reader(tmpZip(z.toByteArray())).use { r ->
            val b = r.manifest.books.single()
            val book = BackupMapper.toBook(b, "/f", "", 10)
            assertEquals(BookType.CBR, book.type); assertTrue(book.isNsfw); assertEquals(4, book.currentPage)
            assertEquals(0xFFDC2626, BackupMapper.toBookmark(r.manifest.bookmarks[0], "w1").colorHex)
            val s = BackupMapper.applySettings(com.flux.reader.domain.model.UserSettings(), r.manifest.settings!!)
            assertEquals("Ana", s.name); assertEquals(com.flux.reader.domain.model.AppThemeMode.AMOLED_BLACK, s.themeMode)
            assertEquals(com.flux.reader.domain.model.AppLanguage.PT, s.language)
        }
    }

    @Test fun epubProgressIsNormalizedToThousand() {
        val b = BackupBook("e", type = "EPUB", progress = 37, currentPage = 412, totalPages = 1113)
        val book = BackupMapper.toBook(b, "/f", "", 1)
        assertEquals(370, book.currentPage); assertEquals(1000, book.totalPages); assertEquals(37, book.progressPercent)
    }

    @Test fun nsfwTagRoundTripsAndDuplicatesAreDetected() {
        val back = BackupMapper.toBackup(epub, "books/b1.epub", null)
        assertTrue("NSFW" in back.tags && "clasico" in back.tags)
        assertNotNull(BackupMapper.findDuplicate(listOf(epub), back.copy(id = "other"), 4)) // mismo título+tipo+tamaño
        assertNotNull(BackupMapper.findDuplicate(listOf(epub), back.copy(title = "otro"), 999)) // mismo id
        assertNull(BackupMapper.findDuplicate(listOf(epub), back.copy(id = "z", title = "otro"), 4))
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsForeignZip() {
        val z = ByteArrayOutputStream()
        ZipOutputStream(z).use { it.putNextEntry(ZipEntry("manifest.json")); it.write("""{"format":"other"}""".toByteArray()); it.closeEntry() }
        BackupCodec.Reader(tmpZip(z.toByteArray()))
    }
}
