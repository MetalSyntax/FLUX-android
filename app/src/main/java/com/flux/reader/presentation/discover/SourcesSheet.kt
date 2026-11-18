package com.flux.reader.presentation.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flux.reader.domain.model.BookSource
import com.flux.reader.domain.model.SourcesState

/** Gestor de repositorios: agregar por URL, instalar/quitar sus fuentes, borrar el repositorio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(
    sources: SourcesState,
    adding: Boolean,
    onAddRepo: (String) -> Unit,
    onRemoveRepo: (String) -> Unit,
    onInstall: (BookSource) -> Unit,
    onUninstall: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var url by rememberSaveable { mutableStateOf("") }
    val installedIds = sources.installed.map { it.id }.toSet()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Fuentes de libros", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Agrega la URL de un repositorio (un archivo JSON) y luego instala las fuentes que quieras. Puedes refrescarlo agregándolo otra vez.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text("https://…/repo.json") }, singleLine = true,
                        shape = RoundedCornerShape(16.dp), colors = com.flux.reader.ui.fluxFieldColors()
                    )
                    FilledTonalButton(onClick = { onAddRepo(url) }, enabled = !adding && url.isNotBlank()) {
                        if (adding) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Agregar")
                    }
                }
            }
            if (sources.repos.isEmpty()) {
                item { Text("No hay repositorios agregados.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            sources.repos.forEach { repo ->
                item(key = "repo:${repo.url}") {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(repo.name, style = MaterialTheme.typography.titleMedium)
                            Text(repo.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onRemoveRepo(repo.url) }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar repositorio ${repo.name}") }
                    }
                }
                items(repo.sources, key = { "src:${repo.url}:${it.id}" }) { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                listOfNotNull(s.lang?.uppercase(), s.type, s.description).joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (s.id in installedIds) OutlinedButton(onClick = { onUninstall(s.id) }) { Text("Quitar") }
                        else FilledTonalButton(onClick = { onInstall(s) }) { Text("Instalar") }
                    }
                }
            }
            item { androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp)) }
        }
    }
}
