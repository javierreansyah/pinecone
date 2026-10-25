package com.javierreansyah.pinecone.ui.features.library.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.libraryPreferenceDefaults
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FilterResultViewModel @Inject constructor(
    private val bookRepository: LibraryRepository,
    private val prefsManager: LibraryPreferencesManager,
) : ViewModel() {
    private val screenKey = "filter_result"

    private val _uiState =
        MutableStateFlow(FilterResultUiState(bookPreferences = libraryPreferenceDefaults(screenKey)))

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
            globalSpaceId.flatMapLatest { spaceId -> prefsManager.preferences(spaceId, screenKey) }
                .collect { preferences -> _uiState.update { it.copy(bookPreferences = preferences) } }
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
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(spaceId, screenKey) { it.copy(layoutMode = mode) }
        }
    }

    fun onSortTypeChange(sortType: SortType) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(spaceId, screenKey) { it.selectSort(sortType) }
        }
    }

    fun toggleFileFilter(format: FileFilter) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(spaceId, screenKey) { it.toggleFormat(format) }
        }
    }

    fun toggleStatusFilter(status: StatusFilter) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch {
            prefsManager.updatePreferences(spaceId, screenKey) { it.toggleStatus(status) }
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
