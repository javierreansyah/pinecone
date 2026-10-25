@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.ui.root

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.features.reader.AudioPlayerScreen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class AudioPlayerHandle(
    val height: Int,
    val bottomInset: Int,
    val navigationBarsPadding: Boolean
)

internal val LocalAudioPlayerHandle = staticCompositionLocalOf<(AudioPlayerHandle) -> Unit> {
    error("The mini-player must be hosted by AudioPlayerSheetHost")
}

internal enum class AudioSheetValue { Collapsed, Expanded }
internal enum class AudioDismissValue { Left, Center, Right }

/** One draggable surface morphs from the measured mini-player into the full player. */
@Composable
fun AudioPlayerSheetHost(backStack: MutableList<NavKey>, content: @Composable () -> Unit) {
    val controls: AudioControlsViewModel = hiltViewModel()
    val playback by controls.state.collectAsStateWithLifecycle()
    val request = backStack.lastOrNull() as? Screen.AudioPlayer
    val underlyingScreen = backStack.lastOrNull { it !is Screen.AudioPlayer }
    val playerStack = rememberNavBackStack()
    val sheet = remember { AnchoredDraggableState(AudioSheetValue.Collapsed) }
    val swipeDismiss = remember { AnchoredDraggableState(AudioDismissValue.Center) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var rootHandle by remember { mutableStateOf<AudioPlayerHandle?>(null) }
    var libraryHandle by remember { mutableStateOf<AudioPlayerHandle?>(null) }
    var dismissedBookId by remember { mutableStateOf<String?>(null) }
    var previousBookId by remember { mutableStateOf(playback.bookId) }
    var predictiveBackInProgress by remember { mutableStateOf(false) }
    var openingNewPlayer by remember { mutableStateOf(false) }
    val reportHandle = remember {
        { value: AudioPlayerHandle ->
            // Outgoing and incoming pages may coexist; never let their measurements compete.
            if (value.navigationBarsPadding) rootHandle = value else libraryHandle = value
        }
    }
    val usesRootHandle = underlyingScreen != Screen.Library
    val navigationBarHeight = WindowInsets.navigationBars.getBottom(density)
    val handle = (if (usesRootHandle) rootHandle else libraryHandle) ?: AudioPlayerHandle(
        height = with(density) { MiniPlayerHeight.roundToPx() } + if (usesRootHandle) navigationBarHeight else 0,
        bottomInset = if (usesRootHandle) 0 else navigationBarHeight,
        navigationBarsPadding = usesRootHandle,
    )
    // Use untransformed sizes/insets in root coordinates, not the animated NavDisplay bounds.
    val bounds = audioMiniPlayerBounds(rootSize, handle)
    val collapsedOffset = bounds.top.coerceIn(1f, rootSize.height.toFloat().coerceAtLeast(1f))
    val offset =
        sheet.offset.takeIf { it.isFinite() }?.coerceIn(0f, collapsedOffset) ?: collapsedOffset
    val fraction = audioSheetExpansion(offset, collapsedOffset)
    val suppressOpeningMiniPlayer = request != null && (
            openingNewPlayer || !playback.ready || playback.bookId != request.bookId
            )
    val opacity = audioSheetOpacity(fraction, suppressMiniPlayer = suppressOpeningMiniPlayer)
    val horizontalOffset = swipeDismiss.offset.takeIf { it.isFinite() } ?: 0f
    val dismissFraction = audioSwipeDismissProgress(horizontalOffset, rootSize.width.toFloat())
    val collapse: () -> Unit = {
        scope.launch {
            sheet.animateTo(AudioSheetValue.Collapsed)
            backStack.collapseAudioPlayer()
        }
    }
    val latestCollapse by rememberUpdatedState(collapse)
    val expand: () -> Unit = {
        if (request == null) playback.bookId?.let { backStack.openAudioPlayer(it) }
    }
    val latestExpand by rememberUpdatedState(expand)
    val dismissPlayer: () -> Unit = {
        if (request == null && fraction <= 0.01f) {
            dismissedBookId = playback.bookId
            playerStack.clear()
            backStack.collapseAudioPlayer()
            controls.dismiss()
        }
    }
    val latestDismissPlayer by rememberUpdatedState(dismissPlayer)

    // Keep player ViewModels alive while collapsed, independently of the app's navigation entries.
    val decorators = listOf<NavEntryDecorator<NavKey>>(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )
    val playerEntries = rememberDecoratedNavEntries(
        backStack = playerStack,
        entryDecorators = decorators,
        entryProvider = entryProvider {
            entry<Screen.AudioPlayer> { args ->
                AudioPlayerScreen(
                    bookId = args.bookId,
                    initialLocatorJson = args.initialLocatorJson,
                    artworkAlpha = opacity.artwork,
                    topBarAlpha = opacity.topBar,
                    onBack = collapse,
                    onBookInfo = { bookId ->
                        scope.launch {
                            sheet.animateTo(AudioSheetValue.Collapsed)
                            backStack.collapseAudioPlayer()
                            backStack.add(Screen.BookInfo(bookId))
                        }
                    },
                )
            }
        },
    )
    LaunchedEffect(request) {
        if (request != null) {
            openingNewPlayer = !playback.ready || playback.bookId != request.bookId
            dismissedBookId = null
            swipeDismiss.snapTo(AudioDismissValue.Center)
            if (playerStack.lastOrNull() != request) {
                playerStack.clear()
                playerStack.add(request)
            }
        } else {
            openingNewPlayer = false
        }
    }
    LaunchedEffect(playback.bookId, playback.ready) {
        if (playerStack.isEmpty() && playback.bookId != null && playback.ready && playback.bookId != dismissedBookId) {
            playerStack.add(Screen.AudioPlayer(requireNotNull(playback.bookId)))
        }
    }
    LaunchedEffect(rootSize.width) {
        val distance = rootSize.width.toFloat().coerceAtLeast(1f)
        swipeDismiss.updateAnchors(DraggableAnchors {
            AudioDismissValue.Left at -distance
            AudioDismissValue.Center at 0f
            AudioDismissValue.Right at distance
        })
    }
    LaunchedEffect(playback.bookId) {
        if (playback.bookId == null && previousBookId != null) {
            playerStack.clear()
            backStack.collapseAudioPlayer()
        }
        previousBookId = playback.bookId
        swipeDismiss.snapTo(AudioDismissValue.Center)
    }
    LaunchedEffect(swipeDismiss) {
        snapshotFlow { swipeDismiss.settledValue }.drop(1).collect { value ->
            if (value != AudioDismissValue.Center) latestDismissPlayer()
        }
    }
    LaunchedEffect(collapsedOffset) {
        sheet.updateAnchors(DraggableAnchors {
            AudioSheetValue.Expanded at 0f
            AudioSheetValue.Collapsed at collapsedOffset
        })
    }
    LaunchedEffect(request, collapsedOffset) {
        sheet.animateTo(if (request != null) AudioSheetValue.Expanded else AudioSheetValue.Collapsed)
    }
    LaunchedEffect(sheet) {
        snapshotFlow { sheet.settledValue }.drop(1).collect { value ->
            if (value == AudioSheetValue.Expanded) openingNewPlayer = false
            // Predictive Back commits navigation only after the gesture is released.
            if (!predictiveBackInProgress) {
                if (value == AudioSheetValue.Collapsed) backStack.collapseAudioPlayer()
                else latestExpand()
            }
        }
    }

    val velocityThreshold = with(density) { 125.dp.toPx() }
    val scrollConnection = remember(sheet, collapsedOffset, velocityThreshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y < 0 && sheet.requireOffset() > 0) {
                    return Offset(0f, sheet.dispatchRawDelta(available.y))
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return if (source == NestedScrollSource.UserInput) {
                    Offset(0f, sheet.dispatchRawDelta(available.y))
                } else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (sheet.requireOffset() > 0f) {
                    sheet.animateTo(
                        audioSheetTarget(
                            sheet.requireOffset(),
                            collapsedOffset,
                            available.y,
                            velocityThreshold
                        )
                    )
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (sheet.requireOffset() > 0f && sheet.requireOffset() < collapsedOffset) {
                    sheet.animateTo(
                        audioSheetTarget(
                            sheet.requireOffset(),
                            collapsedOffset,
                            available.y,
                            velocityThreshold
                        )
                    )
                    return available
                }
                return Velocity.Zero
            }
        }
    }
    val visible =
        request != null || playback.bookId != null && playback.ready && playback.bookId != dismissedBookId
    val collapseLabel = stringResource(R.string.reader_collapse_audio_player)
    val expandLabel = stringResource(R.string.reader_open_audio_player)
    val dismissLabel = stringResource(R.string.reader_dismiss_audio_player)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { rootSize = it }
            .clipToBounds()) {
        CompositionLocalProvider(LocalAudioPlayerHandle provides reportHandle) {
            Box(if (fraction > 0.01f) Modifier.clearAndSetSemantics {} else Modifier) {
                AudioMiniPlayerHost(enabled = underlyingScreen != Screen.Library, content = content)
            }
        }
        // Above app navigation, but below the player's own settings/bookmark dialog handlers.
        PredictiveBackHandler(enabled = request != null || fraction > 0.01f || predictiveBackInProgress) { events ->
            val startingOffset = sheet.requireOffset()
            val restoreValue = if (request != null) AudioSheetValue.Expanded else sheet.settledValue
            predictiveBackInProgress = true
            try {
                sheet.anchoredDrag(dragPriority = MutatePriority.UserInput) { anchors ->
                    events.collect { event ->
                        dragTo(
                            audioPredictiveBackOffset(
                                startingOffset,
                                anchors.positionOf(AudioSheetValue.Collapsed),
                                event.progress,
                            )
                        )
                    }
                }
                // Button/keyboard Back emits no progress, so it also uses the slide-down finish.
                sheet.animateTo(AudioSheetValue.Collapsed)
                backStack.collapseAudioPlayer()
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { sheet.animateTo(restoreValue) }
                throw cancelled
            } finally {
                predictiveBackInProgress = false
            }
        }
        if (visible && rootSize.height > 0 && rootSize.width > 0) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = opacity.background))
                        .clickable(onClickLabel = collapseLabel, onClick = collapse),
                )
            }
            // Keep the sheet full-width so expansion only grows upward.
            // The mini-player card has its own centered width limit.
            val width = rootSize.width
            val height = bounds.height + (rootSize.height - bounds.height) * fraction
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            horizontalOffset.roundToInt(),
                            offset.roundToInt()
                        )
                    }
                    .size(with(density) { width.toDp() }, with(density) { height.toDp() })
                    .graphicsLayer { alpha = 1f - dismissFraction }
                    .clip(
                        RoundedCornerShape(
                            topStart = (24f * fraction).dp,
                            topEnd = (24f * fraction).dp
                        )
                    )
                    .anchoredDraggable(
                        sheet,
                        Orientation.Vertical,
                        enabled = !predictiveBackInProgress
                    )
                    .anchoredDraggable(
                        swipeDismiss,
                        Orientation.Horizontal,
                        enabled = fraction <= 0.01f && request == null,
                    )
                    .nestedScroll(scrollConnection)
                    .semantics {
                        if (fraction > 0.5f) collapse(collapseLabel) { latestCollapse(); true }
                        else expand(expandLabel) { latestExpand(); true }
                        if (fraction <= 0.01f && request == null) {
                            dismiss(dismissLabel) {
                                scope.launch { swipeDismiss.animateTo(AudioDismissValue.Right) }
                                true
                            }
                        }
                    },
            ) {
                if (playerStack.isNotEmpty()) {
                    val fullAlpha = opacity.playerContent
                    Box(
                        Modifier
                            .wrapContentSize(Alignment.TopStart, unbounded = true)
                            .requiredSize(
                                with(density) { rootSize.width.toDp() },
                                with(density) { rootSize.height.toDp() })
                            .graphicsLayer { alpha = fullAlpha }
                            .then(if (fullAlpha < 1f) Modifier.clearAndSetSemantics {} else Modifier),
                    ) {
                        playerEntries.lastOrNull()?.Content()
                    }
                }
                if (opacity.miniPlayer > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = opacity.miniPlayer },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        AudioMiniPlayer(
                            state = playback,
                            coverPath = playback.coverPath,
                            onOpenPlayer = expand,
                            onPlayPause = controls::togglePlayback,
                            onSkip = controls::skip,
                            modifier = Modifier
                                .then(if (handle.navigationBarsPadding) Modifier.navigationBarsPadding() else Modifier)
                                .padding(
                                    horizontal = MiniPlayerHorizontalPadding,
                                    vertical = MiniPlayerOuterVerticalPadding
                                ),
                        )
                    }
                }
            }
        }
    }
}

internal fun audioSheetExpansion(offset: Float, collapsedOffset: Float): Float {
    if (!offset.isFinite() || !collapsedOffset.isFinite() || collapsedOffset <= 0f) return 0f
    return (1f - offset / collapsedOffset).coerceIn(0f, 1f)
}

internal fun audioMiniPlayerBounds(rootSize: IntSize, handle: AudioPlayerHandle): Rect {
    val height = handle.height.coerceIn(0, rootSize.height.coerceAtLeast(0))
    val bottom = (rootSize.height - handle.bottomInset.coerceAtLeast(0)).coerceAtLeast(height)
    return Rect(
        0f,
        (bottom - height).toFloat(),
        rootSize.width.coerceAtLeast(0).toFloat(),
        bottom.toFloat()
    )
}

internal fun audioPredictiveBackOffset(start: Float, collapsed: Float, progress: Float): Float {
    val fraction = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    return start + (collapsed - start) * fraction
}

internal data class AudioSheetOpacity(
    val miniPlayer: Float,
    val playerContent: Float,
    val background: Float,
    val artwork: Float,
    val topBar: Float,
)

internal fun audioSheetOpacity(
    expansion: Float,
    suppressMiniPlayer: Boolean = false
): AudioSheetOpacity {
    val progress = expansion.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val remaining = 1f - progress
    return AudioSheetOpacity(
        miniPlayer = if (suppressMiniPlayer) 0f else (1f - progress / 0.10f).coerceIn(0f, 1f),
        playerContent = (progress / 0.25f).coerceIn(0f, 1f),
        // Cubic ease-out: darken quickly, then approach the opaque endpoint softly.
        background = 1f - remaining * remaining * remaining,
        artwork = ((progress - 0.10f) / 0.40f).coerceIn(0f, 1f),
        topBar = ((progress - 0.50f) / 0.35f).coerceIn(0f, 1f),
    )
}

internal fun audioSwipeDismissProgress(offset: Float, width: Float): Float {
    if (!offset.isFinite() || !width.isFinite() || width <= 0f) return 0f
    return (abs(offset) / width).coerceIn(0f, 1f)
}

internal fun audioSheetTarget(
    offset: Float,
    collapsedOffset: Float,
    velocity: Float,
    threshold: Float
): AudioSheetValue =
    when {
        velocity < -threshold -> AudioSheetValue.Expanded
        velocity > threshold -> AudioSheetValue.Collapsed
        audioSheetExpansion(offset, collapsedOffset) >= 0.5f -> AudioSheetValue.Expanded
        else -> AudioSheetValue.Collapsed
    }
