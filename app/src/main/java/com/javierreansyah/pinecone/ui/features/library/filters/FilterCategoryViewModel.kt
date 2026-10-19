package com.javierreansyah.pinecone.ui.features.library.filters

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.inSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        viewModelScope, SharingStarted.WhileSubscribed(5000), null,
    )

    private val booksFlow: Flow<List<Book>> =
        combine(unfilteredBooks, globalSpaceId) { books, spaceId ->
            books.inSpace(spaceId)
        }

    val allAuthors: StateFlow<List<AuthorEntity>> = combine(bookRepository.getAllAuthors(), booksFlow) { authors, books ->
        val namesInSpace = books.flatMapTo(mutableSetOf()) { (_, _, itAuthors) -> itAuthors }
        authors.filter { (_, itName) -> itName in namesInSpace }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTags: StateFlow<List<TagEntity>> = combine(bookRepository.getAllTags(), booksFlow) { tags, books ->
        val namesInSpace = books.flatMapTo(mutableSetOf()) { (_, _, _, _, _, _, _, _, _, _, _, _, itTags) -> itTags }
        tags.filter { (_, itName2) -> itName2 in namesInSpace }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSpaces: StateFlow<List<SpaceEntity>> =
        bookRepository.getAllSpaces().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val authorsWithCounts: StateFlow<List<Pair<String, Int>>> = combine(allAuthors, booksFlow) { authors, books ->
        authors.map { (_, authorName) ->
            val count = books.count { (_, _, itAuthors2) -> itAuthors2.contains(authorName) }
            Pair(authorName, count)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tagsWithCounts: StateFlow<List<Pair<String, Int>>> = combine(allTags, booksFlow) { tags, books ->
        tags.map { (_, tagName) ->
            val count = books.count { (_, _, _, _, _, _, _, _, _, _, _, _, itTags2) -> itTags2.contains(tagName) }
            Pair(tagName, count)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val spacesWithCounts: StateFlow<List<Pair<String, Int>>> = combine(allSpaces, unfilteredBooks) { spaces, books ->
        spaces.map { (spaceId2, spaceName) ->
            val count = books.count { book -> book.spaceIds.contains(spaceId2) }
            Pair(spaceName, count)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    fun renameFilterItems(
        type: String, oldNames: List<String>, newName: String, onSuccess: (String) -> Unit,
    ) {
        onSuccess(newName.trim())
        viewModelScope.launch(Dispatchers.IO) {
            oldNames.forEach { oldName ->
                bookRepository.renameFilterItem(type, oldName, newName)
            }
        }
    }
}
