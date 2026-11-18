package com.flux.reader.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Formato de copia de seguridad compartido con la web (ver MIGRACION.md). Todo opcional salvo ids. */
const val BACKUP_FORMAT = "flux-backup"
const val BACKUP_VERSION = 1

@Serializable
data class BackupManifest(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    val exportedAt: Long = 0L,
    val source: String = "android",
    val settings: BackupSettings? = null,
    val metadata: JsonObject? = null,
    val books: List<BackupBook> = emptyList(),
    val bookmarks: List<BackupBookmark> = emptyList(),
    val sessions: List<BackupSession> = emptyList(),
    val collections: List<BackupCollection> = emptyList()
)

@Serializable
data class BackupSettings(
    val name: String? = null,
    val avatar: String? = null,
    val theme: String? = null,       // dark | black | white
    val dailyGoal: Int? = null,
    val language: String? = null,    // en | es | pt
    val showNsfw: Boolean? = null
)

@Serializable
data class BackupBook(
    val id: String,
    val title: String = "",
    val author: String = "",
    val type: String = "PDF",        // EPUB | PDF | CBR (CBR incluye CBZ)
    val progress: Int = 0,
    val lastRead: String? = null,
    val lastReadDate: Long = 0L,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val currentCfi: String? = null,
    val isFavorite: Boolean = false,
    val tags: List<String> = emptyList(),  // "NSFW" marca contenido sensible
    val fileName: String? = null,
    val file: String? = null,        // ruta en el zip: books/<id>.<ext>
    val cover: String? = null        // ruta en el zip: covers/<id>.<ext>
)

@Serializable
data class BackupBookmark(
    val id: String,
    val bookId: String,
    val page: Int = 1,
    val cfi: String? = null,
    val label: String = "",
    val color: String = "",
    val createdAt: Long = 0L
)

@Serializable
data class BackupSession(
    val id: String,
    val bookId: String,
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val pagesRead: Int = 0
)

@Serializable
data class BackupCollection(
    val id: String,
    val name: String = "",
    val color: String = "",
    val bookIds: List<String> = emptyList()
)
