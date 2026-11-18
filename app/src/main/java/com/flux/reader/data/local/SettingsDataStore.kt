package com.flux.reader.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flux.reader.domain.model.LibrarySort
import com.flux.reader.domain.model.AppLanguage
import com.flux.reader.domain.model.AppThemeMode
import com.flux.reader.domain.model.ReaderFont
import com.flux.reader.domain.model.ReaderTheme
import com.flux.reader.domain.model.UserSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "flux_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val NAME = stringPreferencesKey("user_name")
        val AVATAR_URI = stringPreferencesKey("avatar_uri")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val ACCENT = longPreferencesKey("accent_color")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val LANGUAGE = stringPreferencesKey("language")
        val SHOW_NSFW = booleanPreferencesKey("show_nsfw")
        val READER_THEME = stringPreferencesKey("reader_theme")
        val READER_FONT = stringPreferencesKey("reader_font")
        val READER_FONT_SCALE = floatPreferencesKey("reader_font_scale")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
        val LIBRARY_GRID = booleanPreferencesKey("library_grid")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            name = prefs[Keys.NAME] ?: "Lector FLUX",
            avatarUri = prefs[Keys.AVATAR_URI],
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.DARK,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: false,
            accentColor = prefs[Keys.ACCENT] ?: 0xFF00C08B,
            dailyGoalPages = prefs[Keys.DAILY_GOAL] ?: 20,
            language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                ?: AppLanguage.ES,
            showNsfwCovers = prefs[Keys.SHOW_NSFW] ?: false,
            readerTheme = prefs[Keys.READER_THEME]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                ?: ReaderTheme.DARK,
            readerFont = prefs[Keys.READER_FONT]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() }
                ?: ReaderFont.SERIF,
            readerFontScale = prefs[Keys.READER_FONT_SCALE] ?: 1.0f,
            librarySort = prefs[Keys.LIBRARY_SORT]?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() } ?: LibrarySort.RECENT,
            libraryGrid = prefs[Keys.LIBRARY_GRID] ?: true
        )
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            val current = UserSettings(
                name = prefs[Keys.NAME] ?: "Lector FLUX",
                avatarUri = prefs[Keys.AVATAR_URI],
                themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                    ?: AppThemeMode.DARK,
                dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR] ?: false,
                accentColor = prefs[Keys.ACCENT] ?: 0xFF00C08B,
                dailyGoalPages = prefs[Keys.DAILY_GOAL] ?: 20,
                language = prefs[Keys.LANGUAGE]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() }
                    ?: AppLanguage.ES,
                showNsfwCovers = prefs[Keys.SHOW_NSFW] ?: false,
                readerTheme = prefs[Keys.READER_THEME]?.let { runCatching { ReaderTheme.valueOf(it) }.getOrNull() }
                    ?: ReaderTheme.DARK,
                readerFont = prefs[Keys.READER_FONT]?.let { runCatching { ReaderFont.valueOf(it) }.getOrNull() }
                    ?: ReaderFont.SERIF,
                readerFontScale = prefs[Keys.READER_FONT_SCALE] ?: 1.0f,
                librarySort = prefs[Keys.LIBRARY_SORT]?.let { runCatching { LibrarySort.valueOf(it) }.getOrNull() } ?: LibrarySort.RECENT,
                libraryGrid = prefs[Keys.LIBRARY_GRID] ?: true
            )
            val updated = transform(current)
            prefs[Keys.NAME] = updated.name
            if (updated.avatarUri != null) prefs[Keys.AVATAR_URI] = updated.avatarUri else prefs.remove(Keys.AVATAR_URI)
            prefs[Keys.THEME_MODE] = updated.themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = updated.dynamicColorEnabled
            prefs[Keys.ACCENT] = updated.accentColor
            prefs[Keys.DAILY_GOAL] = updated.dailyGoalPages
            prefs[Keys.LANGUAGE] = updated.language.name
            prefs[Keys.SHOW_NSFW] = updated.showNsfwCovers
            prefs[Keys.READER_THEME] = updated.readerTheme.name
            prefs[Keys.READER_FONT] = updated.readerFont.name
            prefs[Keys.READER_FONT_SCALE] = updated.readerFontScale
            prefs[Keys.LIBRARY_SORT] = updated.librarySort.name
            prefs[Keys.LIBRARY_GRID] = updated.libraryGrid
        }
    }
}
