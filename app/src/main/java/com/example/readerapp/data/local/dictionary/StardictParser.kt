package com.example.readerapp.data.local.dictionary

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

class StardictParser(private val context: Context) {

    data class DictionaryInfo(
        val name: String,
        val idxOffsetBits: Int,
        val isHtml: Boolean,
        val wordCount: Int
    )

    suspend fun parseDictionary(zipUri: Uri, onProgress: (Int) -> Unit = {}): Pair<String, DictionaryInfo> = withContext(Dispatchers.IO) {
        val dictId = UUID.randomUUID().toString()
        val tempDir = File(context.cacheDir, "dict_extract_$dictId")
        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            // 1. Unzip the file
            onProgress(5)
            context.contentResolver.openInputStream(zipUri)?.use { inputStream ->
                ZipInputStream(BufferedInputStream(inputStream)).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val fileName = entry.name.substringAfterLast("/")
                            val file = File(tempDir, fileName)
                            FileOutputStream(file).use { fos ->
                                zis.copyTo(fos)
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // 2. Find necessary files
            val files = tempDir.listFiles() ?: emptyArray()
            val ifoFile = files.find { it.name.endsWith(".ifo") } ?: throw Exception("Missing .ifo file")
            val idxFile = files.find { it.name.endsWith(".idx") } ?: throw Exception("Missing .idx file")
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
                        GZIPInputStream(BufferedInputStream(fis)).use { gis ->
                            FileOutputStream(dictFile).use { fos ->
                                gis.copyTo(fos)
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
                        GZIPInputStream(BufferedInputStream(fis)).use { gis ->
                            FileOutputStream(synFile).use { fos ->
                                gis.copyTo(fos)
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
            val dictRaf = RandomAccessFile(dictFile, "r")

            var idxOffset = 0
            val totalBytes = idxBytes.size
            var wordsParsed = 0
            var wordIndexCounter = 0
            var lastProgressUpdate = 0L

            val buffer = mutableListOf<DictionaryEntry>()

            while (idxOffset < totalBytes) {
                // Read word (null terminated string)
                val wordStart = idxOffset
                while (idxOffset < totalBytes && idxBytes[idxOffset].toInt() != 0) {
                    idxOffset++
                }
                val word = String(idxBytes, wordStart, idxOffset - wordStart, Charsets.UTF_8)
                idxOffset++ // skip null byte

                // Read offset and size
                val dataOffset: Long
                val dataSize: Int

                if (info.idxOffsetBits == 64) {
                    val bb = ByteBuffer.wrap(idxBytes, idxOffset, 8).order(ByteOrder.BIG_ENDIAN)
                    dataOffset = bb.long
                    idxOffset += 8
                } else {
                    val bb = ByteBuffer.wrap(idxBytes, idxOffset, 4).order(ByteOrder.BIG_ENDIAN)
                    // The bitwise AND handles the unsigned to signed conversion for offset up to 4GB
                    dataOffset = bb.int.toLong() and 0xFFFFFFFFL
                    idxOffset += 4
                }

                val bbSize = ByteBuffer.wrap(idxBytes, idxOffset, 4).order(ByteOrder.BIG_ENDIAN)
                dataSize = bbSize.int
                idxOffset += 4

                // Read definition
                val defBytes = ByteArray(dataSize)
                dictRaf.seek(dataOffset)
                dictRaf.readFully(defBytes)
                val definition = String(defBytes, Charsets.UTF_8)

                buffer.add(DictionaryEntry(wordIndex = wordIndexCounter, word = word, definition = definition))
                wordsParsed++
                wordIndexCounter++

                if (buffer.size >= 300) {
                    dao.insertAll(buffer)
                    buffer.clear()
                }

                // Update progress
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate > 500) {
                    lastProgressUpdate = now
                    val p = 40 + ((idxOffset.toFloat() / totalBytes) * 60).toInt()
                    onProgress(p.coerceAtMost(99))
                }
            }

            if (buffer.isNotEmpty()) {
                dao.insertAll(buffer)
            }

            dictRaf.close()

            // 6. Parse .syn into Database
            if (synFile != null && synFile.exists()) {
                val synBytes = synFile.readBytes()
                var synOffset = 0
                val synTotalBytes = synBytes.size
                val synBuffer = mutableListOf<SynonymEntry>()

                while (synOffset < synTotalBytes) {
                    val wordStart = synOffset
                    while (synOffset < synTotalBytes && synBytes[synOffset].toInt() != 0) {
                        synOffset++
                    }
                    if (synOffset >= synTotalBytes) break
                    val synonym = String(synBytes, wordStart, synOffset - wordStart, Charsets.UTF_8)
                    synOffset++ // skip null byte

                    if (synOffset + 4 > synTotalBytes) break
                    val bb = ByteBuffer.wrap(synBytes, synOffset, 4).order(ByteOrder.BIG_ENDIAN)
                    val originalWordIndex = bb.int
                    synOffset += 4

                    synBuffer.add(SynonymEntry(synonym = synonym, originalWordIndex = originalWordIndex))
                    
                    if (synBuffer.size >= 1000) {
                        dao.insertSynonyms(synBuffer)
                        synBuffer.clear()
                    }
                }
                if (synBuffer.isNotEmpty()) {
                    dao.insertSynonyms(synBuffer)
                }
            }

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
