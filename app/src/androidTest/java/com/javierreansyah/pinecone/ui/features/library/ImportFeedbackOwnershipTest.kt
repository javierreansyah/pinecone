package com.javierreansyah.pinecone.ui.features.library

import android.content.Intent
import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.util.Consumer
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.MainActivity
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.model.OperationKind
import com.javierreansyah.pinecone.data.model.OperationSnapshot
import com.javierreansyah.pinecone.data.model.OperationState
import com.javierreansyah.pinecone.ui.components.ScreenBackDispatcher
import com.javierreansyah.pinecone.ui.components.ScreenNavDisplay
import com.javierreansyah.pinecone.ui.features.library.components.ImportFeedbackState
import com.javierreansyah.pinecone.ui.features.library.components.ImportReport
import com.javierreansyah.pinecone.ui.features.library.main.LibraryRoute
import com.javierreansyah.pinecone.ui.features.library.unsorted.UnsortedScreen
import com.javierreansyah.pinecone.ui.root.HandleDeepLinks
import com.javierreansyah.pinecone.ui.root.Screen
import com.javierreansyah.pinecone.ui.root.getInitialBackStackFromIntent
import com.javierreansyah.pinecone.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImportFeedbackOwnershipTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun unsortedFabScalesWithItsScreenAndReportsWaitUntilTheScreenResumes() {
        val stack = mutableStateListOf<NavKey>(Screen.Library, Screen.Unsorted)
        val feedback = mutableStateOf(ImportFeedbackState())
        installNavigation(stack) { key ->
            if (key == Screen.Unsorted) {
                UnsortedScreen(
                    onNavigateBack = {}, onNavigateToReader = {}, onNavigateToBookInfo = {}, onNavigateToOrganize = {},
                    importFeedbackState = feedback.value,
                    onDismissImportReport = { feedback.value = feedback.value.copy(report = null) },
                )
            } else Text("Library")
        }
        val add = context.getString(R.string.action_add)
        val initial = compose.onNodeWithContentDescription(add).fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.runOnUiThread {
            compose.activity.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
            compose.activity.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.7f))
        }
        compose.mainClock.advanceTimeBy(48)
        val preview = compose.onNodeWithContentDescription(add).fetchSemanticsNode().boundsInRoot
        assertTrue("The FAB must transform with the Unsorted page", preview.width < initial.width)
        compose.runOnUiThread {
            feedback.value = feedback.value.copy(report = ImportReport(id = "report", failedFiles = listOf("failed.pdf"), successCount = 0))
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithText(context.getString(R.string.import_failed_title)).assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText(context.getString(R.string.import_failed_title)).assertExists()
        compose.onNodeWithText(context.getString(R.string.action_close)).performClick()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription(add).assertDoesNotExist()
        compose.onNodeWithText("Library").assertExists()
    }

    @Test
    fun mainLibraryOwnsTheLoaderWhenUnsortedIsDisabledAndOtherPagesHaveNoLoader() {
        val stack = mutableStateListOf<NavKey>(Screen.Library, Screen.Settings)
        val feedback = ImportFeedbackState(operation = OperationSnapshot(
            "import", OperationKind.IMPORT, OperationState.RUNNING, null, 1, 2, emptyList(), null,
        ))
        installNavigation(stack) { key ->
            if (key == Screen.Library) {
                LibraryRoute(
                    onNavigateToReader = {}, onNavigateToShelf = { _, _, _ -> }, onNavigateToBookInfo = {},
                    onNavigateToOrganize = {}, onNavigateToArchives = {}, onNavigateToSettings = {},
                    onNavigateToDictionaries = {}, onImportFilesClick = {}, onScanFolderClick = {},
                    importFeedbackState = feedback, importFeedbackEnabled = true,
                )
            } else Text("Other page")
        }
        val importing = context.getString(R.string.publication_work_importing)
        compose.onNodeWithContentDescription(importing).assertDoesNotExist()
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription(importing).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.action_add)).assertDoesNotExist()
        compose.onNodeWithContentDescription(importing).performClick()
        compose.onNodeWithText(context.getString(R.string.publication_work_processing, 1, 2)).assertExists()
    }

    @Test
    fun coldAndWarmFileImportsFollowTheLatestUnsortedPreference() {
        val first = Intent(Intent.ACTION_VIEW, Uri.parse("content://test/first.epub"))
        assertEquals(listOf(Screen.Library, Screen.Unsorted), getInitialBackStackFromIntent(first, true))
        assertEquals(listOf(Screen.Library), getInitialBackStackFromIntent(first, false))
        val enabled = mutableStateOf(true)
        val stack = mutableStateListOf<NavKey>(Screen.Library, Screen.Settings)
        var listener: Consumer<Intent>? = null
        compose.runOnUiThread {
            compose.activity.setContent {
                HandleDeepLinks(
                    backStack = stack, unsortedEnabled = enabled.value,
                    addOnNewIntentListener = { listener = it }, removeOnNewIntentListener = { listener = null },
                )
                Text("Import routing")
            }
        }
        compose.waitForIdle()
        compose.runOnUiThread { requireNotNull(listener).accept(first) }
        compose.waitForIdle()
        assertEquals(listOf(Screen.Library, Screen.Unsorted), stack.toList())
        compose.runOnUiThread { enabled.value = false }
        compose.waitForIdle()
        compose.runOnUiThread {
            requireNotNull(listener).accept(Intent(Intent.ACTION_VIEW, Uri.parse("content://test/second.epub")))
        }
        compose.waitForIdle()
        assertEquals(listOf(Screen.Library), stack.toList())
    }

    private fun installNavigation(stack: MutableList<NavKey>, content: @Composable (NavKey) -> Unit) {
        compose.runOnUiThread {
            compose.activity.setContent {
                AppTheme {
                    ScreenNavDisplay(
                        backStack = stack,
                        backDispatcher = ScreenBackDispatcher(),
                        onBack = { stack.removeAt(stack.lastIndex) },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                    ) { key -> NavEntry(key) { content(it) } }
                }
            }
        }
    }

    private fun backEvent(progress: Float) = BackEventCompat(0f, 500f, progress, BackEventCompat.EDGE_LEFT)
}
