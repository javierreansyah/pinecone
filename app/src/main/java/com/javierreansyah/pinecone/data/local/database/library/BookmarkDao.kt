package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId ORDER BY createdAt DESC")
    fun getByBookId(bookId: String): Flow<List<BookmarkEntity>>

    @Insert
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()

    @Query("SELECT * FROM bookmarks")
    suspend fun getAllBookmarksSync(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bookmarks: List<BookmarkEntity>)
    @Query("SELECT * FROM bookmarks WHERE bookId = :bookId AND renditionKey = :renditionKey")
    suspend fun getByRendition(bookId: String, renditionKey: String): List<BookmarkEntity>

    @Query("UPDATE bookmarks SET locatorJson = :locatorJson WHERE id = :id")
    suspend fun updateLocator(id: Long, locatorJson: String)
}
