package com.javierreansyah.pinecone.ui.features.reader.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.composables.icons.materialsymbols.outlined.Edit_note
import com.composables.icons.materialsymbols.outlined.Forward_10
import com.composables.icons.materialsymbols.outlined.Headphones
import com.composables.icons.materialsymbols.outlined.List
import com.composables.icons.materialsymbols.outlined.Replay_10
import com.composables.icons.materialsymbols.outlined.Skip_next
import com.composables.icons.materialsymbols.outlined.Skip_previous
import com.composables.icons.materialsymbols.outlined.Tune
import com.composables.icons.materialsymbols.outlinedfilled.Pause
import com.composables.icons.materialsymbols.outlinedfilled.Play_arrow
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import com.javierreansyah.pinecone.ui.components.AnimatedSelectionIcon
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import com.composables.icons.materialsymbols.outlinedfilled.Bookmark_check as FilledBookmarkCheck

@Composable
fun AudioReaderContent(
    state: AudioPlaybackState,
    coverPath: String?,
    background: Color,
    foreground: Color,
    isBookmarked: Boolean = false,
    artworkAlpha: Float = 1f,
    onToggleBookmark: () -> Unit = {},
    onAddNote: () -> Unit = {},
    canAddNote: Boolean = false,
    onPlayPause: () -> Unit,
    onSkip: (Int) -> Unit,
    onTrack: (Int) -> Unit,
    onSeek: (Double) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    CompositionLocalProvider(LocalContentColor provides foreground) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(top = 64.dp, bottom = 16.dp),
        ) {
            val landscape = maxWidth > maxHeight
            val panel: @Composable () -> Unit = {
                AudioPlaybackPanel(
                    state = state,
                    isBookmarked = isBookmarked,
                    onToggleBookmark = onToggleBookmark,
                    onAddNote = onAddNote,
                    canAddNote = canAddNote,
                    onPlayPause = onPlayPause,
                    onSkip = onSkip,
                    onTrack = onTrack,
                    onSeek = onSeek,
                    onOpenBookmarks = onOpenBookmarks,
                    onOpenSettings = onOpenSettings,
                    onRetry = onRetry,
                )
            }
            if (landscape) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AudioArtwork(
                        coverPath = coverPath,
                        modifier = Modifier
                            .weight(0.42f)
                            .aspectRatio(1f)
                            .graphicsLayer { alpha = artworkAlpha },
                    )
                    Column(
                        modifier = Modifier
                            .weight(0.58f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        panel()
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    AudioArtwork(
                        coverPath = coverPath,
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = artworkAlpha },
                    )
                    panel()
                }
            }
        }
    }
}

@Composable
private fun AudioArtwork(
    coverPath: String?,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 2.dp,
    ) {
        if (coverPath != null) {
            AsyncImage(
                model = File(coverPath),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun AudioPlaybackPanel(
    state: AudioPlaybackState,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onAddNote: () -> Unit,
    canAddNote: Boolean,
    onPlayPause: () -> Unit,
    onSkip: (Int) -> Unit,
    onTrack: (Int) -> Unit,
    onSeek: (Double) -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = state.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.author.isNotBlank()) {
                Text(
                    text = state.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        state.error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            onRetry?.let { retry ->
                TextButton(onClick = retry) { Text(stringResource(R.string.reader_audio_retry)) }
            }
        }

        AudioSeekBar(
            state = state,
            onSeek = onSeek,
        )

        AudioTransportButtons(
            playing = state.playing,
            onPlayPause = onPlayPause,
            onTrack = onTrack,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        AudioActionsStrip(
            onSkip = onSkip,
            isBookmarked = isBookmarked,
            onToggleBookmark = onToggleBookmark,
            onAddNote = onAddNote,
            canAddNote = canAddNote,
            onOpenBookmarks = onOpenBookmarks,
            onOpenSettings = onOpenSettings,
        )
    }
}

@Composable
private fun AudioTransportButtons(
    playing: Boolean,
    onPlayPause: () -> Unit,
    onTrack: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = listOf(
        stringResource(R.string.reader_previous_track),
        stringResource(if (playing) R.string.reader_pause else R.string.reader_play),
        stringResource(R.string.reader_next_track),
    )
    val sources = remember { List(3) { MutableInteractionSource() } }

    ButtonGroup(
        overflowIndicator = { ButtonGroupDefaults.OverflowIndicator(menuState = it) },
        modifier = modifier
            .widthIn(max = 400.dp)
            .fillMaxWidth(),
        expandedRatio = 0.32f,
        horizontalArrangement = ButtonGroupDefaults.HorizontalArrangement,
    ) {
        labels.forEachIndexed { index, label ->
            val action = {
                if (index == 1) onPlayPause() else onTrack(if (index == 0) -1 else 1)
            }
            customItem(
                buttonGroupContent = {
                    val interactionSource = sources[index]
                    val isPressed by interactionSource.collectIsPressedAsState()

                    // Preset theme fast expressive motion specs
                    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
                    val cornerSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()
                    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    val iconSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()

                    val scale by animateFloatAsState(
                        targetValue = if (isPressed) 0.93f else 1f,
                        animationSpec = spatialSpec,
                        label = "transportButtonScale_$index",
                    )

                    val isCenter = index == 1
                    val normalCorner = if (isCenter) 28.dp else 38.dp
                    val pressedCorner = if (isCenter) 18.dp else 20.dp

                    val cornerRadius by animateDpAsState(
                        targetValue = if (isPressed) pressedCorner else normalCorner,
                        animationSpec = cornerSpatialSpec,
                        label = "transportButtonCorner_$index",
                    )
                    val shape = RoundedCornerShape(cornerRadius)

                    val containerColor = if (isCenter) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val contentColor = if (isCenter) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }

                    Surface(
                        onClick = action,
                        shape = shape,
                        color = containerColor,
                        contentColor = contentColor,
                        interactionSource = interactionSource,
                        modifier = Modifier
                            .weight(if (isCenter) 1.25f else 1f)
                            .animateWidth(
                                interactionSource = interactionSource,
                                compressionLimit = 36.dp
                            )
                            .height(76.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isCenter) {
                                AnimatedContent(
                                    targetState = playing,
                                    transitionSpec = {
                                        fadeIn(animationSpec = effectsSpec) +
                                                scaleIn(
                                                    initialScale = 0.75f,
                                                    animationSpec = iconSpatialSpec
                                                ) togetherWith fadeOut(animationSpec = effectsSpec) + scaleOut(
                                            targetScale = 0.75f,
                                            animationSpec = iconSpatialSpec
                                        )
                                    },
                                    label = "playPauseTransition",
                                ) { isPlaying ->
                                    Icon(
                                        imageVector = if (isPlaying) MaterialSymbols.OutlinedFilled.Pause else MaterialSymbols.OutlinedFilled.Play_arrow,
                                        contentDescription = label,
                                        modifier = Modifier.size(38.dp),
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (index == 0) MaterialSymbols.Outlined.Skip_previous else MaterialSymbols.Outlined.Skip_next,
                                    contentDescription = label,
                                    modifier = Modifier.size(32.dp),
                                )
                            }
                        }
                    }
                },
                menuContent = { menu ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            menu.dismiss()
                            action()
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun AudioSeekBar(
    state: AudioPlaybackState,
    onSeek: (Double) -> Unit,
) {
    val duration = state.durationSeconds?.takeIf { it > 0 }
    val current =
        duration?.let { (state.elapsedSeconds.toDouble() / it).toFloat().coerceIn(0f, 1f) } ?: 0f
    var previewFraction by remember(state.bookId, state.index) { mutableStateOf<Float?>(null) }
    var pendingFraction by remember(state.bookId, state.index) { mutableStateOf<Float?>(null) }
    var isDragging by remember(state.bookId, state.index, duration) { mutableStateOf(false) }
    var isPressed by remember(state.bookId, state.index, duration) { mutableStateOf(false) }

    val seekCallback by rememberUpdatedState(onSeek)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = (previewFraction ?: pendingFraction ?: current).coerceIn(0f, 1f)

    val commitSeek by rememberUpdatedState { fraction: Float ->
        if (duration != null) {
            val clamped = fraction.coerceIn(0f, 1f)
            pendingFraction = clamped
            seekCallback(clamped.toDouble() * duration)
        }
    }

    LaunchedEffect(pendingFraction) {
        if (pendingFraction != null) {
            delay(1500.milliseconds)
            pendingFraction = null
        }
    }
    LaunchedEffect(current) {
        val target = pendingFraction
        if (target != null && duration != null && abs(state.elapsedSeconds - target * duration) <= 2) {
            pendingFraction = null
        }
    }

    val density = LocalDensity.current
    val strokeWidthPx = remember(density) { with(density) { 4.dp.roundToPx().toFloat() } }
    val indicatorStroke =
        remember(strokeWidthPx) { Stroke(width = strokeWidthPx, cap = StrokeCap.Round) }

    val handleActive = isPressed || isDragging
    val handleScale by animateFloatAsState(
        targetValue = if (handleActive) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "audioHandleVerticalScale",
    )

    val handleOpacity by animateFloatAsState(
        targetValue = if (handleActive) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "audioHandleOpacity",
    )

    // Keep Material's amplitude stable; its internal animation can miss interrupted targets.
    // Animate the available wave height with the theme spring instead. At stroke height it is flat.
    val waveHeight by animateDpAsState(
        targetValue = if (state.playing) 14.dp else 4.dp,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "audioWaveHeight",
    )

    val seekLabel = stringResource(R.string.reader_audio_seek)
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = seekLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    if (duration != null) {
                        setProgress { fraction ->
                            commitSeek(fraction)
                            true
                        }
                    }
                }
                .pointerInput(duration, state.bookId, state.index, rtl) {
                    if (duration != null) {
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    isPressed = false
                                }
                            },
                            onTap = { offset ->
                                commitSeek(audioSeekFraction(offset.x, size.width.toFloat(), rtl))
                            },
                        )
                    }
                }
                .pointerInput(duration, state.bookId, state.index, rtl) {
                    if (duration != null) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                previewFraction =
                                    audioSeekFraction(offset.x, size.width.toFloat(), rtl)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                previewFraction =
                                    audioSeekFraction(change.position.x, size.width.toFloat(), rtl)
                            },
                            onDragEnd = {
                                isDragging = false
                                previewFraction?.let { commitSeek(it) }
                                previewFraction = null
                            },
                            onDragCancel = {
                                isDragging = false
                                previewFraction = null
                            },
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(waveHeight.coerceAtLeast(4.dp))
                    .clearAndSetSemantics {},
                color = primaryColor,
                trackColor = trackColor,
                stroke = indicatorStroke,
                trackStroke = indicatorStroke,
                gapSize = 6.dp,
                stopSize = 3.dp,
                amplitude = { 0.4f },
                wavelength = 20.dp,
                // Keep the wave moving until it has flattened, then stop its phase animation.
                waveSpeed = if (state.playing || waveHeight > 4.dp) 20.dp else 0.dp,
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .graphicsLayer {
                        scaleY = handleScale.coerceAtLeast(0f)
                        alpha = handleOpacity.coerceIn(0f, 1f)
                    },
            ) {
                // Draw only the final handle; animate its layer vertically, never its width/shape.
                val handleWidth = 4.5.dp.toPx()
                val halfWidth = handleWidth / 2f
                val rawX = size.width * if (rtl) 1f - progress else progress
                val shiftedX = rawX + 2.dp.toPx()
                val clampedX =
                    shiftedX.coerceIn(halfWidth, (size.width - halfWidth).coerceAtLeast(halfWidth))
                val centerY = size.height / 2f
                val halfHeight = 22.dp.toPx() / 2f

                drawLine(
                    color = primaryColor,
                    start = Offset(clampedX, centerY - halfHeight),
                    end = Offset(clampedX, centerY + halfHeight),
                    strokeWidth = handleWidth,
                    cap = StrokeCap.Round,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shownSeconds =
                if (duration != null && (previewFraction != null || pendingFraction != null)) {
                    (progress * duration).roundToLong()
                } else {
                    state.elapsedSeconds
                }

            Text(
                text = formatAudioTime(shownSeconds),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            val trackLabel = when {
                state.chapter.isNotBlank() && state.chapter != state.title -> state.chapter
                state.index >= 0 -> stringResource(R.string.reader_track_label, state.index + 1)
                else -> null
            }
            if (trackLabel != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                ) {
                    Text(
                        text = trackLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Text(
                text = duration?.let(::formatAudioTime) ?: "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun AudioActionsStrip(
    onSkip: (Int) -> Unit,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onAddNote: () -> Unit,
    canAddNote: Boolean,
    onOpenBookmarks: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 56.dp * 6 + ButtonGroupDefaults.ConnectedSpaceBetween * 5)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExpressiveConnectedActionButton(
                onClick = { onSkip(-10) },
                modifier = Modifier.weight(1f),
                icon = MaterialSymbols.Outlined.Replay_10,
                contentDescription = stringResource(R.string.reader_rewind),
                shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                pressedShape = ButtonGroupDefaults.connectedLeadingButtonPressShape,
            )
            ExpressiveConnectedActionButton(
                onClick = onToggleBookmark,
                modifier = Modifier.weight(1f),
                icon = MaterialSymbols.Outlined.Bookmark,
                filledIcon = MaterialSymbols.OutlinedFilled.FilledBookmarkCheck,
                selected = isBookmarked,
                contentDescription = stringResource(
                    if (isBookmarked) R.string.reader_remove_bookmark else R.string.reader_add_bookmark
                ),
                shape = RoundedCornerShape(4.dp),
                pressedShape = ButtonGroupDefaults.connectedMiddleButtonPressShape,
                tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpressiveConnectedActionButton(
                onClick = onAddNote,
                modifier = Modifier.weight(1f),
                enabled = canAddNote,
                icon = MaterialSymbols.Outlined.Edit_note,
                contentDescription = stringResource(R.string.reader_add_note),
                shape = RoundedCornerShape(4.dp),
                pressedShape = ButtonGroupDefaults.connectedMiddleButtonPressShape,
            )
            ExpressiveConnectedActionButton(
                onClick = onOpenBookmarks,
                modifier = Modifier.weight(1f),
                icon = MaterialSymbols.Outlined.List,
                contentDescription = stringResource(R.string.reader_bookmarks_title),
                shape = RoundedCornerShape(4.dp),
                pressedShape = ButtonGroupDefaults.connectedMiddleButtonPressShape,
            )
            ExpressiveConnectedActionButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
                icon = MaterialSymbols.Outlined.Tune,
                contentDescription = stringResource(R.string.settings_title),
                shape = RoundedCornerShape(4.dp),
                pressedShape = ButtonGroupDefaults.connectedMiddleButtonPressShape,
            )
            ExpressiveConnectedActionButton(
                onClick = { onSkip(10) },
                modifier = Modifier.weight(1f),
                icon = MaterialSymbols.Outlined.Forward_10,
                contentDescription = stringResource(R.string.reader_forward),
                shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                pressedShape = ButtonGroupDefaults.connectedTrailingButtonPressShape,
            )
        }
    }
}

@Composable
private fun ExpressiveConnectedActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    shape: Shape,
    pressedShape: Shape,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    filledIcon: ImageVector? = null,
    selected: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val actionTint = if (enabled) tint else tint.copy(alpha = 0.38f)
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "actionButtonScale",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = if (isPressed) pressedShape else shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = actionTint,
        interactionSource = interactionSource,
        modifier = modifier
            .height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (filledIcon != null) {
                AnimatedSelectionIcon(
                    selected = selected,
                    outlinedIcon = icon,
                    filledIcon = filledIcon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                    tint = actionTint,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(24.dp),
                    tint = actionTint,
                )
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
    return if (safe >= 3600) {
        String.format(Locale.ROOT, "%d:%02d:%02d", safe / 3600, safe / 60 % 60, safe % 60)
    } else {
        String.format(Locale.ROOT, "%d:%02d", safe / 60, safe % 60)
    }
}
