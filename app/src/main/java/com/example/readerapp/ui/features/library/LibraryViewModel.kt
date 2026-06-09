package com.example.readerapp.ui.features.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.R
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
        bookPreferences = prefsManager.getPreferences(
            screenKey = screenKey,
            defaultSort = when (screenKey) {
                "shelf_detail" -> SortType.Custom
                "library_books" -> SortType.LastRead
                else -> SortType.Added
            },
            defaultAscending = screenKey == "shelf_detail"
        ),
        shelvesPreferences = prefsManager.getPreferences("library_shelves", defaultLayout = LayoutMode.BigList, defaultSort = SortType.Title, defaultAscending = true)
    ))
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _isBooksLoading = MutableStateFlow(true)
    val isBooksLoading: StateFlow<Boolean> = _isBooksLoading.asStateFlow()

    private val _isShelvesLoading = MutableStateFlow(true)
    val isShelvesLoading: StateFlow<Boolean> = _isShelvesLoading.asStateFlow()

    private val booksFlow: Flow<List<Book>> = bookRepository.getAllBooks()
        .map { entities -> entities.map { Book.fromEntity(it) } }

    val allBooks: StateFlow<List<Book>> = booksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

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
                        val mainComp = if (state.bookPreferences.isAscending) baseComparator else baseComparator.reversed()
                        mainComp.thenBy { it.title.lowercase() }
                    }
                    
                    filtered.sortedWith(finalComparator)
                }
        }
    }

    val filteredBooks: StateFlow<List<Book>> = getFilteredAndSortedBooks(booksFlow)
        .onEach { _isBooksLoading.value = false }
        .stateIn(
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
                shelfCrossRefs.find { it.bookId == book.book.id }?.orderIndex ?: 0
            }
            shelfWithCovers.copy(books = sortedBooks)
        }.let { processedShelves ->
            val baseComparator = when (state.shelvesPreferences.sortType) {
                SortType.Title -> compareBy { it.shelf.name.lowercase() }
                SortType.LastRead -> compareBy { shelf: ShelfWithCovers -> shelf.books.maxOfOrNull { it.book.lastReadDate ?: 0L } ?: 0L }
                SortType.Progress -> compareBy { shelf: ShelfWithCovers -> 
                    if (shelf.books.isEmpty()) 0.0 else shelf.books.map { it.book.progression }.average()
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
        val showUnshelved = state.shelvesPreferences.selectedShelfFilter.contains(ShelfFilter.Unshelved)

        val finalShelves = if (showShelves) sortedShelves else emptyList()

        if (showUnshelved && unshelvedBooks.isNotEmpty()) {
            val unshelvedShelf = ShelfWithCovers(
                shelf = com.example.readerapp.data.local.ShelfEntity(
                    id = "unshelved", 
                    name = application.getString(R.string.library_label_unshelved),
                    createdAt = 0L
                ),
                books = unshelvedBooks
            )
            finalShelves + unshelvedShelf
        } else {
            finalShelves
        }
    }.onEach { _isShelvesLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchResults: StateFlow<SearchResults> = combine(booksFlow, shelves, _uiState) { books, shelvesList, state ->
        val query = state.searchQuery
        val category = state.searchCategory
        
        val matchedBooks = if (category != SearchCategory.All && category != SearchCategory.Books) emptyList()
            else if (query.isBlank()) books else books.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.authors.any { author -> author.contains(query, ignoreCase = true) } 
            }

        val matchedShelves = if (category != SearchCategory.All && category != SearchCategory.Shelves) emptyList()
            else if (query.isBlank()) shelvesList.map { it.shelf } else shelvesList.map { it.shelf }.filter { it.name.contains(query, ignoreCase = true) }

        val matchedAuthors = if (category != SearchCategory.All && category != SearchCategory.Authors) emptyList()
            else books.flatMap { it.authors }
                .distinct()
                .let { authors -> if (query.isBlank()) authors else authors.filter { it.contains(query, ignoreCase = true) } }

        val matchedTags = if (category != SearchCategory.All && category != SearchCategory.Tags) emptyList()
            else books.flatMap { it.tags }
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
        books.filter { it.authors.contains(author) }
    }

    fun getBooksByTag(tag: String): Flow<List<Book>> = booksFlow.map { books ->
        books.filter { it.tags.contains(tag) }
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

    fun toggleShelfFilter(filter: ShelfFilter, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val currentPrefs = if (isShelvesTab) state.shelvesPreferences else state.bookPreferences
            val updatedFilter = currentPrefs.selectedShelfFilter.toMutableSet().apply {
                if (contains(filter)) remove(filter) else add(filter)
            }
            val newPrefs = currentPrefs.copy(selectedShelfFilter = updatedFilter)
            prefsManager.savePreferences(if (isShelvesTab) "library_shelves" else screenKey, newPrefs)
            if (isShelvesTab) state.copy(shelvesPreferences = newPrefs) else state.copy(bookPreferences = newPrefs)
        }
    }

    val allAuthors = bookRepository.getAllAuthors().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTags = bookRepository.getAllTags().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val authorsWithCounts = combine(allAuthors, booksFlow) { authors, books ->
        authors.map { author -> 
            val count = books.count { it.authors.contains(author.name) }
            Pair(author.name, count)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tagsWithCounts = combine(allTags, booksFlow) { tags, books ->
        tags.map { tag -> 
            val count = books.count { it.tags.contains(tag.name) }
            Pair(tag.name, count)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun deleteFilterItem(type: String, name: String, onSuccess: () -> Unit) {
        onSuccess()
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            bookRepository.deleteFilterItem(type, name)
        }
    }

    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    fun renameFilterItem(type: String, oldName: String, newName: String, onSuccess: (String) -> Unit) {
        onSuccess(newName.trim())
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            bookRepository.renameFilterItem(type, oldName, newName)
        }
    }
}
