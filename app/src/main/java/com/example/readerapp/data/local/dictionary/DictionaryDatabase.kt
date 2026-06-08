package com.example.readerapp.data.local.dictionary

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(
    tableName = "dictionary_entries",
    indices = [Index(value = ["word"])]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val wordIndex: Int = 0,
    val word: String,
    val definition: String
)

@Entity(
    tableName = "synonym_entries",
    indices = [Index(value = ["synonym"])]
)
data class SynonymEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val synonym: String,
    val originalWordIndex: Int
)

@Dao
interface DictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSynonyms(entries: List<SynonymEntry>)

    @Query("SELECT * FROM dictionary_entries WHERE word = :word COLLATE NOCASE")
    suspend fun getDefinitions(word: String): List<DictionaryEntry>

    @Query("SELECT * FROM dictionary_entries WHERE wordIndex = :wordIndex")
    suspend fun getDefinitionsByIndex(wordIndex: Int): List<DictionaryEntry>

    @Query("SELECT * FROM synonym_entries WHERE synonym = :synonym COLLATE NOCASE")
    suspend fun getSynonyms(synonym: String): List<SynonymEntry>

    // Fallback: try prefix or partial if exact match fails
    @Query("SELECT * FROM dictionary_entries WHERE word LIKE :word || '%' COLLATE NOCASE LIMIT 10")
    suspend fun getPrefixDefinitions(word: String): List<DictionaryEntry>

}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dictionary_entries ADD COLUMN wordIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `synonym_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `synonym` TEXT NOT NULL, `originalWordIndex` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_synonym_entries_synonym` ON `synonym_entries` (`synonym`)")
    }
}

@Database(entities = [DictionaryEntry::class, SynonymEntry::class], version = 2, exportSchema = false)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        fun getDatabase(context: Context, dictionaryId: String): DictionaryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DictionaryDatabase::class.java,
                "dict_$dictionaryId.db"
            )
            .addMigrations(MIGRATION_1_2)
            .build()
        }
    }
}
