package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryBackupPayloadTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun vaultSnapshotV1RoundTripsWithoutLegacyDefaults() {
        val payload = LibraryBackupPayload(
            books = listOf(
                BookBackupRecord(
                    id = "book-1", title = "Book", filePath = "/source/book.epub", addedDate = 1
                )
            ),
            bookmarks = emptyList(), shelves = emptyList(), shelfBookCrossRefs = emptyList(),
            notes = emptyList()
        )
        val objectInfo = VaultObject(
            kind = VaultObjectKind.BOOK,
            sha256 = "a".repeat(64),
            size = 10,
            crc32 = 12,
            storedSize = 10,
            storedCrc32 = 12,
            fileName = "${"a".repeat(64)}.epub",
            sourcePath = "/source/book.epub",
            sourceSize = 10,
            sourceLastModified = 20
        )
        val snapshot = VaultSnapshot(
            id = "20260805_120000_000",
            createdAt = 1,
            backupKind = "manual",
            appVersion = "1.0",
            databaseRevision = 2,
            stateFingerprint = "b".repeat(64),
            recordCounts = mapOf("books" to 1),
            library = payload,
            settings = ReaderSettings(),
            books = listOf(BookObjectReference("book-1", objectInfo)),
            dictionaries = emptyList(),
            activeDictionaryId = ""
        )

        assertEquals(snapshot, json.decodeFromString<VaultSnapshot>(json.encodeToString(snapshot)))
    }

    @Test
    fun backupRecordConvertsToCurrentEntity() {
        val record = BookBackupRecord(
            id = "id", title = "Title", filePath = "/new/book.epub", addedDate = 10
        )
        assertEquals(record.filePath, record.toEntity().filePath)

        val shelfRecord = ShelfBackupRecord(
            id = "shelf-1", spaceId = "space-1", name = "Favorites", createdAt = 100L
        )
        val entity = shelfRecord.toEntity()
        assertEquals("shelf-1", entity.id)
        assertEquals("space-1", entity.spaceId)
        assertEquals("Favorites", entity.name)
        assertEquals(shelfRecord, entity.toBackupRecord())
    }
}
