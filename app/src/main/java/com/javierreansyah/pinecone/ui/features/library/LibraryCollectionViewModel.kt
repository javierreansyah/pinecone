package com.javierreansyah.pinecone.ui.features.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
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

abstract class LibraryCollectionViewModel(
    bookRepository: LibraryRepository,
    deletePublications: DeleteBooks,
    prefsManager: LibraryPreferencesManager,
    screenKey: String,
    computation: kotlinx.coroutines.CoroutineDispatcher,
    source: Flow<List<BookWithDetails>>,
) : ViewModel() {
    val bookActions = BookActions(viewModelScope, bookRepository, deletePublications)

    internal val filters = ScreenFilterPreferences(prefsManager, viewModelScope, screenKey)

    private val booksFlow: Flow<List<Book>> =
        source.map { entities -> entities.map { Book.fromEntity(it) } }
            .flowOn(computation)

    val allBooks: StateFlow<List<Book>> = booksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val filteredBooks: StateFlow<List<Book>> =
        combine(allBooks, filters.preferences) { books, prefs ->
            books.filterAndSort(prefs)
        }.flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

}
