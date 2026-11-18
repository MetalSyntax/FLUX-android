package com.flux.reader.domain.usecase

import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.Bookmark
import com.flux.reader.domain.model.CatalogPage
import com.flux.reader.domain.model.DiscoverBook
import com.flux.reader.domain.model.ReadingSession
import com.flux.reader.domain.model.ReadingStats
import com.flux.reader.domain.model.UserSettings
import com.flux.reader.domain.repository.BookRepository
import com.flux.reader.domain.repository.DiscoverRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

class UpdateReadingProgressUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke(bookId: String, page: Int, totalPages: Int, cfi: String? = null) {
        repository.updateProgress(bookId, page.coerceAtLeast(1), totalPages.coerceAtLeast(1), cfi)
    }
}

class ManageBookmarksUseCase @Inject constructor(
    private val repository: BookRepository
) {
    fun observeForBook(bookId: String): Flow<List<Bookmark>> = repository.observeBookmarks(bookId)
    suspend fun add(bookmark: Bookmark) = repository.addBookmark(bookmark)
    suspend fun remove(bookmarkId: String) = repository.deleteBookmark(bookmarkId)
}

class RecordReadingSessionUseCase @Inject constructor(
    private val repository: BookRepository
) {
    suspend operator fun invoke(session: ReadingSession) {
        val durationMs = session.endTimeEpochMs - session.startTimeEpochMs
        if (durationMs > 15_000L || session.pagesRead > 0) {
            repository.recordSession(session)
        }
    }
}

class SearchDiscoverBooksUseCase @Inject constructor(
    private val discoverRepository: DiscoverRepository
) {
    suspend fun browse(source: com.flux.reader.domain.model.BookSource, query: String?, genre: String?, page: Int = 1): Result<CatalogPage> =
        discoverRepository.browse(source, query, genre, page)

    suspend fun importToLibrary(discoverBook: DiscoverBook, onProgress: (Float) -> Unit = {}): Result<Book> =
        discoverRepository.importFromDiscover(discoverBook, onProgress)
}

class CalculateReadingStatsUseCase @Inject constructor() {

    operator fun invoke(
        books: List<Book>,
        sessions: List<ReadingSession>,
        settings: UserSettings,
        today: LocalDate = LocalDate.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReadingStats {
        val sessionDates = sessions
            .map { Instant.ofEpochMilli(it.startTimeEpochMs).atZone(zoneId).toLocalDate() }
            .toSet()

        val streak = calculateStreak(sessionDates, today)
        val todaySessions = sessions.filter {
            Instant.ofEpochMilli(it.startTimeEpochMs).atZone(zoneId).toLocalDate() == today
        }
        val weekThreshold = today.minusDays(6)
        val weekSessions = sessions.filter {
            !Instant.ofEpochMilli(it.startTimeEpochMs).atZone(zoneId).toLocalDate().isBefore(weekThreshold)
        }

        val todayPages = todaySessions.sumOf { it.pagesRead }
        val weekMinutes = weekSessions.sumOf { it.durationMinutes }
        val totalPages = sessions.sumOf { it.pagesRead }
        val byDay = sessions.groupBy { Instant.ofEpochMilli(it.startTimeEpochMs).atZone(zoneId).toLocalDate() }
        val weekByDay = (6 downTo 0).map { off -> byDay[today.minusDays(off.toLong())]?.sumOf { it.durationMinutes } ?: 0 }
        val goal = settings.dailyGoalPages.coerceAtLeast(1)

        return ReadingStats(
            totalBooks = books.size,
            completedBooks = books.count { it.isCompleted },
            favoriteBooks = books.count { it.isFavorite },
            totalPagesRead = totalPages,
            todayPagesRead = todayPages,
            weekMinutesRead = weekMinutes,
            currentStreakDays = streak,
            dailyGoalPages = goal,
            dailyGoalProgress = (todayPages.toFloat() / goal).coerceIn(0f, 1f),
            weekMinutesByDay = weekByDay,
            totalMinutesRead = sessions.sumOf { it.durationMinutes }
        )
    }

    private fun calculateStreak(sessionDates: Set<LocalDate>, today: LocalDate): Int {
        if (sessionDates.isEmpty()) return 0
        var streak = 0
        var cursor = if (today in sessionDates) today else today.minusDays(1)
        while (cursor in sessionDates) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
