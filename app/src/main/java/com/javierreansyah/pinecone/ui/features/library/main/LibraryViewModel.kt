package com.javierreansyah.pinecone.ui.features.library.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.libraryPreferenceDefaults
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.domain.DeleteBooks
import com.javierreansyah.pinecone.ui.features.library.BookActions
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.LibraryView
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.mapAndSortShelves
import com.javierreansyah.pinecone.ui.features.library.selectSort
import com.javierreansyah.pinecone.ui.features.library.toggleFormat
import com.javierreansyah.pinecone.ui.features.library.toggleShelf
import com.javierreansyah.pinecone.ui.features.library.toggleStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val bookRepository: LibraryRepository,
    deletePublications: DeleteBooks,
    private val prefsManager: LibraryPreferencesManager,
    private val readerPreferences: ReaderPreferences,
    @com.javierreansyah.pinecone.di.DefaultDispatcher private val computation: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    val bookActions = BookActions(viewModelScope, bookRepository, deletePublications)

    private val screenKey = "library_books"

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            bookPreferences = libraryPreferenceDefaults(screenKey),
            shelvesPreferences = libraryPreferenceDefaults("library_shelves")
        )
    )

    private val _isBooksLoading = MutableStateFlow(value = true)
    private val _isShelvesLoading = MutableStateFlow(value = true)

    private val globalSpaceId: StateFlow<String?> = prefsManager.getEffectiveSpaceFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    private val allBooks: StateFlow<List<Book>> =
        globalSpaceId.flatMapLatest { spaceId ->
            bookRepository.getVisibleBooks(spaceId)
                .map { entities -> entities.map(Book::fromEntity) }
                .flowOn(computation)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val allSpaces: StateFlow<List<SpaceEntity>> = bookRepository.getAllSpaces().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val allShelvesEntities: StateFlow<List<ShelfEntity>> =
        bookRepository.searchShelves("").stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    init {
        // Bound prefetch to eight covers and use the same decode/cache policy as displayed images.
        viewModelScope.launch {
            val coverMode = readerPreferences.readerSettings.first().coverDisplayMode
            allBooks.first { it.isNotEmpty() }.let { books ->
                books.take(8)
                    .forEach { book ->
                        val coverPath = book.coverPath
                        if (coverPath != null) {
                            val request =
                                com.javierreansyah.pinecone.ui.features.library.components.book.coverRequest(
                                    application,
                                    coverPath,
                                    coil3.size.SizeResolver(coil3.size.Size(400, 600)),
                                    coverMode,
                                )
                            application.imageLoader.enqueue(request)
                        }
                    }
            }
        }

        viewModelScope.launch {
            globalSpaceId.flatMapLatest { spaceId ->
                combine(
                    prefsManager.preferences(spaceId, screenKey),
                    prefsManager.preferences(spaceId, "library_shelves"),
                ) { books, shelves -> books to shelves }
            }.collect { (books, shelves) ->
                _uiState.update { it.copy(bookPreferences = books, shelvesPreferences = shelves) }
            }
        }
    }

    private val filteredBooks: StateFlow<List<Book>> = combine(
        allBooks,
        _uiState.map { it.bookPreferences }.distinctUntilChanged(),
    ) { books, preferences ->
        books.filterAndSort(preferences)
    }.flowOn(computation).onEach { _isBooksLoading.value = false }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(),
        bookRepository.getAllShelfBookCrossRefs(),
        bookRepository.getAllBooks(),
        _uiState.map { it.shelvesPreferences }.distinctUntilChanged(),
        globalSpaceId,
    ) { shelvesList, crossRefs, allBooksEntities, preferences, spaceId ->
        mapAndSortShelves(
            shelvesList = shelvesList,
            crossRefs = crossRefs,
            allBooksEntities = allBooksEntities,
            prefs = preferences,
            unshelvedLabel = application.getString(R.string.library_label_unshelved),
            globalSpaceId = spaceId,
        )
    }.flowOn(computation).onEach { _isShelvesLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery: Flow<String> = _uiState
        .map { state -> state.searchQuery }
        .distinctUntilChanged()
        .debounce(300L.milliseconds)

    private val searchResults: StateFlow<SearchResults> = combine(
        debouncedSearchQuery,
        _uiState.map { it.searchCategory }.distinctUntilChanged(),
        allBooks,
        shelves,
    ) { query, category, books, shelfList ->
        searchLibrary(books, shelfList.map { it.shelf }, query, category)
    }.flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

    private val hasUnsortedBooks: Flow<Boolean> = bookRepository.hasUnsortedBooks()

    private val librarySettings = readerPreferences.readerSettings

    private val contentState = combine(
        filteredBooks, shelves, allBooks, searchResults,
    ) { books, shelfList, all, results ->
        LibraryScreenUiState(
            filteredBooks = books,
            shelves = shelfList,
            allBooks = all,
            searchResults = results,
        )
    }

    private val navigationState = combine(
        allSpaces, allShelvesEntities, globalSpaceId, hasUnsortedBooks, librarySettings,
    ) { spaces, shelfEntities, spaceId, hasUnsorted, settings ->
        LibraryScreenUiState(
            allSpaces = spaces,
            allShelves = shelfEntities,
            globalSpaceId = spaceId,
            hasUnsortedBooks = hasUnsorted && settings.unsortedEnabled,
            showAllSpaces = settings.showAllSpaces,
            spacesEnabled = settings.spacesEnabled,
            showLibraryBottomNavigation = settings.showLibraryBottomNavigation,
        )
    }

    val uiState: StateFlow<LibraryScreenUiState> = combine(
        _uiState, contentState, navigationState, _isBooksLoading, _isShelvesLoading,
    ) { state, content, navigation, booksLoading, shelvesLoading ->
        content.copy(
            searchQuery = state.searchQuery,
            searchCategory = state.searchCategory,
            bookPreferences = state.bookPreferences,
            shelvesPreferences = state.shelvesPreferences,
            isBooksLoading = booksLoading,
            isShelvesLoading = shelvesLoading,
            allSpaces = navigation.allSpaces,
            allShelves = navigation.allShelves,
            globalSpaceId = navigation.globalSpaceId,
            hasUnsortedBooks = navigation.hasUnsortedBooks,
            showAllSpaces = navigation.showAllSpaces,
            spacesEnabled = navigation.spacesEnabled,
            showLibraryBottomNavigation = navigation.showLibraryBottomNavigation,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryScreenUiState())

    fun deleteShelves(shelfIds: Collection<String>) {
        viewModelScope.launch {
            shelfIds.forEach { bookRepository.deleteShelf(it) }
        }
    }

    fun renameShelf(shelfId: String, newName: String) {
        viewModelScope.launch {
            bookRepository.renameShelf(shelfId, newName)
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchCategoryChange(category: SearchCategory) {
        _uiState.update { it.copy(searchCategory = category) }
    }

    private fun updatePreferences(
        isShelvesTab: Boolean,
        transform: (FilterSortPreferences) -> FilterSortPreferences,
    ) {
        val spaceId = globalSpaceId.value
        val key = if (isShelvesTab) "library_shelves" else screenKey
        viewModelScope.launch { prefsManager.updatePreferences(spaceId, key, transform) }
    }

    fun onLayoutModeChange(mode: LayoutMode, isShelvesTab: Boolean = false) =
        updatePreferences(isShelvesTab) { it.copy(layoutMode = mode) }

    fun onSortTypeChange(sortType: SortType, isShelvesTab: Boolean = false) =
        updatePreferences(isShelvesTab) { it.selectSort(sortType) }

    fun toggleStatusFilter(status: StatusFilter, isShelvesTab: Boolean = false) =
        updatePreferences(isShelvesTab) { it.toggleStatus(status) }

    fun toggleShelfFilter(filter: ShelfFilter, isShelvesTab: Boolean = false) =
        updatePreferences(isShelvesTab) { it.toggleShelf(filter) }

    fun toggleFileFilter(format: FileFilter, isShelvesTab: Boolean = false) =
        updatePreferences(isShelvesTab) { it.toggleFormat(format) }

    fun onLibraryViewChange(view: LibraryView) =
        updatePreferences(false) { it.copy(libraryView = view) }

    fun transferShelves(ids: Collection<String>, destinationId: String, copy: Boolean) {
        viewModelScope.launch { bookRepository.transferShelves(ids, destinationId, copy) }
    }

    fun setGlobalSpace(spaceId: String?) {
        viewModelScope.launch { prefsManager.setGlobalSpace(spaceId) }
    }

}
