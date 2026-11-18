package com.flux.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flux.reader.domain.model.UserSettings
import com.flux.reader.domain.repository.SettingsRepository
import com.flux.reader.navigation.FluxNavGraph
import com.flux.reader.ui.theme.FluxTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.observeSettings()
                .collectAsStateWithLifecycle(initialValue = UserSettings())

            FluxTheme(
                themeMode = settings.themeMode,
                accent = settings.accentColor
            ) {
                com.flux.reader.ui.FluxBackground { FluxNavGraph() }
            }
        }
    }
}
