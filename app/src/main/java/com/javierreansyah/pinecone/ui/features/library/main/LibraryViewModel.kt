package com.javierreansyah.pinecone.ui.features.library.main

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Scale
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.libraryPreferenceDefaults
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.LibraryView
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.mapAndSortShelves
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import com.javierreansyah.pinecone.ui.features.library.selectSort
import com.javierreansyah.pinecone.ui.features.library.toggleFormat
import com.javierreansyah.pinecone.ui.features.library.toggleShelf
import com.javierreansyah.pinecone.ui.features.library.toggleStatus
import dagger.hilt.android.lifecycle.HiltViewModel
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val bookRepository: LibraryRepository,
    private val prefsManager: LibraryPreferencesManager,
    private val readerPreferences: ReaderPreferences,
) : ViewModel() {
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
        // Pre-warm Coil's memory cache with the first 8 book covers as soon as
        // the ViewModel is created. By the time the user taps the search bar the
        // bitmaps are already decoded and cached, so CoverImage renders instantly
        // and nothing competes with the expansion animation on the UI thread.
        viewModelScope.launch {
            allBooks.first { it.isNotEmpty() }.let { books ->
                books.take(8)
                    .forEach { book ->
                        if (book.coverPath != null) {
                            val request = ImageRequest.Builder(application)
                                .data(File(book.coverPath))
                                .size(400, 600)
                                .scale(Scale.FILL)
                                .memoryCacheKey(book.coverPath)
                                .build()
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
    }.onEach { _isBooksLoading.value = false }.stateIn(
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
    }.onEach { _isShelvesLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery: Flow<String> = _uiState
        .map { state -> state.searchQuery }
        .distinctUntilChanged()
        .debounce(300L.milliseconds)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchResults: StateFlow<SearchResults> = combine(
        debouncedSearchQuery,
        _uiState.map { state -> state.searchCategory }
            .distinctUntilChanged(),
    ) { query, category ->
        query to category
    }.flatMapLatest { (query, category) ->
        if (query.isBlank()) {
            combine(allBooks, shelves) { books, shelfList ->
                val matchedBooks =
                    if (category == SearchCategory.All || category == SearchCategory.Books) books else emptyList()
                val matchedShelves =
                    if (category == SearchCategory.All || category == SearchCategory.Shelves) shelfList.map { (itShelf, books1) -> itShelf } else emptyList()
                val matchedAuthors =
                    if (category == SearchCategory.All || category == SearchCategory.Authors) books.asSequence()
                        .flatMap { it.authors }
                        .distinct().toList() else emptyList()
                val matchedTags =
                    if (category == SearchCategory.All || category == SearchCategory.Tags) books.asSequence()
                        .flatMap { it.tags }
                        .distinct().toList() else emptyList()
                SearchResults(
                    contentWording = books.publicationWording(),
                    query = query,
                    books = matchedBooks,
                    shelves = matchedShelves,
                    authors = matchedAuthors,
                    tags = matchedTags,
                )
            }
        } else {
            combine(
                allBooks,
                shelves,
            ) { books, shelvesList ->
                val filteredBooks = books.filter { it.matchesLibraryQuery(query) }
                val matchedBooks =
                    if (category == SearchCategory.All || category == SearchCategory.Books) filteredBooks else emptyList()
                val matchedShelves =
                    if (category == SearchCategory.All || category == SearchCategory.Shelves) shelvesList.filter { (itShelf2, books1) ->
                        itShelf2.name.contains(
                            query,
                            ignoreCase = true,
                        )
                    }.map { (itShelf3, books1) -> itShelf3 } else emptyList()
                val matchedAuthors =
                    if (category == SearchCategory.All || category == SearchCategory.Authors) books
                        .flatMap { it.authors }
                        .distinct()
                        .filter { it.contains(query, ignoreCase = true) } else emptyList()
                val matchedTags =
                    if (category == SearchCategory.All || category == SearchCategory.Tags) books
                        .flatMap { it.tags }
                        .distinct()
                        .filter { it.contains(query, ignoreCase = true) } else emptyList()
                SearchResults(
                    contentWording = books.publicationWording(),
                    query = query,
                    books = matchedBooks,
                    shelves = matchedShelves,
                    authors = matchedAuthors,
                    tags = matchedTags,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

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
            isImporting = state.isImporting,
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

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
        }
    }

    fun deleteBooks(bookIds: Collection<String>) {
        viewModelScope.launch {
            bookIds.forEach { bookRepository.deleteBook(it) }
        }
    }

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

    fun toggleArchive(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleArchive(bookId)
        }
    }

    fun archiveBooks(bookIds: Collection<String>) {
        viewModelScope.launch {
            val currentBooks = allBooks.value.associateBy(Book::id)
            bookIds.forEach { bookId ->
                val book = currentBooks[bookId]
                if (book != null && !book.isArchived) {
                    bookRepository.toggleArchive(bookId)
                }
            }
        }
    }

    fun toggleReadStatus(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleReadStatus(bookId)
        }
    }

    fun markBooksReadStatus(bookIds: Collection<String>, markAsRead: Boolean) {
        viewModelScope.launch {
            val currentBooks = allBooks.value.associateBy(Book::id)
            bookIds.forEach { bookId ->
                val book = currentBooks[bookId]
                if (book != null && book.isRead != markAsRead) {
                    bookRepository.toggleReadStatus(bookId)
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchCategoryChange(category: SearchCategory) {
        _uiState.update { it.copy(searchCategory = category) }
    }

    fun onLayoutModeChange(mode: LayoutMode, isShelvesTab: Boolean = false) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(
                spaceId,
                if (isShelvesTab) "library_shelves" else screenKey
            ) { it.copy(layoutMode = mode) }
        }
    }

    fun onSortTypeChange(sortType: SortType, isShelvesTab: Boolean = false) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(
                spaceId,
                if (isShelvesTab) "library_shelves" else screenKey
            ) { it.selectSort(sortType) }
        }
    }

    fun toggleStatusFilter(status: StatusFilter, isShelvesTab: Boolean = false) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(
                spaceId,
                if (isShelvesTab) "library_shelves" else screenKey
            ) { it.toggleStatus(status) }
        }
    }

    fun toggleShelfFilter(filter: ShelfFilter, isShelvesTab: Boolean = false) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(
                spaceId,
                if (isShelvesTab) "library_shelves" else screenKey
            ) { it.toggleShelf(filter) }
        }
    }

    fun toggleFileFilter(format: FileFilter, isShelvesTab: Boolean = false) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(
                spaceId,
                if (isShelvesTab) "library_shelves" else screenKey
            ) { it.toggleFormat(format) }
        }
    }

    fun onLibraryViewChange(view: LibraryView) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(spaceId, screenKey) { it.copy(libraryView = view) }
        }
    }

    fun transferShelves(ids: Collection<String>, destinationId: String, copy: Boolean) {
        viewModelScope.launch { bookRepository.transferShelves(ids, destinationId, copy) }
    }

    fun setGlobalSpace(spaceId: String?) {
        viewModelScope.launch { prefsManager.setGlobalSpace(spaceId) }
    }

}
