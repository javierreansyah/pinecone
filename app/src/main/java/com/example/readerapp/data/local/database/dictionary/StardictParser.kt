package com.example.readerapp.data.local.database.dictionary

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

class StardictParser(private val context: Context) {

    data class DictionaryInfo(
        val name: String, val idxOffsetBits: Int, val isHtml: Boolean, val wordCount: Int
    )

    /**
     * Read a big-endian 32-bit integer from a byte array without allocating a ByteBuffer.
     */
    private fun readInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF shl 24) or
                (bytes[offset + 1].toInt() and 0xFF shl 16) or
                (bytes[offset + 2].toInt() and 0xFF shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)

    /**
     * Read a big-endian 64-bit long from a byte array without allocating a ByteBuffer.
     */
    private fun readLong(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xFF shl 56) or
                (bytes[offset + 1].toLong() and 0xFF shl 48) or
                (bytes[offset + 2].toLong() and 0xFF shl 40) or
                (bytes[offset + 3].toLong() and 0xFF shl 32) or
                (bytes[offset + 4].toLong() and 0xFF shl 24) or
                (bytes[offset + 5].toLong() and 0xFF shl 16) or
                (bytes[offset + 6].toLong() and 0xFF shl 8) or
                (bytes[offset + 7].toLong() and 0xFF)

    suspend fun parseDictionary(
        zipUri: Uri, onProgress: (Int) -> Unit = {}
    ): Pair<String, DictionaryInfo> = withContext(Dispatchers.IO) {
        val dictId = UUID.randomUUID().toString()
        val tempDir = File(context.cacheDir, "dict_extract_$dictId")
        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            // 1. Unzip the file
            onProgress(5)
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream, 65536)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val fileName = entry.name.substringAfterLast("/")
                            val file = File(tempDir, fileName)
                            FileOutputStream(file).use { fos ->
                                zis.copyTo(fos, bufferSize = 65536)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // 2. Find necessary files
            val files = tempDir.listFiles() ?: emptyArray()
            val ifoFile =
                files.find { it.name.endsWith(".ifo") } ?: throw Exception("Missing .ifo file")
            val idxFile =
                files.find { it.name.endsWith(".idx") } ?: throw Exception("Missing .idx file")
            var dictFile = files.find { it.name.endsWith(".dict") }

            // Find .syn file
            var synFile = files.find { it.name.endsWith(".syn") }

            // 3. Handle dict.dz and syn.dz
            if (dictFile == null) {
                val dzFile = files.find { it.name.endsWith(".dict.dz") }
                if (dzFile != null) {
                    onProgress(15)
                    dictFile = File(tempDir, dzFile.nameWithoutExtension)
                    FileInputStream(dzFile).use { fis ->
                        GZIPInputStream(BufferedInputStream(fis, 65536)).use { gis ->
                            FileOutputStream(dictFile).use { fos ->
                                gis.copyTo(fos, bufferSize = 65536)
                            }
                        }
                    }
                    dzFile.delete()
                } else {
                    throw Exception("Missing .dict or .dict.dz file")
                }
            }

            if (synFile == null) {
                val synDzFile = files.find { it.name.endsWith(".syn.dz") }
                if (synDzFile != null) {
                    synFile = File(tempDir, synDzFile.nameWithoutExtension)
                    FileInputStream(synDzFile).use { fis ->
                        GZIPInputStream(BufferedInputStream(fis, 65536)).use { gis ->
                            FileOutputStream(synFile).use { fos ->
                                gis.copyTo(fos, bufferSize = 65536)
                            }
                        }
                    }
                    synDzFile.delete()
                }
            }

            // 4. Parse .ifo
            onProgress(30)
            val info = parseIfo(ifoFile)

            // 5. Parse .idx and .dict into Database
            onProgress(40)
            val db = DictionaryDatabase.getDatabase(context, dictId)
            val dao = db.dictionaryDao()

            val idxBytes = idxFile.readBytes()

            // Memory-map the .dict file for efficient random reads without per-call syscall overhead.
            // The OS handles paging, so this is both fast and memory-safe on mobile.
            val dictChannel = FileInputStream(dictFile).channel
            val dictMapped = dictChannel.map(
                FileChannel.MapMode.READ_ONLY, 0, dictChannel.size()
            ).order(ByteOrder.BIG_ENDIAN)

            var idxOffset = 0
            val totalBytes = idxBytes.size
            var wordsParsed = 0
            var wordIndexCounter = 0
            var lastProgressUpdate = 0L

            val buffer = mutableListOf<DictionaryEntry>()

            // Wrap the entire import in a single database transaction to avoid
            // per-batch fsync overhead. This is the single largest perf win.
            db.withTransaction {
                while (idxOffset < totalBytes) {
                    // Read word (null terminated string)
                    val wordStart = idxOffset
                    while (idxOffset < totalBytes && idxBytes[idxOffset].toInt() != 0) {
                        idxOffset++
                    }
                    val word = String(idxBytes, wordStart, idxOffset - wordStart, Charsets.UTF_8)
                    idxOffset++ // skip null byte

                    // Read offset and size using manual bit-shifting (avoids ByteBuffer allocation)
                    val dataOffset: Long

                    if (info.idxOffsetBits == 64) {
                        dataOffset = readLong(idxBytes, idxOffset)
                        idxOffset += 8
                    } else {
                        dataOffset = readInt(idxBytes, idxOffset).toLong() and 0xFFFFFFFFL
                        idxOffset += 4
                    }

                    val dataSize: Int = readInt(idxBytes, idxOffset)
                    idxOffset += 4

                    // Read definition from memory-mapped buffer (no syscall per word)
                    val defBytes = ByteArray(dataSize)
                    val slice = dictMapped.duplicate()
                    slice.position(dataOffset.toInt())
                    slice.get(defBytes)
                    val definition = String(defBytes, Charsets.UTF_8)

                    buffer.add(
                        DictionaryEntry(
                            wordIndex = wordIndexCounter, word = word, definition = definition
                        )
                    )
                    wordsParsed++
                    wordIndexCounter++

                    if (buffer.size >= 3000) {
                        dao.insertAll(buffer)
                        buffer.clear()
                    }

                    // Update progress
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate > 500) {
                        lastProgressUpdate = now
                        val p = 40 + ((idxOffset.toFloat() / totalBytes) * 55).toInt()
                        onProgress(p.coerceAtMost(95))
                    }
                }

                if (buffer.isNotEmpty()) {
                    dao.insertAll(buffer)
                }

                // 6. Parse .syn into Database (inside the same transaction)
                if (synFile != null && synFile.exists()) {
                    val synBytes = synFile.readBytes()
                    var synOffset = 0
                    val synTotalBytes = synBytes.size
                    val synBuffer = mutableListOf<SynonymEntry>()

                    while (synOffset < synTotalBytes) {
                        val synWordStart = synOffset
                        while (synOffset < synTotalBytes && synBytes[synOffset].toInt() != 0) {
                            synOffset++
                        }
                        if (synOffset >= synTotalBytes) break
                        val synonym =
                            String(synBytes, synWordStart, synOffset - synWordStart, Charsets.UTF_8)
                        synOffset++ // skip null byte

                        if (synOffset + 4 > synTotalBytes) break
                        val originalWordIndex = readInt(synBytes, synOffset)
                        synOffset += 4

                        synBuffer.add(
                            SynonymEntry(
                                synonym = synonym, originalWordIndex = originalWordIndex
                            )
                        )

                        if (synBuffer.size >= 3000) {
                            dao.insertSynonyms(synBuffer)
                            synBuffer.clear()
                        }
                    }
                    if (synBuffer.isNotEmpty()) {
                        dao.insertSynonyms(synBuffer)
                    }
                }
            }

            dictChannel.close()

            // Room's InvalidationTracker can crash on a background thread if we close the DB
            // immediately after a transaction. We will let the garbage collector and SQLite
            // handle closing the connection since it's a simple local database.

            onProgress(100)
            Pair(dictId, info)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun parseIfo(ifoFile: File): DictionaryInfo {
        var name = "Unknown Dictionary"
        var idxOffsetBits = 32
        var isHtml = false
        var wordCount = 0

        ifoFile.readLines().forEach { line ->
            val parts = line.split("=")
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                when (key) {
                    "bookname" -> name = value
                    "idxoffsetbits" -> idxOffsetBits = value.toIntOrNull() ?: 32
                    "sametypesequence" -> isHtml = value.contains("h")
                    "wordcount" -> wordCount = value.toIntOrNull() ?: 0
                }
            }
        }

        return DictionaryInfo(name, idxOffsetBits, isHtml, wordCount)
    }
}