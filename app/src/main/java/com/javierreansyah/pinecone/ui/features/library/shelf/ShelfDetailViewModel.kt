package com.javierreansyah.pinecone.ui.features.library.shelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.selectSort
import com.javierreansyah.pinecone.ui.features.library.sortShelfBooks
import com.javierreansyah.pinecone.ui.features.library.toggleFormat
import com.javierreansyah.pinecone.ui.features.library.toggleStatus
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
    application: Application,
) : AndroidViewModel(application) {
    private val bookRepository = (application as PineconeApplication).libraryRepository
    private val prefsManager = LibraryPreferencesManager(application)
    private val screenKey = "shelf_detail"

    private val _uiState = MutableStateFlow(
        ShelfDetailUiState(
            bookPreferences = prefsManager.getPreferences(
                screenKey = screenKey, defaultSort = SortType.Custom, defaultAscending = true,
            ),
        ),
    )
    val uiState: StateFlow<ShelfDetailUiState> = _uiState.asStateFlow()

    private val booksFlow: Flow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    private val globalSpaceId: StateFlow<String?> = prefsManager.getEffectiveSpaceFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            globalSpaceId.collect { spaceId ->
                _uiState.update { state ->
                    state.copy(
                        bookPreferences = prefsManager.getPreferences(
                            spaceId = spaceId,
                            screenKey = screenKey,
                            defaultSort = SortType.Custom,
                            defaultAscending = true,
                        ),
                    )
                }
            }
        }
    }

    val allBooks: StateFlow<List<Book>> = booksFlow.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )

    val allSpaces: StateFlow<List<SpaceEntity>> = bookRepository.getAllSpaces().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )
    val spacesEnabled: StateFlow<Boolean> =
        (application as PineconeApplication).readerPreferences.readerSettings
            .map { it.spacesEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun transferShelf(id: String, destinationId: String, copy: Boolean) {
        viewModelScope.launch { bookRepository.transferShelves(listOf(id), destinationId, copy) }
    }

    val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(),
        bookRepository.getAllShelfBookCrossRefs(),
        bookRepository.getAllBooks(),
        globalSpaceId,
    ) { shelvesList, crossRefs, allBooksEntities, spaceId ->
        val sortedShelves = sortShelfBooks(shelvesList, crossRefs)
        val isSpaceSelected = spaceId != null && spaceId != "_all_"
        val currentSpaceShelves = if (isSpaceSelected) {
            shelvesList.filter { (itShelf, books) -> itShelf.spaceId == spaceId }
        } else {
            shelvesList
        }
        val currentSpaceShelfIds =
            currentSpaceShelves.map { (itShelf2, books) -> itShelf2.id }.toSet()
        val shelvedBookIds =
            crossRefs.filter { (itShelfId, bookId, addedAt, orderIndex) -> itShelfId in currentSpaceShelfIds }
                .map { (_, itBookId, addedAt, orderIndex) -> itBookId }.toSet()

        val unshelvedBooks = allBooksEntities.filter { (itBook, _, _, _, itSpaces, credits) ->
            val inSpace = if (isSpaceSelected) {
                itSpaces.any { (spaceId2, name, createdAt) -> spaceId2 == spaceId }
            } else {
                itSpaces.isNotEmpty()
            }
            inSpace && !itBook.isUnsorted && itBook.id !in shelvedBookIds
        }

        val unshelvedShelf = ShelfWithCovers(
            shelf = ShelfEntity(
                id = "unshelved",
                spaceId = spaceId ?: "_all_",
                name = application.getString(R.string.library_label_unshelved),
                createdAt = 0L,
            ),
            books = unshelvedBooks,
        )

        sortedShelves + unshelvedShelf
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getFilteredAndSortedBooks(baseFlow: Flow<List<Book>>): Flow<List<Book>> {
        return combine(baseFlow, _uiState) { books, (stateBookPreferences) ->
            books.filterAndSort(stateBookPreferences)
        }
    }

    fun onLayoutModeChange(mode: LayoutMode) {
        _uiState.update { state ->
            val prefs = state.bookPreferences.copy(layoutMode = mode)
            prefsManager.savePreferences(globalSpaceId.value, screenKey, prefs)
            state.copy(bookPreferences = prefs)
        }
    }

    fun onSortTypeChange(sortType: SortType) {
        _uiState.update { state ->
            val currentPrefs = state.bookPreferences
            val newPrefs = currentPrefs.selectSort(sortType)
            prefsManager.savePreferences(globalSpaceId.value, screenKey, newPrefs)
            state.copy(bookPreferences = newPrefs)
        }
    }

    fun toggleFileFilter(format: FileFilter) {
        _uiState.update { state ->
            val current = state.bookPreferences
            val prefs = current.toggleFormat(format)
            prefsManager.savePreferences(globalSpaceId.value, screenKey, prefs)
            state.copy(bookPreferences = prefs)
        }
    }

    fun toggleStatusFilter(status: StatusFilter) {
        _uiState.update { state ->
            val currentPrefs = state.bookPreferences
            val newPrefs = currentPrefs.toggleStatus(status)
            prefsManager.savePreferences(globalSpaceId.value, screenKey, newPrefs)
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

}
