package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.model.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class PublicationWordingTest {
    @Test
    fun `only nonempty EPUB collections use books`() {
        assertEquals(
            PublicationWording.Books,
            publicationWording(listOf(FileFilter.EPUB, FileFilter.EPUB))
        )
        assertEquals(PublicationWording.Items, publicationWording(emptyList()))
        FileFilter.entries.filterNot { it == FileFilter.EPUB }.forEach { format ->
            assertEquals(format.name, PublicationWording.Items, publicationWording(listOf(format)))
            assertEquals(
                format.name,
                PublicationWording.Items,
                publicationWording(listOf(FileFilter.EPUB, format))
            )
        }
    }

    @Test
    fun `generated EPUB rendition does not turn a PDF into a book`() {
        val pdf = item("source.pdf").copy(activeRendition = "read-mode")
        assertEquals(PublicationWording.Items, pdf.publicationWording)
        assertEquals(PublicationWording.Books, item("source.EPUB").publicationWording)
    }

    @Test
    fun `authors tags and spaces use only their own members`() {
        val novel = item("novel.epub").copy(authors = listOf("Novelist"), tags = listOf("Fiction"))
        val comic = item("comic.cbz").copy(authors = listOf("Artist"), tags = listOf("Comics"))
        val library = listOf(novel, comic)
        assertEquals(
            PublicationWording.Books,
            library.inNamedCollection("author", "Novelist").publicationWording()
        )
        assertEquals(
            PublicationWording.Items,
            library.inNamedCollection("author", "Artist").publicationWording()
        )
        assertEquals(
            PublicationWording.Books,
            library.inNamedCollection("tag", "Fiction").publicationWording()
        )
        assertEquals(
            PublicationWording.Items,
            library.inNamedCollection("tag", "Comics").publicationWording()
        )
        assertEquals(
            PublicationWording.Items,
            library.inNamedCollection("tag", "Empty").publicationWording()
        )
    }

    @Test
    fun `global label actions include other spaces even when visible counts contain only books`() {
        val reading = SpaceEntity("reading", "Reading")
        val audio = SpaceEntity("audio", "Audio")
        val library = listOf(
            item("novel.epub").copy(authors = listOf("Author"), spaces = listOf(reading)),
            item("narration.m4b").copy(authors = listOf("Author"), spaces = listOf(audio)),
        )
        assertEquals(PublicationWording.Books, library.inSpace(reading.id).publicationWording())
        assertEquals(
            PublicationWording.Items,
            library.inNamedCollection("author", "Author").publicationWording()
        )
        assertEquals(
            PublicationWording.Books,
            library.inNamedCollection("space", "Reading").publicationWording()
        )
        assertEquals(
            PublicationWording.Items,
            library.inNamedCollection("space", "Audio").publicationWording()
        )
    }

    @Test
    fun `combined selections retain book wording only when every collection does`() {
        assertEquals(
            PublicationWording.Books,
            listOf(PublicationWording.Books, PublicationWording.Books).combinedWording()
        )
        assertEquals(
            PublicationWording.Items,
            listOf(PublicationWording.Books, PublicationWording.Items).combinedWording()
        )
        assertEquals(PublicationWording.Items, emptyList<PublicationWording>().combinedWording())
    }

    private fun item(filename: String) = Book(
        id = filename, title = filename, authors = emptyList(), coverPath = null,
        progress = 0.0, lastOpened = null, language = null, addedDate = 0L,
        isArchived = false, originalFilename = filename, filePath = "/assets/hash",
    )
}
