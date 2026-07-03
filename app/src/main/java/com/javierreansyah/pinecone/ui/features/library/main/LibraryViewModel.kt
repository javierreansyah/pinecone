package com.javierreansyah.pinecone.ui.features.library.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.mapAndSortShelves
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
    application: Application, private val screenKey: String = "library_books"
) : AndroidViewModel(application) {

    companion object {
        fun provideFactory(
            application: Application,
            screenKey: String = "library_books"
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

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            bookPreferences = prefsManager.getPreferences(
                screenKey = screenKey, defaultSort = when (screenKey) {
                    "shelf_detail" -> SortType.Custom
                    "library_books" -> SortType.LastRead
                    else -> SortType.Added
                }, defaultAscending = screenKey == "shelf_detail"
            ), shelvesPreferences = prefsManager.getPreferences(
                "library_shelves",
                defaultLayout = LayoutMode.BigList,
                defaultSort = SortType.Title,
                defaultAscending = true
            )
        )
    )
    private val _isBooksLoading = MutableStateFlow(true)
    private val _isShelvesLoading = MutableStateFlow(true)

    private val booksFlow: Flow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    private val allBooks: StateFlow<List<Book>> = booksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Pre-warm Coil's memory cache with the first 8 book covers as soon as
        // the ViewModel is created. By the time the user taps the search bar the
        // bitmaps are already decoded and cached, so CoverImage renders instantly
        // and nothing competes with the expansion animation on the UI thread.
        viewModelScope.launch {
            allBooks.take(1).collect { books ->
                books.take(8).forEach { book ->
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
    }

    fun getFilteredAndSortedBooks(baseFlow: Flow<List<Book>>): Flow<List<Book>> {
        return combine(baseFlow, _uiState) { books, state ->
            books.filterAndSort(state.bookPreferences)
        }
    }

    private val filteredBooks: StateFlow<List<Book>> =
        getFilteredAndSortedBooks(booksFlow).onEach { _isBooksLoading.value = false }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(),
        bookRepository.getAllShelfBookCrossRefs(),
        bookRepository.getAllBooks(),
        _uiState
    ) { shelvesList, crossRefs, allBooksEntities, state ->
        mapAndSortShelves(
            shelvesList = shelvesList,
            crossRefs = crossRefs,
            allBooksEntities = allBooksEntities,
            prefs = state.shelvesPreferences,
            unshelvedLabel = application.getString(R.string.library_label_unshelved)
        )
    }.onEach { _isShelvesLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val debouncedSearchQuery: Flow<String> = _uiState
        .map { it.searchQuery }
        .debounce(300L.milliseconds)
        .distinctUntilChanged()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val searchResults: StateFlow<SearchResults> = combine(
        debouncedSearchQuery,
        _uiState.map { it.searchCategory }.distinctUntilChanged()
    ) { query, category ->
        query to category
    }.flatMapLatest { (query, category) ->
        if (query.isBlank()) {
            combine(allBooks, shelves) { books, shelfList ->
                val matchedBooks =
                    if (category == SearchCategory.All || category == SearchCategory.Books) books else emptyList()
                val matchedShelves =
                    if (category == SearchCategory.All || category == SearchCategory.Shelves) shelfList.map { it.shelf } else emptyList()
                val matchedAuthors =
                    if (category == SearchCategory.All || category == SearchCategory.Authors) books.flatMap { it.authors }
                        .distinct() else emptyList()
                val matchedTags =
                    if (category == SearchCategory.All || category == SearchCategory.Tags) books.flatMap { it.tags }
                        .distinct() else emptyList()
                SearchResults(matchedBooks, matchedShelves, matchedAuthors, matchedTags)
            }
        } else {
            combine(
                bookRepository.searchBooks(query)
                    .map { entities -> entities.map { Book.fromEntity(it) } },
                bookRepository.searchShelves(query),
                bookRepository.searchAuthors(query),
                bookRepository.searchTags(query)
            ) { books, shelvesList, authors, tags ->
                val matchedBooks =
                    if (category == SearchCategory.All || category == SearchCategory.Books) books else emptyList()
                val matchedShelves =
                    if (category == SearchCategory.All || category == SearchCategory.Shelves) shelvesList else emptyList()
                val matchedAuthors =
                    if (category == SearchCategory.All || category == SearchCategory.Authors) authors else emptyList()
                val matchedTags =
                    if (category == SearchCategory.All || category == SearchCategory.Tags) tags else emptyList()
                SearchResults(matchedBooks, matchedShelves, matchedAuthors, matchedTags)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

    val uiState: StateFlow<LibraryScreenUiState> = combine(
        _uiState,
        filteredBooks,
        shelves,
        allBooks,
        searchResults,
        _isBooksLoading,
        _isShelvesLoading
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val baseState = array[0] as LibraryUiState

        @Suppress("UNCHECKED_CAST")
        val books = array[1] as List<Book>

        @Suppress("UNCHECKED_CAST")
        val shelfList = array[2] as List<ShelfWithCovers>

        @Suppress("UNCHECKED_CAST")
        val booksList = array[3] as List<Book>

        @Suppress("UNCHECKED_CAST")
        val searchRes = array[4] as SearchResults
        val booksLoading = array[5] as Boolean
        val shelvesLoading = array[6] as Boolean

        LibraryScreenUiState(
            searchQuery = baseState.searchQuery,
            searchCategory = baseState.searchCategory,
            isImporting = baseState.isImporting,
            bookPreferences = baseState.bookPreferences,
            shelvesPreferences = baseState.shelvesPreferences,
            filteredBooks = books,
            shelves = shelfList,
            allBooks = booksList,
            searchResults = searchRes,
            isBooksLoading = booksLoading,
            isShelvesLoading = shelvesLoading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LibraryScreenUiState())

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
        }
    }

    fun deleteShelf(shelfId: String) {
        viewModelScope.launch {
            bookRepository.deleteShelf(shelfId)
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

    fun toggleReadStatus(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleReadStatus(bookId)
        }
    }

    fun removeBookFromShelf(shelfId: String, bookId: String) {
        viewModelScope.launch {
            bookRepository.removeBookFromShelf(shelfId, bookId)
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
                    layoutMode = mode
                )
            prefsManager.savePreferences(if (isShelvesTab) "library_shelves" else screenKey, prefs)
            if (isShelvesTab) state.copy(shelvesPreferences = prefs) else state.copy(bookPreferences = prefs)
        }
    }

    fun onSortTypeChange(sortType: SortType, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val currentPrefs = if (isShelvesTab) state.shelvesPreferences else state.bookPreferences
            val newPrefs = if (currentPrefs.sortType == sortType) {
                currentPrefs.copy(isAscending = !currentPrefs.isAscending)
            } else {
                val initialAscending = sortType != SortType.LastRead
                currentPrefs.copy(sortType = sortType, isAscending = initialAscending)
            }
            prefsManager.savePreferences(
                if (isShelvesTab) "library_shelves" else screenKey, newPrefs
            )
            if (isShelvesTab) state.copy(shelvesPreferences = newPrefs) else state.copy(
                bookPreferences = newPrefs
            )
        }
    }

    fun toggleStatusFilter(status: StatusFilter, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val currentPrefs = if (isShelvesTab) state.shelvesPreferences else state.bookPreferences
            val updatedStatus = currentPrefs.selectedStatus.toMutableSet().apply {
                if (contains(status)) remove(status) else add(status)
            }
            val newPrefs = currentPrefs.copy(selectedStatus = updatedStatus)
            prefsManager.savePreferences(
                if (isShelvesTab) "library_shelves" else screenKey, newPrefs
            )
            if (isShelvesTab) state.copy(shelvesPreferences = newPrefs) else state.copy(
                bookPreferences = newPrefs
            )
        }
    }

    fun toggleShelfFilter(filter: ShelfFilter, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val currentPrefs = if (isShelvesTab) state.shelvesPreferences else state.bookPreferences
            val updatedFilter = currentPrefs.selectedShelfFilter.toMutableSet().apply {
                if (contains(filter)) remove(filter) else add(filter)
            }
            val newPrefs = currentPrefs.copy(selectedShelfFilter = updatedFilter)
            prefsManager.savePreferences(
                if (isShelvesTab) "library_shelves" else screenKey, newPrefs
            )
            if (isShelvesTab) state.copy(shelvesPreferences = newPrefs) else state.copy(
                bookPreferences = newPrefs
            )
        }
    }
}