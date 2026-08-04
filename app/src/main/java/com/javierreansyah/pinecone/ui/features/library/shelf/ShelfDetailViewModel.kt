package com.javierreansyah.pinecone.ui.features.library.shelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
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

class ShelfDetailViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val bookRepository = (application as PineconeApplication).libraryRepository
    private val prefsManager = LibraryPreferencesManager(application)
    private val screenKey = "shelf_detail"

    private val _uiState = MutableStateFlow(
        ShelfDetailUiState(
            bookPreferences = prefsManager.getPreferences(
                screenKey = screenKey, defaultSort = SortType.Custom, defaultAscending = true
            )
        )
    )
    val uiState: StateFlow<ShelfDetailUiState> = _uiState.asStateFlow()

    private val booksFlow: Flow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    private val globalSpaceId: StateFlow<String?> = prefsManager.getGlobalSpaceFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    val allBooks: StateFlow<List<Book>> = combine(booksFlow, globalSpaceId) { books, spaceId ->
        if (spaceId == null || spaceId == "_all_") books else books.filter { book -> book.spaceIds.contains(spaceId) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(),
        bookRepository.getAllShelfBookCrossRefs(),
        bookRepository.getAllBooks(),
        globalSpaceId
    ) { shelvesList, crossRefs, allBooksEntities, spaceId ->
        val sortedShelves = sortShelfBooks(shelvesList, crossRefs, spaceId)
        val shelvedBookIds = crossRefs.map { it.bookId }.toSet()
        val unshelvedBooks = allBooksEntities.filter { 
            it.book.id !in shelvedBookIds && (spaceId == null || spaceId == "_all_" || it.spaces.any { space -> space.id == spaceId })
        }

        val unshelvedShelf = ShelfWithCovers(
            shelf = ShelfEntity(
                id = "unshelved",
                name = application.getString(R.string.library_label_unshelved),
                createdAt = 0L
            ), books = unshelvedBooks
        )

        sortedShelves + unshelvedShelf
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getFilteredAndSortedBooks(baseFlow: Flow<List<Book>>): Flow<List<Book>> {
        return combine(baseFlow, _uiState) { books, state ->
            books.filterAndSort(state.bookPreferences)
        }
    }

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

    // --- Book Context Menu Actions ---

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

    fun removeBookFromShelf(shelfId: String, bookId: String) {
        viewModelScope.launch {
            bookRepository.removeBookFromShelf(shelfId, bookId)
        }
    }

    fun removeBookFromSpace(spaceId: String, bookId: String) {
        viewModelScope.launch {
            bookRepository.removeBookFromSpace(spaceId, bookId)
        }
    }
}
