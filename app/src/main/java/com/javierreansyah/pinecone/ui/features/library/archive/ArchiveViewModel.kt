package com.javierreansyah.pinecone.ui.features.library.archive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.sortShelfBooks
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

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {
    private val bookRepository = (application as PineconeApplication).libraryRepository
    private val prefsManager = LibraryPreferencesManager(application)
    private val screenKey = "archive"

    private val _uiState = MutableStateFlow(
        ArchiveUiState(
            bookPreferences = prefsManager.getPreferences(
                screenKey = screenKey, defaultSort = SortType.Added, defaultAscending = false
            )
        )
    )
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private val booksFlow: Flow<List<Book>> =
        bookRepository.getArchivedBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    val allBooks: StateFlow<List<Book>> = booksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val archivedBooks: StateFlow<List<Book>> = combine(booksFlow, _uiState) { books, state ->
        books.filterAndSort(state.bookPreferences)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(), bookRepository.getAllShelfBookCrossRefs()
    ) { shelvesList, crossRefs ->
        sortShelfBooks(shelvesList, crossRefs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onLayoutModeChange(mode: LayoutMode) {
        _uiState.update { state ->
            val prefs = state.bookPreferences.copy(layoutMode = mode)
            prefsManager.savePreferences(screenKey, prefs)
            state.copy(bookPreferences = prefs)
        }
    }

    fun onSortTypeChange(sortType: SortType) {
        _uiState.update { state ->
            val currentPrefs = state.bookPreferences
            val newPrefs = if (currentPrefs.sortType == sortType) {
                currentPrefs.copy(isAscending = !currentPrefs.isAscending)
            } else {
                val initialAscending = sortType != SortType.LastRead
                currentPrefs.copy(sortType = sortType, isAscending = initialAscending)
            }
            prefsManager.savePreferences(screenKey, newPrefs)
            state.copy(bookPreferences = newPrefs)
        }
    }

    fun toggleStatusFilter(status: StatusFilter) {
        _uiState.update { state ->
            val currentPrefs = state.bookPreferences
            val updatedStatus = currentPrefs.selectedStatus.toMutableSet().apply {
                if (contains(status)) remove(status) else add(status)
            }
            val newPrefs = currentPrefs.copy(selectedStatus = updatedStatus)
            prefsManager.savePreferences(screenKey, newPrefs)
            state.copy(bookPreferences = newPrefs)
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
            val currentBooks = allBooks.value
            bookIds.forEach { bookId ->
                val book = currentBooks.find { it.id == bookId }
                if (book != null && book.isRead != markAsRead) {
                    bookRepository.toggleReadStatus(bookId)
                }
            }
        }
    }

}
