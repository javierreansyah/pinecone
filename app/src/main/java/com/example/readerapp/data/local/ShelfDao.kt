package com.example.readerapp.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class ShelfWithCovers(
    @Embedded val shelf: ShelfEntity,
    @Relation(
        entity = BookEntity::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ShelfBookCrossRefEntity::class,
            parentColumn = "shelfId",
            entityColumn = "bookId"
        )
    )
    val books: List<BookWithDetails>
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

    @Update
    suspend fun updateShelf(shelf: ShelfEntity)

    @Delete
    suspend fun deleteShelf(shelf: ShelfEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShelfBookCrossRef(crossRef: ShelfBookCrossRefEntity)

    @Query("DELETE FROM shelf_book_cross_ref WHERE shelfId = :shelfId AND bookId = :bookId")
    suspend fun deleteShelfBookCrossRef(shelfId: String, bookId: String)

    @Query("SELECT * FROM shelf_book_cross_ref")
    fun getAllShelfBookCrossRefs(): Flow<List<ShelfBookCrossRefEntity>>

    @Query("UPDATE shelf_book_cross_ref SET orderIndex = :orderIndex WHERE shelfId = :shelfId AND bookId = :bookId")
    suspend fun updateShelfBookOrderIndex(shelfId: String, bookId: String, orderIndex: Int)

    @Query("SELECT * FROM shelves")
    suspend fun getAllShelvesSync(): List<ShelfEntity>

    @Query("SELECT * FROM shelf_book_cross_ref")
    suspend fun getAllShelfBookCrossRefsSync(): List<ShelfBookCrossRefEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllShelves(shelves: List<ShelfEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllShelfBookCrossRefs(crossRefs: List<ShelfBookCrossRefEntity>)

    @Query("DELETE FROM shelves")
    suspend fun deleteAllShelves()

    @Query("DELETE FROM shelf_book_cross_ref")
    suspend fun deleteAllShelfBookCrossRefs()

    @Query("DELETE FROM shelves WHERE id NOT IN (SELECT shelfId FROM shelf_book_cross_ref)")
    suspend fun deleteOrphanShelves()
}
