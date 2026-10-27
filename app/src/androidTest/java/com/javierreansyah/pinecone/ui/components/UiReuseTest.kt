package com.javierreansyah.pinecone.ui.components

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Visibility_off
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.ui.features.library.organize.SelectSpaceForShelfDialog
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
    fun systemBackCancelsEditingBeforeNavigating() {
        val editing = mutableStateOf(false)
        val calls = mutableListOf<String>()
        lateinit var dispatcher: OnBackPressedDispatcher
        compose.setContent {
            dispatcher = requireNotNull(LocalOnBackPressedDispatcherOwner.current)
                .onBackPressedDispatcher
            BackHandler { calls += "back" }
            MaterialTheme {
                EditingNavigationIcon(editing.value, { calls += "back" }) {
                    calls += "cancel"
                    editing.value = false
                }
            }
        }
        compose.runOnIdle { editing.value = true }
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.runOnIdle {
            assertEquals(false, editing.value)
            assertEquals(listOf("cancel"), calls)
        }
        compose.runOnIdle { dispatcher.onBackPressed() }
        compose.runOnIdle { assertEquals(listOf("cancel", "back"), calls) }
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
    fun segmentedSpaceChoicesKeepMultipleSelectionsAndDeselect() {
        var confirmed = emptyList<String>()
        compose.setContent {
            MaterialTheme {
                SelectSpaceForShelfDialog(
                    shelfName = "Shelf",
                    spaces = listOf(SpaceEntity("work", "Work"), SpaceEntity("home", "Home")),
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                )
            }
        }
        compose.onNodeWithText("Work").performClick().assertIsOn()
        compose.onNodeWithText("Home").performClick().assertIsOn()
        compose.onNodeWithText("Work").performClick().assertIsOff()
        compose.onNodeWithText(context.getString(R.string.action_create)).performClick()
        compose.runOnIdle { assertEquals(listOf("home"), confirmed) }
    }

    @Test
    fun segmentedSpaceChoicesKeepSingleSelection() {
        var confirmed = emptyList<String>()
        compose.setContent {
            MaterialTheme {
                SelectSpaceForShelfDialog(
                    shelfName = "Shelf",
                    spaces = listOf(SpaceEntity("work", "Work"), SpaceEntity("home", "Home")),
                    singleSelect = true,
                    onDismiss = {},
                    onConfirm = { confirmed = it },
                )
            }
        }
        compose.onNodeWithText("Work").performClick().assertIsSelected()
        compose.onNodeWithText("Home").performClick().assertIsSelected()
        compose.onNodeWithText("Work").assertIsNotSelected()
        compose.onNodeWithText(context.getString(R.string.action_create)).performClick()
        compose.runOnIdle { assertEquals(listOf("home"), confirmed) }
    }

    @Test
    fun accessoriesStayCenteredOnFirstTwoLinesWhenSummaryWraps() {
        val summary = mutableStateOf("First line")
        val fontScale = mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp)) {
                        SegmentedListItem(
                            shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                            modifier = Modifier.testTag("row"),
                            verticalAlignment = Alignment.Top,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                            leadingContent = {
                                ListItemAccessory {
                                    Icon(
                                        MaterialSymbols.Outlined.Visibility_off, null,
                                        modifier = Modifier.testTag("leading"),
                                    )
                                }
                            },
                            trailingContent = {
                                ListItemAccessory {
                                    Switch(false, null, modifier = Modifier.testTag("trailing"))
                                }
                            },
                            supportingContent = {
                                Text(
                                    summary.value,
                                    Modifier.testTag("summary")
                                )
                            },
                        ) { Text("Title", Modifier.testTag("title")) }
                    }
                }
            }
        }
        fun bounds(tag: String) =
            compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

        for (scale in listOf(1f, 1.5f)) {
            compose.runOnIdle {
                fontScale.value = scale
                summary.value = "First line"
            }
            val title = bounds("title")
            val row = bounds("row")
            val supporting = bounds("summary")
            assertEquals(
                "single-line padding at scale=$scale",
                title.top - row.top,
                row.bottom - supporting.bottom,
                1f
            )
            val center = title.top + (title.height + bounds("summary").height) / 2f
            for (tag in listOf("leading", "trailing")) {
                assertEquals(
                    "scale=$scale tag=$tag title=$title summary=${bounds("summary")} accessory=${
                        bounds(
                            tag
                        )
                    }", center, bounds(tag).center.y, 1f
                )
            }
            compose.runOnIdle { summary.value = "First line\nSecond line\nThird line" }
            val multilineRow = bounds("row")
            assertEquals(
                "multiline padding at scale=$scale",
                bounds("title").top - multilineRow.top,
                multilineRow.bottom - bounds("summary").bottom,
                1f,
            )
            for (tag in listOf("leading", "trailing")) {
                assertEquals(
                    "scale=$scale tag=$tag title=$title summary=${bounds("summary")} accessory=${
                        bounds(
                            tag
                        )
                    }", center, bounds(tag).center.y, 1f
                )
            }
        }
    }

    @Test
    fun segmentedSwitchKeepsInvertedValuesAndDisabledRows() {
        val showAll = mutableStateOf(true)
        val enabled = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    SettingsSwitchItem(
                        ListItemDefaults.segmentedShapes(index = 0, count = 1),
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
