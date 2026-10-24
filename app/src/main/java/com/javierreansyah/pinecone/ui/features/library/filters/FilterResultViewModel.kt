package com.javierreansyah.pinecone.ui.features.library.filters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.inSpace
import com.javierreansyah.pinecone.ui.features.library.selectSort
import com.javierreansyah.pinecone.ui.features.library.sortShelfBooks
import com.javierreansyah.pinecone.ui.features.library.toggleFormat
import com.javierreansyah.pinecone.ui.features.library.toggleStatus
import kotlinx.coroutines.Dispatchers
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
    application: Application,
) : AndroidViewModel(application) {
    private val bookRepository = (application as PineconeApplication).libraryRepository
    private val prefsManager = LibraryPreferencesManager(application)
    private val screenKey = "filter_result"

    private val _uiState = MutableStateFlow(
        FilterResultUiState(
            bookPreferences = prefsManager.getPreferences(
                screenKey = screenKey, defaultSort = SortType.Added,
            ),
        ),
    )
    val uiState: StateFlow<FilterResultUiState> = _uiState.asStateFlow()

    private val booksFlow: Flow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    val actionBooks: StateFlow<List<Book>> = combine(
        booksFlow, bookRepository.getArchivedBooks(),
    ) { books, archived -> books + archived.map { Book.fromEntity(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBooksAcrossSpaces: StateFlow<List<Book>> = booksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    private val globalSpaceId: StateFlow<String?> = prefsManager.getEffectiveSpaceFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            globalSpaceId.collect { spaceId ->
                _uiState.update { state ->
                    state.copy(bookPreferences = prefsManager.getPreferences(spaceId, screenKey))
                }
            }
        }
    }

    val allBooks: StateFlow<List<Book>> = combine(booksFlow, globalSpaceId) { books, spaceId ->
        books.inSpace(spaceId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(),
        bookRepository.getAllShelfBookCrossRefs(),
        globalSpaceId,
    ) { shelvesList, crossRefs, spaceId ->
        sortShelfBooks(shelvesList, crossRefs, spaceId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAuthors: StateFlow<List<AuthorEntity>> =
        combine(bookRepository.getAllAuthors(), allBooks) { authors, books ->
            val namesInSpace = books.flatMapTo(mutableSetOf()) { it.authors }
            authors.filter { (_, itName) -> itName in namesInSpace }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTags: StateFlow<List<TagEntity>> =
        combine(bookRepository.getAllTags(), allBooks) { tags, books ->
            val namesInSpace =
                books.flatMapTo(mutableSetOf()) { it.tags }
            tags.filter { (_, itName2) -> itName2 in namesInSpace }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSpaces: StateFlow<List<SpaceEntity>> =
        bookRepository.getAllSpaces().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun getBooksByAuthor(author: String): Flow<List<Book>> = allBooks.map { books ->
        books.filter { author in it.authors }
    }

    fun getBooksByTag(tag: String): Flow<List<Book>> = allBooks.map { books ->
        books.filter { tag in it.tags }
    }

    fun getBooksBySpace(spaceName: String): Flow<List<Book>> =
        combine(allBooksAcrossSpaces, allSpaces) { books, spaces ->
            val space = spaces.find { (_, itName3, _) -> itName3 == spaceName }
            if (space != null) {
                books.inSpace(space.id)
            } else {
                emptyList()
            }
        }

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

    fun deleteFilterItem(type: String, name: String, onSuccess: () -> Unit) {
        onSuccess()
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.deleteFilterItem(type, name)
        }
    }

    fun renameFilterItem(
        type: String, oldName: String, newName: String, onSuccess: (String) -> Unit,
    ) {
        onSuccess(newName.trim())
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.renameFilterItem(type, oldName, newName)
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
            val currentBooks = allBooksAcrossSpaces.value.associateBy(Book::id)
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
            val currentBooks = allBooksAcrossSpaces.value.associateBy(Book::id)
            bookIds.forEach { bookId ->
                val book = currentBooks[bookId]
                if (book != null && book.isRead != markAsRead) {
                    bookRepository.toggleReadStatus(bookId)
                }
            }
        }
    }

}
