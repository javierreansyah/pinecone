package com.javierreansyah.pinecone.ui.root

import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import com.javierreansyah.pinecone.ui.features.reader.components.AudioReaderContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

class AudioPlayerSheetGestureTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var sheet: AnchoredDraggableState<AudioSheetValue>
    private val seeks = mutableListOf<Double>()
    private var dragDistance = 0f
    private val seekLabel
        get() = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.reader_audio_seek)

    private fun showPlayer(height: Dp) {
        compose.setContent {
            MaterialTheme {
                val density = LocalDensity.current
                val collapsed = with(density) { height.toPx() }
                val velocityThreshold = with(density) { 125.dp.toPx() }
                dragDistance = with(density) { 140.dp.toPx() }
                sheet = remember {
                    AnchoredDraggableState(AudioSheetValue.Expanded).apply {
                        updateAnchors(DraggableAnchors {
                            AudioSheetValue.Expanded at 0f
                            AudioSheetValue.Collapsed at collapsed
                        })
                    }
                }
                val connection = remember {
                    AudioSheetNestedScrollConnection(sheet, collapsed, velocityThreshold)
                }
                Box(
                    Modifier
                        .offset { IntOffset(0, sheet.requireOffset().roundToInt()) }
                        .requiredSize(360.dp, height)
                        .anchoredDraggable(sheet, Orientation.Vertical)
                        .nestedScroll(connection)
                        .testTag("audio-sheet"),
                ) {
                    AudioReaderContent(
                        state = AudioPlaybackState(
                            bookId = "test",
                            title = "Test audio",
                            elapsedSeconds = 30,
                            durationSeconds = 60,
                        ),
                        coverPath = null,
                        background = Color.White,
                        foreground = Color.Black,
                        onPlayPause = {}, onSkip = {}, onTrack = {},
                        onSeek = { seeks += it }, onOpenBookmarks = {}, onOpenSettings = {},
                    )
                }
            }
        }
    }

    @Test
    fun downwardSwipeFromPortraitArtworkCollapsesTheSheet() {
        showPlayer(700.dp)
        compose.onNodeWithTag("audio-sheet").performTouchInput {
            swipe(Offset(centerX, height * 0.35f), Offset(centerX, height * 0.75f), 200)
        }
        compose.runOnIdle { assertEquals(AudioSheetValue.Collapsed, sheet.settledValue) }
    }

    @Test
    fun downwardSwipeFromLandscapeScrollableControlsCollapsesTheSheet() {
        showPlayer(400.dp)
        compose.onNodeWithText("Test audio").performTouchInput {
            swipe(center, center + Offset(0f, dragDistance), 150)
        }
        compose.runOnIdle { assertEquals(AudioSheetValue.Collapsed, sheet.settledValue) }
    }

    @Test
    fun verticalSwipeOnSeekBarCollapsesWithoutSeeking() {
        showPlayer(400.dp)
        compose.onNodeWithContentDescription(seekLabel).performTouchInput {
            swipe(center, center + Offset(0f, dragDistance), 150)
        }
        compose.runOnIdle {
            assertEquals(AudioSheetValue.Collapsed, sheet.settledValue)
            assertTrue("A vertical dismissal must not seek", seeks.isEmpty())
        }
    }

    @Test
    fun horizontalSwipeOnSeekBarSeeksWithoutCollapsing() {
        showPlayer(400.dp)
        compose.onNodeWithContentDescription(seekLabel).performTouchInput {
            swipe(Offset(width * 0.2f, centerY), Offset(width * 0.8f, centerY), 200)
        }
        compose.runOnIdle {
            assertEquals(AudioSheetValue.Expanded, sheet.settledValue)
            assertTrue(
                "Horizontal seeking must retain the final position",
                seeks.last() in 45.0..51.0
            )
        }
    }
}
