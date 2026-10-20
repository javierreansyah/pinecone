package com.javierreansyah.pinecone.ui.features.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import java.io.File
import java.util.Locale

@Composable
fun AudioReaderContent(
    state: AudioPlaybackState,
    coverPath: String?,
    background: Color,
    onToggleControls: () -> Unit,
    onPlayPause: () -> Unit,
    onSkip: (Int) -> Unit,
    onTrack: (Int) -> Unit,
    onSeek: (Double) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(background)
            .clickable(onClick = onToggleControls)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        if (coverPath != null) {
            AsyncImage(
                File(coverPath),
                contentDescription = null,
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(32.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                shape = RoundedCornerShape(48.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(220.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Headphones,
                        null,
                        Modifier.size(96.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            state.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Text(state.chapter, style = MaterialTheme.typography.bodyLarge)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loading) CircularProgressIndicator(Modifier.padding(12.dp))
        val duration = state.durationSeconds
        if (duration != null && duration > 0) {
            var seeking by remember { mutableStateOf<Float?>(null) }
            val slider =
                remember(duration) { androidx.compose.material3.SliderState(trackRange = 0f..duration.toFloat()) }
            slider.value =
                seeking ?: state.elapsedSeconds.toFloat().coerceIn(0f, duration.toFloat())
            Slider(
                state = slider, onValueChange = { seeking = it },
                onValueChangeFinished = { seeking?.let { onSeek(it.toDouble()) }; seeking = null })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.elapsedSeconds)); Text(formatTime(duration))
            }
        } else Text(formatTime(state.elapsedSeconds), Modifier.padding(16.dp))
        Row(
            Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalIconButton(onClick = { onTrack(-1) }) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    stringResource(R.string.reader_previous_track)
                )
            }
            FilledTonalIconButton(onClick = { onSkip(-10) }) {
                Icon(
                    Icons.Rounded.Replay10,
                    stringResource(R.string.reader_rewind)
                )
            }
            FilledIconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                Icon(
                    if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    stringResource(if (state.playing) R.string.reader_pause else R.string.reader_play),
                    Modifier.size(36.dp)
                )
            }
            FilledTonalIconButton(onClick = { onSkip(30) }) {
                Icon(
                    Icons.Rounded.Forward30,
                    stringResource(R.string.reader_forward)
                )
            }
            FilledTonalIconButton(onClick = { onTrack(1) }) {
                Icon(
                    Icons.Rounded.SkipNext,
                    stringResource(R.string.reader_next_track)
                )
            }
        }
    }
}

private fun formatTime(seconds: Long): String = if (seconds >= 3600) String.format(
    Locale.ROOT,
    "%d:%02d:%02d",
    seconds / 3600,
    seconds / 60 % 60,
    seconds % 60
)
else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
