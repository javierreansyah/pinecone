package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpaceDao {
    @Query("SELECT * FROM spaces ORDER BY name ASC")
    fun getAllSpaces(): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE id = :id")
    suspend fun getSpaceById(id: String): SpaceEntity?

    @Query("SELECT * FROM spaces WHERE name = :name")
    suspend fun getSpaceByName(name: String): SpaceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpace(space: SpaceEntity)

    @Delete
    suspend fun deleteSpace(space: SpaceEntity)

    @Query("DELETE FROM spaces WHERE id = :id")
    suspend fun deleteSpaceById(id: String)

    @Query("UPDATE spaces SET name = :newName WHERE id = :id")
    suspend fun renameSpace(id: String, newName: String)

    @Query("DELETE FROM spaces WHERE id NOT IN (SELECT DISTINCT spaceId FROM book_space_cross_ref)")
    suspend fun deleteOrphanSpaces()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBookSpaceCrossRef(crossRef: BookSpaceCrossRef)
    
    @Query("DELETE FROM book_space_cross_ref WHERE spaceId = :spaceId AND bookId = :bookId")
    suspend fun deleteBookSpaceCrossRef(spaceId: String, bookId: String)
    
    @Query("DELETE FROM book_space_cross_ref WHERE bookId = :bookId")
    suspend fun deleteBookSpaceCrossRefsByBookId(bookId: String)
    
    @Query("SELECT * FROM book_space_cross_ref")
    fun getAllBookSpaceCrossRefs(): Flow<List<BookSpaceCrossRef>>

    @Query("UPDATE OR IGNORE book_space_cross_ref SET spaceId = :newId WHERE spaceId = :oldId")
    suspend fun mergeBookSpaceCrossRef(oldId: String, newId: String)
}
