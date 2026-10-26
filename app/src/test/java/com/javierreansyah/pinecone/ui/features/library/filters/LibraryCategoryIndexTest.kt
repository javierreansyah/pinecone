package com.javierreansyah.pinecone.ui.features.library.filters

import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.LibrarySpace
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCategoryIndexTest {
    @Test
    fun duplicateLabelsCountEachBookOnceAndSpaceActionsRetainInboxMembership() {
        val work = LibrarySpace("work", "Same name")
        val other = LibrarySpace("other", "Same name")
        val book = Book(
            "book", "Title", listOf("Author", "Author"), null, 0.0, null, null, 0L, false,
            tags = listOf("Tag", "Tag"), spaces = listOf(work, other)
        )
        val inbox = book.copy(id = "inbox", isUnsorted = true)
        val index = LibraryCategoryIndex(listOf(book, inbox))
        assertEquals(listOf(book, inbox), index.authors["Author"])
        assertEquals(listOf(book, inbox), index.tags["Tag"])
        assertEquals(listOf(book), index.spaces["work"])
        assertEquals(listOf(book, inbox), index.spaceNames["Same name"])
    }
}
