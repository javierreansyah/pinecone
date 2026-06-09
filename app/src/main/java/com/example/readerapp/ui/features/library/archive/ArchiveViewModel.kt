package com.example.readerapp.ui.features.library.archive

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.ShelfWithCovers
import com.example.readerapp.data.model.Book
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ArchiveViewModel(application: Application) : AndroidViewModel(application) {
    private val bookRepository = (application as ReaderApplication).bookRepository

    private val booksFlow: Flow<List<Book>> = bookRepository.getAllBooks()
        .map { entities -> entities.map { Book.fromEntity(it) } }

    val allBooks: StateFlow<List<Book>> = booksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val archivedBooks: StateFlow<List<Book>> = booksFlow
        .map { books -> books.filter { it.isArchived } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val shelves: StateFlow<List<ShelfWithCovers>> = combine(
        bookRepository.getAllShelvesWithBooks(),
        bookRepository.getAllShelfBookCrossRefs()
    ) { shelvesList, crossRefs ->
        shelvesList.map { shelfWithCovers ->
            val shelfId = shelfWithCovers.shelf.id
            val shelfCrossRefs = crossRefs.filter { it.shelfId == shelfId }
            val sortedBooks = shelfWithCovers.books.sortedBy { book ->
                shelfCrossRefs.find { it.bookId == book.book.id }?.orderIndex ?: 0
            }
            shelfWithCovers.copy(books = sortedBooks)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            bookRepository.deleteBook(bookId)
        }
    }

    fun toggleArchive(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleArchive(bookId)
        }
    }

    fun toggleReadStatus(bookId: String) {
        viewModelScope.launch {
            bookRepository.toggleReadStatus(bookId)
        }
    }

    fun createShelfAndAddBook(name: String, bookId: String?) {
        viewModelScope.launch {
            val shelfId = bookRepository.createShelf(name)
            if (bookId != null) {
                bookRepository.addBookToShelf(shelfId, bookId)
            }
        }
    }

    fun addBookToShelf(shelfId: String, bookId: String) {
        viewModelScope.launch {
            bookRepository.addBookToShelf(shelfId, bookId)
        }
    }

    fun removeBookFromShelf(shelfId: String, bookId: String) {
        viewModelScope.launch {
            bookRepository.removeBookFromShelf(shelfId, bookId)
        }
    }
}
