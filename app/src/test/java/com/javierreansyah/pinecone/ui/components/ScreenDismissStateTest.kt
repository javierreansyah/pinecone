package com.javierreansyah.pinecone.ui.components

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.material3.MotionScheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenDismissStateTest {
    private val motionSpec = MotionScheme.expressive().defaultEffectsSpec<Float>()
    @Test
    fun committedGestureFinishesShrinkingBeforeFlyingAndClosesOnlyOnce() {
        val state = ScreenDismissState()
        state.seek(0.37f)
        val frames = mutableListOf<Pair<Float, Float>>()
        val clock = FrameClock { frames += state.progress to state.exit }
        var closes = 0

        runBlocking(clock) {
            state.dismiss(motionSpec) {
                assertEquals(1f, state.progress)
                assertEquals(1f, state.exit)
                closes++
            }
            state.dismiss(motionSpec) { closes++ }
        }

        assertTrue(frames.any { (progress, exit) -> progress > 0.37f && progress < 1f && exit == 0f })
        assertTrue(frames.any { (_, exit) -> exit > 0f && exit < 1f })
        assertTrue(frames.all { (progress, exit) -> exit == 0f || progress == 1f })
        assertEquals(1, closes)
    }

    @Test
    fun cancellationRestoresWithoutStartingTheExitAndCanBeFollowedByAnotherGesture() {
        val state = ScreenDismissState()
        state.seek(0.65f)
        val frames = mutableListOf<Pair<Float, Float>>()

        runBlocking(FrameClock { frames += state.progress to state.exit }) { state.restore(motionSpec) }

        assertEquals(0f, state.progress)
        assertTrue(frames.all { (_, exit) -> exit == 0f })
        state.seek(0.2f)
        assertEquals(0.2f, state.progress)
    }

    @Test
    fun buttonBackUsesBothPhasesAndResetAllowsAnotherNavigation() {
        val state = ScreenDismissState()
        var closes = 0
        runBlocking(FrameClock {}) { state.dismiss(motionSpec) { closes++ } }
        state.reset()

        assertEquals(0f, state.progress)
        assertEquals(0f, state.exit)
        runBlocking(FrameClock {}) { state.dismiss(motionSpec) { closes++ } }
        assertEquals(2, closes)
    }

    private class FrameClock(private val afterFrame: () -> Unit) : MonotonicFrameClock {
        private var timeNanos = 0L

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            timeNanos += 16_000_000L
            return onFrame(timeNanos).also { afterFrame() }
        }
    }
}
