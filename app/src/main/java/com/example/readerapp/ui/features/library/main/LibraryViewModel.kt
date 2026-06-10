package com.example.readerapp.ui.features.library.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.R
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.database.library.ShelfEntity
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.local.preferences.LibraryPreferencesManager
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.LibraryUiState
import com.example.readerapp.ui.features.library.SearchCategory
import com.example.readerapp.ui.features.library.SearchResults
import com.example.readerapp.ui.features.library.ShelfFilter
import com.example.readerapp.ui.features.library.SortType
import com.example.readerapp.ui.features.library.StatusFilter
import com.example.readerapp.ui.features.library.LibraryScreenUiState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LibraryViewModel(
    application: Application, private val screenKey: String = "library_books"
) : AndroidViewModel(application) {
    private val bookRepository = (application as ReaderApplication).libraryRepository
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

    fun getFilteredAndSortedBooks(baseFlow: Flow<List<Book>>): Flow<List<Book>> {
        return combine(baseFlow, _uiState) { books, state ->
            books.filter { book ->
                val status = when {
                    book.isRead -> StatusFilter.Finished
                    book.progress <= 0.0 -> StatusFilter.NotStarted
                    else -> StatusFilter.Reading
                }
                state.bookPreferences.selectedStatus.contains(status)
            }.let { filtered ->
                val baseComparator = when (state.bookPreferences.sortType) {
                    SortType.Title -> compareBy { it.title.lowercase() }
                    SortType.Author -> compareBy { it.authors.firstOrNull()?.lowercase() ?: "" }
                    SortType.LastRead -> compareBy { it.lastOpened ?: 0L }
                    SortType.Added -> compareBy { it.addedDate }
                    SortType.Progress -> compareBy { it.progress }
                    SortType.Custom -> {
                        val indexMap = books.withIndex().associate { it.value.id to it.index }
                        compareBy<Book> { indexMap[it.id] ?: 0 }
                    }
                }

                val finalComparator = if (state.bookPreferences.sortType == SortType.Title) {
                    if (state.bookPreferences.isAscending) baseComparator else baseComparator.reversed()
                } else if (state.bookPreferences.sortType == SortType.Custom) {
                    if (state.bookPreferences.isAscending) baseComparator else baseComparator.reversed()
                } else {
                    val mainComp =
                        if (state.bookPreferences.isAscending) baseComparator else baseComparator.reversed()
                    mainComp.thenBy { it.title.lowercase() }
                }

                filtered.sortedWith(finalComparator)
            }
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
        val sortedShelves = shelvesList.map { shelfWithCovers ->
            val shelfId = shelfWithCovers.shelf.id
            val shelfCrossRefs = crossRefs.filter { it.shelfId == shelfId }
            val sortedBooks = shelfWithCovers.books.sortedBy { book ->
                shelfCrossRefs.find { it.bookId == book.book.id }?.orderIndex ?: 0
            }
            shelfWithCovers.copy(books = sortedBooks)
        }.let { processedShelves ->
            val baseComparator = when (state.shelvesPreferences.sortType) {
                SortType.Title -> compareBy { it.shelf.name.lowercase() }
                SortType.LastRead -> compareBy { shelf: ShelfWithCovers ->
                    shelf.books.maxOfOrNull {
                        it.book.lastReadDate ?: 0L
                    } ?: 0L
                }

                SortType.Progress -> compareBy { shelf: ShelfWithCovers ->
                    if (shelf.books.isEmpty()) 0.0 else shelf.books.map { it.book.progression }
                        .average()
                }

                SortType.Added -> compareBy { it.shelf.createdAt }
                else -> compareBy { it.shelf.name.lowercase() }
            }

            val finalComparator = if (state.shelvesPreferences.isAscending) {
                baseComparator.thenBy { it.shelf.name.lowercase() }
            } else {
                baseComparator.reversed().thenBy { it.shelf.name.lowercase() }
            }

            processedShelves.sortedWith(finalComparator)
        }

        val shelvedBookIds = crossRefs.map { it.bookId }.toSet()
        val unshelvedBooks = allBooksEntities.filter { it.book.id !in shelvedBookIds }

        val showShelves = state.shelvesPreferences.selectedShelfFilter.contains(ShelfFilter.Shelves)
        val showUnshelved =
            state.shelvesPreferences.selectedShelfFilter.contains(ShelfFilter.Unshelved)

        val finalShelves = if (showShelves) sortedShelves else emptyList()

        if (showUnshelved && unshelvedBooks.isNotEmpty()) {
            val unshelvedShelf = ShelfWithCovers(
                shelf = ShelfEntity(
                    id = "unshelved",
                    name = application.getString(R.string.library_label_unshelved),
                    createdAt = 0L
                ), books = unshelvedBooks
            )
            finalShelves + unshelvedShelf
        } else {
            finalShelves
        }
    }.onEach { _isShelvesLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val debouncedSearchQuery: Flow<String> = _uiState
        .map { it.searchQuery }
        .debounce(300L)
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
                val matchedBooks = if (category == SearchCategory.All || category == SearchCategory.Books) books else emptyList()
                val matchedShelves = if (category == SearchCategory.All || category == SearchCategory.Shelves) shelfList.map { it.shelf } else emptyList()
                val matchedAuthors = if (category == SearchCategory.All || category == SearchCategory.Authors) books.flatMap { it.authors }.distinct() else emptyList()
                val matchedTags = if (category == SearchCategory.All || category == SearchCategory.Tags) books.flatMap { it.tags }.distinct() else emptyList()
                SearchResults(matchedBooks, matchedShelves, matchedAuthors, matchedTags)
            }
        } else {
            combine(
                bookRepository.searchBooks(query).map { entities -> entities.map { Book.fromEntity(it) } },
                bookRepository.searchShelves(query),
                bookRepository.searchAuthors(query),
                bookRepository.searchTags(query)
            ) { books, shelvesList, authors, tags ->
                val matchedBooks = if (category == SearchCategory.All || category == SearchCategory.Books) books else emptyList()
                val matchedShelves = if (category == SearchCategory.All || category == SearchCategory.Shelves) shelvesList else emptyList()
                val matchedAuthors = if (category == SearchCategory.All || category == SearchCategory.Authors) authors else emptyList()
                val matchedTags = if (category == SearchCategory.All || category == SearchCategory.Tags) tags else emptyList()
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

    fun createShelfAndAddBook(name: String, bookId: String?) {
        viewModelScope.launch {
            val shelfId = bookRepository.createShelf(name)
            if (bookId != null) {
                bookRepository.addBookToShelf(shelfId, bookId)
            }
        }
    }

    fun addBookToShelf(shelfId: String, bookId: String) {
        viewModelScope.launch {
            bookRepository.addBookToShelf(shelfId, bookId)
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