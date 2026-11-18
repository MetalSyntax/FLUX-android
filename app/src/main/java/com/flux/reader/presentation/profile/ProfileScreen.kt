package com.flux.reader.presentation.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flux.reader.domain.model.AppLanguage
import com.flux.reader.domain.model.AppThemeMode
import com.flux.reader.domain.model.Book
import com.flux.reader.ui.TonalCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun fmtSize(b: Long): String = when {
    b >= 1_048_576 -> "%.1f MB".format(b / 1_048_576.0)
    else -> "${(b / 1024).coerceAtLeast(1)} KB"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showName by remember { mutableStateOf(false) }
    var showGoal by remember { mutableStateOf(false) }
    var exportTarget by remember { mutableStateOf<Book?>(null) }
    var deleteTarget by remember { mutableStateOf<Book?>(null) }

    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        uri?.let(viewModel::exportBackup)
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(viewModel::importBackup)
    }
    val exportOriginal = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri: Uri? ->
        val book = exportTarget
        if (uri != null && book != null) viewModel.exportOriginal(book, uri)
        exportTarget = null
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(contentWindowInsets = WindowInsets.statusBars, snackbarHost = { SnackbarHost(snackbar) }) { scaffoldPadding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(scaffoldPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(state.settings.name, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Almacenamiento local · ${state.library.size} libros · ${fmtSize(state.totalStorageBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showName = true }) { Icon(Icons.Filled.Edit, contentDescription = "Editar nombre") }
            }
        }

        item {
            TonalCard() {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Meta diaria", style = MaterialTheme.typography.titleMedium)
                        Text("${state.settings.dailyGoalPages} páginas por día", style = MaterialTheme.typography.bodyMedium)
                    }
                    FilledTonalButton(onClick = { showGoal = true }) { Text("Editar") }
                }
            }
        }

        item {
            TonalCard() {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Apariencia", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppThemeMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, AppThemeMode.entries.size),
                                onClick = { viewModel.setThemeMode(mode) },
                                selected = state.settings.themeMode == mode,
                                colors = com.flux.reader.ui.fluxSegmentColors()
                            ) {
                                Text(
                                    when (mode) {
                                        AppThemeMode.DARK -> "Oscuro"
                                        AppThemeMode.AMOLED_BLACK -> "Negro"
                                        AppThemeMode.LIGHT -> "Claro"
                                    }
                                )
                            }
                        }
                    }
                    Text("Color de acento", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    com.flux.reader.ui.AccentPicker(
                        selected = state.settings.accentColor,
                        accents = com.flux.reader.ui.theme.FluxAccents,
                        onPick = viewModel::setAccent
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Mostrar portadas NSFW", style = MaterialTheme.typography.bodyLarge)
                            Text("Si está apagado se desenfocan", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = state.settings.showNsfwCovers, onCheckedChange = viewModel::setShowNsfw)
                    }
                }
            }
        }

        item {
            TonalCard() {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Idioma", style = MaterialTheme.typography.titleMedium)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppLanguage.entries.forEachIndexed { index, lang ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(index, AppLanguage.entries.size),
                                onClick = { viewModel.setLanguage(lang) },
                                selected = state.settings.language == lang,
                                colors = com.flux.reader.ui.fluxSegmentColors()
                            ) { Text(lang.label) }
                        }
                    }
                    Text(
                        "La interfaz de la app está en español; este ajuste se guarda y se sincroniza con la web.",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            TonalCard() {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Copia de seguridad y migración", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Un solo .zip con libros, portadas, progreso, colecciones, marcadores, estadísticas y ajustes. Compatible con la versión web de FLUX.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !state.busy,
                            onClick = { exportBackup.launch("flux-backup-${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.zip") }
                        ) { Text("Exportar todo") }
                        OutlinedButton(
                            enabled = !state.busy,
                            onClick = { importBackup.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }
                        ) { Text("Importar") }
                    }
                }
            }
        }

        item { Text("Gestor de almacenamiento", style = MaterialTheme.typography.titleMedium) }

        items(state.library, key = { it.id }) { book ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                headlineContent = { Text(book.title, maxLines = 1) },
                supportingContent = { Text("${book.type.name} · ${fmtSize(book.fileSizeBytes)}") },
                trailingContent = {
                    Row {
                        IconButton(onClick = {
                            exportTarget = book
                            exportOriginal.launch(book.fileName.ifBlank { "${book.title}.${java.io.File(book.fileUri).extension}" })
                        }) { Icon(Icons.Filled.FileUpload, contentDescription = "Exportar archivo original de ${book.title}") }
                        IconButton(onClick = { deleteTarget = book }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Eliminar ${book.title}")
                        }
                    }
                }
            )
        }
    }
    }

    if (showName) {
        var text by remember { mutableStateOf(state.settings.name) }
        AlertDialog(
            onDismissRequest = { showName = false },
            title = { Text("Tu nombre") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.updateName(text); showName = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { showName = false }) { Text("Cancelar") } }
        )
    }
    if (showGoal) {
        var text by remember { mutableStateOf(state.settings.dailyGoalPages.toString()) }
        AlertDialog(
            onDismissRequest = { showGoal = false },
            title = { Text("Meta diaria (páginas)") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it.filter(Char::isDigit).take(4) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { text.toIntOrNull()?.let(viewModel::updateGoal); showGoal = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { showGoal = false }) { Text("Cancelar") } }
        )
    }
    deleteTarget?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar libro") },
            text = { Text("Se borrará «${book.title}» y su archivo (${fmtSize(book.fileSizeBytes)}).") },
            confirmButton = { TextButton(onClick = { viewModel.deleteBook(book.id); deleteTarget = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }
}
