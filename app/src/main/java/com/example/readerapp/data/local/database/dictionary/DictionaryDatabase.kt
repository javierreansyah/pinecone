package com.example.readerapp.data.local.database.dictionary

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
import androidx.room.ColumnInfo
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.util.concurrent.ConcurrentHashMap

@Entity(
    tableName = "dictionary_entries",
    indices = [Index(value = ["word"])]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val wordIndex: Int = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val word: String,
    val definition: String
)

@Entity(
    tableName = "synonym_entries",
    indices = [Index(value = ["synonym"])]
)
data class SynonymEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val synonym: String,
    val originalWordIndex: Int
)

@Dao
interface DictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSynonyms(entries: List<SynonymEntry>)

    @Query("SELECT * FROM dictionary_entries WHERE word = :word")
    suspend fun getDefinitions(word: String): List<DictionaryEntry>

    @Query("SELECT * FROM dictionary_entries WHERE wordIndex = :wordIndex")
    suspend fun getDefinitionsByIndex(wordIndex: Int): List<DictionaryEntry>

    @Query("SELECT * FROM synonym_entries WHERE synonym = :synonym")
    suspend fun getSynonyms(synonym: String): List<SynonymEntry>

    @Query("SELECT * FROM dictionary_entries WHERE word LIKE :word || '%' LIMIT 10")
    suspend fun getPrefixDefinitions(word: String): List<DictionaryEntry>
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE dictionary_entries ADD COLUMN wordIndex INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE IF NOT EXISTS `synonym_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `synonym` TEXT NOT NULL, `originalWordIndex` INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_synonym_entries_synonym` ON `synonym_entries` (`synonym`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Recreate dictionary_entries with COLLATE NOCASE on word
        db.execSQL("CREATE TABLE IF NOT EXISTS `dictionary_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `wordIndex` INTEGER NOT NULL DEFAULT 0, `word` TEXT COLLATE NOCASE NOT NULL, `definition` TEXT NOT NULL)")
        db.execSQL("INSERT INTO `dictionary_entries_new` (`id`, `wordIndex`, `word`, `definition`) SELECT `id`, `wordIndex`, `word`, `definition` FROM `dictionary_entries`")
        db.execSQL("DROP TABLE `dictionary_entries`")
        db.execSQL("ALTER TABLE `dictionary_entries_new` RENAME TO `dictionary_entries`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dictionary_entries_word` ON `dictionary_entries` (`word`)")

        // Recreate synonym_entries with COLLATE NOCASE on synonym
        db.execSQL("CREATE TABLE IF NOT EXISTS `synonym_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `synonym` TEXT COLLATE NOCASE NOT NULL, `originalWordIndex` INTEGER NOT NULL)")
        db.execSQL("INSERT INTO `synonym_entries_new` (`id`, `synonym`, `originalWordIndex`) SELECT `id`, `synonym`, `originalWordIndex` FROM `synonym_entries`")
        db.execSQL("DROP TABLE `synonym_entries`")
        db.execSQL("ALTER TABLE `synonym_entries_new` RENAME TO `synonym_entries`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_synonym_entries_synonym` ON `synonym_entries` (`synonym`)")
    }
}

@Database(entities = [DictionaryEntry::class, SynonymEntry::class], version = 3, exportSchema = false)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        @Volatile
        private var instances = ConcurrentHashMap<String, DictionaryDatabase>()

        fun getDatabase(context: Context, dictionaryId: String): DictionaryDatabase {
            return instances.getOrPut(dictionaryId) {
                Room.databaseBuilder(
                    context.applicationContext,
                    DictionaryDatabase::class.java,
                    "dict_$dictionaryId.db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
            }
        }

        fun closeDatabase(dictionaryId: String) {
            instances.remove(dictionaryId)?.close()
        }
    }
}
