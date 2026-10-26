package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShelfMembershipQueryTest {
    @Test
    fun membershipsFollowSpaceAndLiveChangesWithoutLosingShelfContents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val changes = Channel<List<ShelfWithCovers>>(Channel.UNLIMITED)
        try {
            db.spaceDao()
                .insertAllSpaces(listOf(SpaceEntity("work", "Work"), SpaceEntity("home", "Home")))
            db.bookDao().insertAll(
                listOf(
                    BookEntity(
                        id = "title",
                        title = "Title",
                        filePath = "/title.epub",
                        isArchived = true
                    ),
                    BookEntity(id = "other", title = "Other", filePath = "/other.epub"),
                )
            )
            val work = ShelfEntity("work-shelf", "work", "Work shelf")
            val home = ShelfEntity("home-shelf", "home", "Home shelf")
            val dao = db.shelfDao()
            dao.insertShelf(work)
            dao.insertShelf(home)
            dao.addShelfRefs(
                listOf(
                    ShelfBookCrossRefEntity(work.id, "title"),
                    ShelfBookCrossRefEntity(work.id, "other"),
                    ShelfBookCrossRefEntity(home.id, "title"),
                )
            )
            assertEquals(
                setOf(work.id, home.id),
                dao.getShelvesForBook("title", null).first().map { it.shelf.id }.toSet()
            )
            assertEquals(
                listOf(home.id),
                dao.getShelvesForBook("title", "home").first().map { it.shelf.id })
            assertTrue(dao.getShelvesForBook("title", "missing").first().isEmpty())

            val observer = launch {
                dao.getShelvesForBook("title", "work").collect { changes.send(it) }
            }
            try {
                val initial = withTimeout(5_000) { changes.receive() }.single()
                assertEquals(work.id, initial.shelf.id)
                assertEquals(setOf("title", "other"), initial.books.map { it.book.id }.toSet())
                dao.updateShelf(work.copy(name = "Renamed"))
                withTimeout(5_000) {
                    while (changes.receive()
                            .single().shelf.name != "Renamed"
                    ) { /* Await invalidation. */
                    }
                }
                dao.removeShelfMembers(work.id, listOf("title"))
                withTimeout(5_000) {
                    while (changes.receive().isNotEmpty()) { /* Await membership removal. */
                    }
                }
                dao.addShelfRefs(listOf(ShelfBookCrossRefEntity(work.id, "title")))
                withTimeout(5_000) {
                    while (changes.receive().isEmpty()) { /* Await membership addition. */
                    }
                }
                dao.deleteShelf(work)
                withTimeout(5_000) {
                    while (changes.receive().isNotEmpty()) { /* Await shelf deletion. */
                    }
                }
            } finally {
                observer.cancel()
                observer.join()
            }
        } finally {
            changes.close()
            db.close()
        }
    }
}
