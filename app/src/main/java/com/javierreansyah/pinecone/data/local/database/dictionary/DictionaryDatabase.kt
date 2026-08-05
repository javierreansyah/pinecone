package com.javierreansyah.pinecone.data.local.database.dictionary

import android.content.Context
import androidx.room.ColumnInfo
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
import java.util.concurrent.ConcurrentHashMap

@Entity(
    tableName = "dictionary_entries", indices = [Index(value = ["word"])]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val wordIndex: Int = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val word: String,
    val definition: String
)

@Entity(
    tableName = "synonym_entries", indices = [Index(value = ["synonym"])]
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

@Database(
    entities = [DictionaryEntry::class, SynonymEntry::class], version = 1, exportSchema = true
)
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
                ).fallbackToDestructiveMigration(false)
                    .build()
            }
        }

        fun closeDatabase(dictionaryId: String) {
            instances.remove(dictionaryId)?.close()
        }
    }
}
