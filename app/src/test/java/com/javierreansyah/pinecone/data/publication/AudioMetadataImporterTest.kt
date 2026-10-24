package com.javierreansyah.pinecone.data.publication

import com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioMetadataImporterTest {
    private fun base() = ImportedMetadata(PublicationMetadata(), emptyList())

    @Test
    fun embeddedTagsPopulateStandaloneAudioMetadata() {
        val result = AudioTags(
            title = " Hamlet: Act 1, Scene 1 ", artist = " William Shakespeare ",
            genre = "Books & Spoken", year = "1901", durationSeconds = 542.25,
        ).merge("hamlet", base())
        assertEquals("Hamlet: Act 1, Scene 1", result.title)
        assertEquals(
            listOf(PublicationCreditEntity("hamlet", "author", 0, "William Shakespeare")),
            result.credits
        )
        assertEquals(listOf("Books & Spoken"), result.subjects)
        assertEquals("1901", result.metadata.published)
        assertEquals(542.25, result.metadata.duration)
    }

    @Test
    fun explicitWriterTakesPrecedenceOverPerformingArtist() {
        val result = AudioTags(writer = "Author", artist = "Performer").merge("book", base())
        assertEquals("Author", result.credits.single().name)
    }

    @Test
    fun existingPublicationMetadataIsPreserved() {
        val original = base().copy(
            title = "Manifest title",
            credits = listOf(PublicationCreditEntity("book", "author", 0, "Manifest author")),
            subjects = listOf("Spoken"),
            metadata = PublicationMetadata(published = "2020-01-01", duration = 100.0),
        )
        assertEquals(
            original,
            AudioTags(
                title = "Track title",
                artist = "Artist",
                genre = "Spoken",
                year = "1999",
                durationSeconds = 5.0
            )
                .merge("book", original),
        )
    }

    @Test
    fun absentTagsDoNotInventAnAuthorOrDuration() {
        assertEquals(
            base(),
            AudioTags(title = " ", artist = "", durationSeconds = Double.NaN).merge("book", base())
        )
        assertEquals(base(), AudioTags(durationSeconds = -1.0).merge("book", base()))
    }
}
