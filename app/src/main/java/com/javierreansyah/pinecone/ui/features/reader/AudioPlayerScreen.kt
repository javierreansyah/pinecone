package com.javierreansyah.pinecone.ui.features.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import com.javierreansyah.pinecone.ui.features.reader.components.overlay.ReaderOverlay
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

/** The audio presentation is entry-scoped; the service, not this screen, owns playback. */
@Composable
fun AudioPlayerScreen(
    bookId: String,
    initialLocatorJson: String?,
    onBack: () -> Unit,
    onBookInfo: (String) -> Unit,
    artworkAlpha: Float = 1f,
    topBarAlpha: Float = 1f,
) {
    val app: com.javierreansyah.pinecone.ui.root.AppServicesViewModel = hiltViewModel()
    val darkTheme = isSystemInDarkTheme()
    val reader = hiltViewModel<ReaderViewModel, ReaderViewModel.Factory>(
        key = bookId, creationCallback = { it.create(bookId) },
    )
    val search: ReaderSearchViewModel = hiltViewModel()
    val notes = hiltViewModel<ReaderNotesViewModel, ReaderNotesViewModel.Factory>(
        key = bookId, creationCallback = { it.create(bookId) },
    )
    val dictionary: ReaderDictionaryViewModel = hiltViewModel()
    val publication by reader.publication.collectAsStateWithLifecycle()
    val bookState by reader.bookState.collectAsStateWithLifecycle()
    val audioState by app.audioSession.state.collectAsStateWithLifecycle()
    var pendingLocatorJson by rememberSaveable { mutableStateOf(initialLocatorJson) }

    LaunchedEffect(reader) {
        if (reader.publication.value == null) reader.openBook()
    }
    LaunchedEffect(darkTheme) { reader.systemDarkThemeFlow.value = darkTheme }
    LaunchedEffect(publication) {
        if (publication != null && reader.profile.value == ReaderProfile.AUDIO) {
            reader.startAudio()
            val initial = pendingLocatorJson?.let { Locator.fromJSON(JSONObject(it)) }
            if (initial != null) {
                app.audioSession.state.first { it.bookId == bookId && !it.loading }
                reader.audioGo(initial)
                pendingLocatorJson = null
            }
        }
    }

    val router = remember(onBack, onBookInfo) {
        object : ReaderNavigationRouter {
            override fun navigateBack() = onBack()
            override fun navigateToBookInfo(bookId: String) = onBookInfo(bookId)
        }
    }
    Box(Modifier.fillMaxSize()) {
        // Wait for real metadata instead of reserving extra title/author lines.
        if (!bookState.isLoading && (bookState.error != null || audioPlayerMetadataReady(
                bookId,
                audioState
            ))
        ) {
            ReaderOverlay(
                viewModel = reader,
                searchViewModel = search,
                notesViewModel = notes,
                dictionaryViewModel = dictionary,
                router = router,
                bookId = bookId,
                onNavigateToChapter = reader::audioGo,
                onSeekToProgression = { progression ->
                    reader.locatorForProgression(progression)?.let(reader::audioGo)
                },
                onNavigateToLocator = reader::audioGo,
                transparentAudioBackground = true,
                audioArtworkAlpha = artworkAlpha,
                audioTopBarAlpha = topBarAlpha,
            )
        } else {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

internal fun audioPlayerMetadataReady(bookId: String, state: AudioPlaybackState): Boolean =
    state.bookId == bookId && (state.metadataLoaded || state.error != null)
