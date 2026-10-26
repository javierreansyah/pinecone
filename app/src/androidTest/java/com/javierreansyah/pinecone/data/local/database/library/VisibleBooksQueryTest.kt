package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.filterAndSort
import com.javierreansyah.pinecone.ui.features.library.inSpace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import timber.log.Timber
import kotlin.time.measureTimedValue

class VisibleBooksQueryTest {
    @Test
    fun queryMatchesExistingSpaceFilteringAndSortingForLargeCollections() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            database.spaceDao()
                .insertAllSpaces(listOf(SpaceEntity("_all_", "All"), SpaceEntity("work", "Work")))
            val books = List(5_000) { index ->
                BookEntity(
                    id = "book-$index",
                    title = "Title ${index % 37}",
                    filePath = "/book-$index.epub",
                    addedDate = index.toLong(),
                    lastReadDate = (index % 100).toLong(),
                    isArchived = index % 13 == 0,
                    isUnsorted = index % 17 == 0,
                    metadataJson = """{"sortTitle":"Sort ${index % 23}"}""",
                )
            }
            database.bookDao().insertAll(books)
            database.spaceDao().insertAllBookSpaceCrossRefs(
                books.flatMapIndexed { index, book ->
                    buildList {
                        add(BookSpaceCrossRef(book.id, "_all_"))
                        if (index % 10 == 0) add(BookSpaceCrossRef(book.id, "work"))
                    }
                },
            )
            val baseline = measureTimedValue {
                database.bookDao().getAllBooks().first().map(Book::fromEntity)
            }
            for (space in listOf(null, "_all_", "work", "missing")) {
                val optimized = measureTimedValue {
                    database.bookDao().getVisibleBooks(space).first().map(Book::fromEntity)
                }
                val expected = baseline.value.inSpace(space)
                assertEquals(expected, optimized.value)
                for (sort in SortType.entries) {
                    val preferences = FilterSortPreferences(sortType = sort)
                    assertEquals(
                        expected.filterAndSort(preferences),
                        optimized.value.filterAndSort(preferences)
                    )
                }
                Timber.i(
                    "VisibleBooksQuery: space=%s, baseline=%s (%d rows), query=%s (%d rows)",
                    space,
                    baseline.duration,
                    baseline.value.size,
                    optimized.duration,
                    optimized.value.size,
                )
            }
        } finally {
            database.close()
        }
    }
}
