package com.example.readerapp.ui.features.library.filters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.local.preferences.LibraryPreferencesManager
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.SortType
import com.example.readerapp.ui.features.library.StatusFilter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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

class FilterResultViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val bookRepository = (application as ReaderApplication).libraryRepository
    private val prefsManager = LibraryPreferencesManager(application)
    private val screenKey = "filter_result"

    private val _uiState = MutableStateFlow(
        FilterResultUiState(
            bookPreferences = prefsManager.getPreferences(
                screenKey = screenKey, defaultSort = SortType.Added, defaultAscending = false
            )
        )
    )
    val uiState: StateFlow<FilterResultUiState> = _uiState.asStateFlow()

    private val booksFlow: Flow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    val allBooks: StateFlow<List<Book>> = booksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(), bookRepository.getAllShelfBookCrossRefs()
    ) { shelvesList, crossRefs ->
        shelvesList.map { shelfWithCovers ->
            val shelfId = shelfWithCovers.shelf.id
            val shelfCrossRefs = crossRefs.filter { it.shelfId == shelfId }
            val sortedBooks = shelfWithCovers.books.sortedBy { book ->
                shelfCrossRefs.find { it.bookId == book.book.id }?.orderIndex ?: 0
            }
            shelfWithCovers.copy(books = sortedBooks)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuthors =
        bookRepository.getAllAuthors().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTags =
        bookRepository.getAllTags().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getBooksByAuthor(author: String): Flow<List<Book>> = booksFlow.map { books ->
        books.filter { it.authors.contains(author) }
    }

    fun getBooksByTag(tag: String): Flow<List<Book>> = booksFlow.map { books ->
        books.filter { it.tags.contains(tag) }
    }

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

    fun onLayoutModeChange(mode: LayoutMode, isShelvesTab: Boolean = false) {
        _uiState.update { state ->
            val prefs = state.bookPreferences.copy(layoutMode = mode)
            prefsManager.savePreferences(screenKey, prefs)
            state.copy(bookPreferences = prefs)
        }
    }

    fun onSortTypeChange(sortType: SortType, isShelvesTab: Boolean = false) {
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

    fun toggleStatusFilter(status: StatusFilter, isShelvesTab: Boolean = false) {
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

    @OptIn(DelicateCoroutinesApi::class)
    fun deleteFilterItem(type: String, name: String, onSuccess: () -> Unit) {
        onSuccess()
        GlobalScope.launch(Dispatchers.IO) {
            bookRepository.deleteFilterItem(type, name)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun renameFilterItem(
        type: String, oldName: String, newName: String, onSuccess: (String) -> Unit
    ) {
        onSuccess(newName.trim())
        GlobalScope.launch(Dispatchers.IO) {
            bookRepository.renameFilterItem(type, oldName, newName)
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
}
