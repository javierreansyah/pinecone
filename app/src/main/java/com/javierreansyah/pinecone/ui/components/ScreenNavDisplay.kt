package com.javierreansyah.pinecone.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Connects toolbar Back to the same operation used by system Back, without passing progress. */
class ScreenBackDispatcher {
    internal var handler: () -> Unit = {}

    fun onBack() = handler()
}

/**
 * Single-pane Navigation 3 display with an explicit gesture/commit boundary.
 *
 * A seekable content transition keeps the preceding entry composed during both phases. We pop
 * only after the surface has flown away, so gesture progress can never seek into the exit flight.
 * Navigation 3's entry decorators still own saved state, ViewModels, and cleanup after popping.
 */
@Composable
fun <T : Any> ScreenNavDisplay(
    backStack: List<T>,
    backDispatcher: ScreenBackDispatcher,
    onBack: () -> Unit,
    entryDecorators: List<NavEntryDecorator<T>>,
    modifier: Modifier = Modifier,
    entryProvider: (T) -> NavEntry<T>,
) {
    val entranceEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val entranceOffsetSpec = MaterialTheme.motionScheme.defaultEffectsSpec<IntOffset>()
    val exitEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exitOffsetSpec = MaterialTheme.motionScheme.defaultEffectsSpec<IntOffset>()
    val entries = rememberDecoratedNavEntries(backStack, entryDecorators, entryProvider)
    val currentEntry = entries.last()
    val latestEntries by rememberUpdatedState(entries)
    val currentOnBack by rememberUpdatedState(onBack)
    val transitionState = remember { SeekableTransitionState(currentEntry) }
    val transition = rememberTransition(transitionState, label = "Screen navigation")
    val motion = remember { ScreenDismissState() }
    val idleMotion = remember { ScreenDismissState() }
    val scope = rememberCoroutineScope()
    var operation by remember { mutableStateOf<Job?>(null) }
    var preview by remember { mutableStateOf(false) }
    var outgoingKey by remember { mutableStateOf<Any?>(null) }
    var pendingPopKey by remember { mutableStateOf<Any?>(null) }

    val requestBack: () -> Unit = {
        if (!motion.isDismissing) {
            operation?.cancel()
            operation = scope.launch {
                val outgoing = latestEntries.last()
                val previous = latestEntries.getOrNull(latestEntries.lastIndex - 1)
                if (previous == null) {
                    currentOnBack()
                } else {
                    outgoingKey = outgoing.contentKey
                    preview = true
                    if (transitionState.currentState.contentKey != outgoing.contentKey) {
                        transitionState.snapTo(outgoing)
                    }
                    transitionState.seekTo(0.5f, previous)
                    motion.dismiss(exitEffectsSpec) {
                        pendingPopKey = previous.contentKey
                        currentOnBack()
                    }
                }
            }
        }
    }
    SideEffect { backDispatcher.handler = requestBack }
    DisposableEffect(backDispatcher) {
        onDispose { backDispatcher.handler = {} }
    }

    LaunchedEffect(currentEntry.contentKey) {
        if (pendingPopKey == currentEntry.contentKey) {
            // The preceding screen is already visible; remove the offscreen outgoing entry.
            transitionState.snapTo(currentEntry)
        } else {
            operation?.cancel()
            preview = false
            outgoingKey = null
            motion.reset()
            transitionState.animateTo(currentEntry)
        }
        pendingPopKey = null
        outgoingKey = null
        preview = false
        motion.reset()
    }

    // Compose before screen content so selection, drawers, and other local Back handlers win.
    PredictiveBackHandler(enabled = entries.size > 1) { events ->
        if (motion.isDismissing || latestEntries.size < 2) {
            events.collect { }
        } else {
            operation?.cancel()
            val outgoing = latestEntries.last()
            val previous = latestEntries[latestEntries.lastIndex - 1]
            outgoingKey = outgoing.contentKey
            preview = true
            try {
                transitionState.snapTo(outgoing)
                events.collect { event ->
                    if (latestEntries.last().contentKey == outgoing.contentKey) {
                        motion.seek(event.progress)
                        // Only visibility is seeked. All physical motion belongs to the surface.
                        transitionState.seekTo(0.5f, previous)
                    }
                }
                if (latestEntries.last().contentKey == outgoing.contentKey) requestBack()
            } catch (_: CancellationException) {
                if (latestEntries.last().contentKey == outgoing.contentKey) {
                    operation = scope.launch {
                        motion.restore(exitEffectsSpec)
                        transitionState.snapTo(outgoing)
                        outgoingKey = null
                        preview = false
                    }
                }
            }
        }
    }

    Box(modifier) {
        if (preview) {
            // A real 0..1 timeline keeps both entries composed even though their alpha stays 1.
            // Equal alpha endpoints alone would give a theme spring zero duration.
            transition.animateFloat(
                transitionSpec = { exitEffectsSpec },
                label = "Back preview lifetime",
            ) { entry -> if (entry.contentKey == outgoingKey) 0f else 1f }
        }
        transition.AnimatedContent(
            modifier = Modifier.fillMaxSize(),
            contentKey = { it.contentKey },
            transitionSpec = {
                when {
                    preview -> {
                        // Keep both entries composed while the gesture is held.
                        (fadeIn(exitEffectsSpec, initialAlpha = 1f) togetherWith
                                fadeOut(exitEffectsSpec, targetAlpha = 1f))
                    }

                    latestEntries.any { it.contentKey == initialState.contentKey } -> {
                        (slideInHorizontally(
                            animationSpec = entranceOffsetSpec,
                        ) { it } + fadeIn(
                            animationSpec = entranceEffectsSpec,
                        ) togetherWith ExitTransition.None)
                    }

                    else -> {
                        // Covers programmatic pops that did not use the shared Back dispatcher.
                        (EnterTransition.None togetherWith scaleOut(
                            exitEffectsSpec, targetScale = ScreenMinimizedScale,
                        ) + slideOutHorizontally(
                            exitOffsetSpec,
                        ) { it })
                    }
                }.apply {
                    // AnimatedContent retains a page's layer when it becomes the outgoing page.
                    // Relative +/-1 values make consecutive pops tie; use the stack depth so
                    // every outgoing page stays above its parent, including after a nested pop.
                    targetContentZIndex = latestEntries.indexOfFirst {
                        it.contentKey == targetState.contentKey
                    }.coerceAtLeast(0).toFloat()
                }
            },
        ) { entry ->
            val settled = transitionState.currentState.contentKey ==
                    transitionState.targetState.contentKey && !preview
            val lifecycleOwner = rememberLifecycleOwner(
                maxLifecycle = when {
                    latestEntries.none { it.contentKey == entry.contentKey } -> Lifecycle.State.CREATED
                    settled && entry.contentKey == currentEntry.contentKey -> Lifecycle.State.RESUMED
                    else -> Lifecycle.State.STARTED
                },
            )
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                ScreenDismissSurface(
                    state = if (entry.contentKey == outgoingKey) motion else idleMotion,
                    backgroundColor = MaterialTheme.colorScheme.background,
                ) {
                    entry.Content()
                }
            }
        }
        if (motion.isDismissing) {
            // Prevent another navigation action from racing the committed exit animation.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent().changes.forEach { it.consume() }
                        }
                    },
            )
        }
    }
}
