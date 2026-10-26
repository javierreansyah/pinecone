package com.javierreansyah.pinecone.ui.features.library.organize

import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShelfItemState(
    val shelf: ShelfEntity,
    val state: ToggleableState,
)

data class SpaceWithShelvesItemState(
    val space: SpaceEntity,
    val state: ToggleableState,
    val shelves: List<ShelfItemState>,
)

data class OrganizeUiState(
    val spaces: List<SpaceWithShelvesItemState> = emptyList(),
    val isLoading: Boolean = true,
    val spacesEnabled: Boolean = true,
)

internal fun List<SpaceWithShelvesItemState>.prioritizedShelfGroups(): List<SpaceWithShelvesItemState> =
    filter { it.shelves.isNotEmpty() }.sortedBy {
        when (it.state) {
            ToggleableState.On -> 0
            ToggleableState.Indeterminate -> 1
            ToggleableState.Off -> 2
        }
    }

@HiltViewModel(assistedFactory = OrganizeViewModel.Factory::class)
class OrganizeViewModel @AssistedInject constructor(
    @Assisted bookIds: List<String>,
    private val repository: LibraryRepository,
    readerPreferences: ReaderPreferences,
    @com.javierreansyah.pinecone.di.DefaultDispatcher computation: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    private val targetBookIds = bookIds.toSet()

    val uiState: StateFlow<OrganizeUiState> = combine(
        repository.getAllShelvesWithBooks(),
        repository.getAllSpaces(),
        repository.getAllBookSpaceCrossRefs(),
        readerPreferences.readerSettings,
        repository.getAllBooks(),
    ) { shelvesWithBooks, spaces, spaceCrossRefs, settings, books ->
        val validShelves =
            shelvesWithBooks.filter { (itShelf, books1) -> itShelf.id != "unshelved" }
        val organized =
            books.asSequence().filterNot { it.book.isUnsorted }.map { it.book.id }.toSet()
        val membersBySpace =
            spaceCrossRefs.groupBy({ it.spaceId }, { it.bookId }).mapValues { it.value.toSet() }

        val spaceItems =
            spaces.asSequence().filter { settings.spacesEnabled || it.id == "_all_" }.map { space ->
                val spaceId = space.id
                val booksInSpace = membersBySpace[spaceId].orEmpty()
                val countInSpace =
                    targetBookIds.count { id -> id in booksInSpace && (spaceId != "_all_" || id in organized) }

                val spaceState = when (countInSpace) {
                    0 -> ToggleableState.Off
                    targetBookIds.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                }

                val spaceShelves = validShelves.asSequence()
                    .filter { (itShelf2, books1) -> !settings.spacesEnabled || itShelf2.spaceId == spaceId }
                    .map { (shelfWithBooksShelf, shelfWithBooksBooks) ->
                        val booksInShelf =
                            shelfWithBooksBooks.mapTo(mutableSetOf()) { details -> details.book.id }
                        val countInShelf = targetBookIds.count { it in booksInShelf }
                        val shelfState = when (countInShelf) {
                            0 -> ToggleableState.Off
                            targetBookIds.size -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        }
                        ShelfItemState(shelfWithBooksShelf, shelfState)
                    }.sortedBy { (itShelf3, state) -> itShelf3.name.lowercase() }.toList()

                SpaceWithShelvesItemState(
                    space = space,
                    state = spaceState,
                    shelves = spaceShelves,
                )
            }.sortedBy { (itSpace, state, shelves) -> itSpace.name.lowercase() }.toList()

        OrganizeUiState(
            spaces = spaceItems,
            isLoading = false,
            spacesEnabled = settings.spacesEnabled,
        )
    }.flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrganizeUiState())

    val mutationErrors = com.javierreansyah.pinecone.ui.components.MutationErrorState()

    fun toggleShelf(shelfId: String, currentState: ToggleableState) {
        viewModelScope.launch {
            mutationErrors.run {
                repository.setBooksInShelf(
                    shelfId,
                    targetBookIds,
                    currentState != ToggleableState.On
                )
            }
        }
    }

    fun toggleSpace(spaceId: String, currentState: ToggleableState) {
        viewModelScope.launch {
            mutationErrors.run {
                repository.setBooksInSpace(
                    spaceId,
                    targetBookIds,
                    currentState != ToggleableState.On
                )
            }
        }
    }

    fun createShelf(spaceId: String, name: String) {
        viewModelScope.launch {
            mutationErrors.run {
                repository.createShelfWithBooks(
                    spaceId,
                    name,
                    targetBookIds
                )
            }
        }
    }

    fun createSpace(name: String) {
        viewModelScope.launch {
            mutationErrors.run {
                repository.createSpaceWithBooks(
                    name,
                    targetBookIds
                )
            }
        }
    }

    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(bookIds: List<String>): OrganizeViewModel
    }
}
