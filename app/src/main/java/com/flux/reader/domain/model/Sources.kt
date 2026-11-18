package com.flux.reader.domain.model

import kotlinx.serialization.Serializable

/** Motores que la app sabe leer. Las fuentes son solo datos (tipo + URL); nunca código descargado. */
enum class SourceType(val key: String, val hasGenres: Boolean) {
    GUTENDEX("gutendex", true),
    OPEN_LIBRARY("openlibrary", true),
    OPDS("opds", false);

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } }
}

/**
 * Una fuente de libros. [url] es la base del motor: API de Gutendex, raíz de Open Library o feed OPDS.
 * [search] (solo OPDS) es una plantilla con {q}, p. ej. "https://ejemplo.org/opds/search?q={q}".
 */
@Serializable
data class BookSource(
    val id: String,
    val name: String,
    val type: String,
    val url: String,
    val search: String? = null,
    val lang: String? = null,
    val description: String? = null
) {
    val engine: SourceType? get() = SourceType.of(type)
}

/** Índice publicado en una URL (JSON): nombre del repositorio y las fuentes que ofrece. */
@Serializable
data class SourceRepo(val url: String = "", val name: String, val sources: List<BookSource> = emptyList())

@Serializable
data class SourcesState(val repos: List<SourceRepo> = emptyList(), val installed: List<BookSource> = emptyList())
