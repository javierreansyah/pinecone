package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "publication_operations", indices = [Index("bookId")])
data class OperationEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val state: String,
    val generation: Long,
    val sequence: Long,
    val createdAt: Long,
    val bookId: String? = null,
    val sourceHash: String? = null,
    val converterVersion: Int? = null,
    val forceReparse: Boolean = false,
    val attempt: String? = null,
    val completed: Int = 0,
    val total: Int = 0,
    val error: String? = null,
    val acknowledged: Boolean = false,
)

@Entity(tableName = "publication_operation_inputs", primaryKeys = ["operationId", "ordinal"],
    foreignKeys = [ForeignKey(entity = OperationEntity::class, parentColumns = ["id"], childColumns = ["operationId"], onDelete = ForeignKey.CASCADE)])
data class OperationInputEntity(
    val operationId: String,
    val ordinal: Int,
    val uri: String,
    val displayName: String,
    val folder: Boolean = false,
    val stagedPath: String? = null,
    val grantUri: String? = null,
    val folderPathJson: String? = null,
    val state: String = "PENDING",
    val bookId: String? = null,
    val error: String? = null,
)

@Entity(tableName = "library_runtime")
data class LibraryRuntimeEntity(@PrimaryKey val id: Int = 1, val generation: Long = 0, val sequence: Long = 0, val maintenance: Boolean = false)

@Entity(tableName = "publication_deletions")
data class PublicationDeletionEntity(@PrimaryKey val bookId: String, val sequence: Long)

@Entity(tableName = "operation_uri_grants")
data class OperationUriGrantEntity(@PrimaryKey val uri: String)

@Dao
interface OperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun ownGrant(grant: OperationUriGrantEntity)
    @Query("SELECT uri FROM operation_uri_grants WHERE uri NOT IN (SELECT i.grantUri FROM publication_operation_inputs i INNER JOIN publication_operations o ON o.id = i.operationId WHERE i.grantUri IS NOT NULL AND o.state IN ('PREPARING','QUEUED','RUNNING'))") suspend fun unusedGrants(): List<String>
    @Query("DELETE FROM operation_uri_grants WHERE uri = :uri") suspend fun forgetGrant(uri: String)
    @Insert suspend fun insert(operation: OperationEntity)
    @Update suspend fun update(operation: OperationEntity)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertInputs(inputs: List<OperationInputEntity>)
    @Update suspend fun updateInput(input: OperationInputEntity)
    @Query("SELECT * FROM publication_operations WHERE id = :id") suspend fun get(id: String): OperationEntity?
    @Query("SELECT * FROM publication_operations WHERE id = :id") fun observe(id: String): Flow<OperationEntity?>
    @Query("SELECT * FROM publication_operation_inputs WHERE operationId = :id ORDER BY ordinal") suspend fun inputs(id: String): List<OperationInputEntity>
    @Query("SELECT * FROM publication_operations WHERE state IN ('PREPARING','QUEUED','RUNNING') ORDER BY createdAt") suspend fun resumable(): List<OperationEntity>
    @Query("SELECT DISTINCT o.id FROM publication_operations o INNER JOIN publication_operation_inputs i ON i.operationId = o.id WHERE o.state NOT IN ('PREPARING','QUEUED','RUNNING') AND i.stagedPath IS NOT NULL") suspend fun finishedWithStaging(): List<String>
    @Query("SELECT stagedPath FROM publication_operation_inputs WHERE stagedPath IS NOT NULL") suspend fun stagedPaths(): List<String>
    @Query("DELETE FROM publication_operations WHERE acknowledged = 1 AND createdAt < :before AND state NOT IN ('PREPARING','QUEUED','RUNNING')") suspend fun prune(before: Long)
    @Query("SELECT * FROM publication_operations WHERE acknowledged = 0 AND kind != 'READ_MODE' ORDER BY createdAt") fun imports(): Flow<List<OperationEntity>>
    @Query("SELECT * FROM publication_operations WHERE bookId = :bookId AND kind = 'READ_MODE' ORDER BY createdAt DESC, rowid DESC LIMIT 1") fun conversion(bookId: String): Flow<OperationEntity?>
    @Query("SELECT * FROM publication_operations WHERE bookId = :bookId AND state IN ('QUEUED','RUNNING') LIMIT 1") suspend fun activeConversion(bookId: String): OperationEntity?
    @Query("UPDATE publication_operations SET acknowledged = 1 WHERE id = :id") suspend fun acknowledge(id: String)
    @Query("SELECT * FROM library_runtime WHERE id = 1") suspend fun runtimeOrNull(): LibraryRuntimeEntity?
    suspend fun runtime(): LibraryRuntimeEntity = runtimeOrNull() ?: LibraryRuntimeEntity()
    @Query("SELECT generation FROM library_runtime WHERE id = 1") fun generation(): Flow<Long>
    @Query("SELECT * FROM library_runtime WHERE id = 1") fun runtimeFlow(): Flow<LibraryRuntimeEntity?>
    @Query("UPDATE library_runtime SET sequence = sequence + 1 WHERE id = 1") suspend fun advanceSequence()
    @Query("UPDATE library_runtime SET generation = generation + 1, maintenance = 1 WHERE id = 1") suspend fun beginRestore()
    @Query("UPDATE library_runtime SET maintenance = 0 WHERE id = 1") suspend fun finishRestore()
    @Query("UPDATE publication_operations SET state = 'CANCELLED', error = 'library_replaced', attempt = NULL WHERE state IN ('PREPARING','QUEUED','RUNNING')") suspend fun invalidateAll()
    @Query("UPDATE publication_operations SET state = 'CANCELLED', error = 'book_deleted', attempt = NULL WHERE bookId = :bookId AND state IN ('QUEUED','RUNNING')") suspend fun invalidateBook(bookId: String)
    @Query("UPDATE publication_operations SET state = 'CANCELLED', error = 'book_deleted', attempt = NULL WHERE bookId IN (:ids) AND state IN ('QUEUED','RUNNING')") suspend fun invalidateBooks(ids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun recordDeletion(deletion: PublicationDeletionEntity)
    @Query("SELECT sequence FROM publication_deletions WHERE bookId = :bookId") suspend fun deletionSequence(bookId: String): Long?
}
