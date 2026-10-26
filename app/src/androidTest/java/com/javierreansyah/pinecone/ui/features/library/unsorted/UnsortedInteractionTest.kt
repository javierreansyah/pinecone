package com.javierreansyah.pinecone.ui.features.library.unsorted

import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.MainActivity
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.testing.ReadingTestEntryPoint
import com.javierreansyah.pinecone.ui.components.OperationFeedback
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.archive.ArchiveScreen
import com.javierreansyah.pinecone.ui.theme.AppTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class UnsortedInteractionTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun idleMenuRunsBothActionsAndReturnsAfterWorkAtTheSamePosition() {
        val operation = mutableStateOf<String?>(null)
        val unsorted = mutableStateOf(true)
        var imports = 0
        var scans = 0
        compose.runOnUiThread {
            compose.activity.setContent {
                MaterialTheme {
                    Box(Modifier.fillMaxSize()) {
                        OperationFeedback(
                            operation.value, "Importing", "Processing 1 of 2", onCancel = {},
                            progress = 0.5f,
                            modifier = Modifier.align(Alignment.BottomEnd),
                            content = if (unsorted.value) {
                                {
                                    UnsortedImportFabMenu(
                                        onImportFiles = { imports++ },
                                        onScanFolder = { scans++ })
                                }
                            } else null,
                        )
                    }
                }
            }
        }
        val add = context.getString(R.string.action_add)
        val initial = compose.onNodeWithContentDescription(add).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription(add).performClick()
        compose.onNodeWithText(context.getString(R.string.nav_import_files)).performClick()
        compose.runOnIdle { assertEquals(1, imports) }
        compose.onNodeWithContentDescription(add).performClick()
        compose.onNodeWithText(context.getString(R.string.nav_scan_folder)).performClick()
        compose.runOnIdle { assertEquals(1, scans) }
        compose.runOnIdle { operation.value = "import" }
        compose.onNodeWithContentDescription(add).assertDoesNotExist()
        val running =
            compose.onNodeWithContentDescription("Importing").fetchSemanticsNode().boundsInRoot
        assertEquals(initial.right, running.right, 0.5f)
        assertEquals(initial.bottom, running.bottom, 0.5f)
        compose.runOnIdle { operation.value = null }
        val finished = compose.onNodeWithContentDescription(add).fetchSemanticsNode().boundsInRoot
        assertEquals(initial.right, finished.right, 0.5f)
        assertEquals(initial.bottom, finished.bottom, 0.5f)
        compose.runOnIdle { unsorted.value = false }
        compose.onNodeWithContentDescription(add).assertDoesNotExist()
    }

    @Test
    fun longPressSelectsImmediatelyInUnsortedAndArchiveAndInfoExitsSelection(): Unit = runBlocking {
        val app = context.applicationContext as PineconeApplication
        app.startup.initialize()
        val database =
            EntryPointAccessors.fromApplication(app, ReadingTestEntryPoint::class.java).database()
        val id = UUID.randomUUID().toString()
        val unsorted = BookEntity(
            id,
            "AAA unsorted $id",
            filePath = "/$id.epub",
            mediaType = "application/epub+zip",
            isUnsorted = true
        )
        val archived = BookEntity(
            "archive-$id",
            "AAA archive $id",
            filePath = "/archive-$id.epub",
            mediaType = "application/epub+zip",
            isArchived = true
        )
        database.bookDao().insertAll(listOf(unsorted, archived))
        val showArchive = mutableStateOf(false)
        var openedInfo: String? = null
        try {
            compose.runOnUiThread {
                compose.activity.setContent {
                    AppTheme {
                        if (showArchive.value) {
                            ArchiveScreen(
                                onNavigateBack = {},
                                onNavigateToReader = {},
                                onNavigateToBookInfo = { openedInfo = it },
                                onNavigateToOrganize = {})
                        } else {
                            UnsortedScreen(
                                onNavigateBack = {},
                                onNavigateToReader = {},
                                onNavigateToBookInfo = {},
                                onNavigateToOrganize = {})
                        }
                    }
                }
            }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(unsorted.title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(unsorted.title).performTouchInput { longClick() }
            compose.onNodeWithContentDescription(context.getString(R.string.action_more))
                .assertIsEnabled()
            compose.onNodeWithText(context.getString(R.string.action_select_multiple))
                .assertDoesNotExist()
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.onNodeWithContentDescription(context.getString(R.string.action_select_multiple))
                .assertExists()
            compose.onNodeWithText(unsorted.title).performTouchInput { longClick() }
            compose.onNodeWithContentDescription(context.getString(R.string.action_cancel))
                .performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.action_select_multiple))
                .assertExists()
            compose.runOnIdle { showArchive.value = true }
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(archived.title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(archived.title).performTouchInput { longClick() }
            compose.onNodeWithContentDescription(context.getString(R.string.action_more))
                .assertIsEnabled()
            compose.onNodeWithText(context.getString(R.string.action_select_multiple))
                .assertDoesNotExist()
            compose.onNodeWithText(archived.title).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.action_more))
                .assertIsNotEnabled()
            compose.onNodeWithContentDescription(context.getString(R.string.action_select_all))
                .performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.action_deselect_all))
                .performClick()
            compose.onNodeWithText(archived.title).performClick()
            compose.onNodeWithContentDescription(context.getString(R.string.action_more))
                .performClick()
            compose.onNodeWithText(context.getString(R.string.publication_unarchive)).assertExists()
            compose.onNodeWithText(context.getString(PublicationWording.Books.infoTitle))
                .performClick()
            compose.runOnIdle { assertEquals(archived.id, openedInfo) }
            compose.onNodeWithContentDescription(context.getString(R.string.action_select_multiple))
                .assertExists()
        } finally {
            database.bookDao().deleteIds(listOf(unsorted.id, archived.id))
        }
    }
}
