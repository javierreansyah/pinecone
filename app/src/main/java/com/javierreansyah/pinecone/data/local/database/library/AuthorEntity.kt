package com.javierreansyah.pinecone.data.local.database.library

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "authors", indices = [Index(value = ["name"], unique = true)]
)
data class AuthorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String
)
