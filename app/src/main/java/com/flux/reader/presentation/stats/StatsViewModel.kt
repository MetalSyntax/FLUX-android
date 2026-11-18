package com.flux.reader.presentation.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flux.reader.domain.model.ReadingStats
import com.flux.reader.domain.repository.BookRepository
import com.flux.reader.domain.repository.SettingsRepository
import com.flux.reader.domain.usecase.CalculateReadingStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
    bookRepository: BookRepository,
    settingsRepository: SettingsRepository,
    calculateReadingStats: CalculateReadingStatsUseCase
) : ViewModel() {

    val uiState: StateFlow<ReadingStats> = combine(
        bookRepository.observeLibrary(),
        bookRepository.observeSessions(),
        settingsRepository.observeSettings()
    ) { books, sessions, settings ->
        calculateReadingStats(books, sessions, settings)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReadingStats(
            totalBooks = 0,
            completedBooks = 0,
            favoriteBooks = 0,
            totalPagesRead = 0,
            todayPagesRead = 0,
            weekMinutesRead = 0,
            currentStreakDays = 0,
            dailyGoalPages = 20,
            dailyGoalProgress = 0f
        )
    )
}
