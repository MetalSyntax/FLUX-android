package com.flux.reader.data.mapper

import com.flux.reader.data.local.entity.BookEntity
import com.flux.reader.data.local.entity.BookmarkEntity
import com.flux.reader.data.local.entity.CollectionEntity
import com.flux.reader.data.local.entity.ReadingSessionEntity
import com.flux.reader.data.remote.GutendexBookDto
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookCollection
import com.flux.reader.domain.model.BookType
import com.flux.reader.domain.model.Bookmark
import com.flux.reader.domain.model.DiscoverBook
import com.flux.reader.domain.model.ReadingSession

fun BookEntity.toDomain(): Book = Book(
    id = id,
    title = title,
    author = author,
    coverUri = coverUri,
    fileUri = fileUri,
    type = runCatching { BookType.valueOf(type) }.getOrDefault(BookType.EPUB),
    currentPage = currentPage,
    totalPages = totalPages,
    currentCfi = currentCfi,
    lastReadEpochMs = lastReadEpochMs,
    isFavorite = isFavorite,
    isNsfw = isNsfw,
    fileSizeBytes = fileSizeBytes,
    fileName = fileName,
    tags = tagsCsv.split("\u001f").filter { it.isNotBlank() }
)

fun Book.toEntity(): BookEntity = BookEntity(
    id = id,
    title = title,
    author = author,
    coverUri = coverUri,
    fileUri = fileUri,
    type = type.name,
    currentPage = currentPage,
    totalPages = totalPages,
    currentCfi = currentCfi,
    lastReadEpochMs = lastReadEpochMs,
    isFavorite = isFavorite,
    isNsfw = isNsfw,
    fileSizeBytes = fileSizeBytes,
    fileName = fileName,
    tagsCsv = tags.joinToString("\u001f")
)

fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id,
    bookId = bookId,
    page = page,
    cfi = cfi,
    label = label,
    colorHex = colorHex,
    createdAtEpochMs = createdAtEpochMs
)

fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
    id = id,
    bookId = bookId,
    page = page,
    cfi = cfi,
    label = label,
    colorHex = colorHex,
    createdAtEpochMs = createdAtEpochMs
)

fun CollectionEntity.toDomain(): BookCollection = BookCollection(
    id = id,
    name = name,
    colorHex = colorHex,
    bookIds = bookIdsCsv.split(",").filter { it.isNotBlank() }.toSet()
)

fun BookCollection.toEntity(): CollectionEntity = CollectionEntity(
    id = id,
    name = name,
    colorHex = colorHex,
    bookIdsCsv = bookIds.joinToString(",")
)

fun ReadingSessionEntity.toDomain(): ReadingSession = ReadingSession(
    id = id,
    bookId = bookId,
    startTimeEpochMs = startTimeEpochMs,
    endTimeEpochMs = endTimeEpochMs,
    pagesRead = pagesRead
)

fun ReadingSession.toEntity(): ReadingSessionEntity = ReadingSessionEntity(
    id = id,
    bookId = bookId,
    startTimeEpochMs = startTimeEpochMs,
    endTimeEpochMs = endTimeEpochMs,
    pagesRead = pagesRead
)

fun GutendexBookDto.toDomain(fallbackGenre: String = "Clásicos", sourceId: String = "gutenberg"): DiscoverBook = DiscoverBook(
    id = "$sourceId:$id",
    title = title,
    author = authors.firstOrNull()?.name ?: "Autor desconocido",
    genre = subjects.firstOrNull()?.substringBefore(" -- ") ?: fallbackGenre,
    coverUrl = formats["image/jpeg"],
    epubDownloadUrl = formats["application/epub+zip"],
    downloadCount = downloadCount,
    mirrors = com.flux.reader.data.remote.EpubMirrors.candidates(id, null)
)
