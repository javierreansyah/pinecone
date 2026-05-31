@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.example.readerapp.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.data.local.BookmarkEntity
import com.example.readerapp.data.local.NoteEntity
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.ReaderSettings
import com.example.readerapp.data.repository.BookRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.SearchService
import org.readium.r2.shared.publication.services.search.search

class ReaderViewModel(
    private val bookId: String,
    private val repository: BookRepository,
    private val readerPreferences: ReaderPreferences
) : ViewModel() {

    // Publication (opened by openBook, closed by closeBook)
    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication.asStateFlow()

    // Cached positions for progression seeking
    private val _positions = MutableStateFlow<List<Locator>>(emptyList())
    val positions: StateFlow<List<Locator>> = _positions.asStateFlow()

    // Initial locator for navigator creation (loaded once)
    var initialLocator: Locator? = null
        private set

    // UI state
    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    // Search — one-shot navigation events; buffered so emission before
    // the collector is ready is not lost (but NOT replayed on reconnect).
    private val _navigateToLocator = MutableSharedFlow<Locator>(extraBufferCapacity = 1)
    val navigateToLocator: SharedFlow<Locator> = _navigateToLocator.asSharedFlow()

    // Active search job (cancelled when a new search starts or search is closed)
    private var searchJob: Job? = null

    // Current locator from navigator
    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    // Bookmarks for the current book
    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.getBookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Notes for the current book
    val notes: StateFlow<List<NoteEntity>> = repository.getNotes(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Derived bookmark status
    val isBookmarked: StateFlow<Boolean> = combine(_currentLocator, bookmarks) { locator, bookmarksList ->
        if (locator == null) return@combine false
        bookmarksList.any { bookmark ->
            try {
                val bmLocator = org.json.JSONObject(bookmark.locatorJson).let {
                    Locator.fromJSON(it)
                }
                // Check for similar position (Readium standard comparison)
                bmLocator?.href == locator.href && 
                bmLocator?.locations?.totalProgression == locator.locations.totalProgression
            } catch (e: Exception) {
                false
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Settings flow from DataStore
    val settingsFlow: Flow<ReaderSettings> = readerPreferences.readerSettings

    // Brightness (app-level setting)
    val brightness: StateFlow<Float> = readerPreferences.readerSettings
        .map { if (it.autoBrightness) -1.0f else it.brightness }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    // EpubPreferences built from settings
    val epubPreferences: StateFlow<EpubPreferences> = readerPreferences.readerSettings
        .map { it.toEpubPreferences() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EpubPreferences())

    // Table of contents from publication
    val tableOfContents: List<Link>
        get() = _publication.value?.tableOfContents ?: emptyList()

    fun openBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // Load initial locator
                initialLocator = repository.getLastLocator(bookId)

                // Get book entity
                val book = repository.getBook(bookId)
                if (book == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Book not found") }
                    return@launch
                }

                // Open publication
                val pub = repository.openPublication(book)
                if (pub == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Could not open book") }
                    return@launch
                }

                _publication.value = pub

                // Warm up positions to enable totalProgression calculation
                viewModelScope.launch {
                    val p = pub.positions()
                    _positions.value = p
                    // Refresh current locator to update page numbers once positions are loaded
                    _currentLocator.value?.let { onLocatorChanged(it) }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        bookTitle = pub.metadata.title ?: book.title
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Error: ${e.message}")
                }
            }
        }
    }

    fun onLocatorChanged(locator: Locator) {
        _currentLocator.value = locator
        
        val allPositions = _positions.value
        val pageIndex = if (allPositions.isNotEmpty()) {
            // 1. Try to find by total progression (more robust across chapters)
            val byTotal = locator.locations.totalProgression?.let { target ->
                allPositions.indexOfLast { (it.locations.totalProgression ?: -1.0) <= target }
            }?.takeIf { it != -1 }

            // 2. Fallback to href + internal progression
            byTotal ?: allPositions.indexOfLast { it.href == locator.href && (it.locations.progression ?: 0.0) <= (locator.locations.progression ?: 0.0) }
                .takeIf { it != -1 } 
                ?: allPositions.indexOfFirst { it.href == locator.href }
        } else -1

        _uiState.update {
            it.copy(
                currentChapter = locator.title,
                progression = locator.locations.totalProgression ?: 0.0,
                currentPage = if (pageIndex != -1) pageIndex + 1 else null,
                totalPages = if (allPositions.isNotEmpty()) allPositions.size else null
            )
        }
    }

    fun locatorForProgression(targetProgression: Double): Locator? {
        val boundedProgression = targetProgression.coerceIn(0.0, 1.0)
        val candidates = _positions.value
        if (candidates.isEmpty()) return null
        return candidates.minByOrNull { locator ->
            abs((locator.locations.totalProgression ?: 0.0) - boundedProgression)
        }
    }

    fun savePosition(locator: Locator) {
        viewModelScope.launch {
            repository.saveReadingPosition(bookId, locator)
        }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun toggleBookmark() {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch {
            // Find existing bookmark at this position
            val existing = bookmarks.value.find { bookmark ->
                try {
                    val bmLocator = org.json.JSONObject(bookmark.locatorJson).let {
                        Locator.fromJSON(it)
                    }
                    bmLocator?.href == locator.href && 
                    bmLocator?.locations?.totalProgression == locator.locations.totalProgression
                } catch (e: Exception) {
                    false
                }
            }

            if (existing != null) {
                repository.removeBookmark(existing.id)
            } else {
                repository.addBookmark(bookId, locator)
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.removeBookmark(bookmarkId)
        }
    }

    fun addNote(noteText: String) {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch {
            repository.addNote(bookId, locator, noteText)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.removeNote(noteId)
        }
    }

    fun showToc() {
        _uiState.update { it.copy(showToc = true) }
    }

    fun hideToc() {
        _uiState.update { it.copy(showToc = false) }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun showSearch() {
        _uiState.update { it.copy(showSearch = true, searchQuery = "", searchResults = emptyList(), searchLoading = false, activeSearchIndex = null, isInSearchNavigationMode = false) }
    }

    fun hideSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(showSearch = false, isInSearchNavigationMode = false, activeSearchIndex = null) }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun performSearch(query: String) {
        val publication = _publication.value ?: return
        if (query.isBlank()) {
            _uiState.update { it.copy(searchQuery = query, searchResults = emptyList(), searchLoading = false) }
            return
        }

        // Cancel any in-flight search
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = query, searchResults = emptyList(), searchLoading = true) }

        searchJob = viewModelScope.launch {
            try {
                val iterator = publication.search(query)
                if (iterator == null) {
                    _uiState.update { it.copy(searchLoading = false) }
                    return@launch
                }

                val allPositions = _positions.value
                val totalPositions = allPositions.size

                // Drain iterator page by page; next() returns Try<LocatorCollection, SearchError>
                while (true) {
                    val result = iterator.next()
                    val page = result?.getOrNull() ?: break
                    val newItems = page.locators.map { locator ->
                        val posIndex = locator.locations.totalProgression?.let { target ->
                            allPositions.indexOfLast { (it.locations.totalProgression ?: -1.0) <= target }
                        }?.takeIf { it != -1 } ?: allPositions.indexOfLast { pos ->
                            pos.href == locator.href &&
                            (pos.locations.progression ?: 0.0) <= (locator.locations.progression ?: 0.0)
                        }.takeIf { it != -1 }

                        val positionLabel = when {
                            posIndex != null && totalPositions > 0 -> "Position ${posIndex + 1} of $totalPositions"
                            locator.locations.totalProgression != null ->
                                "${(locator.locations.totalProgression!! * 100).toInt()}%"
                            else -> ""
                        }

                        SearchResultItem(
                            locator = locator,
                            chapterTitle = locator.title,
                            positionLabel = positionLabel,
                            textBefore = locator.text.before,
                            highlight = locator.text.highlight,
                            textAfter = locator.text.after
                        )
                    }

                    _uiState.update { state ->
                        state.copy(searchResults = state.searchResults + newItems)
                    }
                }

                _uiState.update { it.copy(searchLoading = false) }
            } catch (_: Exception) {
                _uiState.update { it.copy(searchLoading = false) }
            }
        }
    }

    fun selectSearchResult(index: Int) {
        val results = _uiState.value.searchResults
        if (index < 0 || index >= results.size) return
        _uiState.update {
            it.copy(
                activeSearchIndex = index,
                isInSearchNavigationMode = true,
                showSearch = false,
                showControls = true   // show controls so search helper bar is visible
            )
        }
        _navigateToLocator.tryEmit(results[index].locator)
    }

    fun nextSearchResult() {
        val state = _uiState.value
        val results = state.searchResults
        if (results.isEmpty()) return
        val next = ((state.activeSearchIndex ?: -1) + 1).coerceAtMost(results.size - 1)
        _uiState.update { it.copy(activeSearchIndex = next) }
        _navigateToLocator.tryEmit(results[next].locator)
    }

    fun prevSearchResult() {
        val state = _uiState.value
        val results = state.searchResults
        if (results.isEmpty()) return
        val prev = ((state.activeSearchIndex ?: 1) - 1).coerceAtLeast(0)
        _uiState.update { it.copy(activeSearchIndex = prev) }
        _navigateToLocator.tryEmit(results[prev].locator)
    }

    fun exitSearchNavigation() {
        _uiState.update { it.copy(isInSearchNavigationMode = false, activeSearchIndex = null) }
    }

    fun updateSettings(settings: ReaderSettings) {
        viewModelScope.launch {
            readerPreferences.updateAllSettings(settings)
        }
    }

    fun closeBook() {
        _publication.value?.close()
        _publication.value = null
    }

    override fun onCleared() {
        super.onCleared()
        closeBook()
    }

    class Factory(
        private val bookId: String,
        private val repository: BookRepository,
        private val readerPreferences: ReaderPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReaderViewModel(bookId, repository, readerPreferences) as T
        }
    }
}
