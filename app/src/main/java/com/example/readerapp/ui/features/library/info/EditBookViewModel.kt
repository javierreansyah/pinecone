package com.example.readerapp.ui.features.library.info

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.database.library.AuthorEntity
import com.example.readerapp.data.local.database.library.TagEntity
import com.example.readerapp.data.repository.library.LibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class EditBookViewModel(
    application: Application, private val bookId: String
) : AndroidViewModel(application) {

    private val repository: LibraryRepository = (application as ReaderApplication).libraryRepository

    private val _uiState = MutableStateFlow(EditBookUiState())
    val uiState: StateFlow<EditBookUiState> = _uiState.asStateFlow()

    val allAuthors: StateFlow<List<AuthorEntity>> = repository.getAllAuthors()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTags: StateFlow<List<TagEntity>> = repository.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadBook()
    }

    private fun loadBook() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val bookDetails = repository.getBook(bookId)
            if (bookDetails != null) {
                val book = bookDetails.book
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        title = book.title,
                        description = book.description ?: "",
                        existingCoverPath = book.coverPath,
                        tags = bookDetails.tags.map { tag -> tag.name },
                        authors = bookDetails.sortedAuthors.map { author -> author.name })
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Book not found") }
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun updateCoverUri(uri: Uri?) {
        _uiState.update { it.copy(coverUri = uri) }
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isNotEmpty() && !_uiState.value.tags.contains(trimmed)) {
            _uiState.update { it.copy(tags = it.tags + trimmed) }
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags.filter { t -> t != tag }) }
    }

    fun addAuthor(author: String) {
        val trimmed = author.trim()
        if (trimmed.isNotEmpty() && !_uiState.value.authors.contains(trimmed)) {
            _uiState.update { it.copy(authors = it.authors + trimmed) }
        }
    }

    fun removeAuthor(author: String) {
        _uiState.update { it.copy(authors = it.authors.filter { a -> a != author }) }
    }

    fun saveChanges() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val state = _uiState.value
                repository.updateBookMetadata(
                    bookId = bookId,
                    title = state.title,
                    description = state.description.ifBlank { null },
                    coverUri = state.coverUri,
                    authors = state.authors,
                    tags = state.tags
                )
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    class Factory(
        private val application: Application, private val bookId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return EditBookViewModel(application, bookId) as T
        }
    }
}
