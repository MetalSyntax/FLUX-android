package com.flux.reader.domain.model

enum class BookType {
    EPUB,
    PDF,
    CBR
}

enum class AppThemeMode {
    DARK,
    AMOLED_BLACK,
    LIGHT
}

enum class AppLanguage(val code: String, val label: String) {
    ES("es", "Español"),
    EN("en", "English"),
    PT("pt", "Português")
}

enum class ReaderTheme(val label: String, val backgroundHex: Long, val textHex: Long) {
    LIGHT("Claro", 0xFFFFFFFF, 0xFF1A1A1A),
    SEPIA("Sepia", 0xFFF5ECCD, 0xFF433422),
    MINT("Menta", 0xFFF0F7F4, 0xFF2D4A3E),
    DARK("Oscuro", 0xFF0F172A, 0xFFCBD5E1)
}

enum class ReaderFont(val label: String) {
    SYSTEM_SANS("System Sans"),
    SERIF("Editorial Serif"),
    OUTFIT("Dyslexic / Outfit"),
    MONOSPACE("Monospace")
}

enum class LibraryFilter {
    ALL,
    FAVORITES,
    MANGA,
    EPUB,
    PDF
}

enum class LibrarySort {
    RECENT,
    TITLE,
    AUTHOR
}

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverUri: String,
    val fileUri: String,
    val type: BookType,
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val currentCfi: String? = null,
    val lastReadEpochMs: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isNsfw: Boolean = false,
    val fileSizeBytes: Long = 0L,
    val fileName: String = "",
    val tags: List<String> = emptyList()
) {
    val progressPercent: Int
        get() = if (totalPages <= 1) 0 else ((currentPage.toFloat() / totalPages) * 100f)
            .toInt()
            .coerceIn(0, 100)

    val isCompleted: Boolean
        get() = progressPercent >= 100
}

data class Bookmark(
    val id: String,
    val bookId: String,
    val page: Int,
    val cfi: String? = null,
    val label: String,
    val colorHex: Long,
    val createdAtEpochMs: Long
)

data class ReadingSession(
    val id: String,
    val bookId: String,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val pagesRead: Int
) {
    val durationMinutes: Int
        get() = ((endTimeEpochMs - startTimeEpochMs) / 60_000L).toInt().coerceAtLeast(0)
}

data class BookCollection(
    val id: String,
    val name: String,
    val colorHex: Long,
    val bookIds: Set<String>
)

data class UserSettings(
    val name: String = "Lector FLUX",
    val avatarUri: String? = null,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val dynamicColorEnabled: Boolean = false,
    /** Acento ARGB (por defecto el aqua #00C08B de la web). */
    val accentColor: Long = 0xFF00C08B,
    val dailyGoalPages: Int = 20,
    val language: AppLanguage = AppLanguage.ES,
    val showNsfwCovers: Boolean = false,
    val readerTheme: ReaderTheme = ReaderTheme.DARK,
    val readerFont: ReaderFont = ReaderFont.SERIF,
    val readerFontScale: Float = 1.0f,
    val librarySort: LibrarySort = LibrarySort.RECENT,
    val libraryGrid: Boolean = true
)

@kotlinx.serialization.Serializable
data class DiscoverBook(
    val gutendexId: Int,
    val title: String,
    val author: String,
    val genre: String,
    val coverUrl: String?,
    val epubDownloadUrl: String?,
    val downloadCount: Int
)

data class ReadingStats(
    val totalBooks: Int,
    val completedBooks: Int,
    val favoriteBooks: Int,
    val totalPagesRead: Int,
    val todayPagesRead: Int,
    val weekMinutesRead: Int,
    val currentStreakDays: Int,
    val dailyGoalPages: Int,
    val dailyGoalProgress: Float,
    val weekMinutesByDay: List<Int> = List(7) { 0 }, // últimos 7 días, el último es hoy
    val totalMinutesRead: Int = 0
)

/** Página de catálogo: `hasMore` indica que hay más páginas. */
data class CatalogPage(val books: List<DiscoverBook>, val hasMore: Boolean = false)
