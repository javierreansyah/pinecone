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

@Entity(
    tableName = "dictionary_entries",
    indices = [Index(value = ["word"])]
)
data class DictionaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val word: String,
    val definition: String
)

@Dao
interface DictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<DictionaryEntry>)

    @Query("SELECT * FROM dictionary_entries WHERE word = :word COLLATE NOCASE")
    suspend fun getDefinitions(word: String): List<DictionaryEntry>

    // Fallback: try prefix or partial if exact match fails
    @Query("SELECT * FROM dictionary_entries WHERE word LIKE :word || '%' COLLATE NOCASE LIMIT 10")
    suspend fun getPrefixDefinitions(word: String): List<DictionaryEntry>

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun getWordCount(): Int
}

@Database(entities = [DictionaryEntry::class], version = 1, exportSchema = false)
abstract class DictionaryDatabase : RoomDatabase() {
    abstract fun dictionaryDao(): DictionaryDao

    companion object {
        fun getDatabase(context: Context, dictionaryId: String): DictionaryDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DictionaryDatabase::class.java,
                "dict_$dictionaryId.db"
            ).build()
        }
    }
}
