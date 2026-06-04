package com.example.readerapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadDate DESC, addedDate DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("UPDATE books SET progression = :progression, lastLocatorJson = :lastLocatorJson, lastReadDate = :lastReadDate WHERE id = :id")
    suspend fun updateProgress(id: String, progression: Double, lastLocatorJson: String?, lastReadDate: Long)

    @Query("SELECT COUNT(*) FROM books WHERE id = :id")
    suspend fun exists(id: String): Int

    @Query("DELETE FROM books")
    suspend fun deleteAll()

    @Query("SELECT * FROM books")
    suspend fun getAllBooksSync(): List<BookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<BookEntity>)
}
