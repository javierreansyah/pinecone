package com.javierreansyah.pinecone.ui.features.library.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.domain.DeleteBooks
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.services.positions

@HiltViewModel(assistedFactory = BookInfoViewModel.Factory::class)
class BookInfoViewModel @AssistedInject constructor(
    @Assisted private val bookId: String,
    private val repository: LibraryRepository,
    readerPreferences: com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences,
    private val deletePublications: DeleteBooks,
) : ViewModel() {


    val settings = readerPreferences.readerSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.javierreansyah.pinecone.data.local.preferences.ReaderSettings())

    val uiState: StateFlow<BookInfoUiState> = repository.getBookFlow(bookId)
        .map { entity ->
            BookInfoUiState(
                book = entity?.let { Book.fromEntity(it) },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = BookInfoUiState(),
        )

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        combine(repository.getBookmarks(bookId), repository.getBookFlow(bookId)) { items, item ->
            items.filter { it.renditionKey == item?.book?.activeRendition }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> =
        combine(repository.getNotes(bookId), repository.getBookFlow(bookId)) { items, item ->
            items.filter { it.renditionKey == item?.book?.activeRendition }
        }
            .map { list -> list.filter { note -> note.noteText.isNotBlank() } }
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
                        pub.close()
                        tableOfContents to positions
                    } else {
                        null
                    }
                } ?: (emptyList<Link>() to emptyList())

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
            deletePublications(listOf(bookId))
            onSuccess()
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
            repository.saveReadingPosition(
                bookId,
                locator,
                repository.getBook(bookId)?.book?.activeRendition ?: "original"
            )
            onSaved()
        }
    }

    fun saveJumpReadingPosition(locator: Locator, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.saveJumpReadingPosition(bookId, locator)
            onSaved()
        }
    }


    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(bookId: String): BookInfoViewModel
    }
}
