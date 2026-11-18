package com.flux.reader.presentation.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.Bookmark
import com.flux.reader.domain.model.ReaderFont
import com.flux.reader.domain.model.ReaderTheme
import com.flux.reader.domain.model.ReadingSession
import com.flux.reader.domain.model.UserSettings
import com.flux.reader.domain.repository.BookRepository
import com.flux.reader.domain.repository.SettingsRepository
import com.flux.reader.domain.usecase.ManageBookmarksUseCase
import com.flux.reader.domain.usecase.RecordReadingSessionUseCase
import com.flux.reader.domain.usecase.UpdateReadingProgressUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs

data class ReaderUiState(
    val book: Book? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val settings: UserSettings = UserSettings(),
    val currentPage: Int = 1,
    val controlsVisible: Boolean = true,
    val brightness: Float = 1.0f
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    bookRepository: BookRepository,
    private val settingsRepository: SettingsRepository,
    private val updateProgress: UpdateReadingProgressUseCase,
    private val manageBookmarks: ManageBookmarksUseCase,
    private val recordSession: RecordReadingSessionUseCase
) : ViewModel() {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])
    private val sessionStartMs = System.currentTimeMillis()
    private var initialPage: Int? = null

    private val currentPageFlow = MutableStateFlow(1)
    private val controlsVisibleFlow = MutableStateFlow(true)
    private val brightnessFlow = MutableStateFlow(1.0f)

    val uiState: StateFlow<ReaderUiState> = combine(
        bookRepository.observeBook(bookId),
        manageBookmarks.observeForBook(bookId),
        settingsRepository.observeSettings(),
        currentPageFlow,
        combine(controlsVisibleFlow, brightnessFlow) { c, b -> c to b }
    ) { book, bookmarks, settings, page, (controls, brightness) ->
        if (book != null && initialPage == null) {
            initialPage = book.currentPage
            currentPageFlow.value = book.currentPage
        }
        ReaderUiState(
            book = book,
            bookmarks = bookmarks,
            settings = settings,
            currentPage = page,
            controlsVisible = controls,
            brightness = brightness
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    fun toggleControls() {
        controlsVisibleFlow.value = !controlsVisibleFlow.value
    }

    private var saveJob: Job? = null

    /** Posición actual del lector (EPUB: 1..1000 virtual; PDF/CBZ: página). Guarda con debounce. */
    fun onPosition(page: Int, total: Int) {
        val t = total.coerceAtLeast(1)
        val p = page.coerceIn(1, t)
        currentPageFlow.value = p
        if (initialPage == null) initialPage = p
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            updateProgress(bookId, p, t, null)
        }
    }

    fun setReaderTheme(theme: ReaderTheme) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(readerTheme = theme) }
    }

    fun setReaderFont(font: ReaderFont) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(readerFont = font) }
    }

    fun setFontScale(scale: Float) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(readerFontScale = scale.coerceIn(0.8f, 1.6f)) }
    }

    fun setBrightness(value: Float) {
        brightnessFlow.value = value.coerceIn(0.3f, 1.0f)
    }

    fun addBookmark(label: String, colorHex: Long) = viewModelScope.launch {
        manageBookmarks.add(
            Bookmark(
                id = UUID.randomUUID().toString(),
                bookId = bookId,
                page = currentPageFlow.value,
                label = label.ifBlank { "Página ${currentPageFlow.value}" },
                colorHex = colorHex,
                createdAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    fun removeBookmark(bookmarkId: String) = viewModelScope.launch {
        manageBookmarks.remove(bookmarkId)
    }

    override fun onCleared() {
        val book = uiState.value.book
        val delta = abs(currentPageFlow.value - (initialPage ?: currentPageFlow.value))
        // EPUB usa 1000 posiciones virtuales: ~1 página por cada 1 %
        val pagesRead = if (book?.type == com.flux.reader.domain.model.BookType.EPUB) delta / 10 else delta
        val end = System.currentTimeMillis()
        val pageNow = currentPageFlow.value
        val total = (book?.totalPages ?: 1)
        // viewModelScope ya está cancelado aquí: se guarda posición final y sesión en un scope propio
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (book != null) updateProgress(bookId, pageNow, total, null)
            recordSession(ReadingSession(UUID.randomUUID().toString(), bookId, sessionStartMs, end, pagesRead))
        }
        super.onCleared()
    }
}
