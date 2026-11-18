package com.flux.reader.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class TextToEpubTest {
    @Test fun txtSplitsChaptersAndDecodesLatin1() {
        val text = "Prólogo\n\nHabía una vez.\n\nCapítulo 1\n\nUno <dos> & tres.\nSigue.\n\nCAPÍTULO 2: Fin\n\nAdiós."
        val bytes = text.toByteArray(charset("windows-1252"))
        val doc = TextToEpub.fromTxt(TextToEpub.decode(bytes), "Libro")
        assertEquals(listOf("Prólogo", "Capítulo 1", "CAPÍTULO 2: Fin"), doc.chapters.map { it.title })
        assertTrue(doc.chapters[1].bodyHtml.contains("<p>Uno &lt;dos&gt; &amp; tres.<br/>Sigue.</p>"))
    }

    @Test fun utf8WithBomIsDecoded() {
        assertEquals("ñandú", TextToEpub.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "ñandú".toByteArray()))
    }

    @Test fun markdownBasics() {
        val doc = TextToEpub.fromMarkdown("# Mi libro\nIntro **fuerte** y *suave* y `x<y`.\n\n## Parte dos\n- a\n- b\n\n```\n<raw>\n```\n> cita", "f")
        assertEquals("Mi libro", doc.title)
        assertEquals(listOf("Mi libro", "Parte dos"), doc.chapters.map { it.title })
        assertTrue(doc.chapters[0].bodyHtml.contains("<strong>fuerte</strong> y <em>suave</em> y <code>x&lt;y</code>"))
        val two = doc.chapters[1].bodyHtml
        assertTrue(two.contains("<ul>\n<li>a</li>\n<li>b</li>\n</ul>"))
        assertTrue(two.contains("<pre><code>&lt;raw&gt;\n</code></pre>"))
        assertTrue(two.contains("<blockquote><p>cita</p></blockquote>"))
    }

    @Test fun htmlIsSanitized() {
        val doc = TextToEpub.fromHtml("<html><head><title>T</title><script>evil()</script></head><body onload=\"x()\"><p onclick='y()'>Hola</p><script src=a.js></script><a href=\"javascript:z()\">l</a><iframe src=x></iframe></body></html>", "f")
        val b = doc.chapters[0].bodyHtml
        assertEquals("T", doc.title)
        assertFalse(b.contains("script") || b.contains("onclick") || b.contains("javascript:") || b.contains("iframe"))
        assertTrue(b.contains("<p>Hola</p>"))
    }

    @Test fun fb2WithCoverRoundTripsThroughEpubPackage() {
        val png = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        val fb2 = """<?xml version="1.0" encoding="UTF-8"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
<description><title-info><author><first-name>Ana</first-name><last-name>Pérez</last-name></author>
<book-title>Cuentos</book-title><coverpage><image l:href="#c.png"/></coverpage></title-info></description>
<body><section><title><p>Uno</p></title><p>Texto <emphasis>suave</emphasis> &amp; más.</p><empty-line/><poem><stanza><v>Verso</v></stanza></poem></section>
<section><title><p>Dos</p></title><p>Fin.</p></section></body>
<body name="notes"><section><p>nota</p></section></body>
<binary id="c.png" content-type="image/png">$png</binary></FictionBook>"""
        val dir = createTempDir()
        val src = File(dir, "cuentos.fb2").apply { writeText(fb2) }
        val out = File(dir, "out.epub")
        TextToEpub.convert(src, src.name, out)
        EpubPackage(out).use { e ->
            assertEquals("Cuentos", e.title); assertEquals("Ana Pérez", e.author)
            assertEquals(2, e.spine.size)
            val c1 = e.open(e.spine[0])!!.bufferedReader().readText()
            assertTrue(c1.contains("<em>suave</em> &amp; más.") && c1.contains("<p>Verso</p>") && !c1.contains("nota"))
            assertNotNull(e.coverPath); assertEquals(listOf<Byte>(1, 2, 3, 4), e.open(e.coverPath!!)!!.readBytes().toList())
        }
        dir.deleteRecursively()
    }

    @Test fun emptyTextIsRejected() {
        val dir = createTempDir()
        try { TextToEpub.convert(File(dir, "v.txt").apply { writeText("  \n\n ") }, "v.txt", File(dir, "o.epub")); throw AssertionError() }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("no tiene texto")) }
        dir.deleteRecursively()
    }
}
