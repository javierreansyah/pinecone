package com.example.readerapp.ui.features.info

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.model.Book
import com.example.readerapp.data.repository.library.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BookInfoUiState(
    val book: Book? = null,
    val isLoading: Boolean = true
)

class BookInfoViewModel(
    application: Application,
    private val bookId: String
) : AndroidViewModel(application) {

    private val repository: LibraryRepository = (application as ReaderApplication).libraryRepository

    private val _uiState = MutableStateFlow(BookInfoUiState())
    val uiState: StateFlow<BookInfoUiState> = _uiState.asStateFlow()

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val entity = repository.getBook(bookId)
            _uiState.update {
                it.copy(
                    book = entity?.let { Book.fromEntity(it) },
                    isLoading = false
                )
            }
        }
    }

    class Factory(
        private val application: Application,
        private val bookId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BookInfoViewModel(application, bookId) as T
        }
    }
}
