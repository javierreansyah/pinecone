package com.javierreansyah.pinecone.ui.features.reader

import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
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
    private val repository: LibraryRepository,
) : ViewModel() {

    private val _selectionState = MutableStateFlow(SelectionState())
    val selectionState: StateFlow<SelectionState> = _selectionState.asStateFlow()

    private val _clearSelectionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val clearSelectionEvent: SharedFlow<Unit> = _clearSelectionEvent.asSharedFlow()

    private val _sortOption = MutableStateFlow(SortOption.BOOK_ORDER_ASC)
    private val renditionKey = MutableStateFlow("original")
    fun setRendition(key: String) {
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _tableOfContents = MutableStateFlow<List<Link>>(emptyList())

    fun updateTableOfContents(toc: List<Link>) {
        _tableOfContents.value = toc
    }

    val sortedBookmarks: StateFlow<List<BookmarkEntity>> =
        combine(bookmarks, _sortOption, _tableOfContents) { bms, sort, toc ->
            bms.asSequence().map { bookmark ->
                val locator = try {
                    Locator.fromJSON(JSONObject(bookmark.locatorJson))
                } catch (_: Exception) {
                    null
                }
                bookmark to locator
            }.sortedWith { (b1, l1), (b2, l2) ->
                when (sort) {
                    SortOption.CREATION_DATE_ASC -> b1.createdAt.compareTo(b2.createdAt)
                    SortOption.CREATION_DATE_DESC -> b2.createdAt.compareTo(b1.createdAt)
                    SortOption.BOOK_ORDER_ASC -> compareLocatorsWithFallback(l1, l2, toc)
                        ?: b1.createdAt.compareTo(b2.createdAt)

                    SortOption.BOOK_ORDER_DESC -> compareLocatorsWithFallback(l2, l1, toc)
                        ?: b2.createdAt.compareTo(b1.createdAt)
                }
            }.map { (itFirst, second) -> itFirst }.toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedNotes: StateFlow<List<NoteEntity>> =
        combine(notes, _sortOption, _tableOfContents) { nts, sort, toc ->
            nts.asSequence().map { note ->
                val locator = try {
                    Locator.fromJSON(JSONObject(note.locatorJson))
                } catch (_: Exception) {
                    null
                }
                note to locator
            }.sortedWith { (n1, l1), (n2, l2) ->
                when (sort) {
                    SortOption.CREATION_DATE_ASC -> n1.createdAt.compareTo(n2.createdAt)
                    SortOption.CREATION_DATE_DESC -> n2.createdAt.compareTo(n1.createdAt)
                    SortOption.BOOK_ORDER_ASC -> compareLocatorsWithFallback(l1, l2, toc)
                        ?: n1.createdAt.compareTo(n2.createdAt)

                    SortOption.BOOK_ORDER_DESC -> compareLocatorsWithFallback(l2, l1, toc)
                        ?: n2.createdAt.compareTo(n1.createdAt)
                }
            }.map { (itFirst2, second) -> itFirst2 }.toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    private fun compareLocatorsWithFallback(loc1: Locator?, loc2: Locator?, toc: List<Link>): Int? {
        if (loc1 != null && loc2 != null) {
            val comp = compareLocators(loc1, loc2, toc)
            if (comp != 0) return comp
            return null
        }
        if (loc1 != null) return -1
        if (loc2 != null) return 1
        return null
    }

    private fun compareLocators(loc1: Locator, loc2: Locator, tableOfContents: List<Link>): Int {
        val tp1 = loc1.locations.totalProgression
        val tp2 = loc2.locations.totalProgression
        if (tp1 != null && tp2 != null) return tp1.compareTo(tp2)

        val href1 = loc1.href.toString().substringBefore("#")
        val href2 = loc2.href.toString().substringBefore("#")
        if (href1 != href2) {
            val idx1 =
                tableOfContents.indexOfFirst { link ->
                    link.href.toString().substringBefore("#") == href1
                }
            val idx2 =
                tableOfContents.indexOfFirst { link ->
                    link.href.toString().substringBefore("#") == href2
                }
            if (idx1 != -1 && idx2 != -1) return idx1.compareTo(idx2)
            if (idx1 != -1) return -1
            if (idx2 != -1) return 1
            return href1.compareTo(href2)
        }

        val p1 = loc1.locations.progression
        val p2 = loc2.locations.progression
        if (p1 != null && p2 != null) return p1.compareTo(p2)

        val pos1 = loc1.locations.position
        val pos2 = loc2.locations.position
        if (pos1 != null && pos2 != null) return pos1.compareTo(pos2)

        return 0
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
                repository.removeBookmark(existing.id)
            } else {
                repository.addBookmark(bookId, locator, chapterTitle, renditionKey.value)
            }
        }
    }

    fun deleteBookmark(bookmarkId: Long) {
        viewModelScope.launch {
            repository.removeBookmark(bookmarkId)
        }
    }

    fun addNote(
        locator: Locator,
        noteText: String,
        color: Int = "#40FFEB3B".toColorInt(),
        chapterTitle: String?,
    ) {
        viewModelScope.launch {
            repository.addNote(bookId, locator, noteText, color, chapterTitle, renditionKey.value)
        }
    }

    fun addHighlight(
        locator: Locator,
        color: Int = "#4003A9F4".toColorInt(),
        chapterTitle: String?,
    ) {
        viewModelScope.launch {
            repository.addNote(bookId, locator, "", color, chapterTitle, renditionKey.value)
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
                )
            } else {
                repository.updateNote(note)
            }
        }
    }

    fun deleteNote(noteId: Long) {
        viewModelScope.launch {
            repository.removeNote(noteId)
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
