package com.javierreansyah.pinecone.data.local.database.library

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryMigrationTest {
    @Test fun schemaOneContentAndReadingStateSurviveOperationTableMigration() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        val schema = JSONObject(context.assets.open("${AppDatabase::class.java.name}/1.json").bufferedReader().use { it.readText() })
            .getJSONObject("database")
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    if (table == "books") {
                        val values = ContentValues()
                        val fields = entity.getJSONArray("fields")
                        for (j in 0 until fields.length()) {
                            val field = fields.getJSONObject(j)
                            if (!field.optBoolean("notNull")) continue
                            val key = field.getString("columnName")
                            when (field.getString("affinity")) {
                                "TEXT" -> values.put(key, when (key) {
                                    "id" -> "preserved"
                                    "title" -> "Existing publication"
                                    "activeRendition" -> "original"
                                    "metadataJson", "sourceMetadataJson" -> "{}"
                                    else -> "existing"
                                })
                                "INTEGER" -> values.put(key, 0L)
                                "REAL" -> values.put(key, .75)
                            }
                        }
                        old.insertOrThrow(table, null, values)
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) old.execSQL(setup.getString(i))
                old.version = 1
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(AppDatabase.MIGRATION_1_2).addCallback(AppDatabase.BACKUP_REVISION_CALLBACK).build()
            try {
                val book = requireNotNull(db.bookDao().getById("preserved")).book
                assertEquals("Existing publication", book.title)
                assertEquals(.75, book.progression, 0.0)
                assertEquals(.75, book.furthestProgression, 0.0)
                assertFalse(book.isArchived)
                assertEquals(LibraryRuntimeEntity(), db.operationDao().runtime())
                assertEquals(emptyList<OperationEntity>(), db.operationDao().resumable())
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
