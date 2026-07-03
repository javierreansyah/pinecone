package com.javierreansyah.pinecone.ui.features.library.filters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.model.Book
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FilterCategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val bookRepository = (application as PineconeApplication).libraryRepository

    private val booksFlow: Flow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    val allAuthors =
        bookRepository.getAllAuthors().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTags =
        bookRepository.getAllTags().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val authorsWithCounts = combine(allAuthors, booksFlow) { authors, books ->
        authors.map { author ->
            val count = books.count { it.authors.contains(author.name) }
            Pair(author.name, count)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tagsWithCounts = combine(allTags, booksFlow) { tags, books ->
        tags.map { tag ->
            val count = books.count { it.tags.contains(tag.name) }
            Pair(tag.name, count)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteFilterItem(type: String, name: String, onSuccess: () -> Unit) {
        onSuccess()
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.deleteFilterItem(type, name)
        }
    }

    fun renameFilterItem(
        type: String, oldName: String, newName: String, onSuccess: (String) -> Unit
    ) {
        onSuccess(newName.trim())
        viewModelScope.launch(Dispatchers.IO) {
            bookRepository.renameFilterItem(type, oldName, newName)
        }
    }

    fun renameFilterItems(
        type: String, oldNames: List<String>, newName: String, onSuccess: (String) -> Unit
    ) {
        onSuccess(newName.trim())
        viewModelScope.launch(Dispatchers.IO) {
            oldNames.forEach { oldName ->
                bookRepository.renameFilterItem(type, oldName, newName)
            }
        }
    }
}
