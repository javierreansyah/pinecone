package com.javierreansyah.pinecone.ui.features.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
fun AudioReaderContent(
    state: AudioPlaybackState,
    coverPath: String?,
    background: Color,
    foreground: Color,
    onPlayPause: () -> Unit,
    onSkip: (Int) -> Unit,
    onTrack: (Int) -> Unit,
    onSeek: (Double) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides foreground) {
        BoxWithConstraints(
            Modifier.fillMaxSize().background(background)
                .statusBarsPadding().navigationBarsPadding().padding(top = 72.dp, bottom = 16.dp),
        ) {
            val landscape = maxWidth > maxHeight
            val artworkSize = if (landscape) minOf(maxWidth * 0.4f, maxHeight - 32.dp)
                else minOf(maxWidth - 48.dp, maxHeight * 0.43f, 420.dp)
            val panel: @Composable () -> Unit = {
                AudioPlaybackPanel(state, background, onPlayPause, onSkip, onTrack, onSeek, onOpenBookmarks, onOpenSettings)
            }
            if (landscape) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AudioArtwork(coverPath, Modifier.width(artworkSize.coerceAtLeast(80.dp)))
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { panel() }
                }
            } else {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .heightIn(min = maxHeight).padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
                ) {
                    AudioArtwork(coverPath, Modifier.width(artworkSize.coerceAtLeast(120.dp)))
                    panel()
                }
            }
        }
    }
}

@Composable
private fun AudioArtwork(coverPath: String?, modifier: Modifier) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        if (coverPath != null) {
            AsyncImage(File(coverPath), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Headphones, null, Modifier.size(88.dp)) }
        }
    }
}

@Composable
private fun AudioPlaybackPanel(
    state: AudioPlaybackState,
    background: Color,
    onPlayPause: () -> Unit,
    onSkip: (Int) -> Unit,
    onTrack: (Int) -> Unit,
    onSeek: (Double) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(state.title, style = MaterialTheme.typography.headlineMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (state.author.isNotBlank()) Text(state.author, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.chapter.isNotBlank() && state.chapter != state.title) Text(state.chapter, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loading) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally))
        AudioSeekBar(state, background, onSeek, onSkip)
        AudioTransportButtons(state.playing, onPlayPause, onTrack)
        AudioActionsStrip(onOpenBookmarks, onOpenSettings)
    }
}

@Composable
private fun AudioTransportButtons(playing: Boolean, onPlayPause: () -> Unit, onTrack: (Int) -> Unit) {
    val labels = listOf(
        stringResource(R.string.reader_previous_track),
        stringResource(if (playing) R.string.reader_pause else R.string.reader_play),
        stringResource(R.string.reader_next_track),
    )
    val sources = remember { List(3) { MutableInteractionSource() } }
    ButtonGroup(
        modifier = Modifier.fillMaxWidth(),
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(menuState = it) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val action = { if (index == 1) onPlayPause() else onTrack(if (index == 0) -1 else 1) }
            customItem(
                buttonGroupContent = {
                    val buttonModifier = Modifier.weight(if (index == 1) 1.2f else 1f)
                        .animateWidth(sources[index]).height(72.dp)
                    val shapes = IconButtonDefaults.shapes(shape = MaterialTheme.shapes.extraLarge, pressedShape = MaterialTheme.shapes.large)
                    if (index == 1) {
                        FilledIconButton(
                            onClick = action, modifier = buttonModifier, shapes = shapes, interactionSource = sources[index],
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                        ) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, label, Modifier.size(36.dp)) }
                    } else {
                        FilledTonalIconButton(
                            onClick = action, modifier = buttonModifier, shapes = shapes, interactionSource = sources[index],
                        ) { Icon(if (index == 0) Icons.Rounded.SkipPrevious else Icons.Rounded.SkipNext, label, Modifier.size(32.dp)) }
                    }
                },
                menuContent = { menu ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { menu.dismiss(); action() })
                },
            )
        }
    }
}

@Composable
private fun AudioSeekBar(state: AudioPlaybackState, background: Color, onSeek: (Double) -> Unit, onSkip: (Int) -> Unit) {
    val duration = state.durationSeconds?.takeIf { it > 0 }
    val current = duration?.let { (state.elapsedSeconds.toDouble() / it).toFloat().coerceIn(0f, 1f) } ?: 0f
    var preview by remember(state.bookId, state.index) { mutableStateOf<Float?>(null) }
    var pending by remember(state.bookId, state.index) { mutableStateOf<Float?>(null) }
    val seekCallback by rememberUpdatedState(onSeek)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = preview ?: pending ?: current
    val seek: (Float) -> Unit = { fraction ->
        if (duration != null) {
            pending = fraction.coerceIn(0f, 1f)
            seekCallback(pending!!.toDouble() * duration)
        }
    }
    val commitSeek by rememberUpdatedState(seek)
    LaunchedEffect(pending) {
        if (pending != null) {
            delay(1500)
            pending = null
        }
    }
    LaunchedEffect(current) {
        val target = pending
        if (target != null && duration != null && abs(state.elapsedSeconds - target * duration) <= 2) pending = null
    }
    val seekLabel = stringResource(R.string.reader_audio_seek)
    val density = LocalDensity.current
    val stroke = remember(density) { Stroke(width = with(density) { 3.dp.toPx() }, cap = StrokeCap.Round) }
    Column {
        Box(
            Modifier.fillMaxWidth().height(48.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = seekLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    if (duration != null) setProgress { commitSeek(it); true }
                }
                .pointerInput(duration, state.bookId, state.index, rtl) {
                    if (duration != null) detectTapGestures { commitSeek(audioSeekFraction(it.x, size.width.toFloat(), rtl)) }
                }
                .pointerInput(duration, state.bookId, state.index, rtl) {
                    if (duration != null) detectDragGestures(
                        onDragStart = { preview = audioSeekFraction(it.x, size.width.toFloat(), rtl) },
                        onDrag = { change, _ -> change.consume(); preview = audioSeekFraction(change.position.x, size.width.toFloat(), rtl) },
                        onDragEnd = { preview?.let { commitSeek(it) }; preview = null },
                        onDragCancel = { preview = null },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            LinearWavyProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().height(16.dp).clearAndSetSemantics {},
                color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                stroke = stroke, trackStroke = stroke, stopSize = 0.dp, gapSize = 6.dp,
                amplitude = { 1f }, wavelength = 28.dp, waveSpeed = if (state.playing) 28.dp else 0.dp,
            )
            val handleColor = MaterialTheme.colorScheme.primary
            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                val x = (size.width * if (rtl) 1f - progress else progress).coerceIn(2.dp.toPx(), (size.width - 2.dp.toPx()).coerceAtLeast(2.dp.toPx()))
                drawLine(background, Offset(x, 0f), Offset(x, size.height), strokeWidth = 10.dp.toPx())
                drawLine(handleColor, Offset(x, 2.dp.toPx()), Offset(x, size.height - 2.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            val shownSeconds = if (duration != null && (preview != null || pending != null)) (progress * duration).roundToLong() else state.elapsedSeconds
            Text(formatAudioTime(shownSeconds), style = MaterialTheme.typography.labelLarge)
            Row {
                IconButton(onClick = { onSkip(-10) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Replay10, stringResource(R.string.reader_rewind)) }
                IconButton(onClick = { onSkip(10) }, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Forward10, stringResource(R.string.reader_forward)) }
            }
            Text(duration?.let(::formatAudioTime) ?: "--:--", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun AudioActionsStrip(onOpenBookmarks: () -> Unit, onOpenSettings: () -> Unit) {
    val openBookmarks by rememberUpdatedState(onOpenBookmarks)
    val openSettings by rememberUpdatedState(onOpenSettings)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalFloatingToolbar(
            expanded = true, shape = MaterialTheme.shapes.extraLarge, contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxWidth().pointerInput(rtl) {
                var distance = 0f
                var bookmarks = true
                val threshold = 32.dp.toPx()
                detectVerticalDragGestures(
                    onDragStart = { start -> distance = 0f; bookmarks = (start.x < size.width / 2f) != rtl },
                    onVerticalDrag = { change, amount -> change.consume(); distance += amount },
                    onDragEnd = { if (distance < -threshold) { if (bookmarks) openBookmarks() else openSettings() } },
                    onDragCancel = { distance = 0f },
                )
            },
        ) {
            FilledTonalButton(onClick = onOpenBookmarks, modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Rounded.Bookmarks, null, Modifier.size(20.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.reader_bookmarks_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
            FilledTonalButton(onClick = onOpenSettings, modifier = Modifier.weight(1f).height(52.dp), shape = MaterialTheme.shapes.large, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Rounded.Tune, null, Modifier.size(20.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.settings_title), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

internal fun audioSeekFraction(x: Float, width: Float, rtl: Boolean): Float {
    if (!x.isFinite() || !width.isFinite() || width <= 0f) return 0f
    val fraction = (x / width).coerceIn(0f, 1f)
    return if (rtl) 1f - fraction else fraction
}

internal fun formatAudioTime(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    return if (safe >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", safe / 3600, safe / 60 % 60, safe % 60)
    else String.format(Locale.ROOT, "%d:%02d", safe / 60, safe % 60)
}
