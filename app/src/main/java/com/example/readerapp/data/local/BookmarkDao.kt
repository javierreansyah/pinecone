package com.example.readerapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getByBookId(bookId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks WHERE bookId = :bookId")
    suspend fun deleteAllForBook(bookId: String)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}
