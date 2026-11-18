package com.flux.reader.domain.usecase

import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookCollection
import com.flux.reader.domain.model.BookType
import com.flux.reader.domain.model.LibraryFilter
import com.flux.reader.domain.model.LibrarySort
import com.flux.reader.domain.repository.BookRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLibraryBooksUseCase @Inject constructor(
    private val repository: BookRepository
) {
    operator fun invoke(): Flow<List<Book>> = repository.observeLibrary()
}

class FilterAndSortBooksUseCase @Inject constructor() {
    operator fun invoke(
        books: List<Book>,
        query: String,
        filter: LibraryFilter,
        sort: LibrarySort,
        activeCollection: BookCollection?
    ): List<Book> {
        val normalizedQuery = query.trim().lowercase()
        return books
            .asSequence()
            .filter { book ->
                if (activeCollection != null) {
                    book.id in activeCollection.bookIds
                } else {
                    true
                }
            }
            .filter { book ->
                if (normalizedQuery.isEmpty()) true
                else book.title.lowercase().contains(normalizedQuery) ||
                    book.author.lowercase().contains(normalizedQuery)
            }
            .filter { book ->
                when (filter) {
                    LibraryFilter.ALL -> true
                    LibraryFilter.FAVORITES -> book.isFavorite
                    LibraryFilter.MANGA -> book.type == BookType.CBR
                    LibraryFilter.EPUB -> book.type == BookType.EPUB
                    LibraryFilter.PDF -> book.type == BookType.PDF
                }
            }
            .sortedWith(
                when (sort) {
                    LibrarySort.RECENT -> compareByDescending<Book> { it.lastReadEpochMs }
                    LibrarySort.TITLE -> compareBy<Book> { it.title.lowercase() }
                    LibrarySort.AUTHOR -> compareBy<Book> { it.author.lowercase() }
                }
            )
            .toList()
    }
}

class ToggleFavoriteBookUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke(bookId: String) = repository.toggleFavorite(bookId)
}

class RenameBookUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke(bookId: String, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty()) {
            repository.renameBook(bookId, trimmed)
        }
    }
}

class DeleteBookUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke(bookId: String) = repository.deleteBook(bookId)
}

class ManageCollectionsUseCase @Inject constructor(
    private val repository: BookRepository
) {
    fun observeAll(): Flow<List<BookCollection>> = repository.observeCollections()

    suspend fun createCollection(id: String, name: String, colorHex: Long) {
        if (name.isBlank()) return
        repository.upsertCollection(
            BookCollection(
                id = id,
                name = name.trim(),
                colorHex = colorHex,
                bookIds = emptySet()
            )
        )
    }

    suspend fun addBookToCollection(collection: BookCollection, bookId: String) {
        if (bookId in collection.bookIds) return
        repository.upsertCollection(
            collection.copy(bookIds = collection.bookIds + bookId)
        )
    }

    suspend fun removeBook(collection: BookCollection, bookId: String) =
        repository.upsertCollection(collection.copy(bookIds = collection.bookIds - bookId))

    suspend fun deleteCollection(collectionId: String) =
        repository.deleteCollection(collectionId)
}
