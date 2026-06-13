@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.example.readerapp.ui.features.reader

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.R
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.database.dictionary.DictionaryEntry
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.data.repository.dictionary.DictionaryRepository
import com.example.readerapp.data.repository.library.LibraryRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Locator.Companion.fromJSON
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.search
import kotlin.math.abs

data class ReaderThemeColors(
    val backgroundColor: Color,
    val textColor: Color,
    val backgroundColorInt: Int
)

data class BookState(
    val title: String = "",
    val chapter: String? = null,
    val progression: Double = 0.0,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ControlsState(
    val showControls: Boolean = false,
    val showToc: Boolean = false,
    val showSettings: Boolean = false,
    val showSearch: Boolean = false
)

data class SelectionState(
    val selectionLocator: Locator? = null,
    val editingNote: NoteEntity? = null,
    val viewingHighlight: NoteEntity? = null
)

data class SearchState(
    val query: String = "",
    val results: List<SearchResultItem> = emptyList(),
    val isLoading: Boolean = false,
    val searchPerformed: Boolean = false,
    val isInNavMode: Boolean = false,
    val activeIndex: Int? = null
)

data class DefinitionState(
    val showDefinition: Boolean = false,
    val definitionWord: String = "",
    val definitionResults: List<DictionaryEntry> = emptyList()
)

class ReaderViewModel(
    private val application: Application,
    private val bookId: String,
    private val repository: LibraryRepository,
    private val readerPreferences: ReaderPreferences,
    private val dictionaryRepository: DictionaryRepository,
    // Passed from the activity so the initial StateFlow values are dark-aware
    // from construction — prevents the white flash before DataStore emits.
    initialSystemDark: Boolean = false
) : ViewModel() {

    // Publication (opened by openBook, closed by closeBook)
    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication.asStateFlow()

    // Cached positions for progression seeking
    private val _positions = MutableStateFlow<List<Locator>>(emptyList())

    // Initial locator for navigator creation (loaded once)
    var initialLocator: Locator? = null
        private set

    // Split UI states
    private val _bookState = MutableStateFlow(BookState())
    val bookState: StateFlow<BookState> = _bookState.asStateFlow()

    private val _controlsState = MutableStateFlow(ControlsState())
    val controlsState: StateFlow<ControlsState> = _controlsState.asStateFlow()

    private val _selectionState = MutableStateFlow(SelectionState())
    val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _definitionState = MutableStateFlow(DefinitionState())
    val definitionState: StateFlow<DefinitionState> = _definitionState.asStateFlow()

    // Search — one-shot navigation events; buffered so emission before
    // the collector is ready is not lost (but NOT replayed on reconnect).
    private val _navigateToLocator = MutableSharedFlow<Locator>(extraBufferCapacity = 1)
    val navigateToLocator: SharedFlow<Locator> = _navigateToLocator.asSharedFlow()

    private val _clearSelectionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearSelectionEvent: SharedFlow<Unit> = _clearSelectionEvent.asSharedFlow()

    // Active search job (cancelled when a new search starts or search is closed)
    private var searchJob: Job? = null

    // Current locator from navigator
    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    // Bookmarks for the current book
    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.getBookmarks(bookId)
        .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    // All raw notes from DB
    private val allNotes: StateFlow<List<NoteEntity>> = repository.getNotes(bookId)
        .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    // Notes for the current book (text is not empty)
    val notes: StateFlow<List<NoteEntity>> =
        allNotes.map { list -> list.filter { it.noteText.isNotBlank() } }
            .stateIn(viewModelScope, WhileSubscribed(5000), emptyList())

    // The combined flow for applying decorations in UI
    val allNotesAndHighlights = allNotes

    // Derived bookmark status
    val isBookmarked: StateFlow<Boolean> =
        combine(_currentLocator, bookmarks) { locator, bookmarksList ->
            if (locator == null) return@combine false
            bookmarksList.any { bookmark ->
                try {
                    val bmLocator = fromJSON(JSONObject(bookmark.locatorJson))
                    // Check for similar position (Readium standard comparison)
                    (bmLocator?.href == locator.href) && (bmLocator.locations.totalProgression == locator.locations.totalProgression)
                } catch (_: Exception) {
                    false
                }
            }
        }.stateIn(viewModelScope, WhileSubscribed(5000), false)

    // Settings flow from DataStore
    val settingsFlow: Flow<ReaderSettings> = readerPreferences.readerSettings

    // Brightness (app-level setting).
    // Initial value is -1.0f ("use system brightness") — the correct value for the default
    // autoBrightness=true setting — to prevent the screen spiking to full brightness on launch
    // before DataStore emits. Eagerly starts the DataStore read at ViewModel construction.
    val brightness: StateFlow<Float> =
        readerPreferences.readerSettings.map { if (it.autoBrightness) -1.0f else it.brightness }
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1.0f)

    // System dark theme state — initialised to the value at activity creation so the
    // StateFlows that depend on it have a correct initial value before DataStore emits.
    val systemDarkThemeFlow = MutableStateFlow(initialSystemDark)

    // Combined theme colors flow
    val themeColors: StateFlow<ReaderThemeColors> = combine(
        settingsFlow, systemDarkThemeFlow
    ) { settings, isSystemDark ->
        val uiDarkTheme = when (settings.themeMode) {
            "Dark" -> true
            "Light" -> false
            else -> isSystemDark
        }

        val bgColorInt = when (settings.readerThemePreset) {
            "Light" -> 0xFFFFFFFF.toInt()
            "Warm" -> 0xFFFAF4E8.toInt()
            "Dark" -> 0xFF000000.toInt()
            "Auto" -> if (uiDarkTheme) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            else -> try {
                settings.customBackgroundColor.toColorInt()
            } catch (_: Exception) {
                android.graphics.Color.WHITE
            }
        }

        val textColorInt = when (settings.readerThemePreset) {
            "Light" -> 0xFF000000.toInt()
            "Warm" -> 0xFF121212.toInt()
            "Dark" -> 0xFFFFFFFF.toInt()
            "Auto" -> if (uiDarkTheme) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            else -> try {
                settings.customTextColor.toColorInt()
            } catch (_: Exception) {
                if (uiDarkTheme) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }

        ReaderThemeColors(
            backgroundColor = Color(bgColorInt),
            textColor = Color(textColorInt),
            backgroundColorInt = bgColorInt
        )
    }.stateIn(
        viewModelScope,
        WhileSubscribed(5000),
        // Dark-aware initial value: prevents the Compose overlay and window background
        // from briefly flashing white while DataStore emits the real settings.
        run {
            val bgColorInt = if (initialSystemDark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            val textColorInt = if (initialSystemDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            ReaderThemeColors(Color(bgColorInt), Color(textColorInt), bgColorInt)
        }
    )

    // EpubPreferences built from settings.
    // Uses Eagerly so the DataStore read starts immediately at ViewModel construction —
    // this ensures epubPreferences.value holds the real (dark-mode-correct) preferences
    // by the time setupNavigator() reads it synchronously as initialPreferences.
    val epubPreferences: StateFlow<EpubPreferences> = combine(
        readerPreferences.readerSettings, systemDarkThemeFlow
    ) { settings, isDark ->
        settings.toEpubPreferences(isDark)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ReaderSettings().toEpubPreferences(initialSystemDark)
    )

    // Table of contents from publication
    val tableOfContents: List<Link>
        get() = _publication.value?.tableOfContents ?: emptyList()

    fun openBook() {
        viewModelScope.launch {
            _bookState.update { it.copy(isLoading = true, error = null) }

            try {
                // Load initial locator and book entity in parallel — both are independent DB hits
                val (resolvedLocator, book) = coroutineScope {
                    val locatorDeferred = async { repository.getLastLocator(bookId) }
                    val bookDeferred = async { repository.getBook(bookId) }
                    locatorDeferred.await() to bookDeferred.await()
                }
                initialLocator = resolvedLocator

                if (book == null) {
                    val errorMsg = application.getString(R.string.book_not_found)
                    _bookState.update { it.copy(isLoading = false, error = errorMsg) }
                    return@launch
                }

                // Open publication
                val pub = repository.openPublication(book)
                if (pub == null) {
                    _bookState.update { it.copy(isLoading = false, error = "Could not open book") }
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

                _bookState.update {
                    it.copy(
                        isLoading = false, title = pub.metadata.title ?: book.book.title
                    )
                }
            } catch (e: Exception) {
                _bookState.update {
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
            byTotal ?: allPositions.indexOfLast {
                it.href == locator.href && (it.locations.progression
                    ?: 0.0) <= (locator.locations.progression ?: 0.0)
            }.takeIf { it != -1 } ?: allPositions.indexOfFirst { it.href == locator.href }
        } else -1

        _bookState.update {
            it.copy(
                chapter = locator.title,
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

    fun getPositionLabel(locator: Locator): String {
        val allPositions = _positions.value

        val posIndex = locator.locations.totalProgression?.let { target ->
            allPositions.indexOfLast { (it.locations.totalProgression ?: -1.0) <= target }
        }?.takeIf { it != -1 } ?: allPositions.indexOfLast { pos ->
            pos.href == locator.href && (pos.locations.progression
                ?: 0.0) <= (pos.locations.progression ?: 0.0)
        }.takeIf { it != -1 }

        return when {
            posIndex != null -> application.resources.getQuantityString(
                R.plurals.reader_page_num, posIndex + 1, posIndex + 1
            )

            locator.locations.totalProgression != null -> application.getString(
                R.string.reader_position_at,
                "${(locator.locations.totalProgression!! * 100).toInt()}%"
            )

            else -> ""
        }
    }

    fun getChapterPageLabel(link: Link): String {
        val allPositions = _positions.value
        if (allPositions.isEmpty()) return ""
        val linkHref = link.href.toString().substringBefore("#")
        val posIndex = allPositions.indexOfFirst {
            it.href.toString().substringBefore("#") == linkHref
        }
        return if (posIndex != -1) application.resources.getQuantityString(
            R.plurals.reader_page_num, posIndex + 1, posIndex + 1
        ) else ""
    }

    fun savePosition(locator: Locator) {
        viewModelScope.launch {
            repository.saveReadingPosition(bookId, locator)
        }
    }

    fun toggleControls() {
        _controlsState.update { it.copy(showControls = !it.showControls) }
    }

    fun toggleBookmark() {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch {
            // Find existing bookmark at this position
            val existing = bookmarks.value.find { bookmark ->
                try {
                    val bmLocator = fromJSON(JSONObject(bookmark.locatorJson))
                    (bmLocator?.href == locator.href) && (bmLocator.locations.totalProgression == locator.locations.totalProgression)
                } catch (_: Exception) {
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

    fun addNote(noteText: String, color: Int = "#40FFEB3B".toColorInt()) {
        val locator = _currentLocator.value ?: return
        viewModelScope.launch {
            repository.addNote(
                bookId, locator, noteText, color, locator.title ?: _bookState.value.chapter
            )
        }
    }

    fun addNoteAndEdit(locator: Locator) {
        val newNote = NoteEntity(
            id = 0, // Unsaved
            bookId = bookId,
            locatorJson = locator.toJSON().toString(),
            chapterTitle = locator.title ?: _bookState.value.chapter,
            noteText = "",
            color = "#40FFEB3B".toColorInt()
        )
        editNote(newNote)
    }

    fun showSelectionMenu(locator: Locator) {
        _selectionState.update { it.copy(selectionLocator = locator) }
    }

    fun hideSelectionMenu() {
        _selectionState.update { it.copy(selectionLocator = null) }
        _clearSelectionEvent.tryEmit(Unit)
    }

    fun dismissSelectionBar() {
        _selectionState.update { it.copy(selectionLocator = null) }
    }

    fun addHighlight(locator: Locator, color: Int = "#4003A9F4".toColorInt()) {
        viewModelScope.launch {
            repository.addNote(
                bookId, locator, "", color, locator.title ?: _bookState.value.chapter
            )
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.removeNote(noteId)
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            if (note.id == 0L) {
                repository.addNote(
                    bookId = bookId,
                    locator = fromJSON(JSONObject(note.locatorJson))!!,
                    noteText = note.noteText,
                    color = note.color,
                    chapterTitle = note.chapterTitle
                )
            } else {
                repository.updateNote(note)
            }
        }
    }

    fun editNote(note: NoteEntity) {
        _selectionState.update { it.copy(editingNote = note) }
    }

    fun hideEditNote() {
        _selectionState.update { it.copy(editingNote = null) }
    }

    fun viewHighlight(note: NoteEntity) {
        _selectionState.update { it.copy(viewingHighlight = note) }
    }

    fun hideViewHighlight() {
        _selectionState.update { it.copy(viewingHighlight = null) }
    }

    fun lookupDefinition(word: String) {
        val cleanWord = word.trim().replace(Regex("[^\\w\\s-]"), "")
        if (cleanWord.isBlank()) return

        viewModelScope.launch {
            val activeDictId = readerPreferences.readerSettings.first().activeDictionaryId
            val results = dictionaryRepository.lookupWord(activeDictId, cleanWord)
            _definitionState.update {
                it.copy(
                    showDefinition = true, definitionWord = cleanWord, definitionResults = results
                )
            }
        }
    }

    fun hideDefinition() {
        _definitionState.update { it.copy(showDefinition = false) }
    }

    fun showToc() {
        _controlsState.update { it.copy(showToc = true) }
    }

    fun hideToc() {
        _controlsState.update { it.copy(showToc = false) }
    }

    fun showSettings() {
        _controlsState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _controlsState.update { it.copy(showSettings = false) }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun showSearch(clearState: Boolean = true) {
        _controlsState.update { it.copy(showSearch = true) }
        if (clearState) {
            _searchState.update {
                it.copy(
                    query = "",
                    results = emptyList(),
                    isLoading = false,
                    searchPerformed = false,
                    activeIndex = null,
                    isInNavMode = false
                )
            }
        }
    }

    fun hideSearch() {
        searchJob?.cancel()
        _controlsState.update { it.copy(showSearch = false) }
    }

    fun updateSearchQuery(query: String) {
        _searchState.update { it.copy(query = query, searchPerformed = false) }
    }

    fun performSearch(query: String) {
        val publication = _publication.value ?: return
        if (query.isBlank()) {
            _searchState.update {
                it.copy(
                    query = query,
                    results = emptyList(),
                    isLoading = false,
                    searchPerformed = false,
                    isInNavMode = false,
                    activeIndex = null
                )
            }
            return
        }

        // Cancel any in-flight search
        searchJob?.cancel()
        _searchState.update {
            it.copy(
                query = query,
                results = emptyList(),
                isLoading = true,
                searchPerformed = true,
                isInNavMode = false,
                activeIndex = null
            )
        }

        searchJob = viewModelScope.launch {
            try {
                val iterator = publication.search(query)
                if (iterator == null) {
                    _searchState.update { it.copy(isLoading = false) }
                    return@launch
                }

                val allPositions = _positions.value

                // Drain iterator page by page; next() returns Try<LocatorCollection, SearchError>
                while (true) {
                    val result = iterator.next()
                    val page = result.getOrNull() ?: break
                    val newItems = page.locators.map { locator ->
                        val posIndex = locator.locations.totalProgression?.let { target ->
                            allPositions.indexOfLast {
                                (it.locations.totalProgression ?: -1.0) <= target
                            }
                        }?.takeIf { it != -1 } ?: allPositions.indexOfLast { pos ->
                            pos.href == locator.href && (pos.locations.progression
                                ?: 0.0) <= (pos.locations.progression ?: 0.0)
                        }.takeIf { it != -1 }

                        val positionLabel = when {
                            posIndex != null -> application.resources.getQuantityString(
                                R.plurals.reader_page_num, posIndex + 1, posIndex + 1
                            )

                            locator.locations.totalProgression != null -> application.getString(
                                R.string.reader_position_at,
                                "${(locator.locations.totalProgression!! * 100).toInt()}%"
                            )

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

                    _searchState.update { state ->
                        state.copy(results = state.results + newItems)
                    }
                }

                _searchState.update { it.copy(isLoading = false) }
            } catch (_: Exception) {
                _searchState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun selectSearchResult(index: Int) {
        val results = _searchState.value.results
        if (index < 0 || index >= results.size) return
        _searchState.update {
            it.copy(
                activeIndex = index,
                isInNavMode = true
            )
        }
        _controlsState.update {
            it.copy(
                showSearch = false,
                showControls = true   // show controls so search helper bar is visible
            )
        }
        _navigateToLocator.tryEmit(results[index].locator)
    }

    fun nextSearchResult() {
        val state = _searchState.value
        val results = state.results
        if (results.isEmpty()) return
        val next = ((state.activeIndex ?: -1) + 1).coerceAtMost(results.size - 1)
        _searchState.update { it.copy(activeIndex = next) }
        _navigateToLocator.tryEmit(results[next].locator)
    }

    fun prevSearchResult() {
        val state = _searchState.value
        val results = state.results
        if (results.isEmpty()) return
        val prev = ((state.activeIndex ?: 1) - 1).coerceAtLeast(0)
        _searchState.update { it.copy(activeIndex = prev) }
        _navigateToLocator.tryEmit(results[prev].locator)
    }

    fun exitSearchNavigation() {
        _searchState.update { it.copy(isInNavMode = false, activeIndex = null) }
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
        private val application: Application,
        private val bookId: String,
        private val initialSystemDark: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = application as ReaderApplication
            return ReaderViewModel(
                application = app,
                bookId = bookId,
                repository = app.libraryRepository,
                readerPreferences = app.readerPreferences,
                dictionaryRepository = app.dictionaryRepository,
                initialSystemDark = initialSystemDark
            ) as T
        }
    }
}
