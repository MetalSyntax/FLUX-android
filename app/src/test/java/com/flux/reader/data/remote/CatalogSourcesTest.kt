package com.flux.reader.data.remote

import com.flux.reader.domain.model.DiscoverBook
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

class CatalogSourcesTest {
    private val opds = """<?xml version="1.0"?><feed>
      <entry><id>https://www.gutenberg.org/ebooks/2000.opds</id><title>Don Quijote &amp; Sancho</title><content type="text">Cervantes Saavedra, Miguel de</content></entry>
      <entry><id>https://www.gutenberg.org/ebooks/author/505.opds</id><title>Autor</title></entry>
      <entry><id>https://www.gutenberg.org/ebooks/996</id><title>Quixote</title></entry></feed>"""

    @Test fun opdsParsesBooksAndSkipsNavigationEntries() {
        val l = OpdsParser.parse(opds)
        assertEquals(listOf("gutenberg:2000", "gutenberg:996"), l.map { it.id })
        assertEquals("Don Quijote & Sancho", l[0].title)
        assertEquals("Cervantes Saavedra, Miguel de", l[0].author)
        assertEquals("Autor desconocido", l[1].author)
        assertTrue(l[0].coverUrl!!.endsWith("pg2000.cover.medium.jpg"))
    }

    @Test fun candidatesStartWithListedAndAreDistinct() {
        val c = EpubMirrors.candidates(2000, "https://www.gutenberg.org/cache/epub/2000/pg2000-images-3.epub")
        assertEquals(c.size, c.toSet().size)
        assertEquals("https://www.gutenberg.org/cache/epub/2000/pg2000-images-3.epub", c[0])
        assertTrue(c.any { it.startsWith("https://gutenberg.pglaf.org") })
    }

    @Test fun firstSuccessFallsBackAfterFailures() = runBlocking {
        val tried = mutableListOf<String>()
        val r = EpubMirrors.firstSuccess(listOf("a", "b", "c")) { u -> tried += u; if (u != "b") throw IOException("503") else "ok-$u" }
        assertEquals("ok-b", r); assertEquals(listOf("a", "b"), tried)
    }

    @Test fun firstSuccessThrowsWhenAllFail() = runBlocking {
        try { EpubMirrors.firstSuccess(listOf("a", "b")) { throw IOException("x") }; fail() } catch (e: IOException) { assertTrue(e.message!!.contains("2 intentos")) }
    }

    @Test fun cacheRoundTripsAndExpires() {
        val dir = createTempDir()
        var t = 1_000_000L
        val book = DiscoverBook("s:1", "T", "A", "G", null, "u", 3, listOf("m"))
        CatalogCache(dir) { t }.put("k", listOf(book))
        val disk = CatalogCache(dir) { t + 10 }   // instancia nueva = solo disco
        assertEquals(true, disk.get("k")!!.second); assertEquals(listOf(book), disk.get("k")!!.first)
        val late = CatalogCache(File(dir.path)) { Long.MAX_VALUE / 2 }
        assertEquals(false, late.get("k")!!.second)
        assertNull(late.get("zz"))
        dir.deleteRecursively()
    }
}
