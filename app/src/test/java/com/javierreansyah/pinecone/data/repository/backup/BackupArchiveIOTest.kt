package com.javierreansyah.pinecone.data.repository.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32

class BackupArchiveIOTest {
    @Test
    fun copyCalculatesDigestAndCrcInSinglePass() {
        val bytes = "pinecone-object".repeat(1_000).toByteArray()
        val output = ByteArrayOutputStream()
        val result = BackupArchiveIO.copyAndDigest(ByteArrayInputStream(bytes), output)
        val crc = CRC32().apply { update(bytes) }

        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(bytes.size.toLong(), result.size)
        assertEquals(crc.value, result.crc32)
        assertEquals(64, result.sha256.length)
    }

    @Test
    fun archivePathsRejectTraversalAndAbsoluteNames() {
        assertTrue(BackupArchiveIO.safeArchivePath("objects/books/hash.epub"))
        assertFalse(BackupArchiveIO.safeArchivePath("../outside"))
        assertFalse(BackupArchiveIO.safeArchivePath("objects/../outside"))
        assertFalse(BackupArchiveIO.safeArchivePath("/absolute"))
        assertFalse(BackupArchiveIO.safeArchivePath("objects//book"))
    }
}
