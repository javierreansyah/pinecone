package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun getAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id")
    suspend fun getCollectionById(id: String): CollectionEntity?

    @Query("SELECT * FROM collections WHERE name = :name")
    suspend fun getCollectionByName(name: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCollection(collection: CollectionEntity)

    @Delete
    suspend fun deleteCollection(collection: CollectionEntity)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollectionById(id: String)

    @Query("UPDATE collections SET name = :newName WHERE id = :id")
    suspend fun renameCollection(id: String, newName: String)

    @Query("DELETE FROM collections WHERE id NOT IN (SELECT DISTINCT collectionId FROM books WHERE collectionId IS NOT NULL)")
    suspend fun deleteOrphanCollections()
}
