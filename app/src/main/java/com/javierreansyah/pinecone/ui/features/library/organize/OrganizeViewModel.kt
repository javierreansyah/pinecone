package com.javierreansyah.pinecone.ui.features.library.organize

import android.app.Application
import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class OrganizeUiState(
    val shelves: List<ShelfItemState> = emptyList(),
    val spaces: List<SpaceItemState> = emptyList(),
    val isLoading: Boolean = true
)

data class ShelfItemState(
    val shelf: ShelfEntity,
    val state: ToggleableState
)

data class SpaceItemState(
    val space: SpaceEntity,
    val state: ToggleableState
)

class OrganizeViewModel(
    application: Application,
    bookIdsStr: String
) : AndroidViewModel(application) {
    private val repository = (application as PineconeApplication).libraryRepository
    private val targetBookIds = bookIdsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    // User modifications to the states. Key is ID, value is new ToggleableState (On or Off).
    // Indeterminate means no change from initial.
    private val _shelfChanges = MutableStateFlow<Map<String, ToggleableState>>(emptyMap())
    private val _spaceChanges = MutableStateFlow<Map<String, ToggleableState>>(emptyMap())

    val uiState: StateFlow<OrganizeUiState> = combine(
        repository.getAllShelvesWithBooks(),
        repository.getAllSpaces(),
        repository.getAllBookSpaceCrossRefs(),
        _shelfChanges,
        _spaceChanges
    ) { shelvesWithBooks, spaces, spaceCrossRefs, shelfChanges, spaceChanges ->
        
        val shelfItems = shelvesWithBooks.filter { it.shelf.id != "unshelved" }.map { shelfWithBooks ->
            val shelfId = shelfWithBooks.shelf.id
            val booksInShelf = shelfWithBooks.books.map { it.book.id }
            val countInShelf = targetBookIds.count { it in booksInShelf }
            
            val initialState = when {
                countInShelf == 0 -> ToggleableState.Off
                countInShelf == targetBookIds.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            
            val currentState = shelfChanges[shelfId] ?: initialState
            ShelfItemState(shelfWithBooks.shelf, currentState)
        }.sortedBy { it.shelf.name }

        val spaceItems = spaces.filter { it.id != "_all_" }.map { space ->
            val spaceId = space.id
            val booksInSpace = spaceCrossRefs.filter { it.spaceId == spaceId }.map { it.bookId }
            val countInSpace = targetBookIds.count { it in booksInSpace }

            val initialState = when {
                countInSpace == 0 -> ToggleableState.Off
                countInSpace == targetBookIds.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }

            val currentState = spaceChanges[spaceId] ?: initialState
            SpaceItemState(space, currentState)
        }.sortedBy { it.space.name }

        OrganizeUiState(
            shelves = shelfItems,
            spaces = spaceItems,
            isLoading = false
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OrganizeUiState())

    fun toggleShelf(shelfId: String, currentState: ToggleableState) {
        val newState = when (currentState) {
            ToggleableState.Indeterminate -> ToggleableState.On // If mixed, clicking it selects all
            ToggleableState.On -> ToggleableState.Off // If selected, clicking deselects
            ToggleableState.Off -> ToggleableState.On // If deselected, clicking selects
        }
        _shelfChanges.value = _shelfChanges.value.toMutableMap().apply { put(shelfId, newState) }
    }

    fun toggleSpace(spaceId: String, currentState: ToggleableState) {
        val newState = when (currentState) {
            ToggleableState.Indeterminate -> ToggleableState.On
            ToggleableState.On -> ToggleableState.Off
            ToggleableState.Off -> ToggleableState.On
        }
        _spaceChanges.value = _spaceChanges.value.toMutableMap().apply { put(spaceId, newState) }
    }

    fun createShelf(name: String) {
        viewModelScope.launch {
            val shelfId = repository.createShelf(name)
            _shelfChanges.value = _shelfChanges.value.toMutableMap().apply { put(shelfId, ToggleableState.On) }
        }
    }

    fun createSpace(name: String) {
        viewModelScope.launch {
            val spaceId = repository.createSpace(name)
            _spaceChanges.value = _spaceChanges.value.toMutableMap().apply { put(spaceId, ToggleableState.On) }
        }
    }

    fun save(onComplete: () -> Unit) {
        viewModelScope.launch {
            // Apply shelf changes
            _shelfChanges.value.forEach { (shelfId, state) ->
                targetBookIds.forEach { bookId ->
                    if (state == ToggleableState.On) {
                        repository.addBookToShelf(shelfId, bookId)
                    } else if (state == ToggleableState.Off) {
                        repository.removeBookFromShelf(shelfId, bookId)
                    }
                }
            }

            // Apply space changes
            _spaceChanges.value.forEach { (spaceId, state) ->
                targetBookIds.forEach { bookId ->
                    if (state == ToggleableState.On) {
                        repository.addBookToSpace(spaceId, bookId)
                    } else if (state == ToggleableState.Off) {
                        repository.removeBookFromSpace(spaceId, bookId)
                    }
                }
            }
            onComplete()
        }
    }
}
