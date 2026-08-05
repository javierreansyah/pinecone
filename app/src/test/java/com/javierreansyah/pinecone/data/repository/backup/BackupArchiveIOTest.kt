package com.javierreansyah.pinecone.data.repository.backup

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveIOTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test(expected = IllegalArgumentException::class)
    fun extractRejectsTraversal() {
        val bytes = zip("../outside.txt", "bad".toByteArray())
        BackupArchiveIO.extract(
            ByteArrayInputStream(bytes),
            temporaryFolder.newFolder("extract")
        ) { true }
    }

    @Test(expected = IllegalArgumentException::class)
    fun extractRejectsUnexpectedEntry() {
        val bytes = zip("executable.bin", byteArrayOf(1, 2, 3))
        BackupArchiveIO.extract(
            ByteArrayInputStream(bytes),
            temporaryFolder.newFolder("unexpected")
        ) { it == "library.json" }
    }

    @Test
    fun descriptorAndVerifyRoundTrip() {
        val root = temporaryFolder.newFolder("verify")
        val file = File(root, "library.json").apply { writeText("{\"books\":[]}") }
        val descriptor = BackupArchiveIO.descriptor("library.json", file)
        BackupArchiveIO.verify(root, listOf(descriptor))
        assertEquals(file.length(), descriptor.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun verifyDetectsTampering() {
        val root = temporaryFolder.newFolder("tamper")
        val file = File(root, "library.json").apply { writeText("original") }
        val descriptor = BackupArchiveIO.descriptor("library.json", file)
        file.writeText("modified")
        BackupArchiveIO.verify(root, listOf(descriptor))
    }

    private fun zip(path: String, bytes: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry(path))
            zip.write(bytes)
            zip.closeEntry()
        }
        return output.toByteArray()
    }
}
