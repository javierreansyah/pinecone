package com.example.readerapp.ui.features.library.info

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.model.Book
import com.example.readerapp.data.repository.library.LibraryRepository
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch


class BookInfoViewModel(
    application: Application, private val bookId: String
) : AndroidViewModel(application) {

    private val repository: LibraryRepository = (application as ReaderApplication).libraryRepository

    val shelves: StateFlow<List<ShelfWithCovers>> = repository.getAllShelvesWithBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<BookInfoUiState> = repository.getBookFlow(bookId)
        .map { entity ->
            BookInfoUiState(
                book = entity?.let { Book.fromEntity(it) },
                isLoading = false
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BookInfoUiState(isLoading = true)
        )

    fun toggleReadStatus() {
        viewModelScope.launch {
            repository.toggleReadStatus(bookId)
        }
    }

    fun toggleArchive() {
        viewModelScope.launch {
            repository.toggleArchive(bookId)
        }
    }

    fun deleteBook(onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.deleteBook(bookId)
            onSuccess()
        }
    }

    fun addBookToShelf(shelfId: String) {
        viewModelScope.launch {
            repository.addBookToShelf(shelfId, bookId)
        }
    }

    fun createShelfAndAddBook(name: String) {
        viewModelScope.launch {
            val shelfId = repository.createShelf(name)
            repository.addBookToShelf(shelfId, bookId)
        }
    }

    class Factory(
        private val application: Application, private val bookId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BookInfoViewModel(application, bookId) as T
        }
    }
}
