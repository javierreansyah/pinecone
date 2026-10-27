package com.javierreansyah.pinecone.data.local.database.dictionary

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class DictionaryStoreTest {
    @Test
    fun stagedHistoricalDictionaryMigratesAndClosesBeforeReplacement() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File.createTempFile("historical-dictionary-", ".db", context.cacheDir)
        try {
            context.assets.open("compatibility/v1.0.0/dictionary.db").use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, 0).use {
                it.execSQL("DROP INDEX index_dictionary_entries_wordIndex")
                it.version = 1
            }
            prepareRestoredDictionary(context, file)
            val db = openDictionaryDatabase(context, file.absolutePath)
            try {
                assertEquals("A retained definition", db.dictionaryDao().getDefinitions("pinecone").single().definition)
                assertEquals(2, db.openHelper.readableDatabase.version)
            } finally {
                db.close()
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun stagedDictionaryCannotForgeAValidSchemaIdentity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File.createTempFile("invalid-dictionary-", ".db", context.cacheDir)
        try {
            context.assets.open("compatibility/v1.0.0/dictionary.db").use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, 0).use {
                it.execSQL("ALTER TABLE dictionary_entries DROP COLUMN definition")
            }
            org.junit.Assert.assertThrows(IllegalStateException::class.java) {
                prepareRestoredDictionary(context, file)
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun cancellationOnReturnFromIoDiscardsTheCompletedDatabase() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DictionaryStore(context, Dispatchers.IO)
        val archive = java.io.File.createTempFile("dictionary-return-", ".zip", context.cacheDir)
        val before = context.databaseList().toSet()
        val returns = java.util.concurrent.LinkedBlockingQueue<Runnable>()
        val returning = object : kotlinx.coroutines.CoroutineDispatcher() {
            override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
                returns.add(block)
            }
        }
        val worker =
            java.util.concurrent.Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val completed = java.util.concurrent.CountDownLatch(1)
        val scope =
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + returning)
        try {
            writeDictionary(archive, false)
            val job = scope.async {
                StardictParser(context, store, worker).parseDictionary(
                    android.net.Uri.fromFile(
                        archive
                    )
                ) {
                    if (it == 100) completed.countDown()
                }
            }
            returns.remove().run()
            org.junit.Assert.assertTrue(completed.await(10, java.util.concurrent.TimeUnit.SECONDS))
            // A barrier on the same worker ensures IO completed while its return remains queued.
            kotlinx.coroutines.withContext(worker) { }
            job.cancel()
            while (!job.isCompleted) {
                requireNotNull(returns.poll(10, java.util.concurrent.TimeUnit.SECONDS)).run()
            }
            job.join()
            assertEquals(before, context.databaseList().toSet())
        } finally {
            scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
            worker.close()
            archive.delete()
        }
    }

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
                        if (!invalidOffset && progress == 95) throw kotlinx.coroutines.CancellationException(
                            "Cancelled import"
                        )
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
            val parsed = StardictParser(
                context,
                store,
                Dispatchers.IO
            ).parseDictionary(android.net.Uri.fromFile(archive))
            id = parsed.first
            assertEquals("A=B dictionary", parsed.second.name)
            assertEquals(
                "one",
                requireNotNull(store.withDictionary(parsed.first) { it.getDefinitions("hello") }).single().definition
            )
        } finally {
            archive.delete()
            id?.let { store.delete(it) }
        }
    }

    @Test
    fun largeDefinitionsFlushBeforeTheRowLimitAndRemainReadable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DictionaryStore(context, Dispatchers.IO)
        val archive = java.io.File.createTempFile("dictionary-large-", ".zip", context.cacheDir)
        var id: String? = null
        try {
            val definition = ByteArray(1024 * 1024) { 'a'.code.toByte() }
            writeDictionary(archive, false, count = 192, definition = definition)
            val parsed = StardictParser(context, store, Dispatchers.IO)
                .parseDictionary(android.net.Uri.fromFile(archive))
            id = parsed.first
            for (word in listOf("word-0", "word-191")) {
                val result =
                    requireNotNull(store.withDictionary(parsed.first) { it.getDefinitions(word) }).single()
                assertEquals(definition.size, result.definition.length)
                org.junit.Assert.assertTrue(result.definition.all { it == 'a' })
            }
        } finally {
            archive.delete()
            id?.let { store.delete(it) }
        }
    }

    private fun writeDictionary(
        file: java.io.File, invalidOffset: Boolean, count: Int = 1,
        definition: ByteArray = "one".toByteArray(),
    ) {
        val index = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(index).use {
            repeat(count) { index ->
                it.write((if (count == 1) "hello" else "word-$index").toByteArray())
                it.writeByte(0)
                it.writeInt(if (invalidOffset) 100 else 0)
                it.writeInt(definition.size)
            }
        }
        java.util.zip.ZipOutputStream(file.outputStream()).use { zip ->
            for ((name, bytes) in mapOf(
                "test.ifo" to "bookname=A=B dictionary\nwordcount=$count\nidxoffsetbits=32".toByteArray(),
                "test.idx" to index.toByteArray(),
                "test.dict" to definition,
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
            store.create(id) {
                it.dictionaryDao()
                    .insertAll(listOf(DictionaryEntry(word = "hello", definition = "definition")))
            }
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
                assertEquals(
                    "retained definition",
                    db.dictionaryDao().getDefinitionsByIndex(42).single().definition
                )
                db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'index' AND name = 'index_dictionary_entries_wordIndex'")
                    .use {
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
