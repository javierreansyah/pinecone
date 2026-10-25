@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.ui.root

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Forward_10
import com.composables.icons.materialsymbols.outlined.Headphones
import com.composables.icons.materialsymbols.outlined.Pause
import com.composables.icons.materialsymbols.outlined.Play_arrow
import com.composables.icons.materialsymbols.outlined.Replay_10
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import java.io.File
import kotlinx.coroutines.flow.flowOf

/** Extra scroll space, not viewport padding: content can draw behind the floating card. */
val LocalAudioPlayerBottomPadding: ProvidableCompositionLocal<Dp> =
    staticCompositionLocalOf { 0.dp }

internal val MiniPlayerArtworkRadius = 10.dp
internal val MiniPlayerArtworkInset = 10.dp
internal val MiniPlayerContainerRadius = MiniPlayerArtworkRadius + MiniPlayerArtworkInset
internal val MiniPlayerArtworkSize = 48.dp
internal val MiniPlayerProgressHeight = 4.dp
internal val MiniPlayerOuterVerticalPadding = 8.dp
internal val MiniPlayerMaxWidth = 600.dp
internal val MiniPlayerHorizontalPadding = 12.dp
internal val MiniPlayerMaxOuterWidth = MiniPlayerMaxWidth + MiniPlayerHorizontalPadding * 2
internal val MiniPlayerHeight = MiniPlayerArtworkSize + MiniPlayerArtworkInset * 2 +
        MiniPlayerProgressHeight + MiniPlayerOuterVerticalPadding * 2

/** Floats the mini-player over every screen, keeping the surrounding area transparent. */
@Composable
fun AudioMiniPlayerHost(enabled: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val app: com.javierreansyah.pinecone.ui.root.AppServicesViewModel = hiltViewModel()
    val session = app.audioSession
    val state by session.state.collectAsStateWithLifecycle()
    val navigator by session.navigator.collectAsStateWithLifecycle()
    val visible = enabled && state.bookId != null && navigator != null
    val density = LocalDensity.current
    var handleHeight by remember { mutableIntStateOf(0) }
    val navigationBarHeight = WindowInsets.navigationBars.getBottom(density)
    val bottomPadding = with(density) {
        if (visible) (handleHeight - navigationBarHeight).coerceAtLeast(0).toDp() else 0.dp
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        CompositionLocalProvider(LocalAudioPlayerBottomPadding provides bottomPadding) {
            content()
        }
        if (enabled) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { handleHeight = it.height }) {
                AudioMiniPlayerBar()
            }
        }
    }
}

/** Reserves the sheet's collapsed bounds outside the screen's navigation bar. */
@Composable
fun AudioMiniPlayerBar(includeNavigationBarPadding: Boolean = true, bottomInset: Dp = 0.dp) {
    val context = LocalContext.current
    val app: com.javierreansyah.pinecone.ui.root.AppServicesViewModel = hiltViewModel()
    val session = app.audioSession
    val state by session.state.collectAsStateWithLifecycle()
    val navigator by session.navigator.collectAsStateWithLifecycle()
    if (state.bookId == null || navigator == null) return

    val bookFlow = remember(state.bookId) {
        state.bookId?.let(app.libraryRepository::getBookFlow) ?: flowOf(null)
    }
    val book by bookFlow.collectAsStateWithLifecycle(initialValue = null)
    val reportHandle = LocalAudioPlayerHandle.current
    val density = LocalDensity.current
    var height by remember { mutableIntStateOf(0) }
    val bottomInsetPx = with(density) { bottomInset.roundToPx() }
    LaunchedEffect(height, bottomInsetPx, includeNavigationBarPadding) {
        if (height > 0) reportHandle(
            AudioPlayerHandle(
                height,
                bottomInsetPx,
                includeNavigationBarPadding
            )
        )
    }
    Box(
        Modifier
            .widthIn(max = MiniPlayerMaxOuterWidth)
            .onSizeChanged { height = it.height }
            .graphicsLayer { alpha = 0f }
            .clearAndSetSemantics {},
    ) {
        AudioMiniPlayer(
            state = state,
            coverPath = book?.book?.coverPath,
            onOpenPlayer = {},
            onPlayPause = {},
            onSkip = {},
            modifier = Modifier
                .then(if (includeNavigationBarPadding) Modifier.navigationBarsPadding() else Modifier)
                .padding(
                    horizontal = MiniPlayerHorizontalPadding,
                    vertical = MiniPlayerOuterVerticalPadding
                ),
        )
    }
}

@Composable
internal fun AudioMiniPlayer(
    state: AudioPlaybackState,
    coverPath: String?,
    onOpenPlayer: () -> Unit,
    onPlayPause: () -> Unit,
    onSkip: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLabel = stringResource(R.string.reader_open_audio_player)
    Surface(
        onClick = onOpenPlayer,
        modifier = modifier
            .widthIn(max = MiniPlayerMaxWidth)
            .fillMaxWidth()
            .semantics { onClick(label = openLabel, action = null) },
        shape = RoundedCornerShape(MiniPlayerContainerRadius),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = MiniPlayerArtworkInset,
                        end = 4.dp,
                        top = MiniPlayerArtworkInset,
                        bottom = MiniPlayerArtworkInset + MiniPlayerProgressHeight,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(MiniPlayerArtworkSize),
                    shape = RoundedCornerShape(MiniPlayerArtworkRadius),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    if (coverPath != null) {
                        AsyncImage(
                            model = File(coverPath),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(MaterialSymbols.Outlined.Headphones, contentDescription = null)
                        }
                    }
                }
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = state.error ?: state.chapter.ifBlank { state.author }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.error != null) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = { onSkip(-10) }) {
                    Icon(MaterialSymbols.Outlined.Replay_10, stringResource(R.string.reader_rewind))
                }
                FilledIconButton(
                    onClick = onPlayPause,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = if (state.playing) MaterialSymbols.Outlined.Pause else MaterialSymbols.Outlined.Play_arrow,
                        contentDescription = stringResource(if (state.playing) R.string.reader_pause else R.string.reader_play),
                    )
                }
                IconButton(onClick = { onSkip(10) }) {
                    Icon(MaterialSymbols.Outlined.Forward_10, stringResource(R.string.reader_forward))
                }
            }
            if (state.loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MiniPlayerProgressHeight)
                        .align(Alignment.BottomCenter),
                )
            } else {
                val duration = state.durationSeconds?.takeIf { it > 0 }
                LinearProgressIndicator(
                    progress = {
                        duration?.let {
                            (state.elapsedSeconds.toFloat() / it).coerceIn(
                                0f,
                                1f
                            )
                        } ?: 0f
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MiniPlayerProgressHeight)
                        .align(Alignment.BottomCenter),
                )
            }
        }
    }
}
