package com.javierreansyah.pinecone.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal const val ScreenMinimizedScale = 0.9f
internal const val ScreenShrinkMillis = 80
internal const val ScreenExitMillis = 180
internal const val ScreenReturnMillis = 180

/** Shared by navigation and overlays; only the outer surface knows about gesture progress. */
@Stable
internal class ScreenDismissState {
    var progress by mutableFloatStateOf(0f)
        private set
    var exit by mutableFloatStateOf(0f)
        private set
    var isDismissing by mutableStateOf(false)
        private set

    fun seek(progress: Float) {
        if (!isDismissing) this.progress = progress.coerceIn(0f, 1f)
    }

    suspend fun restore() {
        animate(progress, 0f, animationSpec = tween(ScreenReturnMillis)) { value, _ ->
            progress = value
        }
    }

    suspend fun dismiss(onDismissed: () -> Unit) {
        if (isDismissing) return
        isDismissing = true
        // Settle into the box first, including when Back was tapped instead of swiped.
        animate(progress, 1f, animationSpec = tween(ScreenShrinkMillis)) { value, _ ->
            progress = value
        }
        animate(
            exit, 1f,
            animationSpec = tween(ScreenExitMillis, easing = FastOutSlowInEasing),
        ) { value, _ -> exit = value }
        onDismissed()
    }

    fun reset() {
        progress = 0f
        exit = 0f
        isDismissing = false
    }
}

@Composable
internal fun ScreenDismissSurface(
    state: ScreenDismissState,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val progress = state.progress
                val scale = 1f - (1f - ScreenMinimizedScale) * progress
                scaleX = scale
                scaleY = scale
                // Physical right for gestures from either edge, including RTL layouts.
                translationX = size.width * state.exit
                shape = RoundedCornerShape((28f * progress).dp)
                clip = progress > 0f
            }
            .background(backgroundColor),
    ) {
        content()
    }
}

@Composable
fun PredictiveBackOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable (onBack: () -> Unit) -> Unit,
) {
    val state = remember { ScreenDismissState() }
    val scope = rememberCoroutineScope()
    val currentOnClose by rememberUpdatedState(onClose)
    var animation by remember { mutableStateOf<Job?>(null) }
    val dismiss: () -> Unit = {
        if (!state.isDismissing) {
            animation?.cancel()
            animation = scope.launch { state.dismiss { currentOnClose() } }
        }
    }

    PredictiveBackHandler {
        if (state.isDismissing) {
            it.collect { }
        } else {
            animation?.cancel()
            try {
                it.collect { event -> state.seek(event.progress) }
                dismiss()
            } catch (_: CancellationException) {
                animation = scope.launch { state.restore() }
            }
        }
    }

    ScreenDismissSurface(state, modifier, backgroundColor) {
        content(dismiss)
    }
}
