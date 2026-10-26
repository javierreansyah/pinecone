package com.javierreansyah.pinecone.ui.features.library.shelf

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.libraryPreferenceDefaults
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.domain.DeleteBooks
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.selectSort
import com.javierreansyah.pinecone.ui.features.library.sortShelfBooks
import com.javierreansyah.pinecone.ui.features.library.toggleFormat
import com.javierreansyah.pinecone.ui.features.library.toggleStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShelfDetailViewModel @Inject constructor(
    application: Application,
    private val bookRepository: LibraryRepository,
    private val deletePublications: DeleteBooks,
    private val prefsManager: LibraryPreferencesManager,
    private val readerPreferences: ReaderPreferences,
    @com.javierreansyah.pinecone.di.DefaultDispatcher private val computation: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    private val screenKey = "shelf_detail"

    private val _uiState =
        MutableStateFlow(ShelfDetailUiState(bookPreferences = libraryPreferenceDefaults(screenKey)))

    val uiState: StateFlow<ShelfDetailUiState> = _uiState.asStateFlow()


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


    val allSpaces: StateFlow<List<SpaceEntity>> = bookRepository.getAllSpaces().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )
    val spacesEnabled: StateFlow<Boolean> =
        readerPreferences.readerSettings
            .map { it.spacesEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun transferShelf(id: String, destinationId: String, copy: Boolean) {
        viewModelScope.launch { bookRepository.transferShelves(listOf(id), destinationId, copy) }
    }

    private val selectedShelfId = MutableStateFlow<String?>(null)
    fun selectShelf(id: String) { selectedShelfId.value = id }

    val selectedShelf: StateFlow<ShelfWithCovers?> = combine(selectedShelfId, globalSpaceId) { id, space -> id to space }
        .flatMapLatest { (id, space) ->
            when (id) {
                null -> kotlinx.coroutines.flow.flowOf(null)
                "unshelved" -> bookRepository.getUnshelvedBooks(space).map { books ->
                    ShelfWithCovers(ShelfEntity("unshelved", space ?: "_all_", application.getString(R.string.library_label_unshelved), 0L), books)
                }
                else -> combine(bookRepository.getShelfWithBooks(id), bookRepository.getShelfRefs(id)) { shelf, refs ->
                    shelf?.copy(books = shelf.books.filter { !it.book.isArchived })?.let {
                        sortShelfBooks(listOf(it), refs, space).firstOrNull()
                    }
                }
            }
        }.flowOn(computation).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allBooks: StateFlow<List<Book>> = selectedShelf.map { shelf ->
        shelf?.books.orEmpty().map(Book::fromEntity)
    }.flowOn(computation).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBooks: StateFlow<List<Book>> = combine(allBooks, _uiState.map { it.bookPreferences }.distinctUntilChanged()) { books, prefs ->
        books.filterAndSort(prefs)
    }.flowOn(computation).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
            prefsManager.updatePreferences(globalSpaceId.value, screenKey) {
                it.copy(sortType = SortType.Custom, isAscending = true)
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            deletePublications(listOf(bookId))
        }
    }

    fun deleteBooks(bookIds: Collection<String>) {
        viewModelScope.launch {
            deletePublications(bookIds)
        }
    }

    fun toggleArchive(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleArchive(bookId)
        }
    }

    fun archiveBooks(bookIds: Collection<String>) {
        viewModelScope.launch { bookRepository.setArchived(bookIds, true) }
    }

    fun toggleReadStatus(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleReadStatus(bookId)
        }
    }

    fun markBooksReadStatus(bookIds: Collection<String>, markAsRead: Boolean) {
        viewModelScope.launch { bookRepository.setReadStatus(bookIds, markAsRead) }
    }

}
