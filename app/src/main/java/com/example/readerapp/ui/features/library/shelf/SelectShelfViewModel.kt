package com.example.readerapp.ui.features.library.shelf

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SelectShelfViewModel(
    application: Application
) : AndroidViewModel(application) {
    private val bookRepository = (application as ReaderApplication).libraryRepository

    val shelves: StateFlow<List<ShelfWithCovers>> = bookRepository.getAllShelvesWithBooks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun addBookToShelf(shelfId: String, bookId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            bookRepository.addBookToShelf(shelfId, bookId)
            onComplete()
        }
    }

    fun createShelfAndAddBook(name: String, bookId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val shelfId = bookRepository.createShelf(name)
            bookRepository.addBookToShelf(shelfId, bookId)
            onComplete()
        }
    }
}
