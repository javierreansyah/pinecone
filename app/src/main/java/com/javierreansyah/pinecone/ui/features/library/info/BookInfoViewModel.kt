package com.javierreansyah.pinecone.ui.features.library.info

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.data.repository.library.PublicationRepository
import com.javierreansyah.pinecone.data.repository.library.ReadingRepository
import com.javierreansyah.pinecone.domain.DeleteBooks
import com.javierreansyah.pinecone.ui.features.library.sortShelfBooks
import com.javierreansyah.pinecone.ui.features.library.sortShelves
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
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
    private val publications: PublicationRepository,
    private val reading: ReadingRepository,
    readerPreferences: com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences,
    private val deletePublications: DeleteBooks,
    libraryPreferences: LibraryPreferencesManager,
    @com.javierreansyah.pinecone.di.DefaultDispatcher computation: kotlinx.coroutines.CoroutineDispatcher,
) : ViewModel() {
    val mutationErrors = com.javierreansyah.pinecone.ui.components.MutationErrorState()

    val settings = readerPreferences.readerSettings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        com.javierreansyah.pinecone.data.local.preferences.ReaderSettings()
    )

    private val bookDetails = repository.getBookFlow(bookId)
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    val uiState: StateFlow<BookInfoUiState> = bookDetails
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val shelves: StateFlow<List<ShelfWithCovers>> = libraryPreferences.getEffectiveSpaceFlow()
        .flatMapLatest { spaceId ->
            combine(
                repository.getShelvesForBook(bookId, spaceId),
                repository.getAllShelfBookCrossRefs(),
                libraryPreferences.preferences(spaceId, "library_shelves"),
            ) { memberships, refs, preferences ->
                sortShelves(sortShelfBooks(memberships, refs, spaceId), preferences)
            }
        }.flowOn(computation)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val spaceNames = repository.getAllSpaces()
        .map { spaces -> spaces.associate { it.id to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val bookmarks: StateFlow<List<BookmarkEntity>> =
        combine(reading.getBookmarks(bookId), bookDetails) { items, item ->
            items.filter { it.renditionKey == item?.book?.activeRendition }
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> =
        combine(reading.getNotes(bookId), bookDetails) { items, item ->
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
            try {
                val details = repository.getBook(bookId)
                if (details != null) {
                    val (toc, pos) = withContext(Dispatchers.IO) {
                        val pub = publications.openPublication(details)
                        if (pub != null) {
                            try {
                                pub.tableOfContents to pub.positions()
                            } finally {
                                pub.close()
                            }
                        } else {
                            null
                        }
                    } ?: (emptyList<Link>() to emptyList())

                    _tableOfContents.value = toc
                    _positions.value = pos
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.log.Timber.e(error, "Could not load publication contents")
            }
        }
    }

    fun toggleReadStatus() {
        viewModelScope.launch {
            mutationErrors.run {
                repository.toggleReadStatus(bookId)
            }
        }
    }

    fun toggleArchive() {
        viewModelScope.launch {
            mutationErrors.run {
                repository.toggleArchive(bookId)
            }
        }
    }

    fun deleteBook(onSuccess: () -> Unit) {
        viewModelScope.launch {
            mutationErrors.run {
                deletePublications(listOf(bookId))
                onSuccess()
            }
        }
    }


    fun deleteFurthestPosition() {
        viewModelScope.launch {
            mutationErrors.run {
                reading.resetFurthestToCurrent(bookId)
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            mutationErrors.run {
                reading.removeBookmark(bookmarkId)
            }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            mutationErrors.run {
                reading.removeNote(noteId)
            }
        }
    }

    fun saveReadingPosition(locator: Locator, onSaved: () -> Unit) {
        viewModelScope.launch {
            mutationErrors.run {
                reading.saveReadingPosition(
                    bookId,
                    locator,
                    repository.getBook(bookId)?.book?.activeRendition ?: "original"
                )
                onSaved()
            }
        }
    }

    fun saveJumpReadingPosition(locator: Locator, onSaved: () -> Unit) {
        viewModelScope.launch {
            mutationErrors.run {
                reading.saveJumpReadingPosition(bookId, locator)
                onSaved()
            }
        }
    }


    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(bookId: String): BookInfoViewModel
    }
}
