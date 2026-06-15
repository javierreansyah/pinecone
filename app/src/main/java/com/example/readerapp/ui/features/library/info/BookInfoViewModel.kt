package com.example.readerapp.ui.features.library.info

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.model.Book
import com.example.readerapp.data.repository.library.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.positions

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

    val bookmarks: StateFlow<List<BookmarkEntity>> = repository.getBookmarks(bookId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = repository.getNotes(bookId)
        .map { list -> list.filter { it.noteText.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tableOfContents = MutableStateFlow<List<Link>>(emptyList())
    val tableOfContents: StateFlow<List<Link>> = _tableOfContents.asStateFlow()

    private val _positions = MutableStateFlow<List<Locator>>(emptyList())
    val positions: StateFlow<List<Locator>> = _positions.asStateFlow()

    init {
        viewModelScope.launch {
            val details = repository.getBook(bookId)
            if (details != null) {
                val (toc, pos) = withContext(Dispatchers.IO) {
                    val pub = repository.openPublication(details)
                    if (pub != null) {
                        val tableOfContents = pub.tableOfContents
                        val positions = pub.positions()
                        pub.close() // Close the publication as we only need metadata and page mappings
                        tableOfContents to positions
                    } else {
                        null
                    }
                } ?: (emptyList<Link>() to emptyList<Locator>())

                _tableOfContents.value = toc
                _positions.value = pos
            }
        }
    }

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

    fun deleteFurthestPosition() {
        viewModelScope.launch {
            repository.resetFurthestToCurrent(bookId)
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.removeBookmark(bookmarkId)
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.removeNote(noteId)
        }
    }

    fun saveReadingPosition(locator: Locator, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.saveReadingPosition(bookId, locator)
            onSaved()
        }
    }

    fun saveJumpReadingPosition(locator: Locator, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.saveJumpReadingPosition(bookId, locator)
            onSaved()
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
