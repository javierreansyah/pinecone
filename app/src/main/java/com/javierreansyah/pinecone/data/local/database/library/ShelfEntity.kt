package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "shelves",
    foreignKeys = [
        ForeignKey(
            entity = SpaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["spaceId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("spaceId")]
)
data class ShelfEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val spaceId: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

