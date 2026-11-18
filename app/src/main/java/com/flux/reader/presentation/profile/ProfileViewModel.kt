package com.flux.reader.presentation.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.flux.reader.data.backup.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import com.flux.reader.domain.model.AppLanguage
import com.flux.reader.domain.model.AppThemeMode
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.UserSettings
import com.flux.reader.domain.repository.BookRepository
import com.flux.reader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val settings: UserSettings = UserSettings(),
    val library: List<Book> = emptyList(),
    val totalStorageBytes: Long = 0L,
    val busy: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val bookRepository: BookRepository,
    private val backupService: BackupService,
    @ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        settingsRepository.observeSettings(),
        bookRepository.observeLibrary(),
        busy, message
    ) { settings, books, isBusy, msg ->
        ProfileUiState(
            settings = settings,
            library = books,
            totalStorageBytes = books.sumOf { it.fileSizeBytes },
            busy = isBusy,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun updateName(name: String) = viewModelScope.launch {
        if (name.isNotBlank()) {
            settingsRepository.updateSettings { it.copy(name = name.trim()) }
        }
    }

    fun setThemeMode(mode: AppThemeMode) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(themeMode = mode) }
    }

    fun setAccent(argb: Long) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(accentColor = argb) }
    }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(dynamicColorEnabled = enabled) }
    }

    fun setLanguage(language: AppLanguage) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(language = language) }
    }

    fun setDailyGoal(pages: Int) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(dailyGoalPages = pages.coerceIn(5, 300)) }
    }

    fun setShowNsfw(show: Boolean) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(showNsfwCovers = show) }
    }

    fun dismissMessage() { message.value = null }

    fun updateGoal(pages: Int) = setDailyGoal(pages)

    private fun <T> run(block: suspend () -> Result<T>, ok: (T) -> String) = viewModelScope.launch {
        busy.value = true
        message.value = block().fold(ok) { "Error: ${it.message ?: it.javaClass.simpleName}" }
        busy.value = false
    }

    fun exportBackup(target: Uri) = run({ backupService.export(target) }) { "Copia exportada: $it libro(s)" }

    fun importBackup(source: Uri) = run({ backupService.import(source) }) {
        "Copia importada: ${it.added} nuevo(s), ${it.skipped} ya existían" + if (it.withoutFile > 0) ", ${it.withoutFile} sin archivo omitido(s)" else ""
    }

    /** Guarda una copia del archivo original del libro donde elija el usuario. */
    fun exportOriginal(book: Book, target: Uri) = run({
        withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(target, "wt")!!.use { out -> java.io.File(book.fileUri).inputStream().use { it.copyTo(out) } }
            }
        }
    }) { "Archivo exportado" }

    fun deleteBook(bookId: String) = viewModelScope.launch {
        bookRepository.deleteBook(bookId)
    }
}
