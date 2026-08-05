package com.javierreansyah.pinecone.data.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryBackupPayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun legacyPayloadWithoutSpacesStillDecodes() {
        val source = """
            {
              "version": 1,
              "books": [{
                "id": "book-1",
                "title": "Legacy",
                "filePath": "/old/device/book.epub",
                "addedDate": 123,
                "collectionId": "removed-column"
              }],
              "bookmarks": [],
              "shelves": [],
              "shelfBookCrossRefs": [],
              "notes": [],
              "authors": [],
              "tags": [],
              "bookAuthorCrossRefs": [],
              "bookTagCrossRefs": []
            }
        """.trimIndent()

        val payload = json.decodeFromString<LibraryBackupPayload>(source)

        assertEquals("book-1", payload.books.single().id)
        assertTrue(payload.spaces.isEmpty())
        assertTrue(payload.bookSpaceCrossRefs.isEmpty())
    }

    @Test
    fun backupBookConvertsToCurrentEntityWithoutArchiveCoupling() {
        val record = BookBackupRecord(
            id = "id", title = "Title", filePath = "/new/book.epub", addedDate = 10
        )
        val entity = record.toEntity()
        assertEquals(record.id, entity.id)
        assertEquals(record.filePath, entity.filePath)
    }
}
