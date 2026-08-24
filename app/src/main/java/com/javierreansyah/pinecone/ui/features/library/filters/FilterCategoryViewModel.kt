package com.javierreansyah.pinecone.ui.features.library.filters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.inSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FilterCategoryViewModel(application: Application) : AndroidViewModel(application) {
    private val bookRepository = (application as PineconeApplication).libraryRepository
    private val prefsManager = LibraryPreferencesManager(application)

    private val unfilteredBooks: Flow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    private val globalSpaceId = prefsManager.getGlobalSpaceFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null
    )

    private val booksFlow: Flow<List<Book>> = combine(unfilteredBooks, globalSpaceId) { books, spaceId ->
        books.inSpace(spaceId)
    }

    val allAuthors = combine(bookRepository.getAllAuthors(), booksFlow) { authors, books ->
        val namesInSpace = books.flatMap { it.authors }.toSet()
        authors.filter { it.name in namesInSpace }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTags = combine(bookRepository.getAllTags(), booksFlow) { tags, books ->
        val namesInSpace = books.flatMap { it.tags }.toSet()
        tags.filter { it.name in namesInSpace }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSpaces =
        bookRepository.getAllSpaces().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    val spacesWithCounts = combine(allSpaces, unfilteredBooks) { spaces, books ->
        spaces.map { space ->
            val count = books.count { book -> book.spaceIds.contains(space.id) }
            Pair(space.name, count)
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
