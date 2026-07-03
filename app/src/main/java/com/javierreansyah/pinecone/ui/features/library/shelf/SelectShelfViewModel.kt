package com.javierreansyah.pinecone.ui.features.library.shelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SelectShelfViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val bookRepository = (application as PineconeApplication).libraryRepository

    val shelves: StateFlow<List<ShelfWithCovers>> = bookRepository.getAllShelvesWithBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addBooksToShelf(shelfId: String, bookIdsStr: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val ids = bookIdsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            ids.forEach { bookId ->
                bookRepository.addBookToShelf(shelfId, bookId)
            }
            onComplete()
        }
    }

    fun createShelfAndAddBooks(name: String, bookIdsStr: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val shelfId = bookRepository.createShelf(name)
            val ids = bookIdsStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            ids.forEach { bookId ->
                bookRepository.addBookToShelf(shelfId, bookId)
            }
            onComplete()
        }
    }
}
