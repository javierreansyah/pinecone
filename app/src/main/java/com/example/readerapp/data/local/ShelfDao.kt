package com.example.readerapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class ShelfWithCovers(
    @Embedded val shelf: ShelfEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ShelfBookCrossRefEntity::class,
            parentColumn = "shelfId",
            entityColumn = "bookId"
        )
    )
    val books: List<BookEntity>
)

@Dao
interface ShelfDao {
    @Transaction
    @Query("SELECT * FROM shelves ORDER BY createdAt DESC")
    fun getAllShelvesWithBooks(): Flow<List<ShelfWithCovers>>

    @Query("SELECT * FROM shelves WHERE id = :shelfId")
    suspend fun getShelfById(shelfId: String): ShelfEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShelf(shelf: ShelfEntity)

    @Delete
    suspend fun deleteShelf(shelf: ShelfEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShelfBookCrossRef(crossRef: ShelfBookCrossRefEntity)

    @Query("DELETE FROM shelf_book_cross_ref WHERE shelfId = :shelfId AND bookId = :bookId")
    suspend fun deleteShelfBookCrossRef(shelfId: String, bookId: String)
}
