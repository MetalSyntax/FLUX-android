package com.flux.reader.presentation.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.flux.reader.ui.FluxChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flux.reader.domain.model.BookType
import com.flux.reader.domain.model.ReaderFont
import com.flux.reader.domain.model.ReaderTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onClose: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showBookmarksSheet by remember { mutableStateOf(false) }
    var jumpRequest by remember { mutableStateOf<Int?>(null) }

    val isEpub = state.book?.type == BookType.EPUB
    val bgColor = if (isEpub) Color(state.settings.readerTheme.backgroundHex) else Color.Black
    val textColor = if (isEpub) Color(state.settings.readerTheme.textHex) else Color.White
    val book = state.book

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        if (book == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (isEpub) {
            EpubReader(
                book = book,
                prefs = state.settings,
                jumpRequest = jumpRequest,
                onJumpHandled = { jumpRequest = null },
                onPosition = viewModel::onPosition,
                onToggleControls = viewModel::toggleControls,
                controlsVisible = state.controlsVisible,
                barColor = bgColor,
                barTextColor = textColor
            )
        } else {
            PageReader(
                book = book,
                jumpRequest = jumpRequest,
                onJumpHandled = { jumpRequest = null },
                onPosition = viewModel::onPosition,
                onToggleControls = viewModel::toggleControls,
                controlsVisible = state.controlsVisible
            )
        }

        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(color = bgColor.copy(alpha = 0.92f), contentColor = textColor) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }
                    Text(book?.title ?: "Lector FLUX", maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { viewModel.addBookmark("Página ${state.currentPage}", 0xFF00C08B) }) {
                        Icon(Icons.Filled.BookmarkAdd, contentDescription = "Añadir marcador")
                    }
                    IconButton(onClick = { showBookmarksSheet = true }) {
                        Icon(Icons.Filled.Bookmarks, contentDescription = "Ver marcadores")
                    }
                    if (isEpub) {
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = "Ajustes de lectura")
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(onDismissRequest = { showSettingsSheet = false }) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Tema de lectura", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderTheme.entries.forEach { theme ->
                        FluxChip(
                            selected = state.settings.readerTheme == theme,
                            onClick = { viewModel.setReaderTheme(theme) },
                            label = { Text(theme.label) }
                        )
                    }
                }
                Text("Tipografía", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReaderFont.entries.forEach { font ->
                        FluxChip(
                            selected = state.settings.readerFont == font,
                            onClick = { viewModel.setReaderFont(font) },
                            label = { Text(font.label) }
                        )
                    }
                }
                Text("Tamaño de fuente", style = MaterialTheme.typography.titleMedium)
                Slider(
                    value = state.settings.readerFontScale,
                    onValueChange = viewModel::setFontScale,
                    valueRange = 0.8f..1.6f
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }

    if (showBookmarksSheet) {
        ModalBottomSheet(onDismissRequest = { showBookmarksSheet = false }) {
            Column(modifier = Modifier.padding(16.dp).navigationBarsPadding()) {
                Text("Marcadores guardados", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
                if (state.bookmarks.isEmpty()) {
                    Text("No hay marcadores en este libro todavía.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    state.bookmarks.forEach { bookmark ->
                        ListItem(
                            headlineContent = { Text(bookmark.label) },
                            supportingContent = {
                                Text(if (isEpub) "${(bookmark.page / 10f).toInt()} %" else "Página ${bookmark.page}")
                            },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeBookmark(bookmark.id) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Eliminar marcador")
                                }
                            },
                            modifier = Modifier.clickable {
                                jumpRequest = bookmark.page
                                showBookmarksSheet = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
