package com.javierreansyah.pinecone.data.local.database.dictionary

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class DictionaryStoreTest {
    @Test
    fun parserFailureAndCancellationDiscardUnpublishedDatabases() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DictionaryStore(context, Dispatchers.IO)
        val parser = StardictParser(context, store, Dispatchers.IO)
        val before = context.databaseList().toSet()
        val archive = java.io.File.createTempFile("dictionary-input-", ".zip", context.cacheDir)
        try {
            for (invalidOffset in listOf(false, true)) {
                writeDictionary(archive, invalidOffset)
                try {
                    parser.parseDictionary(android.net.Uri.fromFile(archive)) { progress ->
                        if (!invalidOffset && progress == 95) throw kotlinx.coroutines.CancellationException("Cancelled import")
                    }
                    throw AssertionError("Import should fail or be cancelled")
                } catch (_: kotlinx.coroutines.CancellationException) {
                    assertEquals(before, context.databaseList().toSet())
                } catch (_: IllegalArgumentException) {
                    assertEquals(before, context.databaseList().toSet())
                }
            }
        } finally {
            archive.delete()
        }
    }

    @Test
    fun parserKeepsEqualsSignsInDictionaryNamesAndPublishesUsableWords() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DictionaryStore(context, Dispatchers.IO)
        val archive = java.io.File.createTempFile("dictionary-input-", ".zip", context.cacheDir)
        var id: String? = null
        try {
            writeDictionary(archive, false)
            val parsed = StardictParser(context, store, Dispatchers.IO).parseDictionary(android.net.Uri.fromFile(archive))
            id = parsed.first
            assertEquals("A=B dictionary", parsed.second.name)
            assertEquals("one", requireNotNull(store.withDictionary(parsed.first) { it.getDefinitions("hello") }).single().definition)
        } finally {
            archive.delete()
            id?.let { store.delete(it) }
        }
    }

    private fun writeDictionary(file: java.io.File, invalidOffset: Boolean) {
        val index = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(index).use {
            it.write("hello".toByteArray())
            it.writeByte(0)
            it.writeInt(if (invalidOffset) 100 else 0)
            it.writeInt(3)
        }
        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in mapOf(
                "test.ifo" to "bookname=A=B dictionary\nwordcount=1\nidxoffsetbits=32".toByteArray(),
                "test.idx" to index.toByteArray(),
                "test.dict" to "one".toByteArray(),
            )) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    @Test
    fun deletionWaitsForLookupLeaseAndCannotRecreateDeletedDictionary() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DictionaryStore(context, Dispatchers.IO)
        val id = UUID.randomUUID().toString()
        try {
            store.create(id) { it.dictionaryDao().insertAll(listOf(DictionaryEntry(word = "hello", definition = "definition"))) }
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val lookup = async {
                store.withDictionary(id) { dao ->
                    entered.complete(Unit)
                    release.await()
                    dao.getDefinitions("hello")
                }
            }
            entered.await()
            val deleting = async { store.delete(id) }
            assertFalse(deleting.isCompleted)
            release.complete(Unit)
            assertEquals("definition", requireNotNull(lookup.await()).single().definition)
            deleting.await()
            assertNull(store.withDictionary(id) { error("Deleted database was recreated") })
        } finally {
            store.delete(id)
        }
    }

    @Test
    fun versionOneMigrationPreservesWordsAndAddsSynonymIndex() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        try {
            android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                db.execSQL("CREATE TABLE dictionary_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, wordIndex INTEGER NOT NULL, word TEXT COLLATE NOCASE NOT NULL, definition TEXT NOT NULL)")
                db.execSQL("CREATE INDEX index_dictionary_entries_word ON dictionary_entries(word)")
                db.execSQL("CREATE TABLE synonym_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, synonym TEXT COLLATE NOCASE NOT NULL, originalWordIndex INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX index_synonym_entries_synonym ON synonym_entries(synonym)")
                db.execSQL("INSERT INTO dictionary_entries VALUES (1, 42, 'hello', 'retained definition')")
                db.version = 1
            }
            val db = Room.databaseBuilder(context, DictionaryDatabase::class.java, name)
                .addMigrations(DictionaryDatabase.MIGRATION_1_2).build()
            try {
                assertEquals("retained definition", db.dictionaryDao().getDefinitionsByIndex(42).single().definition)
                db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_dictionary_entries_wordIndex'").use {
                    assertEquals(1, it.count)
                }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
