package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.model.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCommonTest {
    private val work = SpaceEntity(id = "work", name = "Work")
    private val leisure = SpaceEntity(id = "leisure", name = "Leisure")
    private val books = listOf(
        book(id = "work-only", spaces = listOf(work)),
        book(id = "both", spaces = listOf(work, leisure)),
        book(id = "no-space")
    )

    @Test
    fun `specific space keeps only books assigned to it`() {
        assertEquals(listOf("work-only", "both"), books.inSpace(work.id).map { it.id })
    }

    @Test
    fun `all spaces and unset selection keep only books assigned to at least one space`() {
        assertEquals(listOf("work-only", "both"), books.inSpace(ALL_SPACES_ID).map { it.id })
        assertEquals(listOf("work-only", "both"), books.inSpace(null).map { it.id })
    }

    @Test
    fun `unsorted books are correctly identified when book has no spaces`() {
        val hasUnsorted = books.any { it.spaces.isEmpty() }
        assertEquals(true, hasUnsorted)

        val allSortedBooks = listOf(
            book(id = "work-only", spaces = listOf(work)),
            book(id = "both", spaces = listOf(work, leisure))
        )
        assertEquals(false, allSortedBooks.any { it.spaces.isEmpty() })
    }

    private fun book(id: String, spaces: List<SpaceEntity> = emptyList()) = Book(
        id = id,
        title = id,
        authors = emptyList(),
        coverPath = null,
        progress = 0.0,
        lastOpened = null,
        language = null,
        addedDate = 0L,
        isArchived = false,
        spaces = spaces
    )
}
