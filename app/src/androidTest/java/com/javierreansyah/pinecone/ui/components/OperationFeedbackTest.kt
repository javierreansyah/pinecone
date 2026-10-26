package com.javierreansyah.pinecone.ui.components

import android.view.KeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.features.library.components.ImportReport
import com.javierreansyah.pinecone.ui.features.library.components.toFeedbackReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OperationFeedbackTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fabExitIsOneRightwardFlightWithoutASecondSettlingPhase() {
        val visible = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    OperationFeedback(
                        "import", "Importing", "Processing", onCancel = {}, showFab = visible.value,
                        modifier = Modifier.align(Alignment.BottomEnd), progress = 0.5f,
                    )
                }
            }
        }
        compose.waitForIdle()
        val initial =
            compose.onNodeWithContentDescription("Importing").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible.value = false }
        compose.mainClock.advanceTimeByFrame()
        var previousLeft = initial.left
        var movingFrames = 0
        repeat(15) {
            compose.mainClock.advanceTimeBy(16)
            val bounds = compose.onAllNodesWithContentDescription("Importing").fetchSemanticsNodes()
                .firstOrNull()?.boundsInRoot
            if (bounds != null && bounds.width > 0f) {
                assertTrue(
                    "The exit must not reverse direction",
                    bounds.left >= previousLeft - 0.5f
                )
                assertEquals("The vertical anchor must stay fixed", initial.top, bounds.top, 0.5f)
                if (bounds.left > previousLeft + 0.5f) movingFrames++
                previousLeft = bounds.left
            }
        }
        assertTrue("The FAB should move across multiple frames", movingFrames > 2)
        compose.onNodeWithContentDescription("Importing").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun discreteProgressUpdatesAnimateThroughIntermediateValues() {
        val progress = mutableFloatStateOf(0.1f)
        compose.setContent {
            MaterialTheme {
                OperationFeedback(
                    "import",
                    "Importing",
                    "Processing",
                    onCancel = {},
                    progress = progress.floatValue
                )
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { progress.floatValue = 0.9f }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(32)
        val displayed = compose.onNode(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true,
        ).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue(
            "Progress should interpolate between worker updates: $displayed",
            displayed > 0.1f && displayed < 0.9f
        )
        compose.mainClock.autoAdvance = true
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.9f, 0f..1f)
            ),
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun fabOpensLiveProgressAndDismissalDoesNotCancelWork() {
        val message = mutableStateOf("Processing 1 of 10")
        val progress = mutableFloatStateOf(0.1f)
        var cancelled = 0
        compose.setContent {
            MaterialTheme {
                OperationFeedback(
                    "import",
                    "Importing",
                    message.value,
                    onCancel = { cancelled++ },
                    progress = progress.floatValue
                )
            }
        }
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.1f, 0f..1f)
            ),
            useUnmergedTree = true,
        ).assertExists()
        compose.onNodeWithText(message.value).assertDoesNotExist()
        compose.onNodeWithContentDescription("Importing").performClick()
        compose.onNodeWithText(message.value).assertExists()
        compose.runOnIdle {
            message.value = "Processing 2 of 10"
            progress.floatValue = 0.2f
        }
        compose.onNodeWithText(message.value).assertExists()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText(message.value).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, cancelled) }
        compose.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(0.2f, 0f..1f)
            ),
            useUnmergedTree = true,
        ).assertExists()
        compose.onNodeWithContentDescription("Importing").performClick()
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        compose.runOnIdle { assertEquals(1, cancelled) }
        compose.onNodeWithText(message.value).assertDoesNotExist()
    }

    @Test
    fun pdfSuccessClosesProgressSilentlyAndFailureOpensAReportWithoutClickingTheFab() {
        val operation = mutableStateOf<String?>("pdf")
        val controlsVisible = mutableStateOf(false)
        val report = mutableStateOf<OperationReport?>(null)
        compose.setContent {
            MaterialTheme {
                OperationFeedback(
                    operation.value, "Preparing read mode", "Processing 1 of 2", onCancel = {},
                    report = report.value, onDismissReport = { report.value = null },
                    showFab = controlsVisible.value, progress = 0.5f,
                )
            }
        }
        compose.onNodeWithContentDescription("Preparing read mode").assertDoesNotExist()
        compose.runOnIdle { controlsVisible.value = true }
        compose.onNodeWithContentDescription("Preparing read mode").performClick()
        compose.onNodeWithText("Processing 1 of 2").assertExists()
        compose.runOnIdle {
            operation.value = null
            controlsVisible.value = false
        }
        compose.onNodeWithText("Processing 1 of 2").assertDoesNotExist()
        compose.onNodeWithContentDescription("Preparing read mode").assertDoesNotExist()
        compose.runOnIdle {
            report.value =
                OperationReport("failure", "Read mode failed", "Could not convert this PDF.")
        }
        compose.onNodeWithText("Read mode failed").assertExists()
        compose.onNodeWithText("Could not convert this PDF.").assertExists()
        compose.onNodeWithText(context.getString(R.string.action_close)).performClick()
        compose.onNodeWithText("Read mode failed").assertDoesNotExist()
    }

    @Test
    fun importFailureReportsOpenAutomaticallyAndAdvanceWithoutDroppingTheNextResult() {
        val reports = mutableStateOf(
            listOf(
                ImportReport(id = "partial", failedFiles = listOf("first.pdf"), successCount = 2),
                ImportReport(
                    id = "failure",
                    failedFiles = listOf("broken.pdf"),
                    successCount = 0,
                    inputRequired = true
                ),
            )
        )
        compose.setContent {
            MaterialTheme {
                OperationFeedback(
                    null,
                    "Importing",
                    "",
                    onCancel = {},
                    report = reports.value.firstOrNull()?.toFeedbackReport(),
                    onDismissReport = { reports.value = reports.value.drop(1) },
                )
            }
        }
        compose.onNodeWithText(
            context.resources.getQuantityString(
                R.plurals.import_partial_success_message,
                2,
                2
            ) + "\n\n" +
                    context.getString(R.string.import_failed_files, "first.pdf"),
        ).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_close)).performClick()
        compose.onNodeWithText(
            context.getString(R.string.import_failed_files, "broken.pdf") + "\n\n" +
                    context.getString(R.string.publication_input_required),
        ).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_close)).performClick()
        compose.onNodeWithText(context.getString(R.string.import_failed_title)).assertDoesNotExist()
    }
}
