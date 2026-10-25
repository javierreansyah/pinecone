package com.javierreansyah.pinecone.ui.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.libraryPreferenceDefaults
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

abstract class LibraryCollectionViewModel(
    private val bookRepository: LibraryRepository,
    private val prefsManager: LibraryPreferencesManager,
    private val screenKey: String,
    source: (LibraryRepository) -> Flow<List<BookWithDetails>>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryCollectionUiState(bookPreferences = libraryPreferenceDefaults(screenKey)))

    val uiState: StateFlow<LibraryCollectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.preferences(null, screenKey).collect { preferences ->
                _uiState.update { it.copy(bookPreferences = preferences) }
            }
        }
    }

    private val booksFlow: Flow<List<Book>> =
        source(bookRepository).map { entities -> entities.map { Book.fromEntity(it) } }

    val allBooks: StateFlow<List<Book>> = booksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val filteredBooks: StateFlow<List<Book>> =
        combine(allBooks, _uiState) { books, state ->
            books.filterAndSort(state.bookPreferences)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(), bookRepository.getAllShelfBookCrossRefs(),
    ) { shelvesList, crossRefs ->
        sortShelfBooks(shelvesList, crossRefs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onLayoutModeChange(mode: LayoutMode) {
        val spaceId = null
        viewModelScope.launch {
            prefsManager.updatePreferences(spaceId, screenKey) { it.copy(layoutMode = mode) }
        }
    }

    fun onSortTypeChange(sortType: SortType) {
        val spaceId = null
        viewModelScope.launch {
            prefsManager.updatePreferences(spaceId, screenKey) { it.selectSort(sortType) }
        }
    }

    fun toggleFileFilter(format: FileFilter) {
        val spaceId = null
        viewModelScope.launch {
            prefsManager.updatePreferences(spaceId, screenKey) { it.toggleFormat(format) }
        }
    }

    fun toggleStatusFilter(status: StatusFilter) {
        val spaceId = null
        viewModelScope.launch {
            prefsManager.updatePreferences(spaceId, screenKey) { it.toggleStatus(status) }
        }
    }

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

    fun toggleArchive(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleArchive(bookId)
        }
    }

    fun toggleArchiveBooks(bookIds: Collection<String>) {
        viewModelScope.launch {
            bookIds.forEach { bookRepository.toggleArchive(it) }
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

}
