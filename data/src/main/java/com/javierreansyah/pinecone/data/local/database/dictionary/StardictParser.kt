package com.javierreansyah.pinecone.data.local.database.dictionary

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.javierreansyah.pinecone.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import javax.inject.Inject

class StardictParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dictionaries: DictionaryStore,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    data class DictionaryInfo(
        val name: String, val idxOffsetBits: Int, val isHtml: Boolean, val wordCount: Int,
    )

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        bytes[offset].toInt() and 0xFF shl 24 or
                (bytes[offset + 1].toInt() and 0xFF shl 16) or
                (bytes[offset + 2].toInt() and 0xFF shl 8) or
                (bytes[offset + 3].toInt() and 0xFF)

    private fun readLong(bytes: ByteArray, offset: Int): Long =
        bytes[offset].toLong() and 0xFF shl 56 or
                (bytes[offset + 1].toLong() and 0xFF shl 48) or
                (bytes[offset + 2].toLong() and 0xFF shl 40) or
                (bytes[offset + 3].toLong() and 0xFF shl 32) or
                (bytes[offset + 4].toLong() and 0xFF shl 24) or
                (bytes[offset + 5].toLong() and 0xFF shl 16) or
                (bytes[offset + 6].toLong() and 0xFF shl 8) or
                (bytes[offset + 7].toLong() and 0xFF)

    suspend fun parseDictionary(
        zipUri: Uri, onProgress: (Int) -> Unit = {},
    ): Pair<String, DictionaryInfo> {
        // Retain ownership if cancellation discards the result at the dispatcher hand-off.
        try {
            return parseOnIo(zipUri, onProgress) { }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            null?.let { id ->
                withContext(kotlinx.coroutines.NonCancellable) { dictionaries.delete(id) }
            }
            throw cancelled
        }
    }

    private suspend fun parseOnIo(
        zipUri: Uri,
        onProgress: (Int) -> Unit,
        acquired: (String) -> Unit,
    ): Pair<String, DictionaryInfo> = withContext(ioDispatcher) {
        val dictId = UUID.randomUUID().toString()
        val tempDir = File(context.cacheDir, "dict_extract_$dictId")
        if (!tempDir.exists()) tempDir.mkdirs()

        var completed = false
        try {
            var extractedBytes = 0L
            onProgress(5)
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream, 65536)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            currentCoroutineContext().ensureActive()
                            val fileName = entry.name.substringAfterLast("/")
                            require(
                                fileName.isNotBlank() && fileName !in setOf(
                                    ".",
                                    ".."
                                ) && '\\' !in fileName
                            ) { "Invalid dictionary entry name" }
                            val file = File(tempDir, fileName)
                            require(!file.exists()) { "Duplicate dictionary entry" }
                            FileOutputStream(file).use { fos ->
                                extractedBytes += copyFile(
                                    zis,
                                    fos,
                                    MAX_EXTRACTED_BYTES - extractedBytes
                                )
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            val files = tempDir.listFiles() ?: emptyArray()
            val ifoFile =
                files.find { it.name.endsWith(".ifo") } ?: throw Exception("Missing .ifo file")
            val idxFile =
                files.find { it.name.endsWith(".idx") } ?: throw Exception("Missing .idx file")
            var dictFile = files.find { it.name.endsWith(".dict") }

            var synFile = files.find { it.name.endsWith(".syn") }

            if (dictFile == null) {
                val dzFile = files.find { it.name.endsWith(".dict.dz") }
                if (dzFile != null) {
                    onProgress(15)
                    dictFile = File(tempDir, dzFile.nameWithoutExtension)
                    FileInputStream(dzFile).use { fis ->
                        GZIPInputStream(BufferedInputStream(fis, 65536)).use { gis ->
                            FileOutputStream(dictFile).use { fos ->
                                extractedBytes += copyFile(
                                    gis,
                                    fos,
                                    MAX_EXTRACTED_BYTES - extractedBytes
                                )
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
                                copyFile(
                                    gis,
                                    fos,
                                    MAX_EXTRACTED_BYTES - extractedBytes
                                )
                            }
                        }
                    }
                    synDzFile.delete()
                }
            }

            onProgress(30)
            val info = parseIfo(ifoFile)

            onProgress(40)
            dictionaries.create(dictId) { db ->
                val dao = db.dictionaryDao()

                require(idxFile.length() <= 128L * 1024 * 1024) { "Dictionary index is too large" }
                val idxBytes = idxFile.readBytes()

                FileInputStream(dictFile).channel.use { dictChannel ->
                    require(dictChannel.size() in 1..Int.MAX_VALUE.toLong()) { "Dictionary data is too large or empty" }
                    val dictMapped = dictChannel.map(
                        FileChannel.MapMode.READ_ONLY, 0, dictChannel.size(),
                    ).order(ByteOrder.BIG_ENDIAN)

                    var idxOffset = 0
                    val totalBytes = idxBytes.size
                    var wordIndexCounter = 0
                    var lastProgressUpdate = 0L

                    val buffer = mutableListOf<DictionaryEntry>()

                    db.withTransaction {
                        while (idxOffset < totalBytes) {
                            currentCoroutineContext().ensureActive()
                            val wordStart = idxOffset
                            while (idxOffset < totalBytes && idxBytes[idxOffset].toInt() != 0) {
                                idxOffset++
                            }
                            require(idxOffset < totalBytes) { "Unterminated dictionary word" }
                            val word =
                                String(idxBytes, wordStart, idxOffset - wordStart, Charsets.UTF_8)
                            idxOffset++

                            require(idxOffset + (if (info.idxOffsetBits == 64) 12 else 8) <= totalBytes) { "Truncated dictionary index" }
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

                            require(dataSize >= 0 && dataOffset >= 0 && dataOffset + dataSize <= dictMapped.limit()) {
                                "Invalid dictionary definition range"
                            }
                            require(dataSize <= 64 * 1024 * 1024) { "Dictionary definition is too large" }
                            val defBytes = ByteArray(dataSize)
                            val slice = dictMapped.duplicate()
                            slice.position(dataOffset.toInt())
                            slice[defBytes]
                            val definition = String(defBytes, Charsets.UTF_8)

                            buffer.add(
                                DictionaryEntry(
                                    wordIndex = wordIndexCounter,
                                    word = word,
                                    definition = definition,
                                ),
                            )
                            wordIndexCounter++

                            if (buffer.size >= 3000) {
                                dao.insertAll(buffer)
                                buffer.clear()
                            }

                            val now = System.currentTimeMillis()
                            if (now - lastProgressUpdate > 500) {
                                lastProgressUpdate = now
                                val p = 40 + (idxOffset.toFloat() / totalBytes * 55).toInt()
                                onProgress(p.coerceAtMost(95))
                            }
                        }

                        if (buffer.isNotEmpty()) {
                            dao.insertAll(buffer)
                        }

                        if (synFile != null && synFile.exists()) {
                            require(synFile.length() <= 128L * 1024 * 1024) { "Dictionary synonym index is too large" }
                            val synBytes = synFile.readBytes()
                            var synOffset = 0
                            val synTotalBytes = synBytes.size
                            val synBuffer = mutableListOf<SynonymEntry>()

                            while (synOffset < synTotalBytes) {
                                currentCoroutineContext().ensureActive()
                                val synWordStart = synOffset
                                while (synOffset < synTotalBytes && synBytes[synOffset].toInt() != 0) {
                                    synOffset++
                                }
                                if (synOffset >= synTotalBytes) break
                                val synonym =
                                    String(
                                        synBytes,
                                        synWordStart,
                                        synOffset - synWordStart,
                                        Charsets.UTF_8
                                    )
                                synOffset++

                                if (synOffset + 4 > synTotalBytes) break
                                val originalWordIndex = readInt(synBytes, synOffset)
                                synOffset += 4

                                synBuffer.add(
                                    SynonymEntry(
                                        synonym = synonym, originalWordIndex = originalWordIndex,
                                    ),
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

                }
            }
            onProgress(100)
            acquired(dictId)
            completed = true
            dictId to info
        } finally {
            tempDir.deleteRecursively()
            if (!completed) withContext(kotlinx.coroutines.NonCancellable) {
                dictionaries.delete(
                    dictId
                )
            }
        }
    }

    private suspend fun copyFile(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        limit: Long
    ): Long {
        val buffer = ByteArray(65536)
        var count = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = withContext(Dispatchers.IO) {
                input.read(buffer)
            }
            if (read < 0) return count
            count += read
            require(count <= limit) { "Extracted dictionary is too large" }
            withContext(Dispatchers.IO) {
                output.write(buffer, 0, read)
            }
        }
    }

    private companion object {
        const val MAX_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024
    }

    private fun parseIfo(ifoFile: File): DictionaryInfo {
        var name = "Unknown Dictionary"
        var idxOffsetBits = 32
        var isHtml = false
        var wordCount = 0

        ifoFile.readLines().forEach { line ->
            val parts = line.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                when (key) {
                    "bookname" -> name = value
                    "idxoffsetbits" -> idxOffsetBits = value.toIntOrNull() ?: 32
                    "sametypesequence" -> isHtml = "h" in value
                    "wordcount" -> wordCount = value.toIntOrNull() ?: 0
                }
            }
        }

        require(idxOffsetBits == 32 || idxOffsetBits == 64) { "Unsupported index offset size" }
        return DictionaryInfo(name, idxOffsetBits, isHtml, wordCount)
    }
}
