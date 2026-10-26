package com.javierreansyah.pinecone.ui.features.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import com.javierreansyah.pinecone.ui.features.reader.components.AudioReaderContent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class AudioProgressIndicatorTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun quickPlayPauseRestoresTheFlatProgressTrack() {
        val playing = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme(motionScheme = MotionScheme.expressive()) {
                AudioReaderContent(
                    state = AudioPlaybackState(
                        bookId = "test",
                        playing = playing.value,
                        elapsedSeconds = 30,
                        durationSeconds = 60
                    ),
                    coverPath = null,
                    background = Color.White,
                    foreground = Color.Black,
                    onPlayPause = {},
                    onSkip = {},
                    onTrack = {},
                    onSeek = {},
                    onOpenBookmarks = {},
                    onOpenSettings = {},
                )
            }
        }
        compose.mainClock.advanceTimeBy(32)
        val seekLabel =
            InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.reader_audio_seek)

        fun trackPixels(): IntArray {
            val bitmap = compose.onNodeWithContentDescription(seekLabel).captureToImage()
            return IntArray(bitmap.width * bitmap.height).also { bitmap.readPixels(it) }
        }

        val flat = trackPixels()
        for (playMillis in listOf(32L, 64L, 128L)) {
            compose.runOnIdle { playing.value = true }
            compose.mainClock.advanceTimeBy(playMillis)
            compose.runOnIdle { playing.value = false }
            compose.mainClock.advanceTimeBy(1000)
            assertArrayEquals("Paused must return to the original flat track", flat, trackPixels())
        }
        compose.runOnIdle { playing.value = true }
        compose.mainClock.advanceTimeBy(2000)
        assertFalse("Playing must render a wave", flat.contentEquals(trackPixels()))
        compose.runOnIdle { playing.value = false }
        compose.mainClock.advanceTimeBy(32)
        assertFalse(
            "Pausing should animate the wave away rather than snap flat",
            flat.contentEquals(trackPixels())
        )
        compose.mainClock.advanceTimeBy(2000)
        assertArrayEquals("The interrupted spring must settle to a flat track", flat, trackPixels())
    }
}
