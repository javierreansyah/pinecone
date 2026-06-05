package com.example.readerapp.ui.features.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.model.Book
import com.example.readerapp.data.local.ShelfWithCovers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.readerapp.data.local.LibraryPreferencesManager

class LibraryViewModel(
    application: Application,
    private val screenKey: String = "library_books"
) : AndroidViewModel(application) {
    private val bookRepository = (application as ReaderApplication).bookRepository
    private val prefsManager = LibraryPreferencesManager(application)

    private val _uiState = MutableStateFlow(LibraryUiState(
        bookPreferences = prefsManager.getPreferences(screenKey, defaultSort = SortType.Added),
        shelvesPreferences = prefsManager.getPreferences("library_shelves", defaultLayout = LayoutMode.BigList, defaultSort = SortType.Title, defaultAscending = true)
    ))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val booksFlow: Flow<List<Book>> = bookRepository.getAllBooks()
        .map { entities -> entities.map { Book.fromEntity(it) } }

    fun getFilteredAndSortedBooks(baseFlow: Flow<List<Book>>): Flow<List<Book>> {
        return combine(baseFlow, _uiState) { books, state ->
            books
                .filter { !it.isArchived }
                .filter { book ->
                    val status = when {
                        book.isRead -> StatusFilter.Finished
                        book.progress <= 0.0 -> StatusFilter.NotStarted
                        else -> StatusFilter.Reading
                    }
                    state.bookPreferences.selectedStatus.contains(status)
                }
                .let { filtered ->
                    val comparator = when (state.bookPreferences.sortType) {
                        SortType.Title -> compareBy<Book> { it.title.lowercase() }
                        SortType.Author -> compareBy { it.author?.lowercase() ?: "" }
                        SortType.LastRead -> compareBy { it.lastOpened ?: 0L }
                        SortType.Added -> compareBy { it.addedDate }
                        SortType.Progress -> compareBy { it.progress }
                        SortType.Custom -> compareBy { 0 }
                    }
                    if (state.bookPreferences.isAscending) filtered.sortedWith(comparator)
                    else filtered.sortedWith(comparator.reversed())
                }
        }
    }

    val filteredBooks: StateFlow<List<Book>> = getFilteredAndSortedBooks(booksFlow).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val archivedBooks: StateFlow<List<Book>> = booksFlow
        .map { books -> books.filter { it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(),
        bookRepository.getAllShelfBookCrossRefs(),
        bookRepository.getAllBooks(),
        _uiState
    ) { shelvesList, crossRefs, allBooksEntities, state ->
        val sortedShelves = shelvesList.map { shelfWithCovers ->
            val shelfId = shelfWithCovers.shelf.id
            val shelfCrossRefs = crossRefs.filter { it.shelfId == shelfId }
            val sortedBooks = shelfWithCovers.books.sortedBy { book ->
                shelfCrossRefs.find { it.bookId == book.id }?.orderIndex ?: 0
            }
            shelfWithCovers.copy(books = sortedBooks)
        }.let { processedShelves ->
            val comparator = when (state.shelvesPreferences.sortType) {
                SortType.Title -> compareBy<ShelfWithCovers> { it.shelf.name.lowercase() }
                SortType.LastRead -> compareBy { shelf -> shelf.books.maxOfOrNull { it.lastReadDate ?: 0L } ?: 0L }
                SortType.Progress -> compareBy { shelf -> 
                    if (shelf.books.isEmpty()) 0.0 else shelf.books.map { it.progression }.average()
                }
                SortType.Added -> compareBy { it.shelf.id }
                else -> compareBy { it.shelf.name.lowercase() }
            }
            if (state.shelvesPreferences.isAscending) processedShelves.sortedWith(comparator)
            else processedShelves.sortedWith(comparator.reversed())
        }

        val shelvedBookIds = crossRefs.map { it.bookId }.toSet()
        val unshelvedBooks = allBooksEntities.filter { it.id !in shelvedBookIds }

        if (unshelvedBooks.isNotEmpty()) {
            val unshelvedShelf = ShelfWithCovers(
                shelf = com.example.readerapp.data.local.ShelfEntity(
                    id = "unshelved", 
                    name = "Unshelved", 
                    createdAt = 0L
                ),
                books = unshelvedBooks
            )
            sortedShelves + unshelvedShelf
        } else {
            sortedShelves
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults: StateFlow<SearchResults> = combine(booksFlow, shelves, _uiState) { books, shelvesList, state ->
        val query = state.searchQuery
        val category = state.searchCategory
        
        val matchedBooks = if (category != SearchCategory.All && category != SearchCategory.Books) emptyList()
            else if (query.isBlank()) books else books.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.author?.contains(query, ignoreCase = true) == true 
            }

        val matchedShelves = if (category != SearchCategory.All && category != SearchCategory.Shelves) emptyList()
            else if (query.isBlank()) shelvesList.map { it.shelf } else shelvesList.map { it.shelf }.filter { it.name.contains(query, ignoreCase = true) }

        val matchedAuthors = if (category != SearchCategory.All && category != SearchCategory.Authors) emptyList()
            else books.mapNotNull { it.author }
                .distinct()
                .let { authors -> if (query.isBlank()) authors else authors.filter { it.contains(query, ignoreCase = true) } }

        val matchedTags = if (category != SearchCategory.All && category != SearchCategory.Tags) emptyList()
            else books.flatMap { it.tags?.split(",")?.map { t -> t.trim() } ?: emptyList() }
                .filter { it.isNotEmpty() }
                .distinct()
                .let { tags -> if (query.isBlank()) tags else tags.filter { it.contains(query, ignoreCase = true) } }

        SearchResults(matchedBooks, matchedShelves, matchedAuthors, matchedTags)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchResults())

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

    fun updateShelfOrder(shelfId: String, newBookIdsOrder: List<String>) {
        viewModelScope.launch {
            bookRepository.updateShelfOrder(shelfId, newBookIdsOrder)
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onSearchCategoryChange(category: SearchCategory) {
        _uiState.update { it.copy(searchCategory = category) }
    }

    fun getBooksByAuthor(author: String): Flow<List<Book>> = booksFlow.map { books ->
        books.filter { it.author == author }
    }

    fun getBooksByTag(tag: String): Flow<List<Book>> = booksFlow.map { books ->
        books.filter { it.tags?.split(",")?.map { t -> t.trim() }?.contains(tag) == true }
    }

    fun onLayoutModeChange(mode: LayoutMode, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val prefs = if (isShelvesTab) state.shelvesPreferences.copy(layoutMode = mode) else state.bookPreferences.copy(layoutMode = mode)
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
            prefsManager.savePreferences(if (isShelvesTab) "library_shelves" else screenKey, newPrefs)
            if (isShelvesTab) state.copy(shelvesPreferences = newPrefs) else state.copy(bookPreferences = newPrefs)
        }
    }

    fun toggleStatusFilter(status: StatusFilter, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val currentPrefs = if (isShelvesTab) state.shelvesPreferences else state.bookPreferences
            val updatedStatus = currentPrefs.selectedStatus.toMutableSet().apply {
                if (contains(status)) remove(status) else add(status)
            }
            val newPrefs = currentPrefs.copy(selectedStatus = updatedStatus)
            prefsManager.savePreferences(if (isShelvesTab) "library_shelves" else screenKey, newPrefs)
            if (isShelvesTab) state.copy(shelvesPreferences = newPrefs) else state.copy(bookPreferences = newPrefs)
        }
    }
}
