package com.javierreansyah.pinecone.ui.features.library.filters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.AuthorEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.database.library.TagEntity
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.inSpace
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
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
    prefsManager: LibraryPreferencesManager,
    @com.javierreansyah.pinecone.di.DefaultDispatcher private val computation: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    val mutationErrors = com.javierreansyah.pinecone.ui.components.MutationErrorState()


    private val unfilteredBooks: StateFlow<List<Book>> =
        bookRepository.getAllBooks().map { entities -> entities.map { Book.fromEntity(it) } }
            .flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Label edits affect archived items too, even though visible counts exclude them.
    private val actionBooks: StateFlow<List<Book>> = combine(
        unfilteredBooks, bookRepository.getArchivedBooks(),
    ) { books, archived -> books + archived.map { Book.fromEntity(it) } }
        .flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val globalSpaceId = prefsManager.getEffectiveSpaceFlow().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), null,
    )

    private val booksFlow: StateFlow<List<Book>> =
        combine(unfilteredBooks, globalSpaceId) { books, spaceId ->
            books.inSpace(spaceId)
        }.flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val visibleIndex = booksFlow.map(::LibraryCategoryIndex).flowOn(computation)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LibraryCategoryIndex(emptyList())
        )
    private val actionIndex = actionBooks.map(::LibraryCategoryIndex).flowOn(computation)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LibraryCategoryIndex(emptyList())
        )
    private val spaceIndex = unfilteredBooks.map(::LibraryCategoryIndex).flowOn(computation)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            LibraryCategoryIndex(emptyList())
        )

    val allAuthors: StateFlow<List<AuthorEntity>> =
        combine(bookRepository.getAllAuthors(), visibleIndex) { authors, index ->
            authors.filter { it.name in index.authors }
        }.flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTags: StateFlow<List<TagEntity>> =
        combine(bookRepository.getAllTags(), visibleIndex) { tags, index ->
            tags.filter { it.name in index.tags }
        }.flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allSpaces: StateFlow<List<SpaceEntity>> =
        bookRepository.getAllSpaces().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val authorsWithCounts: StateFlow<List<FilterCollectionSummary>> =
        combine(allAuthors, visibleIndex, actionIndex) { authors, books, allBooks ->
            authors.map { (_, authorName) ->
                val members = books.authors[authorName].orEmpty()
                FilterCollectionSummary(
                    authorName, members.size, members.publicationWording(),
                    allBooks.authors[authorName].orEmpty().publicationWording()
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val tagsWithCounts: StateFlow<List<FilterCollectionSummary>> =
        combine(allTags, visibleIndex, actionIndex) { tags, books, allBooks ->
            tags.map { (_, tagName) ->
                val members = books.tags[tagName].orEmpty()
                FilterCollectionSummary(
                    tagName, members.size, members.publicationWording(),
                    allBooks.tags[tagName].orEmpty().publicationWording()
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val spacesWithCounts: StateFlow<List<FilterCollectionSummary>> =
        combine(allSpaces, spaceIndex, actionIndex) { spaces, books, allBooks ->
            spaces.filter { it.id != "_all_" }.map { (spaceId2, spaceName, createdAt) ->
                val members = books.spaces[spaceId2].orEmpty()
                FilterCollectionSummary(
                    spaceName, members.size, members.publicationWording(),
                    allBooks.spaceNames[spaceName].orEmpty().publicationWording()
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

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

    fun renameFilterItems(
        type: String, oldNames: List<String>, newName: String, onSuccess: (String) -> Unit,
    ) {
        viewModelScope.launch {
            mutationErrors.run {
                oldNames.forEach { oldName ->
                    bookRepository.renameFilterItem(type, oldName, newName)
                }
                onSuccess(newName.trim())
            }
        }
    }
}
