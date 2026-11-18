package com.flux.reader.presentation.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flux.reader.domain.model.Book
import com.flux.reader.domain.model.DiscoverBook
import com.flux.reader.domain.model.BookSource
import com.flux.reader.domain.model.SourcesState
import com.flux.reader.domain.repository.DiscoverRepository
import com.flux.reader.domain.usecase.GetLibraryBooksUseCase
import com.flux.reader.domain.usecase.SearchDiscoverBooksUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DiscoverUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val results: List<DiscoverBook> = emptyList(),
    val continueReadingBook: Book? = null,
    val errorMessage: String? = null,
    val lastGenre: String? = "Adventure",
    val downloadingIds: Set<String> = emptySet(),
    val progress: Map<String, Float> = emptyMap(),
    val page: Int = 1,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val addedIds: Set<String> = emptySet(),
    val toast: String? = null,
    val sources: SourcesState = SourcesState(),
    val selectedSourceId: String? = null,
    val addingRepo: Boolean = false
) {
    val selectedSource: BookSource? get() = sources.installed.firstOrNull { it.id == selectedSourceId } ?: sources.installed.firstOrNull()
}

/** Mensaje amable para fallas de red (gutendex.com puede tardar o caer). */
internal fun friendlyError(t: Throwable): String = when (t) {
    is io.ktor.client.plugins.HttpRequestTimeoutException,
    is io.ktor.client.network.sockets.ConnectTimeoutException,
    is io.ktor.client.network.sockets.SocketTimeoutException ->
        "La fuente tardó demasiado en responder. Revisa tu conexión e inténtalo de nuevo."
    is java.io.IOException -> "No se pudo conectar con el catálogo. Revisa tu conexión a internet."
    else -> "No se pudo cargar el catálogo: ${t.message ?: "error desconocido"}"
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val searchDiscoverBooks: SearchDiscoverBooksUseCase,
    private val discoverRepository: DiscoverRepository,
    getLibraryBooks: GetLibraryBooksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getLibraryBooks().collect { books ->
                _uiState.update { state ->
                    state.copy(continueReadingBook = books.maxByOrNull { it.lastReadEpochMs })
                }
            }
        }
        viewModelScope.launch {
            discoverRepository.sources.collect { src ->
                val before = _uiState.value.selectedSource
                _uiState.update { it.copy(sources = src) }
                val now = _uiState.value.selectedSource
                // Recarga al instalar la primera fuente o al quitar la seleccionada.
                if (now != null && now != before) loadGenre(_uiState.value.lastGenre ?: "Adventure")
                if (now == null) _uiState.update { it.copy(results = emptyList(), hasMore = false, errorMessage = null, isLoading = false) }
            }
        }
    }

    fun selectSource(id: String) {
        _uiState.update { it.copy(selectedSourceId = id, query = "") }
        loadGenre(_uiState.value.lastGenre ?: "Adventure")
    }

    fun addRepo(url: String) = viewModelScope.launch {
        _uiState.update { it.copy(addingRepo = true) }
        discoverRepository.addRepo(url)
            .onSuccess { r -> _uiState.update { it.copy(toast = "Repositorio «${r.name}» agregado: ${r.sources.size} fuente(s) para instalar") } }
            .onFailure { e -> _uiState.update { it.copy(toast = "No se pudo agregar: ${e.message ?: friendlyError(e)}") } }
        _uiState.update { it.copy(addingRepo = false) }
    }

    fun removeRepo(url: String) = discoverRepository.removeRepo(url)
    fun install(source: BookSource) = discoverRepository.installSource(source)
    fun uninstall(id: String) = discoverRepository.uninstallSource(id)

    fun onQueryChange(newQuery: String) {
        _uiState.update { it.copy(query = newQuery) }
    }

    private var job: kotlinx.coroutines.Job? = null

    /** Carga la página [page] de la búsqueda o del género actual; la 1 reemplaza, las demás se agregan. */
    private fun load(page: Int, search: String?, genre: String?) {
        job?.cancel()
        job = viewModelScope.launch {
            _uiState.update {
                if (page == 1) it.copy(isLoading = true, errorMessage = null, lastGenre = genre, results = emptyList(), page = 1, hasMore = false)
                else it.copy(isLoadingMore = true, errorMessage = null)
            }
            val source = _uiState.value.selectedSource ?: run { _uiState.update { it.copy(isLoading = false, isLoadingMore = false) }; return@launch }
            val r = searchDiscoverBooks.browse(source, search, genre.takeIf { source.engine?.hasGenres == true }, page)
            r.onSuccess { p ->
                _uiState.update { s ->
                    s.copy(isLoading = false, isLoadingMore = false, page = page, hasMore = p.hasMore,
                        results = (if (page == 1) p.books else s.results + p.books).distinctBy { it.id })
                }
            }.onFailure { err -> _uiState.update { it.copy(isLoading = false, isLoadingMore = false, errorMessage = friendlyError(err)) } }
        }
    }

    fun executeSearch() {
        val q = _uiState.value.query.trim()
        if (q.isNotEmpty()) load(1, q, null)
    }

    fun loadGenre(genre: String) = load(1, null, genre)

    fun loadMore() {
        val s = _uiState.value
        if (!s.hasMore || s.isLoading || s.isLoadingMore) return
        load(s.page + 1, if (s.lastGenre == null) s.query.trim() else null, s.lastGenre)
    }

    fun retry() {
        val s = _uiState.value
        val page = if (s.results.isEmpty()) 1 else s.page + 1
        load(page, if (s.lastGenre == null) s.query.trim() else null, s.lastGenre)
    }

    fun dismissToast() = _uiState.update { it.copy(toast = null) }

    fun addToLibrary(book: DiscoverBook) = viewModelScope.launch {
        _uiState.update { it.copy(downloadingIds = it.downloadingIds + book.id) }
        searchDiscoverBooks.importToLibrary(book) { f -> _uiState.update { it.copy(progress = it.progress + (book.id to f)) } }
            .onSuccess { _uiState.update { s -> s.copy(addedIds = s.addedIds + book.id, toast = "«${book.title}» añadido a tu biblioteca") } }
            .onFailure { e -> _uiState.update { s -> s.copy(toast = "No se pudo descargar: ${friendlyError(e)}") } }
        _uiState.update { it.copy(downloadingIds = it.downloadingIds - book.id, progress = it.progress - book.id) }
    }
}
