package com.javierreansyah.pinecone.ui.features.library.organize

import android.app.Application
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ShelfItemState(
    val shelf: ShelfEntity,
    val state: ToggleableState
)

data class SpaceWithShelvesItemState(
    val space: SpaceEntity,
    val state: ToggleableState,
    val shelves: List<ShelfItemState>
)

data class OrganizeUiState(
    val spaces: List<SpaceWithShelvesItemState> = emptyList(),
    val isLoading: Boolean = true
)

class OrganizeViewModel(
    application: Application,
    bookIdsStr: String
) : AndroidViewModel(application) {
    private val repository = (application as PineconeApplication).libraryRepository
    private val targetBookIds = bookIdsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    val uiState: StateFlow<OrganizeUiState> = combine(
        repository.getAllShelvesWithBooks(),
        repository.getAllSpaces(),
        repository.getAllBookSpaceCrossRefs()
    ) { shelvesWithBooks, spaces, spaceCrossRefs ->
        val validShelves = shelvesWithBooks.filter { it.shelf.id != "unshelved" }

        val spaceItems = spaces.filter { it.id != "_all_" }.map { space ->
            val spaceId = space.id
            val booksInSpace = spaceCrossRefs.filter { it.spaceId == spaceId }.map { it.bookId }
            val countInSpace = targetBookIds.count { it in booksInSpace }

            val spaceState = when (countInSpace) {
                0 -> ToggleableState.Off
                targetBookIds.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }

            val spaceShelves = validShelves
                .filter { it.shelf.spaceId == spaceId }
                .map { shelfWithBooks ->
                    val booksInShelf = shelfWithBooks.books.map { it.book.id }
                    val countInShelf = targetBookIds.count { it in booksInShelf }
                    val shelfState = when (countInShelf) {
                        0 -> ToggleableState.Off
                        targetBookIds.size -> ToggleableState.On
                        else -> ToggleableState.Indeterminate
                    }
                    ShelfItemState(shelfWithBooks.shelf, shelfState)
                }.sortedBy { it.shelf.name.lowercase() }

            SpaceWithShelvesItemState(
                space = space,
                state = spaceState,
                shelves = spaceShelves
            )
        }.sortedBy { it.space.name.lowercase() }

        OrganizeUiState(
            spaces = spaceItems,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrganizeUiState())

    fun toggleShelf(shelfId: String, currentState: ToggleableState) {
        val newState = when (currentState) {
            ToggleableState.Indeterminate -> ToggleableState.On
            ToggleableState.On -> ToggleableState.Off
            ToggleableState.Off -> ToggleableState.On
        }
        viewModelScope.launch {
            targetBookIds.forEach { bookId ->
                if (newState == ToggleableState.On) {
                    repository.addBookToShelf(shelfId, bookId)
                } else {
                    repository.removeBookFromShelf(shelfId, bookId)
                }
            }
        }
    }

    fun toggleSpace(spaceId: String, currentState: ToggleableState) {
        val newState = when (currentState) {
            ToggleableState.Indeterminate -> ToggleableState.On
            ToggleableState.On -> ToggleableState.Off
            ToggleableState.Off -> ToggleableState.On
        }
        viewModelScope.launch {
            targetBookIds.forEach { bookId ->
                if (newState == ToggleableState.On) {
                    repository.addBookToSpace(spaceId, bookId)
                } else {
                    repository.removeBookFromSpace(spaceId, bookId)
                }
            }
        }
    }

    fun createShelf(spaceId: String, name: String) {
        viewModelScope.launch {
            val shelfId = repository.createShelf(spaceId, name)
            targetBookIds.forEach { bookId -> repository.addBookToShelf(shelfId, bookId) }
        }
    }

    fun createSpace(name: String) {
        viewModelScope.launch {
            val spaceId = repository.createSpace(name)
            targetBookIds.forEach { bookId -> repository.addBookToSpace(spaceId, bookId) }
        }
    }
}
