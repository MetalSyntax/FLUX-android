package com.flux.reader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flux.reader.data.local.entity.BookEntity
import com.flux.reader.data.local.entity.BookmarkEntity
import com.flux.reader.data.local.entity.CollectionEntity
import com.flux.reader.data.local.entity.ReadingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FluxDao {

    @Query("SELECT * FROM books ORDER BY lastReadEpochMs DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    fun observeBook(bookId: String): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBook(bookId: String)

    @Query("UPDATE books SET currentPage = :page, totalPages = :totalPages, currentCfi = :cfi, lastReadEpochMs = :timestamp WHERE id = :bookId")
    suspend fun updateProgress(bookId: String, page: Int, totalPages: Int, cfi: String?, timestamp: Long)

    @Query("UPDATE books SET title = :newTitle WHERE id = :bookId")
    suspend fun renameBook(bookId: String, newTitle: String)

    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAtEpochMs DESC")
    fun observeBookmarks(bookId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmark(bookmarkId: String)

    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun observeCollections(): Flow<List<CollectionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: String)

    @Query("SELECT * FROM reading_sessions ORDER BY startTimeEpochMs DESC")
    fun observeSessions(): Flow<List<ReadingSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadingSessionEntity)

    // Lecturas puntuales (exportar / importar copia de seguridad)
    @Query("SELECT * FROM books")
    suspend fun allBooks(): List<BookEntity>

    @Query("SELECT * FROM bookmarks")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Query("SELECT * FROM collections")
    suspend fun allCollections(): List<CollectionEntity>

    @Query("SELECT * FROM reading_sessions")
    suspend fun allSessions(): List<ReadingSessionEntity>

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteBookmarksOf(bookId: String)
}
