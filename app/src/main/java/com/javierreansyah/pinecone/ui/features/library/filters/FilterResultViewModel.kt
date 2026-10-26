package com.javierreansyah.pinecone.ui.features.library.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.libraryPreferenceDefaults
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.domain.DeleteBooks
import com.javierreansyah.pinecone.ui.features.library.BookActions
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.inSpace
import com.javierreansyah.pinecone.ui.features.library.selectSort
import com.javierreansyah.pinecone.ui.features.library.toggleFormat
import com.javierreansyah.pinecone.ui.features.library.toggleStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FilterResultViewModel @Inject constructor(
    private val bookRepository: LibraryRepository,
    deletePublications: DeleteBooks,
    private val prefsManager: LibraryPreferencesManager,
    @com.javierreansyah.pinecone.di.DefaultDispatcher private val computation: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    val bookActions = BookActions(viewModelScope, bookRepository, deletePublications)

    val mutationErrors = com.javierreansyah.pinecone.ui.components.MutationErrorState()

    private val screenKey = "filter_result"

    private val booksFlow: StateFlow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }
            .flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val actionBooks: StateFlow<List<Book>> = combine(
        booksFlow, bookRepository.getArchivedBooks(),
    ) { books, archived -> books + archived.map { Book.fromEntity(it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBooksAcrossSpaces: StateFlow<List<Book>> = booksFlow

    private val globalSpaceId: StateFlow<String?> = prefsManager.getEffectiveSpaceFlow().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null,
    )

    val preferences: StateFlow<FilterSortPreferences> = globalSpaceId
        .flatMapLatest { spaceId -> prefsManager.preferences(spaceId, screenKey) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, libraryPreferenceDefaults(screenKey))

    val allBooks: StateFlow<List<Book>> = combine(booksFlow, globalSpaceId) { books, spaceId ->
        books.inSpace(spaceId)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

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

    private val selection = MutableStateFlow("" to "")
    fun selectCollection(type: String, value: String) {
        selection.value = type to value
    }

    val unfilteredBooks: StateFlow<List<Book>> = combine(
        selection,
        allBooks,
        allBooksAcrossSpaces,
        allSpaces
    ) { (type, value), visible, all, spaces ->
        when (type) {
            "author" -> visible.filter { value in it.authors }
            "tag" -> visible.filter { value in it.tags }
            "space" -> spaces.firstOrNull { it.name == value }?.let { all.inSpace(it.id) }.orEmpty()
            else -> emptyList()
        }
    }.flowOn(computation).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredBooks: StateFlow<List<Book>> = combine(
        unfilteredBooks,
        preferences
    ) { books, prefs ->
        books.filterAndSort(prefs)
    }.flowOn(computation).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun updatePreferences(transform: (FilterSortPreferences) -> FilterSortPreferences) {
        val spaceId = globalSpaceId.value
        viewModelScope.launch { prefsManager.updatePreferences(spaceId, screenKey, transform) }
    }

    fun onLayoutModeChange(mode: LayoutMode) = updatePreferences { it.copy(layoutMode = mode) }

    fun onSortTypeChange(sortType: SortType) = updatePreferences { it.selectSort(sortType) }

    fun toggleFileFilter(format: FileFilter) = updatePreferences { it.toggleFormat(format) }

    fun toggleStatusFilter(status: StatusFilter) = updatePreferences { it.toggleStatus(status) }

    fun deleteFilterItem(type: String, name: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            mutationErrors.run {
                bookRepository.deleteFilterItem(type, name)
                onSuccess()
            }
        }
    }

    fun renameFilterItem(
        type: String, oldName: String, newName: String, onSuccess: (String) -> Unit,
    ) {
        viewModelScope.launch {
            mutationErrors.run {
                bookRepository.renameFilterItem(type, oldName, newName)
                onSuccess(newName.trim())
            }
        }
    }

}
