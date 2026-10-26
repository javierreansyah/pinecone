package com.javierreansyah.pinecone.ui.features.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import com.javierreansyah.pinecone.ui.features.reader.components.AudioReaderContent
import com.javierreansyah.pinecone.ui.features.reader.components.NoteBottomSheet
import com.javierreansyah.pinecone.ui.features.reader.components.ReaderBottomSheet
import com.javierreansyah.pinecone.ui.features.reader.components.overlay.PineconeThemedContent
import com.javierreansyah.pinecone.ui.features.reader.components.overlay.ReaderTopBar
import com.javierreansyah.pinecone.ui.features.reader.components.settings.ReaderSettingsContent
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** Entry-scoped presentation; playback and its publication belong exclusively to the service. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    bookId: String,
    initialLocatorJson: String?,
    onBack: () -> Unit,
    onBookInfo: (String) -> Unit,
    artworkAlpha: Float = 1f,
    topBarAlpha: Float = 1f,
) {
    // Use the class-derived default keys: both models share this entry's store.
    val player =
        hiltViewModel<AudioPlayerViewModel, AudioPlayerViewModel.Factory>(creationCallback = {
            it.create(bookId)
        })
    val notes =
        hiltViewModel<ReaderNotesViewModel, ReaderNotesViewModel.Factory>(creationCallback = {
            it.create(bookId)
        })
    val state by player.state.collectAsStateWithLifecycle()
    val settings by player.settings.collectAsStateWithLifecycle()
    val useBookSettings by player.useBookSettings.collectAsStateWithLifecycle()
    val jumpOrigin by player.jumpOrigin.collectAsStateWithLifecycle()
    val bookmarks by notes.sortedBookmarks.collectAsStateWithLifecycle()
    val noteItems by notes.sortedNotes.collectAsStateWithLifecycle()
    val sort by notes.sortOption.collectAsStateWithLifecycle()
    val selection by notes.selectionState.collectAsStateWithLifecycle()
    var showContents by rememberSaveable { mutableStateOf(false) }
    var contentsTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(player) { player.open(initialLocatorJson) }
    LaunchedEffect(state.tableOfContents) { notes.updateTableOfContents(state.tableOfContents) }
    val bookmarked = state.locator?.let { current ->
        bookmarks.any {
            Locator.fromJSON(JSONObject(it.locatorJson))?.isSamePosition(current) == true
        }
    } == true
    val toggleBookmark =
        { state.locator?.let { notes.toggleBookmark(it, it.title ?: state.chapter) }; Unit }
    PineconeThemedContent(
        settings.toReaderThemeSettings().isUiDark(isSystemInDarkTheme()),
        settings
    ) {
        Box(Modifier.fillMaxSize()) {
            if (audioPlayerMetadataReady(bookId, state)) {
                AudioReaderContent(
                    state = state,
                    coverPath = state.coverPath,
                    background = Color.Transparent,
                    foreground = MaterialTheme.colorScheme.onBackground,
                    artworkAlpha = artworkAlpha,
                    isBookmarked = bookmarked,
                    onToggleBookmark = toggleBookmark,
                    onAddNote = {
                        state.locator?.let {
                            notes.addNoteAndEdit(
                                it,
                                it.title ?: state.chapter
                            )
                        }
                    },
                    canAddNote = state.locator != null,
                    onPlayPause = player::togglePlayback,
                    onSkip = player::skip,
                    onSeek = player::seek,
                    onTrack = player::track,
                    onOpenBookmarks = { contentsTab = 1; showContents = true },
                    onOpenSettings = { showSettings = true },
                    onRetry = { player.open(retry = true) },
                )
            } else CircularProgressIndicator(Modifier.align(Alignment.Center))
            ReaderTopBar(
                modifier = Modifier.graphicsLayer { alpha = topBarAlpha },
                isBookmarked = bookmarked,
                isInSearchMode = false,
                searchQuery = "",
                onSearchTextClick = {},
                onCloseSearch = {},
                onSearchClick = {},
                onBack = onBack,
                onTocClick = { contentsTab = 0; showContents = true },
                onSettingsClick = { showSettings = true },
                onToggleBookmark = toggleBookmark,
                onInfoClick = { onBookInfo(bookId) },
                readerBgColor = Color.Transparent,
                readerTextColor = MaterialTheme.colorScheme.onBackground,
                jumpOrigin = jumpOrigin,
                onGoBackToOriginClick = player::returnToOrigin,
                onClearJumpOriginClick = player::clearJumpOrigin,
                canSearch = false,
                typographySettings = false,
                audioPlayer = true,
            )
        }
        if (showContents) ReaderBottomSheet(
            tableOfContents = state.tableOfContents,
            sortedBookmarks = bookmarks,
            sortedNotes = noteItems,
            sortOption = sort,
            onSortOptionChange = notes::setSortOption,
            currentLocator = state.locator,
            onChapterClick = { player.go(it); showContents = false },
            onBookmarkClick = { player.go(it); showContents = false },
            onNoteClick = { player.go(it); showContents = false },
            onAddNote = { text ->
                state.locator?.let {
                    notes.addNote(
                        it,
                        text,
                        chapterTitle = it.title ?: state.chapter
                    )
                }
            },
            onDeleteBookmark = notes::deleteBookmark,
            onDeleteNote = notes::deleteNote,
            onDismiss = { showContents = false },
            getPositionLabel = player::getPositionLabel,
            getChapterPageLabel = { "" },
            initialTab = contentsTab,
        )
        if (showSettings) ModalBottomSheet(onDismissRequest = { showSettings = false }) {
            ReaderSettingsContent(
                settings, player::updateSettings, profile = ReaderProfile.AUDIO,
                useBookSettings = useBookSettings, onUseBookSettings = player::setUseBookSettings
            )
        }
        selection.editingNote?.let { note ->
            NoteBottomSheet(
                note = note, onUpdateNote = notes::updateNote,
                onDeleteNote = notes::deleteNote, onDismiss = notes::hideEditNote
            )
        }
    }
}

internal fun audioPlayerMetadataReady(bookId: String, state: AudioPlaybackState): Boolean =
    state.bookId == bookId && (state.metadataLoaded || state.error != null)
