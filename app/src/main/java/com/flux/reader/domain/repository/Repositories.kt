package com.flux.reader.domain.repository

import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookCollection
import com.flux.reader.domain.model.Bookmark
import com.flux.reader.domain.model.CatalogPage
import com.flux.reader.domain.model.DiscoverBook
import com.flux.reader.domain.model.ReadingSession
import com.flux.reader.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun observeLibrary(): Flow<List<Book>>
    fun observeBook(bookId: String): Flow<Book?>
    suspend fun upsertBook(book: Book)
    suspend fun deleteBook(bookId: String)
    suspend fun updateProgress(bookId: String, page: Int, totalPages: Int, cfi: String? = null)
    suspend fun toggleFavorite(bookId: String)
    suspend fun toggleNsfw(bookId: String)
    suspend fun renameBook(bookId: String, newTitle: String)

    fun observeBookmarks(bookId: String): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmarkId: String)

    fun observeCollections(): Flow<List<BookCollection>>
    suspend fun upsertCollection(collection: BookCollection)
    suspend fun deleteCollection(collectionId: String)

    fun observeSessions(): Flow<List<ReadingSession>>
    suspend fun recordSession(session: ReadingSession)
}

interface SettingsRepository {
    fun observeSettings(): Flow<UserSettings>
    suspend fun updateSettings(transform: (UserSettings) -> UserSettings)
}

interface DiscoverRepository {
    suspend fun searchCatalog(query: String, page: Int = 1): Result<CatalogPage>
    suspend fun getCuratedByGenre(genre: String, page: Int = 1): Result<CatalogPage>
    suspend fun importFromDiscover(discoverBook: DiscoverBook, onProgress: (Float) -> Unit = {}): Result<Book>
}
