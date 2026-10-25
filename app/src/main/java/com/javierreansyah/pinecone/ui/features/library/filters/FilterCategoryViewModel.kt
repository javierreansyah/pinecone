package com.javierreansyah.pinecone.ui.features.library.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.inNamedCollection
import com.javierreansyah.pinecone.ui.features.library.inSpace
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FilterCollectionSummary(
    val name: String,
    val count: Int,
    val wording: PublicationWording,
    val actionWording: PublicationWording = wording,
)

@HiltViewModel
class FilterCategoryViewModel @Inject constructor(
    private val bookRepository: LibraryRepository,
    private val prefsManager: LibraryPreferencesManager,
) : ViewModel() {

    private val unfilteredBooks: Flow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }

    // Label edits affect archived items too, even though visible counts exclude them.
    private val actionBooks: Flow<List<Book>> = combine(
        unfilteredBooks, bookRepository.getArchivedBooks(),
    ) { books, archived -> books + archived.map { Book.fromEntity(it) } }

    private val globalSpaceId = prefsManager.getEffectiveSpaceFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null,
    )

    private val booksFlow: Flow<List<Book>> =
        combine(unfilteredBooks, globalSpaceId) { books, spaceId ->
            books.inSpace(spaceId)
        }

    val allAuthors: StateFlow<List<AuthorEntity>> =
        combine(bookRepository.getAllAuthors(), booksFlow) { authors, books ->
            val namesInSpace =
                books.flatMapTo(mutableSetOf()) { book -> book.authors }
            authors.filter { (_, itName) -> itName in namesInSpace }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allTags: StateFlow<List<TagEntity>> =
        combine(bookRepository.getAllTags(), booksFlow) { tags, books ->
            val namesInSpace =
                books.flatMapTo(mutableSetOf()) { book -> book.tags }
            tags.filter { (_, itName2) -> itName2 in namesInSpace }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSpaces: StateFlow<List<SpaceEntity>> =
        bookRepository.getAllSpaces().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val authorsWithCounts: StateFlow<List<FilterCollectionSummary>> =
        combine(allAuthors, booksFlow, actionBooks) { authors, books, allBooks ->
            authors.map { (_, authorName) ->
                val members = books.filter { authorName in it.authors }
                FilterCollectionSummary(
                    authorName, members.size, members.publicationWording(),
                    allBooks.inNamedCollection("author", authorName).publicationWording()
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tagsWithCounts: StateFlow<List<FilterCollectionSummary>> =
        combine(allTags, booksFlow, actionBooks) { tags, books, allBooks ->
            tags.map { (_, tagName) ->
                val members = books.filter { tagName in it.tags }
                FilterCollectionSummary(
                    tagName, members.size, members.publicationWording(),
                    allBooks.inNamedCollection("tag", tagName).publicationWording()
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val spacesWithCounts: StateFlow<List<FilterCollectionSummary>> =
        combine(allSpaces, unfilteredBooks, actionBooks) { spaces, books, allBooks ->
            spaces.filter { it.id != "_all_" }.map { (spaceId2, spaceName, createdAt) ->
                val members = books.filter { !it.isUnsorted && spaceId2 in it.spaceIds }
                FilterCollectionSummary(
                    spaceName, members.size, members.publicationWording(),
                    allBooks.inNamedCollection("space", spaceName).publicationWording()
                )
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
