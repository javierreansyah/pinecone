package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "backup_state")
data class BackupStateEntity(
    @PrimaryKey val id: Int = 1,
    val revision: Long = 0L,
    val pendingSettingsJson: String? = null
)

@Dao
interface BackupStateDao {
    @Query("SELECT revision FROM backup_state WHERE id = 1")
    suspend fun revision(): Long

    @Query("SELECT pendingSettingsJson FROM backup_state WHERE id = 1")
    suspend fun pendingSettingsJson(): String?

    @Query("UPDATE backup_state SET pendingSettingsJson = :value WHERE id = 1")
    suspend fun setPendingSettingsJson(value: String?)
}
