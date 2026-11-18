package com.flux.reader.data.files

import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.net.URLDecoder
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Lectura mínima de un EPUB (container.xml -> OPF -> spine) solo con java.util.zip + DOM. */
class EpubPackage(file: File) : Closeable {
    private val zip = ZipFile(file)
    var title: String = ""; private set
    var author: String = ""; private set
    var coverPath: String? = null; private set
    var spine: List<String> = emptyList(); private set

    init {
        val container = parse(zip.getEntry("META-INF/container.xml")?.let { zip.getInputStream(it) }
            ?: error("EPUB sin container.xml"))
        val opfPath = (container.getElementsByTagName("rootfile").item(0) as Element).getAttribute("full-path")
        val opf = parse(zip.getInputStream(zip.getEntry(opfPath) ?: error("EPUB sin OPF")))
        val base = opfPath.substringBeforeLast('/', "")

        title = opf.getElementsByTagName("dc:title").item(0)?.textContent?.trim().orEmpty()
        author = opf.getElementsByTagName("dc:creator").item(0)?.textContent?.trim().orEmpty()

        class Item(val href: String, val type: String, val props: String)
        val items = HashMap<String, Item>()
        val manifestNodes = opf.getElementsByTagName("item")
        for (i in 0 until manifestNodes.length) {
            val e = manifestNodes.item(i) as Element
            items[e.getAttribute("id")] = Item(resolve(base, e.getAttribute("href")), e.getAttribute("media-type"), e.getAttribute("properties"))
        }
        val refs = opf.getElementsByTagName("itemref")
        spine = (0 until refs.length).mapNotNull { items[(refs.item(it) as Element).getAttribute("idref")] }
            .filter { it.type.contains("html") || it.type.contains("xml") }.map { it.href }

        // portada: <meta name="cover" content="id"> o propiedad cover-image
        val metas = opf.getElementsByTagName("meta")
        var coverId: String? = null
        for (i in 0 until metas.length) {
            val e = metas.item(i) as Element
            if (e.getAttribute("name") == "cover") coverId = e.getAttribute("content")
        }
        coverPath = coverId?.let { items[it]?.href }
            ?: items.values.firstOrNull { it.props.contains("cover-image") }?.href
            ?: items.values.firstOrNull { it.type.startsWith("image/") && it.href.contains("cover", true) }?.href
    }

    fun open(path: String): InputStream? = zip.getEntry(path)?.let { zip.getInputStream(it) }
    override fun close() = zip.close()

    private fun parse(input: InputStream) = input.use {
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            // los EPUB traen DOCTYPE; se evita resolver DTD externas (sin red, sin XXE)
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        }.newDocumentBuilder().apply { setEntityResolver { _, _ -> org.xml.sax.InputSource(java.io.StringReader("")) } }.parse(it)
    }

    companion object {
        /** Resuelve href relativo (con %xx y "..") contra un directorio dentro del zip. */
        fun resolve(baseDir: String, href: String): String {
            val clean = URLDecoder.decode(href.replace("+", "%2B"), "UTF-8").substringBefore('#')
            val parts = ArrayList<String>()
            val all = if (clean.startsWith("/")) clean.removePrefix("/") else if (baseDir.isEmpty()) clean else "$baseDir/$clean"
            for (p in all.split('/')) when (p) { "", "." -> {}; ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1); else -> parts.add(p) }
            return parts.joinToString("/")
        }
    }
}
