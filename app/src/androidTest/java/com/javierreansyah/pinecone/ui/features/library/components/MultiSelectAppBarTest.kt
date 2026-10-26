package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MultiSelectAppBarTest {
    @get:Rule
    val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun emptySelectionDisablesActionsAndSingleSelectionOffersInfoAtTheRightEdge() {
        val count = mutableIntStateOf(0)
        var openedInfo = 0
        var selectedAll = 0
        compose.setContent {
            MaterialTheme {
                MultiSelectAppBar(
                    selectedCount = count.intValue,
                    isAllSelected = false,
                    showMarkAsRead = true,
                    onCloseMultiSelect = {},
                    onClearSelection = {},
                    onSelectAll = { selectedAll++ },
                    onMarkAsReadUnread = {},
                    onOrganize = {},
                    onArchive = {},
                    onDelete = {},
                    // The toolbar must also enforce the one-item requirement itself.
                    onInfo = { openedInfo++ },
                    wording = PublicationWording.Books,
                )
            }
        }
        val more = compose.onNodeWithContentDescription(context.getString(R.string.action_more))
        val organize =
            compose.onNodeWithContentDescription(context.getString(R.string.action_organize))
        more.assertIsNotEnabled()
        organize.assertIsNotEnabled()
        compose.onNodeWithContentDescription(context.getString(R.string.action_select_all))
            .performClick()
        compose.runOnIdle { assertEquals(1, selectedAll); count.intValue = 1 }
        organize.assertIsEnabled()
        more.assertIsEnabled()
        assertTrue(more.fetchSemanticsNode().boundsInRoot.left > organize.fetchSemanticsNode().boundsInRoot.left)
        compose.onNodeWithText(context.getString(R.string.publication_mark_as_read))
            .assertDoesNotExist()
        more.performClick()
        compose.onNodeWithText(context.getString(PublicationWording.Books.infoTitle)).performClick()
        compose.runOnIdle { assertEquals(1, openedInfo) }
        compose.onNodeWithText(context.getString(R.string.publication_mark_as_read))
            .assertDoesNotExist()
    }

    @Test
    fun multipleSelectionUsesContextualReadAndArchiveActionsAndOmitsInfo() {
        val unread = mutableStateOf(true)
        val archived = mutableStateOf(false)
        var marked = 0
        var archiveChanges = 0
        compose.setContent {
            MaterialTheme {
                MultiSelectAppBar(
                    selectedCount = 2,
                    isAllSelected = true,
                    showMarkAsRead = unread.value,
                    onCloseMultiSelect = {},
                    onClearSelection = {},
                    onSelectAll = {},
                    onMarkAsReadUnread = { marked++; unread.value = false },
                    onOrganize = {},
                    onArchive = { archiveChanges++; archived.value = !archived.value },
                    onDelete = {},
                    onInfo = {},
                    isUnarchive = archived.value,
                    wording = PublicationWording.Books,
                )
            }
        }
        val more = compose.onNodeWithContentDescription(context.getString(R.string.action_more))
        more.performClick()
        compose.onNodeWithText(context.getString(PublicationWording.Books.infoTitle))
            .assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.publication_mark_as_unread))
            .assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.publication_mark_as_read)).performClick()
        compose.runOnIdle { assertEquals(1, marked) }
        more.performClick()
        compose.onNodeWithText(context.getString(R.string.publication_mark_as_unread))
            .assertExists()
        compose.onNodeWithText(context.getString(R.string.publication_archive)).performClick()
        compose.runOnIdle { assertEquals(1, archiveChanges) }
        more.performClick()
        compose.onNodeWithText(context.getString(R.string.publication_unarchive)).performClick()
        compose.runOnIdle { assertEquals(2, archiveChanges) }
    }

    @Test
    fun deletingRequiresConfirmationAndCancellingKeepsTheSelection() {
        var deleted = 0
        compose.setContent {
            MaterialTheme {
                MultiSelectAppBar(
                    selectedCount = 2,
                    isAllSelected = true,
                    showMarkAsRead = true,
                    onCloseMultiSelect = {},
                    onClearSelection = {},
                    onSelectAll = {},
                    onMarkAsReadUnread = {},
                    onOrganize = {},
                    onArchive = {},
                    onDelete = { deleted++ },
                    onInfo = null,
                )
            }
        }
        val more = compose.onNodeWithContentDescription(context.getString(R.string.action_more))
        val delete = compose.onNodeWithText(context.getString(R.string.action_delete))
        more.performClick()
        delete.performClick()
        compose.runOnIdle { assertEquals(0, deleted) }
        compose.onNodeWithText(context.getString(R.string.action_cancel)).performClick()
        compose.onNodeWithText("2").assertExists()
        compose.runOnIdle { assertEquals(0, deleted) }
        more.performClick()
        delete.performClick()
        delete.performClick()
        compose.runOnIdle { assertEquals(1, deleted) }
    }
}
