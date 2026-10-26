package com.javierreansyah.pinecone.ui.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.libraryPreferenceDefaults
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.domain.DeleteBooks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

abstract class LibraryCollectionViewModel(
    bookRepository: LibraryRepository,
    deletePublications: DeleteBooks,
    private val prefsManager: LibraryPreferencesManager,
    private val screenKey: String,
    computation: kotlinx.coroutines.CoroutineDispatcher,
    source: Flow<List<BookWithDetails>>,
) : ViewModel() {
    val bookActions = BookActions(viewModelScope, bookRepository, deletePublications)

    val preferences: StateFlow<FilterSortPreferences> =
        prefsManager.preferences(null, screenKey).stateIn(
            viewModelScope, SharingStarted.Eagerly, libraryPreferenceDefaults(screenKey),
        )

    private val booksFlow: Flow<List<Book>> =
        source.map { entities -> entities.map { Book.fromEntity(it) } }
            .flowOn(computation)

    val allBooks: StateFlow<List<Book>> = booksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val filteredBooks: StateFlow<List<Book>> =
        combine(allBooks, preferences) { books, prefs ->
            books.filterAndSort(prefs)
        }.flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun updatePreferences(transform: (FilterSortPreferences) -> FilterSortPreferences) {
        viewModelScope.launch { prefsManager.updatePreferences(null, screenKey, transform) }
    }

    fun onLayoutModeChange(mode: LayoutMode) = updatePreferences { it.copy(layoutMode = mode) }

    fun onSortTypeChange(sortType: SortType) = updatePreferences { it.selectSort(sortType) }

    fun toggleFileFilter(format: FileFilter) = updatePreferences { it.toggleFormat(format) }

    fun toggleStatusFilter(status: StatusFilter) = updatePreferences { it.toggleStatus(status) }

}
