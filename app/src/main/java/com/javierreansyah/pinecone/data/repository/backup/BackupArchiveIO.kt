package com.javierreansyah.pinecone.data.repository.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.CRC32

internal data class StreamDigest(val size: Long, val sha256: String, val crc32: Long)

internal object BackupArchiveIO {
    const val MAX_ENTRY_BYTES = 4L * 1024 * 1024 * 1024
    const val MAX_ARCHIVE_BYTES = 16L * 1024 * 1024 * 1024

    fun copyAndDigest(input: InputStream, output: OutputStream? = null): StreamDigest {
        val digest = MessageDigest.getInstance("SHA-256")
        val crc = CRC32()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
        var size = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            size += count
            require(size <= MAX_ENTRY_BYTES)
            digest.update(buffer, 0, count)
            crc.update(buffer, 0, count)
            output?.write(buffer, 0, count)
        }
        return StreamDigest(size, digest.digest().toHex(), crc.value)
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).toHex()

    fun safeArchivePath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.isNotBlank() && !normalized.startsWith('/') &&
            normalized.split('/').none { it.isBlank() || it == "." || it == ".." }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
