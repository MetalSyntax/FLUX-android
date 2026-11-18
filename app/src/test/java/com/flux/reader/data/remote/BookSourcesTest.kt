package com.flux.reader.data.remote

import com.flux.reader.domain.model.BookSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class BookSourcesTest {
    private val repoJson = """{"name":"Mi repo","extra":1,"sources":[
      {"id":"gutenberg","name":"Gutenberg","type":"gutendex","url":"https://gutendex.com/books/"},
      {"id":"bad id!","name":"X","type":"opds","url":"https://a.org/f"},
      {"id":"evil","name":"X","type":"script","url":"https://a.org/f"},
      {"id":"ftp","name":"X","type":"opds","url":"ftp://a.org/f"},
      {"id":"nosearch","name":"X","type":"opds","url":"https://a.org/f","search":"https://a.org/s"},
      {"id":"feed","name":"Feed","type":"opds","url":"https://a.org/f","search":"https://a.org/s?q={q}"},
      {"id":"gutenberg","name":"Duplicado","type":"gutendex","url":"https://otro.org/"}]}"""

    @Test fun repoKeepsOnlyValidSources() {
        val r = RepoIndexParser.parse("https://r.org/repo.json", repoJson)
        assertEquals("https://r.org/repo.json", r.url)
        assertEquals(listOf("gutenberg", "feed"), r.sources.map { it.id })
        assertEquals("Gutenberg", r.sources[0].name)
    }

    @Test fun repoWithoutUsableSourcesFails() {
        try { RepoIndexParser.parse("u", """{"name":"x","sources":[{"id":"a","name":"a","type":"js","url":"https://a"}]}"""); fail() }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("compatibles")) }
    }

    @Test fun storeStartsEmptyPersistsAndRemovesRepoSources() {
        val dir = createTempDir(); val f = File(dir, "sources.json")
        val store = SourceStore(f)
        assertTrue(store.state.value.installed.isEmpty() && store.state.value.repos.isEmpty())
        val repo = RepoIndexParser.parse("https://r.org/repo.json", repoJson)
        store.addRepo(repo); store.install(repo.sources[0]); store.install(repo.sources[1])
        store.install(BookSource("manual", "M", "opds", "https://m.org/f"))
        assertEquals(listOf("gutenberg", "feed", "manual"), SourceStore(f).state.value.installed.map { it.id })
        // Refrescar el repo sin "feed" la desinstala; la ajena se queda.
        store.addRepo(repo.copy(sources = repo.sources.take(1)))
        assertEquals(listOf("gutenberg", "manual"), store.state.value.installed.map { it.id })
        store.removeRepo(repo.url)
        assertEquals(listOf("manual"), SourceStore(f).state.value.installed.map { it.id })
        dir.deleteRecursively()
    }

    @Test fun opdsParsesAcquisitionEntriesRelativeLinksAndNext() {
        val xml = """<feed><link rel="next" href="/opds/all?page=2" type="application/atom+xml"/>
          <entry><id>urn:1</id><title>Libro &amp; uno</title><author><name>Ana</name></author>
            <category term="f" label="Ficción"/>
            <link type="application/epub+zip" href="/b/1.epub" rel="http://opds-spec.org/acquisition/open-access"/>
            <link rel="http://opds-spec.org/acquisition" type="application/epub+zip" href='https://cdn.org/1.epub'/>
            <link rel="http://opds-spec.org/image/thumbnail" href="/c/1.jpg"/></entry>
          <entry><id>urn:nav</id><title>Sección</title><link rel="subsection" href="/opds/x" type="application/atom+xml"/></entry>
        </feed>"""
        val p = OpdsFeed.parse(xml, "https://a.org/opds/all", "feed")
        assertEquals(1, p.books.size)
        val b = p.books[0]
        assertEquals("feed:urn:1", b.id); assertEquals("Libro & uno", b.title); assertEquals("Ana", b.author); assertEquals("Ficción", b.genre)
        assertEquals("https://a.org/b/1.epub", b.epubDownloadUrl); assertEquals(listOf("https://cdn.org/1.epub"), b.mirrors)
        assertEquals("https://a.org/c/1.jpg", b.coverUrl)
        assertEquals("https://a.org/opds/all?page=2", p.next)
        assertNull(OpdsFeed.parse("<feed></feed>", "https://a.org/", "f").next)
    }

    @Test fun openLibraryKeepsPublicBooksWithArchiveCopies() {
        val dto = OpenLibraryResponseDto(2, listOf(
            OpenLibraryDocDto("/works/OL1W", "Quijote", listOf("Cervantes"), 9, listOf("q1", "q1", "q2"), "public", listOf("Novela")),
            OpenLibraryDocDto("/works/OL2W", "Prestado", ia = listOf("p"), ebookAccess = "borrowable")
        ))
        val l = OpenLibraryMapper.toBooks(dto, "ol", "")
        assertEquals(1, l.size)
        assertEquals("ol:/works/OL1W", l[0].id)
        assertEquals("https://archive.org/download/q1/q1.epub", l[0].epubDownloadUrl)
        assertEquals(listOf("https://archive.org/download/q2/q2.epub"), l[0].mirrors)
        assertEquals("https://covers.openlibrary.org/b/id/9-M.jpg", l[0].coverUrl)
        assertEquals("quijote ebook_access:public", OpenLibraryMapper.query("quijote", null))
        assertEquals("subject:\"adventure\" ebook_access:public", OpenLibraryMapper.query(null, "Adventure"))
    }
}
