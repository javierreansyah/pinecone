package com.javierreansyah.pinecone.ui.features.library.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Scale
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.LibraryView
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.inSpace
import com.javierreansyah.pinecone.ui.features.library.mapAndSortShelves
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import com.javierreansyah.pinecone.ui.features.library.selectSort
import com.javierreansyah.pinecone.ui.features.library.toggleFormat
import com.javierreansyah.pinecone.ui.features.library.toggleShelf
import com.javierreansyah.pinecone.ui.features.library.toggleStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

class LibraryViewModel(
    application: Application, private val screenKey: String = "library_books",
) : AndroidViewModel(application) {

    companion object {
        fun provideFactory(
            application: Application,
            screenKey: String = "library_books",
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                    return LibraryViewModel(application, screenKey) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class")
            }
        }
    }

    private val bookRepository = (application as PineconeApplication).libraryRepository
    private val prefsManager = LibraryPreferencesManager(application)
    private val readerPreferences = (application as PineconeApplication).readerPreferences

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            bookPreferences = prefsManager.getPreferences(
                screenKey = screenKey,
                defaultSort = when (screenKey) {
                    "shelf_detail" -> SortType.Custom
                    "library_books" -> SortType.LastRead
                    else -> SortType.Added
                },
                defaultAscending = screenKey == "shelf_detail",
            ),
            shelvesPreferences = prefsManager.getPreferences(
                "library_shelves",
                defaultLayout = LayoutMode.BigList,
                defaultSort = SortType.Title,
                defaultAscending = true,
            ),
        ),
    )
    private val _isBooksLoading = MutableStateFlow(value = true)
    private val _isShelvesLoading = MutableStateFlow(value = true)

    private val booksFlow: Flow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    private val globalSpaceId: StateFlow<String?> = prefsManager.getEffectiveSpaceFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    private val allBooks: StateFlow<List<Book>> =
        combine(booksFlow, globalSpaceId) { books, spaceId ->
            books.inSpace(spaceId)
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

    private val allCrossRefs = bookRepository.getAllShelfBookCrossRefs().stateIn(
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
            allBooks.take(1).collect { books ->
                books.take(8).forEach { (_, _, _, bookCoverPath, progress, lastOpened, language, addedDate, isArchived, description, publisher, published, tags, identifier, mediaType, filePath, isRead, furthestProgression, lastLocatorJson, furthestLocatorJson, spaces, metadata, credits, activeRendition, isUnsorted, originalFilename) ->
                    if (bookCoverPath != null) {
                        val request = ImageRequest.Builder(application)
                            .data(File(bookCoverPath))
                            .size(400, 600)
                            .scale(Scale.FILL)
                            .memoryCacheKey(bookCoverPath)
                            .build()
                        application.imageLoader.enqueue(request)
                    }
                }
            }
        }

        viewModelScope.launch {
            globalSpaceId.collect { spaceId ->
                _uiState.update { state ->
                    state.copy(
                        bookPreferences = prefsManager.getPreferences(
                            spaceId = spaceId,
                            screenKey = screenKey,
                            defaultSort = when (screenKey) {
                                "shelf_detail" -> SortType.Custom
                                "library_books" -> SortType.LastRead
                                else -> SortType.Added
                            },
                            defaultAscending = screenKey == "shelf_detail",
                        ),
                        shelvesPreferences = prefsManager.getPreferences(
                            spaceId = spaceId,
                            screenKey = "library_shelves",
                            defaultLayout = LayoutMode.BigList,
                            defaultSort = SortType.Title,
                            defaultAscending = true,
                        ),
                    )
                }
            }
        }
    }

    private fun getFilteredAndSortedBooks(baseFlow: Flow<List<Book>>): Flow<List<Book>> {
        return combine(
            baseFlow,
            _uiState,
            allCrossRefs
        ) { books, (_, _, _, stateBookPreferences, _), _ ->
            books.filterAndSort(stateBookPreferences)
        }
    }

    private val filteredBooks: StateFlow<List<Book>> =
        getFilteredAndSortedBooks(allBooks).onEach { _isBooksLoading.value = false }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList(),
        )

    private val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(),
        bookRepository.getAllShelfBookCrossRefs(),
        bookRepository.getAllBooks(),
        _uiState,
        globalSpaceId,
    ) { shelvesList, crossRefs, allBooksEntities, (_, _, _, _, stateShelvesPreferences), spaceId ->
        mapAndSortShelves(
            shelvesList = shelvesList,
            crossRefs = crossRefs,
            allBooksEntities = allBooksEntities,
            prefs = stateShelvesPreferences,
            unshelvedLabel = application.getString(R.string.library_label_unshelved),
            globalSpaceId = spaceId,
        )
    }.onEach { _isShelvesLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class)
    private val debouncedSearchQuery: Flow<String> = _uiState
        .map { (itSearchQuery, searchCategory, isImporting, bookPreferences, shelvesPreferences) -> itSearchQuery }
        .debounce(300L.milliseconds)
        .distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val searchResults: StateFlow<SearchResults> = combine(
        debouncedSearchQuery,
        _uiState.map { (_, itSearchCategory, isImporting, bookPreferences, shelvesPreferences) -> itSearchCategory }.distinctUntilChanged(),
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

    private val hasUnsortedBooks: Flow<Boolean> = booksFlow.map { books ->
        books.any { it.isUnsorted }
    }

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
        _uiState.update { state ->
            val prefs =
                if (isShelvesTab) state.shelvesPreferences.copy(layoutMode = mode) else state.bookPreferences.copy(
                    layoutMode = mode,
                )
            prefsManager.savePreferences(
                globalSpaceId.value,
                if (isShelvesTab) "library_shelves" else screenKey,
                prefs,
            )
            if (isShelvesTab) state.copy(shelvesPreferences = prefs) else state.copy(bookPreferences = prefs)
        }
    }

    fun onSortTypeChange(sortType: SortType, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val currentPrefs = if (isShelvesTab) state.shelvesPreferences else state.bookPreferences
            val newPrefs = currentPrefs.selectSort(sortType)
            prefsManager.savePreferences(
                globalSpaceId.value, if (isShelvesTab) "library_shelves" else screenKey, newPrefs,
            )
            if (isShelvesTab) state.copy(shelvesPreferences = newPrefs) else state.copy(
                bookPreferences = newPrefs,
            )
        }
    }

    fun toggleStatusFilter(status: StatusFilter, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val currentPrefs = if (isShelvesTab) state.shelvesPreferences else state.bookPreferences
            val newPrefs = currentPrefs.toggleStatus(status)
            prefsManager.savePreferences(
                globalSpaceId.value, if (isShelvesTab) "library_shelves" else screenKey, newPrefs,
            )
            if (isShelvesTab) state.copy(shelvesPreferences = newPrefs) else state.copy(
                bookPreferences = newPrefs,
            )
        }
    }

    fun toggleShelfFilter(filter: ShelfFilter, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val currentPrefs = if (isShelvesTab) state.shelvesPreferences else state.bookPreferences
            val newPrefs = currentPrefs.toggleShelf(filter)
            prefsManager.savePreferences(
                globalSpaceId.value, if (isShelvesTab) "library_shelves" else screenKey, newPrefs,
            )
            if (isShelvesTab) state.copy(shelvesPreferences = newPrefs) else state.copy(
                bookPreferences = newPrefs,
            )
        }
    }

    fun toggleFileFilter(format: FileFilter, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val current = if (isShelvesTab) state.shelvesPreferences else state.bookPreferences
            val prefs = current.toggleFormat(format)
            prefsManager.savePreferences(
                globalSpaceId.value,
                if (isShelvesTab) "library_shelves" else screenKey,
                prefs
            )
            if (isShelvesTab) state.copy(shelvesPreferences = prefs) else state.copy(bookPreferences = prefs)
        }
    }

    fun onLibraryViewChange(view: LibraryView) {
        _uiState.update { state ->
            val prefs = state.bookPreferences.copy(libraryView = view)
            prefsManager.savePreferences(globalSpaceId.value, screenKey, prefs)
            state.copy(bookPreferences = prefs)
        }
    }

    fun transferShelves(ids: Collection<String>, destinationId: String, copy: Boolean) {
        viewModelScope.launch { bookRepository.transferShelves(ids, destinationId, copy) }
    }

    fun setGlobalSpace(spaceId: String?) {
        prefsManager.setGlobalSpace(spaceId)
    }

}
