package com.flux.reader.presentation.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.flux.reader.data.files.LibraryFiles
import com.flux.reader.domain.repository.BookRepository
import androidx.lifecycle.viewModelScope
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.BookCollection
import com.flux.reader.domain.model.LibraryFilter
import com.flux.reader.domain.model.LibrarySort
import com.flux.reader.domain.repository.SettingsRepository
import com.flux.reader.domain.usecase.DeleteBookUseCase
import com.flux.reader.domain.usecase.FilterAndSortBooksUseCase
import com.flux.reader.domain.usecase.GetLibraryBooksUseCase
import com.flux.reader.domain.usecase.ManageCollectionsUseCase
import com.flux.reader.domain.usecase.RenameBookUseCase
import com.flux.reader.domain.usecase.ToggleFavoriteBookUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val collections: List<BookCollection> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: LibraryFilter = LibraryFilter.ALL,
    val sortBy: LibrarySort = LibrarySort.RECENT,
    val activeCollectionId: String? = null,
    val isGridView: Boolean = true,
    val showNsfwCovers: Boolean = false,
    val importing: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    getLibraryBooks: GetLibraryBooksUseCase,
    private val filterAndSortBooks: FilterAndSortBooksUseCase,
    private val toggleFavorite: ToggleFavoriteBookUseCase,
    private val renameBook: RenameBookUseCase,
    private val deleteBook: DeleteBookUseCase,
    private val manageCollections: ManageCollectionsUseCase,
    private val bookRepository: BookRepository,
    private val libraryFiles: LibraryFiles,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val activeFilter = MutableStateFlow(LibraryFilter.ALL)
    private val importing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val activeCollectionId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        getLibraryBooks(),
        manageCollections.observeAll(),
        settingsRepository.observeSettings(),
        combine(searchQuery, activeFilter, activeCollectionId) { q, f, c -> Triple(q, f, c) },
        combine(importing, message) { i, m -> i to m }
    ) { allBooks, collections, settings, (query, filter, colId), (isImporting, msg) ->
        val activeCol = collections.find { it.id == colId }
        LibraryUiState(
            books = filterAndSortBooks(allBooks, query, filter, settings.librarySort, activeCol),
            collections = collections,
            searchQuery = query,
            activeFilter = filter,
            sortBy = settings.librarySort,
            activeCollectionId = colId,
            isGridView = settings.libraryGrid,
            showNsfwCovers = settings.showNsfwCovers,
            importing = isImporting,
            message = msg
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onFilterSelect(filter: LibraryFilter) {
        activeFilter.value = filter
        activeCollectionId.value = null
    }

    fun onSortChange(sort: LibrarySort) = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(librarySort = sort) }
    }

    fun toggleNsfw(bookId: String) = viewModelScope.launch { bookRepository.toggleNsfw(bookId) }

    fun dismissMessage() { message.value = null }

    /** Importa archivos elegidos con el selector de documentos (SAF). */
    fun importUris(uris: List<Uri>) = viewModelScope.launch {
        importing.value = true
        var ok = 0
        val errors = ArrayList<String>()
        for (uri in uris) {
            libraryFiles.importUri(uri)
                .onSuccess { bookRepository.upsertBook(it); ok++ }
                .onFailure { errors += "${libraryFiles.displayName(uri)}: ${it.message ?: "error"}" }
        }
        importing.value = false
        message.value = buildString {
            if (ok > 0) append("$ok libro(s) importado(s)")
            if (errors.isNotEmpty()) { if (isNotEmpty()) append(". "); append("No se pudo: ${errors.joinToString("; ")}") }
        }.ifBlank { null }
    }

    fun onRemoveFromCollection(collection: BookCollection, bookId: String) = viewModelScope.launch {
        manageCollections.removeBook(collection, bookId)
    }

    fun onDeleteCollection(id: String) = viewModelScope.launch {
        if (activeCollectionId.value == id) activeCollectionId.value = null
        manageCollections.deleteCollection(id)
    }

    fun onCollectionSelect(collectionId: String?) {
        activeCollectionId.value = if (activeCollectionId.value == collectionId) null else collectionId
    }

    fun toggleViewMode() = viewModelScope.launch {
        settingsRepository.updateSettings { it.copy(libraryGrid = !it.libraryGrid) }
    }

    fun onToggleFavorite(bookId: String) = viewModelScope.launch {
        toggleFavorite(bookId)
    }

    fun onRenameBook(bookId: String, newTitle: String) = viewModelScope.launch {
        renameBook(bookId, newTitle)
    }

    fun onDeleteBook(bookId: String) = viewModelScope.launch {
        deleteBook(bookId)
    }

    fun onCreateCollection(name: String, colorHex: Long) = viewModelScope.launch {
        manageCollections.createCollection(UUID.randomUUID().toString(), name, colorHex)
    }

    fun onAddBookToCollection(collection: BookCollection, bookId: String) = viewModelScope.launch {
        manageCollections.addBookToCollection(collection, bookId)
    }
}
