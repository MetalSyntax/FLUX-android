package com.flux.reader.presentation.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import com.flux.reader.ui.FluxChip
import com.flux.reader.ui.GlassCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.flux.reader.data.backup.BackupMapper
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookCollection
import com.flux.reader.domain.model.LibraryFilter
import com.flux.reader.domain.model.LibrarySort
import java.io.File

/** Coil: ruta local -> File; URL -> String. */
fun coverModel(uri: String): Any? = if (uri.isBlank()) null else if (uri.startsWith("/")) File(uri) else uri

private val collectionColors = listOf(0xFF00C08BL, 0xFF7C3AEDL, 0xFFDC2626L, 0xFF16A34AL, 0xFFD97706L, 0xFF0891B2L)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    importTick: Int = 0,
    onOpenBook: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedBookForMenu by remember { mutableStateOf<Book?>(null) }
    var renameTarget by remember { mutableStateOf<Book?>(null) }
    var deleteTarget by remember { mutableStateOf<Book?>(null) }
    var collectionTarget by remember { mutableStateOf<Book?>(null) }
    var showNewCollection by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) viewModel.importUris(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        tree?.let { viewModel.syncFolder(it) }
    }
    LaunchedEffect(importTick) { if (importTick > 0) picker.launch(arrayOf("application/epub+zip", "application/pdf", "application/vnd.comicbook+zip",
        "application/vnd.comicbook-rar", "application/x-cbz", "application/x-cbr", "application/zip",
        "application/x-rar-compressed", "text/plain", "text/markdown", "text/html", "application/xhtml+xml",
        "application/x-fictionbook+xml", "application/xml", "text/xml", "application/octet-stream")) }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent),
                title = { Text("FLUX", fontWeight = FontWeight.Bold, letterSpacing = 4.sp, fontSize = 18.sp) },
                actions = {
                    var folderMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { if (viewModel.hasSyncFolder) folderMenu = true else folderPicker.launch(null) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = "Sincronizar carpeta")
                        }
                        DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                            DropdownMenuItem(text = { Text("Buscar libros nuevos en la carpeta") }, onClick = { folderMenu = false; viewModel.syncFolder() })
                            DropdownMenuItem(text = { Text("Elegir otra carpeta") }, onClick = { folderMenu = false; folderPicker.launch(null) })
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordenar")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            LibrarySort.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (sort) {
                                                LibrarySort.RECENT -> "Recientes"
                                                LibrarySort.TITLE -> "Título"
                                                LibrarySort.AUTHOR -> "Autor"
                                            },
                                            fontWeight = if (sort == state.sortBy) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = { viewModel.onSortChange(sort); showSortMenu = false }
                                )
                            }
                        }
                    }
                    IconButton(onClick = viewModel::toggleViewMode) {
                        Icon(
                            imageVector = if (state.isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                            contentDescription = "Cambiar vista"
                        )
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar libros, autores o manga...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = com.flux.reader.ui.fluxFieldColors()
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(LibraryFilter.entries) { filter ->
                    FluxChip(
                        selected = state.activeFilter == filter && state.activeCollectionId == null,
                        onClick = { viewModel.onFilterSelect(filter) },
                        label = {
                            Text(
                                when (filter) {
                                    LibraryFilter.ALL -> "Todos"
                                    LibraryFilter.FAVORITES -> "Favoritos"
                                    LibraryFilter.MANGA -> "Manga"
                                    LibraryFilter.EPUB -> "EPUB"
                                    LibraryFilter.PDF -> "PDF"
                                }
                            )
                        }
                    )
                }
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FluxChip(
                        selected = false,
                        onClick = { showNewCollection = true },
                        label = { Text("Colección") },
                        leadingIcon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }
                items(state.collections, key = { it.id }) { col ->
                    FluxChip(
                        selected = state.activeCollectionId == col.id,
                        onClick = { viewModel.onCollectionSelect(col.id) },
                        label = { Text("${col.name} (${col.bookIds.size})") },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Color(col.colorHex))) }
                    )
                }
            }

            if (state.importing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.books.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Tu biblioteca está vacía. Toca + para importar EPUB, PDF, CBZ/CBR, TXT, Markdown, HTML o FB2.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (state.isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(state.books, key = { it.id }) { book ->
                        BookGridCard(
                            book = book,
                            blurCover = book.isNsfw && !state.showNsfwCovers,
                            onClick = { onOpenBook(book.id) },
                            onMoreClick = { selectedBookForMenu = book }
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.books, key = { it.id }) { book ->
                        BookListRow(
                            book = book,
                            blurCover = book.isNsfw && !state.showNsfwCovers,
                            onClick = { onOpenBook(book.id) },
                            onMoreClick = { selectedBookForMenu = book }
                        )
                    }
                }
            }
        }
    }

    selectedBookForMenu?.let { book ->
        ModalBottomSheet(onDismissRequest = { selectedBookForMenu = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                ListItem(
                    headlineContent = { Text(if (book.isFavorite) "Quitar de favoritos" else "Añadir a favoritos") },
                    leadingContent = { Icon(if (book.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null) },
                    modifier = Modifier.clickable { viewModel.onToggleFavorite(book.id); selectedBookForMenu = null }
                )
                ListItem(
                    headlineContent = { Text("Renombrar") },
                    leadingContent = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    modifier = Modifier.clickable { renameTarget = book; selectedBookForMenu = null }
                )
                ListItem(
                    headlineContent = { Text("Añadir a colección") },
                    leadingContent = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
                    modifier = Modifier.clickable { collectionTarget = book; selectedBookForMenu = null }
                )
                ListItem(
                    headlineContent = { Text(if (book.isNsfw) "Quitar marca NSFW" else "Marcar como NSFW") },
                    leadingContent = { Icon(if (book.isNsfw) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null) },
                    modifier = Modifier.clickable { viewModel.toggleNsfw(book.id); selectedBookForMenu = null }
                )
                ListItem(
                    headlineContent = { Text("Eliminar de la biblioteca") },
                    leadingContent = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    modifier = Modifier.clickable { deleteTarget = book; selectedBookForMenu = null }
                )
            }
        }
    }

    renameTarget?.let { book ->
        var text by remember(book.id) { mutableStateOf(book.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Renombrar") },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.onRenameBook(book.id, text); renameTarget = null }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancelar") } }
        )
    }

    deleteTarget?.let { book ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminar libro") },
            text = { Text("Se borrará «${book.title}» y su archivo de la app. Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { viewModel.onDeleteBook(book.id); deleteTarget = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } }
        )
    }

    if (showNewCollection) {
        var name by remember { mutableStateOf("") }
        var colorIdx by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showNewCollection = false },
            title = { Text("Nueva colección") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nombre") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        collectionColors.forEachIndexed { i, c ->
                            Box(
                                Modifier.size(if (i == colorIdx) 32.dp else 26.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Color(c)).clickable { colorIdx = i }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onCreateCollection(name, collectionColors[colorIdx]); showNewCollection = false }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { showNewCollection = false }) { Text("Cancelar") } }
        )
    }

    collectionTarget?.let { book ->
        ModalBottomSheet(onDismissRequest = { collectionTarget = null }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text("Colecciones", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                if (state.collections.isEmpty()) {
                    Text("Aún no hay colecciones. Crea una con el chip «Colección».", modifier = Modifier.padding(16.dp))
                }
                state.collections.forEach { col ->
                    val inCol = book.id in col.bookIds
                    ListItem(
                        headlineContent = { Text(col.name) },
                        supportingContent = { Text(if (inCol) "Quitar de esta colección" else "Añadir a esta colección") },
                        modifier = Modifier.clickable {
                            if (inCol) viewModel.onRemoveFromCollection(col, book.id) else viewModel.onAddBookToCollection(col, book.id)
                            collectionTarget = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookGridCard(
    book: Book,
    blurCover: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    GlassCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
            ) {
                AsyncImage(
                    model = coverModel(book.coverUri),
                    contentDescription = book.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurCover) Modifier.blur(18.dp) else Modifier)
                )
                IconButton(
                    onClick = onMoreClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Opciones")
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${book.author} · ${book.type.name}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { book.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
}

@Composable
private fun BookListRow(
    book: Book,
    blurCover: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    GlassCard(onClick = onClick, shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = coverModel(book.coverUri),
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 52.dp, height = 72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (blurCover) Modifier.blur(14.dp) else Modifier)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${book.author} · ${book.type.name} · ${book.progressPercent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onMoreClick) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Opciones")
            }
        }
    }
}
