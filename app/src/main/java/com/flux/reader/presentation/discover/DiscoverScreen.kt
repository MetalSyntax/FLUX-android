package com.flux.reader.presentation.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import com.flux.reader.ui.FluxChip
import com.flux.reader.ui.GlassCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage

private val discoverGenres = listOf("Adventure", "Mystery", "Romance", "Science Fiction", "Philosophy")

@Composable
fun DiscoverScreen(
    onOpenBook: (String) -> Unit,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSources by rememberSaveable { mutableStateOf(false) }
    if (showSources) SourcesSheet(
        sources = state.sources, adding = state.addingRepo,
        onAddRepo = viewModel::addRepo, onRemoveRepo = viewModel::removeRepo,
        onInstall = viewModel::install, onUninstall = viewModel::uninstall,
        onDismiss = { showSources = false }
    )
    val source = state.selectedSource
    LaunchedEffect(state.toast) { state.toast?.let { snackbar.showSnackbar(it); viewModel.dismissToast() } }

    Scaffold(contentWindowInsets = WindowInsets.statusBars, snackbarHost = { SnackbarHost(snackbar) }) { scaffoldPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        state.continueReadingBook?.let { lastBook ->
            item {
                GlassCard(shape = RoundedCornerShape(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = com.flux.reader.presentation.library.coverModel(lastBook.coverUri),
                            contentDescription = lastBook.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 72.dp, height = 104.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Continuar leyendo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(lastBook.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${lastBook.author} · ${lastBook.progressPercent}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(onClick = { onOpenBook(lastBook.id) }) {
                                Text("Abrir lector")
                            }
                        }
                    }
                }
            }
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                items(state.sources.installed, key = { it.id }) { s ->
                    FluxChip(selected = s.id == source?.id, onClick = { viewModel.selectSource(s.id) }, label = { Text(s.name) })
                }
                item { FilledTonalButton(onClick = { showSources = true }) { Text("Fuentes") } }
            }
        }

        if (source == null) {
            item {
                GlassCard(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("No hay fuentes instaladas", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Descubrir no trae catálogos de fábrica. Agrega un repositorio de fuentes e instala las que quieras usar.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = { showSources = true }) { Text("Gestionar fuentes") }
                    }
                }
            }
        }

        if (source != null) item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar en ${source.name}...") },
                    enabled = source.engine != com.flux.reader.domain.model.SourceType.OPDS || source.search != null,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = com.flux.reader.ui.fluxFieldColors()
                )
                FilledTonalButton(onClick = viewModel::executeSearch) {
                    Text("Buscar")
                }
            }
        }

        if (source?.engine?.hasGenres == true) item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(discoverGenres) { genre ->
                    FluxChip(
                        selected = genre == state.lastGenre,
                        onClick = { viewModel.loadGenre(genre) },
                        label = { Text(genre) }
                    )
                }
            }
        }

        if (state.isLoading) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        state.errorMessage?.let { msg ->
            item {
                GlassCard(shape = RoundedCornerShape(16.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        FilledTonalButton(onClick = viewModel::retry) { Text("Reintentar") }
                    }
                }
            }
        }

        if (source != null && !state.isLoading && state.errorMessage == null && state.results.isEmpty()) {
            item { Text("Sin resultados.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }

        items(state.results, key = { it.id }) { item ->
            GlassCard(shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = item.coverUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 56.dp, height = 82.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${item.author} · ${item.genre}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val busy = item.id in state.downloadingIds
                    val added = item.id in state.addedIds
                    FilledTonalButton(
                        onClick = { viewModel.addToLibrary(item) },
                        enabled = !busy && !added && item.epubDownloadUrl != null
                    ) {
                        val pr = state.progress[item.id]
                        if (busy && pr != null && pr > 0f) CircularProgressIndicator(progress = { pr }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (added) "Añadido" else if (busy) (state.progress[item.id]?.let { "${(it * 100).toInt()}%" } ?: "Descargando") else "Añadir")
                    }
                }
            }
        }
        if (state.hasMore || state.isLoadingMore) {
            item {
                Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    if (state.isLoadingMore) CircularProgressIndicator()
                    else FilledTonalButton(onClick = viewModel::loadMore) { Text("Cargar más") }
                }
            }
        }
    }
}
}
