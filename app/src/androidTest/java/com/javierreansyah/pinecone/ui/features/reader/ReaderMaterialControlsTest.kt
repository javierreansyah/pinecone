package com.javierreansyah.pinecone.ui.features.reader

import android.annotation.SuppressLint

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.core.graphics.toColorInt
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.reader.audio.AudioPlaybackState
import com.javierreansyah.pinecone.ui.features.reader.components.AudioReaderContent
import com.javierreansyah.pinecone.ui.features.reader.components.NoteBottomSheet
import com.javierreansyah.pinecone.ui.features.reader.components.settings.AdvancedTabContent
import com.javierreansyah.pinecone.ui.features.reader.components.settings.ReaderSettingsContent
import com.javierreansyah.pinecone.ui.features.reader.components.settings.SettingsSwitchRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ReaderMaterialControlsTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun settingsSwitchHasOneToggleAndHonorsDisabledState() {
        val checked = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        var changes = 0
        compose.setContent {
            MaterialTheme {
                SettingsSwitchRow(
                    title = "Keep screen on", isChecked = checked.value, enabled = enabled.value,
                    onCheckedChange = { checked.value = it; changes++ },
                )
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText("Keep screen on").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle {
            assertEquals(1, changes)
            enabled.value = false
        }
        compose.onNodeWithText("Keep screen on").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(1, changes) }
    }

    @Test
    fun disabledSettingKeepsTheEnabledBackground() {
        compose.setContent {
            MaterialTheme {
                Column {
                    SettingsSwitchRow("Enabled", false, {})
                    SettingsSwitchRow("Disabled", false, {}, enabled = false)
                }
            }
        }
        val enabled = compose.onNodeWithText("Enabled").captureToImage().toPixelMap()[0, 0]
        val disabled = compose.onNodeWithText("Disabled").captureToImage().toPixelMap()[0, 0]
        assertEquals(enabled, disabled)
    }

    @Test
    @SuppressLint("StringFormatTrivial") // Localized fixed-decimal formatting matches the UI; concatenation does not.
    fun audioSliderValuesStayOnTheRightOfTheirLabels() {
        val settings = ReaderSettings().let {
            it.copy(
                formatPreferences = it.formatPreferences.copy(
                    audioSpeed = 1.2,
                    audioPitch = 1.4
                )
            )
        }
        compose.setContent {
            MaterialTheme { ReaderSettingsContent(settings, {}, profile = ReaderProfile.AUDIO) }
        }
        for ((labelId, value) in listOf(
            R.string.reader_playback_speed to 1.2,
            R.string.reader_playback_pitch to 1.4
        )) {
            val label = compose.onNodeWithText(context.getString(labelId), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val amount = compose.onNodeWithText(
                String.format(context.resources.configuration.locales[0], "%.1f", value),
                useUnmergedTree = true,
            ).fetchSemanticsNode().boundsInRoot
            assertTrue(amount.left > label.right)
            assertTrue(abs(amount.center.y - label.center.y) < 1f)
        }
    }

    @Test
    fun readerChoiceValueStaysOnTheRightOfItsLabel() {
        compose.setContent {
            MaterialTheme {
                AdvancedTabContent(
                    ReaderSettings(),
                    {},
                    includeTextPreferences = false,
                    onRestoreDefaultsClick = {})
            }
        }
        val label = compose.onNodeWithText(
            context.getString(R.string.reader_settings_force_orientation),
            useUnmergedTree = true
        )
            .fetchSemanticsNode().boundsInRoot
        val value = compose.onNodeWithText(
            context.getString(R.string.reader_settings_orientation_auto),
            useUnmergedTree = true
        )
            .fetchSemanticsNode().boundsInRoot
        assertTrue(value.left > label.right)
        assertTrue(abs(value.center.y - label.center.y) < 1f)
    }

    @Test
    fun audioBookmarkExposesItsStateAndUnavailableNoteStaysDisabled() {
        val bookmarked = mutableStateOf(false)
        var bookmarkChanges = 0
        compose.setContent {
            MaterialTheme {
                AudioReaderContent(
                    state = AudioPlaybackState(bookId = "test"), coverPath = null,
                    background = Color.White, foreground = Color.Black,
                    isBookmarked = bookmarked.value,
                    onToggleBookmark = { bookmarked.value = !bookmarked.value; bookmarkChanges++ },
                    onPlayPause = {}, onSkip = {}, onTrack = {}, onSeek = {},
                    onOpenBookmarks = {}, onOpenSettings = {},
                )
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.reader_add_bookmark))
            .assertIsOff().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_remove_bookmark))
            .assertIsOn()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_add_note))
            .assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, bookmarkChanges) }
    }

    @Test
    fun filledNoteFieldSavesEditedTextAndPreservesTheNote() {
        val note =
            NoteEntity(id = 7, bookId = "book", locatorJson = "{}", noteText = "Before", color = 42)
        var saved: NoteEntity? = null
        compose.setContent {
            MaterialTheme {
                NoteBottomSheet(
                    note,
                    onUpdateNote = { saved = it },
                    onDeleteNote = {},
                    onDismiss = {})
            }
        }
        compose.onNode(hasSetTextAction()).performTextReplacement("After\nSecond line")
        val editor = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInRoot
        val swatch = compose.onAllNodes(isSelectable())[2]
        val swatchBounds = swatch.fetchSemanticsNode().boundsInRoot
        assertTrue(swatchBounds.top >= editor.bottom)
        assertTrue(swatchBounds.center.x < editor.center.x)
        swatch.performClick()
        compose.onNodeWithText(context.getString(R.string.action_save)).performClick()
        compose.runOnIdle {
            assertEquals(
                note.copy(
                    noteText = "After\nSecond line",
                    color = "#408bc24a".toColorInt()
                ), saved
            )
        }
    }
}
