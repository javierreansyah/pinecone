package com.javierreansyah.pinecone.ui.features.library.main

import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.publication.PublicationMetadata
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchTest {
    private val books = listOf(
        book("second", "Two").copy(authors = listOf("Javier", "Ana"), tags = listOf("Science")),
        book("first", "One").copy(
            authors = listOf("Ana"), tags = listOf("Science", "History"),
            metadata = PublicationMetadata(subtitle = "Hidden galaxies"),
        ),
    )
    private val shelves = listOf(ShelfEntity("s", "_all_", "Galaxies"))

    @Test
    fun blankQueriesPreserveSourceOrderAndDistinctNames() {
        for (query in listOf("", "  ", "\t")) {
            val result = searchLibrary(books, shelves, query, SearchCategory.All)
            assertEquals(books, result.books)
            assertEquals(shelves, result.shelves)
            assertEquals(listOf("Javier", "Ana"), result.authors)
            assertEquals(listOf("Science", "History"), result.tags)
        }
    }

    @Test
    fun metadataAndNamesAreMatchedWithoutChangingQueryOrWording() {
        val result = searchLibrary(books, shelves, "GALAXIES", SearchCategory.All)
        assertEquals(listOf(books[1]), result.books)
        assertEquals(shelves, result.shelves)
        assertEquals("GALAXIES", result.query)
        assertEquals(books.publicationWording(), result.contentWording)
        assertTrue(result.authors.isEmpty())
        assertTrue(result.tags.isEmpty())
    }

    @Test
    fun eachCategoryIncludesOnlyItsOwnMatches() {
        for (category in SearchCategory.entries.filter { it != SearchCategory.All }) {
            val result = searchLibrary(books, shelves, "", category)
            assertEquals(category == SearchCategory.Books, result.books.isNotEmpty())
            assertEquals(category == SearchCategory.Shelves, result.shelves.isNotEmpty())
            assertEquals(category == SearchCategory.Authors, result.authors.isNotEmpty())
            assertEquals(category == SearchCategory.Tags, result.tags.isNotEmpty())
        }
        assertEquals(
            listOf("Ana"),
            searchLibrary(books, shelves, "ana", SearchCategory.Authors).authors
        )
        assertEquals(
            listOf("History"),
            searchLibrary(books, shelves, "HIST", SearchCategory.Tags).tags
        )
    }

    private fun book(id: String, title: String) = Book(
        id = id, title = title, authors = emptyList(), coverPath = null, progress = 0.0,
        lastOpened = null, language = null, addedDate = 0, isArchived = false,
    )
}
