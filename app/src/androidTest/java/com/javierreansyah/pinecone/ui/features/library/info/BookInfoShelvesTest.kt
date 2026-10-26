package com.javierreansyah.pinecone.ui.features.library.info

import android.net.Uri
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.MainActivity
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.testing.ReadingTestEntryPoint
import com.javierreansyah.pinecone.testing.syntheticPdf
import com.javierreansyah.pinecone.ui.theme.AppTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class BookInfoShelvesTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun shelfCarouselOpensShelfAndHidesAfterLastMembershipIsRemoved(): Unit = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PineconeApplication
        app.startup.initialize()
        val services = EntryPointAccessors.fromApplication(app, ReadingTestEntryPoint::class.java)
        val source = File.createTempFile("info-shelves-", ".pdf", app.cacheDir)
        source.writeBytes(syntheticPdf(3) + "\n% ${UUID.randomUUID()}\n".toByteArray(Charsets.US_ASCII))
        val book = services.publications().importBook(Uri.fromFile(source))
        val library = app.libraryRepository
        val spaceId = LibraryPreferencesManager(app, app.readerPreferences).getEffectiveSpaceFlow().first() ?: "_all_"
        val name = "Shelf ${UUID.randomUUID()}"
        val shelfId = library.createShelfWithBooks(spaceId, name, listOf(book.book.id))
        try {
            val openedShelf = AtomicReference<Triple<String, String, Int>>()
            val openedReader = AtomicReference<String>()
            compose.runOnUiThread {
                compose.activity.setContent {
                    AppTheme {
                        BookInfoScreen(
                            bookId = book.book.id,
                            onNavigateToReader = { id, _ -> openedReader.set(id) },
                            onNavigateBack = {},
                            onNavigateToEdit = {},
                            onNavigateToShelf = { id, title, count -> openedShelf.set(Triple(id, title, count)) },
                            onNavigateToTag = {},
                            onNavigateToOrganize = {},
                        )
                    }
                }
            }
            val tab = app.getString(R.string.library_tab_shelves)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(tab).fetchSemanticsNodes().size == 1 }
            compose.onNodeWithText(tab).performScrollTo().performClick()
            compose.onNodeWithText(name).performScrollTo().performClick()
            assertEquals(Triple(shelfId, name, 1), openedShelf.get())
            compose.onAllNodesWithText(book.book.title).onLast().performScrollTo().performClick()
            assertEquals(book.book.id, openedReader.get())
            library.setBooksInShelf(shelfId, listOf(book.book.id), false)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(tab).fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithText(tab).assertDoesNotExist()
            // Adding membership again must restore the tab without recreating book info.
            val replacement = library.createShelfWithBooks(spaceId, name, listOf(book.book.id))
            try {
                compose.waitUntil(10_000) { compose.onAllNodesWithText(tab).fetchSemanticsNodes().size == 1 }
                compose.onNodeWithText(tab).performScrollTo().performClick()
                compose.onNodeWithText(name).assertExists()
            } finally {
                library.deleteShelf(replacement)
            }
        } finally {
            library.deleteShelf(shelfId)
            library.deleteBook(book.book.id)
            source.delete()
        }
    }
}
