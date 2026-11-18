package com.flux.reader.data.remote

import com.flux.reader.domain.model.DiscoverBook
import java.io.File
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Descargas: URLs candidatas, en orden de preferencia. Openlib hace lo mismo (lista de mirrors, prueba uno a uno). */
object EpubMirrors {
    fun candidates(id: Int, listed: String?): List<String> = listOfNotNull(
        listed,
        "https://www.gutenberg.org/cache/epub/$id/pg$id-images-3.epub",
        "https://gutenberg.pglaf.org/cache/epub/$id/pg$id-images-3.epub",
        "https://gutenberg.pglaf.org/cache/epub/$id/pg$id.epub",
        "https://www.gutenberg.org/cache/epub/$id/pg$id.epub"
    ).distinct()

    /** Prueba cada URL hasta que una funcione; si todas fallan lanza la última causa con el resumen. */
    suspend fun <T> firstSuccess(urls: List<String>, attempt: suspend (String) -> T): T {
        var last: Throwable? = null
        for (u in urls) {
            try { return attempt(u) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Throwable) { last = e }
        }
        throw java.io.IOException("Ningún servidor respondió (${urls.size} intentos): ${last?.message}", last)
    }
}

/** Parser mínimo del feed OPDS de búsqueda de Gutenberg (regex: sin dependencias, probable en JVM puro). */
object OpdsParser {
    private val entryRx = Regex("<entry>(.*?)</entry>", RegexOption.DOT_MATCHES_ALL)
    private val idRx = Regex("<id>[^<]*?/ebooks/(\\d+)(?:\\.opds)?</id>")
    private val titleRx = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val authorRx = Regex("<content[^>]*>(.*?)</content>", RegexOption.DOT_MATCHES_ALL)

    fun parse(xml: String): List<DiscoverBook> = entryRx.findAll(xml).mapNotNull { m ->
        val e = m.groupValues[1]
        val id = idRx.find(e)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
        DiscoverBook(
            gutendexId = id,
            title = unescape(titleRx.find(e)?.groupValues?.get(1) ?: return@mapNotNull null),
            author = unescape(authorRx.find(e)?.groupValues?.get(1) ?: "Autor desconocido"),
            genre = "Clásicos",
            coverUrl = "https://www.gutenberg.org/cache/epub/$id/pg$id.cover.medium.jpg",
            epubDownloadUrl = "https://www.gutenberg.org/ebooks/$id.epub3.images",
            downloadCount = 0
        )
    }.toList()

    private fun unescape(s: String) = s.trim().replace("&amp;", "&").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
}

/** Caché de catálogo: memoria + un JSON por consulta en disco (TTL fresco; el vencido sirve si falla la red). */
class CatalogCache(private val dir: File, private val now: () -> Long = System::currentTimeMillis) {
    private val mem = HashMap<String, Pair<Long, List<DiscoverBook>>>()
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(DiscoverBook.serializer())

    private fun file(key: String) = File(dir, "cat_${key.hashCode().toUInt()}.json")

    /** Devuelve la lista y si sigue fresca. */
    @Synchronized
    fun get(key: String, ttlMs: Long = 6 * 3600_000L): Pair<List<DiscoverBook>, Boolean>? {
        val (t, l) = mem[key] ?: runCatching {
            val f = file(key)
            f.lastModified() to json.decodeFromString(ser, f.readText())
        }.getOrNull()?.also { mem[key] = it } ?: return null
        return l to (now() - t < ttlMs)
    }

    @Synchronized
    fun put(key: String, list: List<DiscoverBook>) {
        mem[key] = now() to list
        runCatching { dir.mkdirs(); file(key).writeText(json.encodeToString(ser, list)) }
        // ponytail: sin poda de disco; son ~20 KB por consulta, el SO limpia cacheDir.
    }
}
