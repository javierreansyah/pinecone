package com.javierreansyah.pinecone.ui.features.library.organize

import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.local.database.library.AppDatabase
import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookSpaceCrossRef
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.data.repository.library.PublicationAssetStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class OrganizeViewModelTest {
    @Test
    fun membershipsKeepArchiveInboxAndHiddenSpaceRulesWithoutLoadingBookDetails() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = ReaderPreferences(context)
        val previous = preferences.readerSettings.first()
        val queries = CopyOnWriteArrayList<String>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryCallback({ sql, _ -> queries += sql }, { it.run() }).build()
        val store = ViewModelStore()
        try {
            db.spaceDao()
                .insertAllSpaces(listOf(SpaceEntity("_all_", "All"), SpaceEntity("work", "Work")))
            db.bookDao().insertAll(
                listOf(
                    BookEntity("visible", "Visible", filePath = "/visible"),
                    BookEntity("inbox", "Inbox", filePath = "/inbox", isUnsorted = true),
                    BookEntity("archived", "Archived", filePath = "/archived", isArchived = true),
                )
            )
            val ids = listOf("visible", "inbox", "archived")
            db.spaceDao().insertAllBookSpaceCrossRefs(ids.flatMap { id ->
                listOf(BookSpaceCrossRef(id, "_all_"), BookSpaceCrossRef(id, "work"))
            })
            db.shelfDao().insertShelf(ShelfEntity("shelf", "work", "Shelf"))
            db.shelfDao().addShelfRefs(ids.map { ShelfBookCrossRefEntity("shelf", it) })
            preferences.updateSettings { it.copy(spacesEnabled = true) }
            queries.clear()
            val repository = LibraryRepository(
                context, db, db.bookDao(), db.shelfDao(), db.spaceDao(),
                PublicationAssetStore(context), Dispatchers.IO
            )
            val model = withContext(Dispatchers.Main) {
                ViewModelProvider.create(store, viewModelFactory {
                    initializer {
                        OrganizeViewModel(
                            ids,
                            repository,
                            preferences,
                            Dispatchers.Default
                        )
                    }
                })[OrganizeViewModel::class]
            }
            val enabled =
                withTimeout(5000) { model.uiState.first { !it.isLoading && it.spacesEnabled } }
            assertEquals(
                ToggleableState.Indeterminate,
                enabled.spaces.first { it.space.id == "_all_" }.state
            )
            val work = enabled.spaces.first { it.space.id == "work" }
            assertEquals(ToggleableState.On, work.state)
            assertEquals(ToggleableState.Indeterminate, work.shelves.single().state)
            preferences.updateSettings { it.copy(spacesEnabled = false) }
            val disabled =
                withTimeout(5000) { model.uiState.first { !it.isLoading && !it.spacesEnabled } }
            assertEquals("_all_", disabled.spaces.single().space.id)
            assertEquals(
                ToggleableState.Indeterminate,
                disabled.spaces.single().shelves.single().state
            )
            assertTrue(queries.none { sql ->
                sql.startsWith("SELECT", true) &&
                        Regex("\\b(authors|tags|publication_credits|book_author_cross_ref|book_tag_cross_ref)\\b").containsMatchIn(
                            sql
                        )
            })
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            preferences.updateAllSettings(previous)
            db.close()
        }
    }
}
