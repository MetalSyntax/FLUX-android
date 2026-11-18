package com.flux.reader.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val coverUri: String,
    val fileUri: String,
    val type: String,
    val currentPage: Int,
    val totalPages: Int,
    val currentCfi: String?,
    val lastReadEpochMs: Long,
    val isFavorite: Boolean,
    val isNsfw: Boolean,
    val fileSizeBytes: Long,
    val fileName: String = "",
    val tagsCsv: String = ""
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val page: Int,
    val cfi: String?,
    val label: String,
    val colorHex: Long,
    val createdAtEpochMs: Long
)

@Entity(tableName = "reading_sessions")
data class ReadingSessionEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val pagesRead: Int
)

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long,
    val bookIdsCsv: String
)
