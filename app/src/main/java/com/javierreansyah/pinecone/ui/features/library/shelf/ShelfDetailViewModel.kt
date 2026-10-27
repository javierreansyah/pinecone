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
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.domain.DeleteBooks
import com.javierreansyah.pinecone.ui.features.library.BookActions
import com.javierreansyah.pinecone.ui.features.library.ScreenFilterPreferences
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.sortShelfBooks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShelfDetailViewModel @Inject constructor(
    application: Application,
    private val bookRepository: LibraryRepository,
    deletePublications: DeleteBooks,
    private val prefsManager: LibraryPreferencesManager,
    readerPreferences: ReaderPreferences,
    @com.javierreansyah.pinecone.di.DefaultDispatcher private val computation: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    val bookActions = BookActions(viewModelScope, bookRepository, deletePublications)

    private val screenKey = "shelf_detail"

    private val globalSpaceId: StateFlow<String?> = prefsManager.getEffectiveSpaceFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    internal val filters =
        ScreenFilterPreferences(prefsManager, viewModelScope, screenKey, globalSpaceId)

    val allSpaces: StateFlow<List<SpaceEntity>> = bookRepository.getAllSpaces().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList(),
    )
    val spacesEnabled: StateFlow<Boolean> =
        readerPreferences.readerSettings
            .map { it.spacesEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun transferShelf(id: String, destinationId: String, copy: Boolean) {
        viewModelScope.launch {
            bookActions.mutationErrors.run {
                bookRepository.transferShelves(
                    listOf(id),
                    destinationId,
                    copy
                )
            }
        }
    }

    private val selectedShelfId = MutableStateFlow<String?>(null)
    fun selectShelf(id: String) {
        selectedShelfId.value = id
    }

    val selectedShelf: StateFlow<ShelfWithCovers?> =
        combine(selectedShelfId, globalSpaceId) { id, space -> id to space }
            .flatMapLatest { (id, space) ->
                when (id) {
                    null -> kotlinx.coroutines.flow.flowOf(null)
                    "unshelved" -> bookRepository.getUnshelvedBooks(space).map { books ->
                        ShelfWithCovers(
                            ShelfEntity(
                                "unshelved",
                                space ?: "_all_",
                                application.getString(R.string.library_label_unshelved),
                                0L
                            ), books
                        )
                    }

                    else -> combine(
                        bookRepository.getShelfWithBooks(id),
                        bookRepository.getShelfRefs(id)
                    ) { shelf, refs ->
                        shelf?.copy(books = shelf.books.filter { !it.book.isArchived })?.let {
                            sortShelfBooks(listOf(it), refs, space).firstOrNull()
                        }
                    }
                }
            }.flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val allBooks: StateFlow<List<Book>> = selectedShelf.map { shelf ->
        shelf?.books.orEmpty().map(Book::fromEntity)
    }.flowOn(computation).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBooks: StateFlow<List<Book>> = combine(
        allBooks,
        filters.preferences
    ) { books, prefs ->
        books.filterAndSort(prefs)
    }.flowOn(computation).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteShelf(shelfId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            bookActions.mutationErrors.run {
                bookRepository.deleteShelf(shelfId)
                onSuccess()
            }
        }
    }

    fun renameShelf(shelfId: String, newName: String) {
        viewModelScope.launch {
            bookActions.mutationErrors.run { bookRepository.renameShelf(shelfId, newName) }
        }
    }

    fun updateShelfOrder(shelfId: String, newBookIdsOrder: List<String>) {
        viewModelScope.launch {
            bookActions.mutationErrors.run {
                bookRepository.updateShelfOrder(shelfId, newBookIdsOrder)
                prefsManager.updatePreferences(globalSpaceId.value, screenKey) {
                    it.copy(sortType = SortType.Custom, isAscending = true)
                }
            }
        }
    }

}
