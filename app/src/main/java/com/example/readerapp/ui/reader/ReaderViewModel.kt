package com.example.readerapp.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.data.local.BookmarkEntity
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.ReaderSettings
import com.example.readerapp.data.repository.BookRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

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

    // Current locator from navigator
    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    // Bookmarks for the current book
    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.getBookmarks(bookId)
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
            allPositions.indexOfFirst { it.href == locator.href && (it.locations.progression ?: 0.0) >= (locator.locations.progression ?: 0.0) }
                .takeIf { it != -1 } ?: allPositions.indexOfLast { it.href == locator.href }
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
