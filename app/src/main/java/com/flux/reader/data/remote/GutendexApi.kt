package com.flux.reader.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class GutendexResponseDto(
    val count: Int = 0,
    val next: String? = null,
    val results: List<GutendexBookDto> = emptyList()
)

@Serializable
data class GutendexBookDto(
    val id: Int,
    val title: String,
    val authors: List<GutendexAuthorDto> = emptyList(),
    val subjects: List<String> = emptyList(),
    val formats: Map<String, String> = emptyMap(),
    @SerialName("download_count") val downloadCount: Int = 0
)

@Serializable
data class GutendexAuthorDto(
    val name: String
)

@Singleton
class GutendexApi @Inject constructor(
    private val client: HttpClient
) {
    // Sin mime_type: en gutendex.com ese filtro (y consultas no cacheadas) cuelga >45 s; se filtra EPUB en cliente.
    suspend fun searchBooks(baseUrl: String, query: String, page: Int = 1): GutendexResponseDto = fetch(baseUrl, "search" to query, page)

    suspend fun getBooksByTopic(baseUrl: String, topic: String, page: Int = 1): GutendexResponseDto = fetch(baseUrl, "topic" to topic.lowercase(), page)

    private suspend fun fetch(baseUrl: String, p: Pair<String, String>, page: Int): GutendexResponseDto =
        client.get(baseUrl) {
            parameter(p.first, p.second)
            if (page > 1) parameter("page", page)
            timeout { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 10_000 }
        }.body()

    /** Fallback: feed OPDS oficial de Gutenberg (una sola página). */
    suspend fun opdsSearch(query: String): String =
        client.get("https://www.gutenberg.org/ebooks/search.opds/") {
            parameter("query", query)
            timeout { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000; socketTimeoutMillis = 10_000 }
        }.also { check(it.status.value in 200..299) { "OPDS ${it.status.value}" } }.bodyAsText()
}
