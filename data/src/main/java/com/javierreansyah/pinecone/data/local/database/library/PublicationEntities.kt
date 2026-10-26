package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Durable renditions, including generated EPUBs. Locations never cross rendition boundaries. */
@Serializable
@Entity(
    tableName = "publication_renditions",
    primaryKeys = ["bookId", "key"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId")],
)
data class PublicationRenditionEntity(
    val bookId: String,
    val key: String,
    val filePath: String,
    val mediaType: String,
    val profile: String,
    val sha256: String,
    val converterVersion: Int? = null,
    val sourceMapJson: String? = null,
    val lastLocatorJson: String? = null,
    val progression: Double = 0.0,
    val furthestLocatorJson: String? = null,
    val furthestProgression: Double = 0.0,
    val jumpOriginLocatorJson: String? = null,
    val preferencesJson: String = "{}",
)

/** A credit is publication-scoped: equal display names do not imply equal people. */
@Serializable
@Entity(
    tableName = "publication_credits",
    primaryKeys = ["bookId", "role", "position"],
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookId"), Index("name"), Index("role")],
)
data class PublicationCreditEntity(
    val bookId: String,
    val role: String,
    val position: Int,
    val name: String,
    val identifier: String? = null,
    val metadataJson: String = "{}",
)

@Dao
interface PublicationDao {
    @Query("SELECT * FROM publication_renditions WHERE bookId IN (:ids)")
    suspend fun renditionsForBooks(ids: List<String>): List<PublicationRenditionEntity>
    @Query("SELECT * FROM publication_renditions WHERE bookId = :bookId AND `key` = :key")
    suspend fun rendition(bookId: String, key: String): PublicationRenditionEntity?

    @Query("SELECT * FROM publication_renditions WHERE bookId = :bookId AND `key` = :key")
    fun renditionFlow(bookId: String, key: String): Flow<PublicationRenditionEntity?>

    @Query("SELECT * FROM publication_renditions WHERE bookId = :bookId")
    suspend fun renditions(bookId: String): List<PublicationRenditionEntity>

    @Query("SELECT * FROM publication_renditions")
    suspend fun allRenditions(): List<PublicationRenditionEntity>

    @Upsert
    suspend fun putRenditions(items: List<PublicationRenditionEntity>)

    @Query("SELECT * FROM publication_credits ORDER BY role, position")
    suspend fun allCredits(): List<PublicationCreditEntity>

    @Query("SELECT DISTINCT name FROM publication_credits WHERE role = :role ORDER BY name")
    fun contributorNames(role: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCredits(items: List<PublicationCreditEntity>)

    @Query("DELETE FROM publication_credits WHERE bookId = :bookId")
    suspend fun deleteCredits(bookId: String)

    @Query("UPDATE publication_credits SET name = :newName WHERE name = :oldName AND role IN ('author', 'writer')")
    suspend fun renamePrimaryCredit(oldName: String, newName: String)

    @Query("DELETE FROM publication_credits WHERE name = :name AND role IN ('author', 'writer')")
    suspend fun deletePrimaryCredit(name: String)
}
