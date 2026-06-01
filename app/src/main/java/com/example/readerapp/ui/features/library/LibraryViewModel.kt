package com.example.readerapp.ui.features.library

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.model.Book
import com.example.readerapp.data.local.ShelfWithCovers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val bookRepository = (application as ReaderApplication).bookRepository
    private val readerPreferences = ReaderPreferences(application)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val booksFlow: Flow<List<Book>> = bookRepository.getAllBooks()
        .map { entities -> entities.map { Book.fromEntity(it) } }

    val filteredBooks: StateFlow<List<Book>> = combine(booksFlow, _uiState) { books, state ->
        books
            .filter { !it.isArchived }
            .filter { book ->
                val matchesSearch = book.title.contains(state.searchQuery, ignoreCase = true) ||
                        (book.author?.contains(state.searchQuery, ignoreCase = true) == true)
                
                val status = when {
                    book.progress <= 0.0 -> StatusFilter.NotStarted
                    book.progress >= 1.0 -> StatusFilter.Finished
                    else -> StatusFilter.Reading
                }
                val matchesStatus = state.selectedStatus.contains(status)
                
                matchesSearch && matchesStatus
            }
            .let { filtered ->
                val comparator = when (state.sortType) {
                    SortType.Title -> compareBy<Book> { it.title.lowercase() }
                    SortType.Author -> compareBy { it.author?.lowercase() ?: "" }
                    SortType.LastRead -> compareBy { it.lastOpened ?: 0L }
                    SortType.Added -> compareBy { it.addedDate }
                    SortType.Progress -> compareBy { it.progress }
                }
                if (state.isAscending) filtered.sortedWith(comparator)
                else filtered.sortedWith(comparator.reversed())
            }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val archivedBooks: StateFlow<List<Book>> = booksFlow
        .map { books -> books.filter { it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelves: StateFlow<List<ShelfWithCovers>> = bookRepository.getAllShelvesWithBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Import bundled books on launch. importBundledBooks checks for existence to avoid duplicates.
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            bookRepository.importBundledBooks()
            readerPreferences.setHasImportedBundled(true)
            _uiState.update { it.copy(isImporting = false) }
        }
    }

    fun importBook(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isImporting = true) }
            bookRepository.importBook(uri)
            _uiState.update { it.copy(isImporting = false) }
        }
    }

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

    fun deleteShelf(shelfId: String) {
        viewModelScope.launch {
            bookRepository.deleteShelf(shelfId)
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onLayoutModeChange(mode: LayoutMode) {
        _uiState.update { it.copy(layoutMode = mode) }
    }

    fun onSortTypeChange(sortType: SortType) {
        _uiState.update { state ->
            if (state.sortType == sortType) {
                state.copy(isAscending = !state.isAscending)
            } else {
                state.copy(sortType = sortType, isAscending = true)
            }
        }
    }

    fun toggleStatusFilter(status: StatusFilter) {
        _uiState.update { state ->
            val updated = state.selectedStatus.toMutableSet().apply {
                if (contains(status)) remove(status) else add(status)
            }
            state.copy(selectedStatus = updated)
        }
    }
}
