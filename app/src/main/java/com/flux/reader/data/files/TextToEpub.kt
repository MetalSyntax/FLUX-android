package com.flux.reader.data.files

import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Convierte TXT, Markdown, HTML y FB2 a un EPUB mínimo al importar: así el lector EPUB, el progreso
 * y la lectura en voz alta funcionan igual para todos los formatos.
 */
object TextToEpub {
    val EXTENSIONS = setOf("txt", "md", "markdown", "html", "htm", "xhtml", "fb2")

    data class Chapter(val title: String, val bodyHtml: String)
    data class Doc(val title: String, val author: String, val chapters: List<Chapter>, val cover: Pair<String, ByteArray>? = null)

    /** Convierte [src] (formato por la extensión de [name]) y escribe el EPUB en [dest]. */
    fun convert(src: File, name: String, dest: File) {
        val ext = name.substringAfterLast('.', "").lowercase()
        val base = name.substringBeforeLast('.')
        val doc = when (ext) {
            "txt" -> fromTxt(decode(src.readBytes()), base)
            "md", "markdown" -> fromMarkdown(decode(src.readBytes()), base)
            "html", "htm", "xhtml" -> fromHtml(decode(src.readBytes()), base)
            "fb2" -> fromFb2(src.readBytes(), base)
            else -> throw IllegalArgumentException("Formato no soportado: .$ext")
        }
        require(doc.chapters.any { it.bodyHtml.isNotBlank() }) { "El archivo no tiene texto" }
        write(doc, dest)
    }

    /** UTF-8 (con o sin BOM); si no es válido, Windows-1252 (TXT viejos en español). */
    fun decode(bytes: ByteArray): String {
        val b = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) bytes.copyOfRange(3, bytes.size) else bytes
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString()
        } catch (_: CharacterCodingException) {
            String(b, Charset.forName("windows-1252"))
        }
    }

    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private val chapterRx = Regex("^\\s*(cap[ií]tulo|chapter|parte|part|libro|book|pr[oó]logo|prologue|ep[ií]logo|epilogue)\\b.{0,80}$", RegexOption.IGNORE_CASE)
    private const val CHUNK = 150 // párrafos por sección si el texto no tiene capítulos (WebView lento con un solo bloque enorme)

    fun fromTxt(text: String, fallbackTitle: String): Doc {
        val paras = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
        val chapters = ArrayList<Chapter>()
        var title = ""; val body = StringBuilder(); var count = 0
        fun flush() { if (body.isNotEmpty()) chapters += Chapter(title.ifBlank { "Sección ${chapters.size + 1}" }, body.toString()); body.clear(); count = 0; title = "" }
        for (p in paras) {
            if (!p.contains('\n') && chapterRx.matches(p)) { flush(); title = p; body.append("<h2>").append(esc(p)).append("</h2>\n"); continue }
            if (count >= CHUNK) flush()
            body.append("<p>").append(esc(p).replace("\n", "<br/>")).append("</p>\n"); count++
        }
        flush()
        return Doc(fallbackTitle, "", chapters)
    }

    private fun inlineMd(s: String): String = esc(s)
        .replace(Regex("`([^`]+)`"), "<code>$1</code>")
        .replace(Regex("\\*\\*([^*]+)\\*\\*|__([^_]+)__")) { "<strong>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</strong>" }
        .replace(Regex("(?<![*\\w])\\*([^*]+)\\*(?!\\*)|(?<![_\\w])_([^_]+)_(?!\\w)")) { "<em>${it.groupValues[1].ifEmpty { it.groupValues[2] }}</em>" }
        .replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
        .replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")

    /** Markdown básico: títulos (# y ##+ abren sección), párrafos, listas, citas, código, negrita/cursiva. */
    fun fromMarkdown(text: String, fallbackTitle: String): Doc {
        val chapters = ArrayList<Chapter>()
        var title = ""; var docTitle = ""
        val body = StringBuilder(); val para = ArrayList<String>()
        var inCode = false; var list: String? = null
        fun endPara() { if (para.isNotEmpty()) { body.append("<p>").append(inlineMd(para.joinToString(" "))).append("</p>\n"); para.clear() } }
        fun endList() { list?.let { body.append("</$it>\n") }; list = null }
        fun flush() { endPara(); endList(); if (body.isNotBlank()) chapters += Chapter(title.ifBlank { "Sección ${chapters.size + 1}" }, body.toString()); body.clear(); title = "" }
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw.trimEnd()
            if (line.trimStart().startsWith("```")) {
                endPara(); endList(); body.append(if (inCode) "</code></pre>\n" else "<pre><code>"); inCode = !inCode; continue
            }
            if (inCode) { body.append(esc(raw)).append('\n'); continue }
            val h = Regex("^(#{1,6})\\s+(.*?)\\s*#*$").find(line)
            val ul = Regex("^\\s*[-*+]\\s+(.*)").find(line)
            val ol = Regex("^\\s*\\d+[.)]\\s+(.*)").find(line)
            when {
                h != null -> {
                    val level = h.groupValues[1].length; val t = h.groupValues[2]
                    if (level <= 2) { flush(); title = t; if (docTitle.isBlank() && level == 1) docTitle = t } else { endPara(); endList() }
                    body.append("<h${level + 1}>").append(inlineMd(t)).append("</h${level + 1}>\n")
                }
                line.isBlank() -> { endPara(); endList() }
                Regex("^\\s*(-{3,}|\\*{3,}|_{3,})\\s*$").matches(line) -> { endPara(); endList(); body.append("<hr/>\n") }
                ul != null || ol != null -> {
                    endPara(); val tag = if (ul != null) "ul" else "ol"
                    if (list != tag) { endList(); body.append("<$tag>\n"); list = tag }
                    body.append("<li>").append(inlineMd((ul ?: ol)!!.groupValues[1])).append("</li>\n")
                }
                line.trimStart().startsWith(">") -> { endPara(); endList(); body.append("<blockquote><p>").append(inlineMd(line.trimStart().removePrefix(">").trim())).append("</p></blockquote>\n") }
                else -> { endList(); para += line.trim() }
            }
        }
        if (inCode) body.append("</code></pre>\n")
        flush()
        return Doc(docTitle.ifBlank { fallbackTitle }, "", chapters)
    }

    /** HTML: se queda con el <body>, sin scripts, estilos, iframes ni manejadores on*. */
    fun fromHtml(html: String, fallbackTitle: String): Doc {
        val title = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)?.trim()
        val body = Regex("<body[^>]*>(.*)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1) ?: html
        return Doc(title?.takeIf { it.isNotBlank() } ?: fallbackTitle, "", listOf(Chapter(title ?: fallbackTitle, sanitizeHtml(body))))
    }

    fun sanitizeHtml(s: String): String = s
        .replace(Regex("<(script|style|iframe|object|embed|form)\\b[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
        .replace(Regex("<(script|iframe|object|embed|link|meta|base)\\b[^>]*/?>", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+on\\w+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("(href|src)\\s*=\\s*([\"'])\\s*javascript:[^\"']*\\2", RegexOption.IGNORE_CASE), "$1=$2#$2")

    /** FB2 (FictionBook): título, autor, portada y una sección por cada <section> de primer nivel. */
    fun fromFb2(bytes: ByteArray, fallbackTitle: String): Doc {
        val dom = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            isExpandEntityReferences = false
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
        fun first(parent: Element, tag: String): Element? = parent.getElementsByTagName(tag).item(0) as? Element
        val info = first(dom.documentElement, "title-info")
        val title = info?.let { first(it, "book-title") }?.textContent?.trim().orEmpty()
        val author = info?.let { first(it, "author") }?.let { a ->
            listOf("first-name", "middle-name", "last-name").mapNotNull { first(a, it)?.textContent?.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
                .ifBlank { first(a, "nickname")?.textContent?.trim().orEmpty() }
        }.orEmpty()

        val binaries = HashMap<String, Pair<String, ByteArray>>()
        val bins = dom.getElementsByTagName("binary")
        for (i in 0 until bins.length) {
            val b = bins.item(i) as Element
            runCatching { binaries[b.getAttribute("id")] = b.getAttribute("content-type") to Base64.getMimeDecoder().decode(b.textContent.trim()) }
        }
        fun href(e: Element) = (e.getAttribute("l:href").ifEmpty { e.getAttribute("xlink:href") }.ifEmpty { e.getAttribute("href") }).removePrefix("#")
        val cover = info?.let { first(it, "coverpage") }?.let { first(it, "image") }?.let { binaries[href(it)] }

        fun inline(n: Node): String = when (n.nodeType) {
            Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> esc(n.textContent)
            Node.ELEMENT_NODE -> {
                val kids = (0 until n.childNodes.length).joinToString("") { inline(n.childNodes.item(it)) }
                when (n.nodeName) {
                    "emphasis" -> "<em>$kids</em>"; "strong" -> "<strong>$kids</strong>"
                    "strikethrough" -> "<s>$kids</s>"; "sup" -> "<sup>$kids</sup>"; "sub" -> "<sub>$kids</sub>"; "code" -> "<code>$kids</code>"
                    else -> kids
                }
            }
            else -> ""
        }
        fun block(e: Element, depth: Int, out: StringBuilder) {
            for (i in 0 until e.childNodes.length) {
                val c = e.childNodes.item(i) as? Element ?: continue
                when (c.nodeName) {
                    "title" -> { val h = (depth + 1).coerceAtMost(6); out.append("<h$h>"); (0 until c.childNodes.length).mapNotNull { c.childNodes.item(it) as? Element }.joinTo(out, "<br/>") { inline(it) }; out.append("</h$h>\n") }
                    "p", "v", "subtitle", "text-author" -> out.append(if (c.nodeName == "subtitle") "<h${(depth + 2).coerceAtMost(6)}>" else "<p>").append(inline(c)).append(if (c.nodeName == "subtitle") "</h${(depth + 2).coerceAtMost(6)}>\n" else "</p>\n")
                    "empty-line" -> out.append("<br/>\n")
                    "epigraph", "cite" -> { out.append("<blockquote>"); block(c, depth, out); out.append("</blockquote>\n") }
                    "poem", "stanza", "section" -> block(c, depth + 1, out)
                    else -> {}
                }
            }
        }
        val chapters = ArrayList<Chapter>()
        val bodies = dom.getElementsByTagName("body")
        for (i in 0 until bodies.length) {
            val body = bodies.item(i) as Element
            if (body.getAttribute("name") == "notes") continue
            val sections = (0 until body.childNodes.length).mapNotNull { body.childNodes.item(it) as? Element }.filter { it.nodeName == "section" }
            if (sections.isEmpty()) { val sb = StringBuilder(); block(body, 0, sb); chapters += Chapter(title.ifBlank { fallbackTitle }, sb.toString()) }
            for (s in sections) {
                val sb = StringBuilder(); block(s, 0, sb)
                val t = first(s, "title")?.textContent?.trim()?.replace(Regex("\\s+"), " ").orEmpty()
                chapters += Chapter(t.ifBlank { "Sección ${chapters.size + 1}" }, sb.toString())
            }
        }
        return Doc(title.ifBlank { fallbackTitle }, author, chapters, cover)
    }

    /** EPUB 2 mínimo: mimetype sin comprimir primero, container, OPF, NCX y un XHTML por capítulo. */
    fun write(doc: Doc, dest: File) {
        ZipOutputStream(dest.outputStream().buffered()).use { z ->
            val mime = "application/epub+zip".toByteArray()
            z.putNextEntry(ZipEntry("mimetype").apply { method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size; crc = CRC32().apply { update(mime) }.value })
            z.write(mime); z.closeEntry()
            fun put(path: String, text: String) { z.putNextEntry(ZipEntry(path)); z.write(text.toByteArray()); z.closeEntry() }
            put("META-INF/container.xml", """<?xml version="1.0" encoding="UTF-8"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            val chapters = doc.chapters.filter { it.bodyHtml.isNotBlank() }
            chapters.forEachIndexed { i, c ->
                put("OEBPS/c${i + 1}.xhtml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/><title>${esc(c.title)}</title></head><body>\n${c.bodyHtml}</body></html>")
            }
            val coverExt = doc.cover?.first?.substringAfter('/')?.replace("jpeg", "jpg")?.takeIf { it.matches(Regex("[a-z]{3,4}")) } ?: "jpg"
            doc.cover?.let { (_, bytes) -> z.putNextEntry(ZipEntry("OEBPS/cover.$coverExt")); z.write(bytes); z.closeEntry() }
            val manifest = StringBuilder()
            chapters.indices.forEach { manifest.append("<item id=\"c${it + 1}\" href=\"c${it + 1}.xhtml\" media-type=\"application/xhtml+xml\"/>") }
            doc.cover?.let { manifest.append("<item id=\"cover\" href=\"cover.$coverExt\" media-type=\"${esc(it.first.ifBlank { "image/jpeg" })}\"/>") }
            manifest.append("<item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>")
            val spine = chapters.indices.joinToString("") { "<itemref idref=\"c${it + 1}\"/>" }
            put("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:flux:${dest.nameWithoutExtension}</dc:identifier><dc:title>${esc(doc.title)}</dc:title>${if (doc.author.isNotBlank()) "<dc:creator>${esc(doc.author)}</dc:creator>" else ""}<dc:language>und</dc:language>${if (doc.cover != null) "<meta name=\"cover\" content=\"cover\"/>" else ""}</metadata><manifest>$manifest</manifest><spine toc="ncx">$spine</spine></package>""")
            val nav = chapters.mapIndexed { i, c -> "<navPoint id=\"n${i + 1}\" playOrder=\"${i + 1}\"><navLabel><text>${esc(c.title)}</text></navLabel><content src=\"c${i + 1}.xhtml\"/></navPoint>" }.joinToString("")
            put("OEBPS/toc.ncx", """<?xml version="1.0" encoding="UTF-8"?><ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1"><head><meta name="dtb:uid" content="urn:flux:${dest.nameWithoutExtension}"/></head><docTitle><text>${esc(doc.title)}</text></docTitle><navMap>$nav</navMap></ncx>""")
        }
    }
}
