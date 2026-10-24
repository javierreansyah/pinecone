package com.javierreansyah.pinecone.ui.features.library.info

import com.javierreansyah.pinecone.data.local.database.library.PublicationCreditEntity
import com.javierreansyah.pinecone.data.model.Book
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetadataDisplayTest {
    @Test
    fun mergedCreditsHaveOnePublisherAndAuthorsFirst() {
        val book = book().copy(
            authors = listOf("Author", " Author ", "N/A"),
            publisher = "Press A, Press B",
            credits = listOf(
                credit("narrator", "Narrator"), credit("author", "Author"),
                credit("publisher", "Press A"), credit("publisher", "Press B", 1),
                credit("publisher", " press a ", 2), credit("editor", "N/A"),
            ),
        )
        assertEquals(
            listOf(
                "author" to listOf("Author"),
                "publisher" to listOf("Press A", "Press B"),
                "narrator" to listOf("Narrator"),
            ),
            metadataCredits(book),
        )
    }

    @Test
    fun richCreditsAreShownWhenLegacyFieldsAreEmpty() {
        assertEquals(
            listOf("author" to listOf("Writer"), "publisher" to listOf("Press")),
            metadataCredits(
                book().copy(
                    credits = listOf(
                        credit("publisher", "Press"),
                        credit("author", "Writer")
                    )
                )
            ),
        )
        assertEquals(emptyList<Pair<String, List<String>>>(), metadataCredits(book()))
    }

    @Test
    fun missingMetadataIsHiddenAndUsefulValuesAreTrimmed() {
        listOf(null, "", " ", "N/A", " n/a ", "NA", "Not available", "null").forEach {
            assertNull(metadataValue(it))
        }
        assertEquals("Example", metadataValue(" Example "))
        assertEquals(
            listOf("English"),
            cleanMetadataValues(listOf(" English ", "english", "", "N/A"))
        )
    }

    private fun credit(role: String, name: String, position: Int = 0) =
        PublicationCreditEntity("test", role, position, name)

    private fun book() = Book(
        id = "test", title = "Test", authors = emptyList(), coverPath = null,
        progress = 0.0, lastOpened = null, language = null, addedDate = 0L, isArchived = false,
    )
}
