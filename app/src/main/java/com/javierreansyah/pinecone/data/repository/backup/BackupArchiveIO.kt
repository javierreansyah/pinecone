package com.javierreansyah.pinecone.data.repository.backup

import com.javierreansyah.pinecone.data.model.BackupEntry
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

internal object BackupArchiveIO {
    private const val MAX_ENTRY_BYTES = 4L * 1024 * 1024 * 1024
    private const val MAX_ARCHIVE_BYTES = 16L * 1024 * 1024 * 1024

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).toHex()

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    fun descriptor(path: String, bytes: ByteArray) = BackupEntry(path, bytes.size.toLong(), sha256(bytes))
    fun descriptor(path: String, file: File) = BackupEntry(path, file.length(), sha256(file))

    fun writeBytes(zip: ZipOutputStream, path: String, bytes: ByteArray) {
        zip.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
        zip.putNextEntry(ZipEntry(path))
        zip.write(bytes)
        zip.closeEntry()
    }

    fun writeFile(zip: ZipOutputStream, path: String, file: File, alreadyCompressed: Boolean) {
        val entry = ZipEntry(path)
        if (alreadyCompressed) {
            entry.method = ZipEntry.STORED
            entry.size = file.length()
            entry.compressedSize = file.length()
            val crc = CRC32()
            FileInputStream(file).use { input -> updateCrc(input, crc) }
            entry.crc = crc.value
        } else {
            zip.setLevel(java.util.zip.Deflater.BEST_COMPRESSION)
        }
        zip.putNextEntry(entry)
        FileInputStream(file).use { it.copyTo(zip) }
        zip.closeEntry()
    }

    fun extract(
        input: InputStream,
        target: File,
        allowed: (String) -> Boolean
    ): Set<String> {
        val root = target.canonicalFile.apply { mkdirs() }
        val seen = mutableSetOf<String>()
        var total = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val path = entry.name.replace('\\', '/')
                require(path.isNotBlank() && !path.startsWith('/') && allowed(path))
                require(seen.add(path))
                val output = File(root, path).canonicalFile
                require(output.path.startsWith(root.path + File.separator))
                if (entry.isDirectory) {
                    output.mkdirs()
                } else {
                    output.parentFile?.mkdirs()
                    FileOutputStream(output).use { stream ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var entryBytes = 0L
                        while (true) {
                            val count = zip.read(buffer)
                            if (count < 0) break
                            entryBytes += count
                            total += count
                            require(entryBytes <= MAX_ENTRY_BYTES && total <= MAX_ARCHIVE_BYTES)
                            stream.write(buffer, 0, count)
                        }
                        if (entry.compressedSize > 0L) {
                            require(entryBytes / entry.compressedSize.coerceAtLeast(1L) <= 200L)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        return seen
    }

    fun verify(root: File, expected: List<BackupEntry>) {
        val unique = expected.map { it.path }.toSet()
        require(unique.size == expected.size)
        for (entry in expected) {
            val file = File(root, entry.path).canonicalFile
            require(file.path.startsWith(root.canonicalPath + File.separator))
            require(file.isFile && file.length() == entry.size)
            require(sha256(file) == entry.sha256)
        }
    }

    private fun updateCrc(input: InputStream, crc: CRC32) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            crc.update(buffer, 0, count)
        }
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
