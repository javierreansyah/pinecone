package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.data.model.fromEntity
import com.javierreansyah.pinecone.ui.features.library.shelf.ShelfDetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCommonTest {
    private val all = SpaceEntity(ALL_SPACES_ID, "All")
    private val work = SpaceEntity("work", "Work")
    private val leisure = SpaceEntity("leisure", "Leisure")
    private val books = listOf(
        book("work-only", listOf(all, work)),
        book("both", listOf(all, work, leisure)),
        book("all-only", listOf(all)),
        book("inbox", listOf(all)).copy(isUnsorted = true),
    )

    @Test
    fun `specific space keeps only its organized books`() {
        assertEquals(listOf("work-only", "both"), books.inSpace(work.id).map { it.id })
    }

    @Test
    fun `All aggregates organized books once and excludes the inbox despite All membership`() {
        val expected = listOf("work-only", "both", "all-only")
        assertEquals(expected, books.inSpace(ALL_SPACES_ID).map { it.id })
        assertEquals(expected, books.inSpace(null).map { it.id })
    }

    @Test
    fun `removing the last custom space does not return an organized book to the inbox`() {
        val removedFromWork = books.first().copy(
            spaces = listOf(
                com.javierreansyah.pinecone.data.model.LibrarySpace(
                    all.id,
                    all.name,
                    all.createdAt
                ),
            )
        )
        assertEquals(listOf(removedFromWork), listOf(removedFromWork).inSpace(ALL_SPACES_ID))
        assertTrue(listOf(removedFromWork).inSpace(work.id).isEmpty())
    }

    @Test
    fun `format selection is multiselect and combines with reading status`() {
        val epub = book("epub").copy(mediaType = "application/epub+zip")
        val pdf = book("pdf").copy(mediaType = "application/pdf", isRead = true)
        val audio = book("audio").copy(mediaType = "audio/mpeg")
        val formats =
            FilterSortPreferences(selectedFormats = setOf(FileFilter.EPUB, FileFilter.PDF))
        assertEquals(
            setOf("epub", "pdf"),
            listOf(epub, pdf, audio).filterAndSort(formats).map { it.id }.toSet()
        )
        assertEquals(
            listOf(pdf),
            listOf(
                epub,
                pdf,
                audio
            ).filterAndSort(formats.copy(selectedStatus = setOf(StatusFilter.Finished)))
        )
        assertTrue(
            listOf(
                epub,
                pdf,
                audio
            ).filterAndSort(formats.copy(selectedFormats = emptySet())).isEmpty()
        )
    }

    @Test
    fun `formats recognize source filenames when imported assets have generic names`() {
        val cases = mapOf(
            "BOOK.EPUB" to FileFilter.EPUB, "book.pdf" to FileFilter.PDF,
            "comic.cbz" to FileFilter.Comics, "comic.divina" to FileFilter.Comics,
            "book.m4b" to FileFilter.Audio, "page.png" to FileFilter.Images,
            "site.webpub" to FileFilter.WebPublication, "unknown.bin" to FileFilter.Other,
        )
        cases.forEach { (filename, expected) ->
            assertEquals(
                filename,
                expected,
                book("id").copy(originalFilename = filename, filePath = "/assets/hash.zip")
                    .fileFormat()
            )
        }
    }

    @Test
    fun `All references original shelves and preserves custom book order`() {
        val first = details("first", "application/pdf")
        val second = details("second", "application/epub+zip")
        val a = ShelfWithCovers(ShelfEntity("a", work.id, "Favorites"), listOf(first, second))
        val b = ShelfWithCovers(ShelfEntity("b", leisure.id, "Favorites"), listOf(second))
        val refs = listOf(
            ShelfBookCrossRefEntity("a", "first", orderIndex = 1),
            ShelfBookCrossRefEntity("a", "second"),
            ShelfBookCrossRefEntity("b", "second"),
        )
        val result = sortShelfBooks(listOf(a, b), refs, ALL_SPACES_ID)
        assertEquals(listOf(a.shelf, b.shelf), result.map { it.shelf })
        assertEquals(listOf("second", "first"), result.first().books.map { it.book.id })
        assertEquals(listOf("a"), sortShelfBooks(listOf(a, b), refs, work.id).map { it.shelf.id })
    }

    @Test
    fun `shelf format filters hide unmatched shelves and previews without making books unshelved`() {
        val pdf = details("pdf", "application/pdf")
        val epub = details("epub", "application/epub+zip")
        val mixed = ShelfWithCovers(ShelfEntity("mixed", work.id, "Mixed"), listOf(pdf, epub))
        val epubOnly = ShelfWithCovers(ShelfEntity("epub-only", work.id, "EPUB"), listOf(epub))
        val empty = ShelfWithCovers(ShelfEntity("empty", work.id, "Empty"), emptyList())
        val refs = listOf(
            ShelfBookCrossRefEntity("mixed", "pdf"),
            ShelfBookCrossRefEntity("mixed", "epub"),
            ShelfBookCrossRefEntity("epub-only", "epub")
        )
        val shelves = listOf(mixed, epubOnly, empty)
        val prefs = FilterSortPreferences(selectedFormats = setOf(FileFilter.PDF))
        val result =
            mapAndSortShelves(shelves, refs, listOf(pdf, epub), prefs, "Unshelved", ALL_SPACES_ID)
        assertEquals(listOf("mixed"), result.map { it.shelf.id })
        assertEquals(listOf("pdf"), result.single().books.map { it.book.id })
        assertEquals(
            2,
            mapAndSortShelves(
                shelves,
                refs,
                listOf(pdf, epub),
                FilterSortPreferences(),
                "Unshelved",
                ALL_SPACES_ID
            ).size
        )
        assertEquals(2, mixed.books.size)
    }

    @Test
    fun `shelves with no visible books stay hidden even with all formats selected`() {
        val inbox = details("inbox", "application/pdf").let {
            it.copy(book = it.book.copy(isUnsorted = true))
        }
        val outsideSpace = details("outside", "application/pdf").copy(spaces = listOf(all, leisure))
        val shelves = listOf(
            ShelfWithCovers(ShelfEntity("empty", work.id, "Empty"), emptyList()),
            ShelfWithCovers(ShelfEntity("inbox", work.id, "Inbox"), listOf(inbox)),
            ShelfWithCovers(ShelfEntity("outside", work.id, "Outside"), listOf(outsideSpace)),
        )

        assertTrue(
            mapAndSortShelves(
                shelves, emptyList(), emptyList(), FilterSortPreferences(), "Unshelved", work.id
            ).isEmpty()
        )
        assertEquals(1, shelves[1].books.size)
        assertEquals(1, shelves[2].books.size)
    }

    @Test
    fun `deselecting all formats hides shelves and unshelved without changing attachments`() {
        val pdf = details("pdf", "application/pdf")
        val shelf = ShelfWithCovers(ShelfEntity("shelf", work.id, "Shelf"), listOf(pdf))
        val refs = listOf(ShelfBookCrossRefEntity("shelf", "pdf"))
        val unshelved = details("unshelved-book", "application/pdf")
        assertTrue(
            mapAndSortShelves(
                listOf(shelf), refs, listOf(pdf, unshelved),
                FilterSortPreferences(selectedFormats = emptySet()), "Unshelved", ALL_SPACES_ID
            ).isEmpty()
        )
        assertEquals(listOf(pdf), shelf.books)
        assertEquals(1, refs.size)
    }

    @Test
    fun `unshelved includes All owned books and excludes inbox books`() {
        val organized = details("organized", "application/pdf")
        val inbox = details(
            "inbox",
            "application/pdf"
        ).let { it.copy(book = it.book.copy(isUnsorted = true)) }
        val result = mapAndSortShelves(
            emptyList(),
            emptyList(),
            listOf(organized, inbox),
            FilterSortPreferences(),
            "Unshelved",
            ALL_SPACES_ID
        )
        assertEquals("unshelved", result.single().shelf.id)
        assertEquals(listOf("organized"), result.single().books.map { it.book.id })
    }

    @Test
    fun `default shelf order shows earlier additions first when legacy positions are equal`() {
        val first = details("z-first", "application/pdf")
        val second = details("a-second", "application/pdf")
        val shelf = ShelfWithCovers(ShelfEntity("shelf", work.id, "Shelf"), listOf(second, first))
        val refs = listOf(
            ShelfBookCrossRefEntity("shelf", second.book.id, addedAt = 200L),
            ShelfBookCrossRefEntity("shelf", first.book.id, addedAt = 100L),
        )
        val sorted = sortShelfBooks(listOf(shelf), refs).single().books.map { Book.fromEntity(it) }
        val prefs = ShelfDetailUiState().bookPreferences

        assertEquals(
            listOf(first.book.id, second.book.id),
            sorted.filterAndSort(prefs).map { it.id })
        assertEquals(
            listOf(second.book.id, first.book.id),
            sorted.filterAndSort(prefs.selectSort(SortType.Custom)).map { it.id },
        )
    }

    @Test
    fun `appended book follows custom positions even when imported earlier`() {
        val first = details("first", "application/pdf").let {
            it.copy(book = it.book.copy(addedDate = 300L))
        }
        val second = details("second", "application/pdf")
        val appended = details("appended", "application/pdf").let {
            it.copy(book = it.book.copy(addedDate = 1L))
        }
        val shelf = ShelfWithCovers(
            ShelfEntity("shelf", work.id, "Shelf"), listOf(appended, first, second),
        )
        val refs = listOf(
            ShelfBookCrossRefEntity("shelf", first.book.id, addedAt = 100L, orderIndex = 5),
            ShelfBookCrossRefEntity("shelf", second.book.id, addedAt = 50L, orderIndex = 2),
            ShelfBookCrossRefEntity("shelf", appended.book.id, addedAt = 200L, orderIndex = 6),
        )
        val sorted = sortShelfBooks(listOf(shelf), refs).single().books.map { Book.fromEntity(it) }

        assertEquals(
            listOf("second", "first", "appended"),
            sorted.filterAndSort(ShelfDetailUiState().bookPreferences).map { it.id },
        )
    }

    private fun details(id: String, mime: String) = BookWithDetails(
        BookEntity(id = id, title = id, filePath = "/$id", mediaType = mime),
        emptyList(), emptyList(), emptyList(), listOf(all, work, leisure),
    )

    private fun book(id: String, spaces: List<SpaceEntity> = emptyList()) = Book(
        id = id, title = id, authors = emptyList(), coverPath = null, progress = 0.0,
        lastOpened = null, language = null, addedDate = 0L, isArchived = false,
        spaces = spaces.map {
            com.javierreansyah.pinecone.data.model.LibrarySpace(
                it.id,
                it.name,
                it.createdAt
            )
        },
    )
}
