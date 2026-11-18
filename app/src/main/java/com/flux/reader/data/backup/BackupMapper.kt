package com.flux.reader.data.backup

import com.flux.reader.domain.model.AppLanguage
import com.flux.reader.domain.model.AppThemeMode
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookCollection
import com.flux.reader.domain.model.BookType
import com.flux.reader.domain.model.Bookmark
import com.flux.reader.domain.model.ReadingSession
import com.flux.reader.domain.model.UserSettings

/** Conversión dominio <-> formato de copia. Puro (sin Android). */
object BackupMapper {
    const val NSFW_TAG = "NSFW"
    const val EPUB_VIRTUAL_PAGES = 1000

    // La web guarda colores como tokens CSS; se mantienen al exportar si coinciden con la paleta.
    private val colorTokens = mapOf(
        "var(--color-primary)" to 0xFF00C08BL,
        "var(--color-purple)" to 0xFF7C3AEDL,
        "var(--color-red)" to 0xFFDC2626L,
        "var(--color-green)" to 0xFF16A34AL,
        "var(--color-orange)" to 0xFFD97706L,
        "var(--color-cyan)" to 0xFF0891B2L,
        "var(--color-favorite)" to 0xFFF59E0BL
    )

    fun colorToString(argb: Long): String =
        colorTokens.entries.firstOrNull { it.value == argb }?.key ?: "#%06X".format(argb and 0xFFFFFF)

    fun colorFromString(s: String): Long {
        colorTokens[s.trim()]?.let { return it }
        val hex = s.trim().removePrefix("#")
        if (hex.length == 6) hex.toLongOrNull(16)?.let { return 0xFF000000 or it }
        return 0xFF00C08B
    }

    fun extOf(fileName: String?, type: BookType): String {
        val e = fileName?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (e.length in 1..5 && e.all { it.isLetterOrDigit() }) return e
        return when (type) { BookType.EPUB -> "epub"; BookType.PDF -> "pdf"; BookType.CBR -> "cbz" }
    }

    fun typeOf(s: String): BookType = when (s.uppercase()) {
        "EPUB" -> BookType.EPUB
        "CBR", "CBZ", "ZIP" -> BookType.CBR
        else -> BookType.PDF
    }

    fun toBackup(b: Book, filePath: String?, coverPath: String?) = BackupBook(
        id = b.id, title = b.title, author = b.author, type = b.type.name,
        progress = b.progressPercent, lastRead = null, lastReadDate = b.lastReadEpochMs,
        currentPage = b.currentPage, totalPages = b.totalPages, currentCfi = b.currentCfi,
        isFavorite = b.isFavorite,
        tags = b.tags + if (b.isNsfw) listOf(NSFW_TAG) else emptyList(),
        fileName = b.fileName.ifBlank { null }, file = filePath, cover = coverPath
    )

    /** [fileUri]/[coverUri] son las rutas locales donde ya se copiaron los archivos. */
    fun toBook(b: BackupBook, fileUri: String, coverUri: String, size: Long): Book {
        val type = typeOf(b.type)
        val (page, total) = if (type == BookType.EPUB) {
            // la web usa ubicaciones de epub.js; aquí se normaliza al porcentaje (0..1000)
            (b.progress * EPUB_VIRTUAL_PAGES / 100).coerceIn(1, EPUB_VIRTUAL_PAGES) to EPUB_VIRTUAL_PAGES
        } else {
            val t = b.totalPages.coerceAtLeast(1)
            b.currentPage.coerceIn(1, t) to t
        }
        return Book(
            id = b.id, title = b.title.ifBlank { "Sin título" }, author = b.author,
            coverUri = coverUri, fileUri = fileUri, type = type,
            currentPage = page, totalPages = total, currentCfi = b.currentCfi,
            lastReadEpochMs = b.lastReadDate, isFavorite = b.isFavorite,
            isNsfw = b.tags.any { it.equals(NSFW_TAG, true) },
            fileSizeBytes = size, fileName = b.fileName.orEmpty(),
            tags = b.tags.filterNot { it.equals(NSFW_TAG, true) }
        )
    }

    /** Mismo libro si coincide el id o (título + tipo + tamaño del archivo). */
    fun findDuplicate(existing: List<Book>, incoming: BackupBook, incomingSize: Long): Book? =
        existing.firstOrNull { it.id == incoming.id }
            ?: existing.firstOrNull {
                incomingSize > 0 && it.fileSizeBytes == incomingSize &&
                    it.title == incoming.title && it.type == typeOf(incoming.type)
            }

    fun toBackup(m: Bookmark) = BackupBookmark(m.id, m.bookId, m.page, m.cfi, m.label, colorToString(m.colorHex), m.createdAtEpochMs)
    fun toBookmark(m: BackupBookmark, bookId: String) =
        Bookmark(m.id, bookId, m.page, m.cfi, m.label, colorFromString(m.color), m.createdAt)

    fun toBackup(s: ReadingSession) = BackupSession(s.id, s.bookId, s.startTimeEpochMs, s.endTimeEpochMs, s.pagesRead)
    fun toSession(s: BackupSession, bookId: String) = ReadingSession(s.id, bookId, s.startTime, s.endTime, s.pagesRead)

    fun toBackup(c: BookCollection) = BackupCollection(c.id, c.name, colorToString(c.colorHex), c.bookIds.toList())

    fun toBackup(s: UserSettings) = BackupSettings(
        name = s.name, avatar = null,
        theme = when (s.themeMode) { AppThemeMode.DARK -> "dark"; AppThemeMode.AMOLED_BLACK -> "black"; AppThemeMode.LIGHT -> "white" },
        dailyGoal = s.dailyGoalPages, language = s.language.code, showNsfw = s.showNsfwCovers
    )

    fun applySettings(cur: UserSettings, b: BackupSettings): UserSettings = cur.copy(
        name = b.name?.takeIf { it.isNotBlank() } ?: cur.name,
        themeMode = when (b.theme) {
            "dark" -> AppThemeMode.DARK; "black" -> AppThemeMode.AMOLED_BLACK; "white" -> AppThemeMode.LIGHT
            else -> cur.themeMode
        },
        dailyGoalPages = b.dailyGoal?.coerceIn(1, 1000) ?: cur.dailyGoalPages,
        language = AppLanguage.entries.firstOrNull { it.code == b.language } ?: cur.language,
        showNsfwCovers = b.showNsfw ?: cur.showNsfwCovers
    )
}
