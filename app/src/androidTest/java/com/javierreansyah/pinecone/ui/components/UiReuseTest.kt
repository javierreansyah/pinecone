package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Visibility_off
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.ui.features.reader.components.settings.IncrementDecrementControl
import com.javierreansyah.pinecone.ui.features.reader.components.settings.ReaderSettingsContent
import com.javierreansyah.pinecone.ui.features.settings.components.SettingsSwitchItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class UiReuseTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun choicesUseValuesEvenWhenLabelsMatch() {
        var selected = false
        compose.setContent {
            MaterialTheme {
                SegmentedButtonGroup(
                    options = listOf(false, true), icons = emptyList(), selected = false,
                    optionLabel = { "Same label" }, onSelected = { selected = it },
                )
            }
        }
        compose.onAllNodesWithText("Same label")[1].performClick()
        compose.runOnIdle { assertEquals(true, selected) }
    }

    @Test
    fun numericButtonsKeepTheirDirectionAndDisabledState() {
        val enabled = mutableStateOf(true)
        val steps = mutableListOf<Int>()
        compose.setContent {
            MaterialTheme {
                IncrementDecrementControl(
                    label = "Spacing", value = "1.0",
                    onStep = { steps += it }, enabled = enabled.value
                )
            }
        }
        val decrease =
            compose.onNodeWithContentDescription(context.getString(R.string.action_decrease))
        val increase =
            compose.onNodeWithContentDescription(context.getString(R.string.action_increase))
        decrease.performClick()
        increase.performClick()
        compose.runOnIdle {
            assertEquals(listOf(-1, 1), steps)
            enabled.value = false
        }
        decrease.assertIsNotEnabled()
        increase.assertIsNotEnabled()
    }

    @Test
    fun readerVerticalMarginKeepsStepDirectionAndBounds() {
        val settings = mutableStateOf(ReaderSettings())
        compose.setContent {
            MaterialTheme {
                ReaderSettingsContent(settings.value, { settings.value = it(settings.value) })
            }
        }
        val label = context.getString(R.string.reader_settings_vertical_margin)
        compose.onNodeWithText(label).performScrollTo()
        fun step(action: Int) =
            compose.onAllNodesWithContentDescription(context.getString(action))[3]
                .performScrollTo().performClick()

        step(R.string.action_decrease)
        compose.runOnIdle { assertEquals(28.8, settings.value.verticalMargin, 0.00001) }
        step(R.string.action_increase)
        compose.runOnIdle {
            assertEquals(32.0, settings.value.verticalMargin, 0.00001)
            settings.value = settings.value.copy(verticalMargin = 0.0)
        }
        step(R.string.action_decrease)
        compose.runOnIdle {
            assertEquals(0.0, settings.value.verticalMargin, 0.00001)
            settings.value = settings.value.copy(verticalMargin = 128.0)
        }
        step(R.string.action_increase)
        compose.runOnIdle { assertEquals(128.0, settings.value.verticalMargin, 0.00001) }
    }

    @Test
    fun editingButtonsKeepBackCancelAndSaveSeparate() {
        val editing = mutableStateOf(false)
        val calls = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                Column {
                    EditingNavigationIcon(editing.value, { calls += "back" }, { calls += "cancel" })
                    EditingActions(editing.value, { calls += "save" }) {}
                }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.action_back)).performClick()
        compose.runOnIdle { editing.value = true }
        compose.onNodeWithContentDescription(context.getString(R.string.action_cancel))
            .performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.action_save)).performClick()
        compose.runOnIdle { assertEquals(listOf("back", "cancel", "save"), calls) }
    }

    @Test
    fun segmentedSwitchKeepsInvertedValuesAndDisabledRows() {
        val showAll = mutableStateOf(true)
        val enabled = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                SegmentedColumn {
                    SettingsSwitchItem(
                        "Hide All", "Hide the All space", !showAll.value,
                        { showAll.value = !it }, MaterialSymbols.Outlined.Visibility_off,
                        enabled = enabled.value
                    )
                }
            }
        }
        compose.onAllNodesWithText("Hide All")[0].performClick()
        compose.runOnIdle {
            assertEquals(false, showAll.value)
            enabled.value = false
        }
        compose.onAllNodesWithText("Hide All")[0].assertIsNotEnabled()
    }
}
