package com.javierreansyah.pinecone.ui.features.reader

import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.repository.library.ReadingRepository
import com.javierreansyah.pinecone.ui.features.reader.components.SortOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

data class SelectionState(
    val selectionLocator: Locator? = null,
    val editingNote: NoteEntity? = null,
    val viewingHighlight: NoteEntity? = null,
)

@HiltViewModel(assistedFactory = ReaderNotesViewModel.Factory::class)
class ReaderNotesViewModel @dagger.assisted.AssistedInject constructor(
    @dagger.assisted.Assisted private val bookId: String,
    private val repository: ReadingRepository,
) : ViewModel() {

    private val _selectionState = MutableStateFlow(SelectionState())
    val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()

    private val _clearSelectionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearSelectionEvent: SharedFlow<Unit> = _clearSelectionEvent.asSharedFlow()

    private val _sortOption = MutableStateFlow(SortOption.BOOK_ORDER_ASC)
    private val renditionKey = MutableStateFlow("original")
    private var session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null

    fun setRendition(
        key: String,
        token: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = null
    ) {
        if (token != null) session = token
        if (session == null) viewModelScope.launch { session = repository.currentSession(bookId) }
        renditionKey.value = key
        _selectionState.value = SelectionState()
    }

    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    val bookmarks: StateFlow<List<BookmarkEntity>> = combine(
        repository.getBookmarks(bookId),
        renditionKey
    ) { items, key -> items.filter { it.renditionKey == key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allNotesAndHighlights: StateFlow<List<NoteEntity>> = combine(
        repository.getNotes(bookId),
        renditionKey
    ) { items, key -> items.filter { it.renditionKey == key } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = allNotesAndHighlights

    private val _tableOfContents = MutableStateFlow<List<Link>>(emptyList())

    fun updateTableOfContents(toc: List<Link>) {
        _tableOfContents.value = toc
    }

    val sortedBookmarks: StateFlow<List<BookmarkEntity>> =
        combine(bookmarks, _sortOption, _tableOfContents) { items, sort, toc ->
            sortAnnotations(
                items,
                sort,
                toc,
                { it.locatorJson.toLocatorOrNull() },
                { it.createdAt })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedNotes: StateFlow<List<NoteEntity>> =
        combine(notes, _sortOption, _tableOfContents) { items, sort, toc ->
            sortAnnotations(
                items,
                sort,
                toc,
                { it.locatorJson.toLocatorOrNull() },
                { it.createdAt })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun toggleBookmark(locator: Locator, chapterTitle: String?) {
        viewModelScope.launch {
            val existing = bookmarks.value.find { bookmark ->
                try {
                    val bmLocator = Locator.fromJSON(JSONObject(bookmark.locatorJson))
                    bmLocator != null && bmLocator.isSamePosition(locator)
                } catch (_: Exception) {
                    false
                }
            }
            if (existing != null) {
                repository.removeBookmark(existing.id, session)
            } else {
                repository.addBookmark(bookId, locator, chapterTitle, renditionKey.value, session)
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.removeBookmark(bookmarkId, session)
        }
    }

    fun addNote(
        locator: Locator,
        noteText: String,
        color: Int = "#40FFEB3B".toColorInt(),
        chapterTitle: String?,
    ) {
        viewModelScope.launch {
            repository.addNote(
                bookId,
                locator,
                noteText,
                color,
                chapterTitle,
                renditionKey.value,
                session
            )
        }
    }

    fun addHighlight(
        locator: Locator,
        color: Int = "#4003A9F4".toColorInt(),
        chapterTitle: String?,
    ) {
        viewModelScope.launch {
            repository.addNote(
                bookId,
                locator,
                "",
                color,
                chapterTitle,
                renditionKey.value,
                session
            )
        }
    }

    fun updateNote(note: NoteEntity) {
        viewModelScope.launch {
            if (note.id == 0L) {
                repository.addNote(
                    bookId = bookId,
                    locator = Locator.fromJSON(JSONObject(note.locatorJson)) ?: return@launch,
                    noteText = note.noteText,
                    color = note.color,
                    chapterTitle = note.chapterTitle,
                    renditionKey = note.renditionKey,
                    session = session,
                )
            } else {
                repository.updateNote(note, session)
            }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.removeNote(noteId, session)
        }
    }

    fun showSelectionMenu(locator: Locator) {
        _selectionState.update { it.copy(selectionLocator = locator) }
    }

    fun hideSelectionMenu() {
        _selectionState.update { it.copy(selectionLocator = null) }
        _clearSelectionEvent.tryEmit(Unit)
    }

    fun dismissSelectionBar() {
        _selectionState.update { it.copy(selectionLocator = null) }
    }

    fun addNoteAndEdit(locator: Locator, chapterTitle: String?) {
        val newNote = NoteEntity(
            bookId = bookId,
            locatorJson = locator.toJSON().toString(),
            chapterTitle = chapterTitle,
            noteText = "",
            color = "#40FFEB3B".toColorInt(),
            renditionKey = renditionKey.value,
        )
        editNote(newNote)
    }

    fun editNote(note: NoteEntity) {
        _selectionState.update { it.copy(editingNote = note) }
    }

    fun hideEditNote() {
        _selectionState.update { it.copy(editingNote = null) }
    }

    fun viewHighlight(note: NoteEntity) {
        _selectionState.update { it.copy(viewingHighlight = note) }
    }

    fun hideViewHighlight() {
        _selectionState.update { it.copy(viewingHighlight = null) }
    }

    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(bookId: String): ReaderNotesViewModel
    }
}
