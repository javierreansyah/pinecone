package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import kotlinx.serialization.Serializable

@Serializable
data class DictionaryBackupPayload(
    val version: Int = 1,
    val installedDictionaries: List<InstalledDictionary>,
    val activeDictionaryId: String
)

@Serializable
data class DictionaryBackupManifest(
    val format: String = "pinecone-dictionary",
    val formatVersion: Int = 1,
    val createdAt: Long,
    val dictionary: InstalledDictionary,
    val databaseSize: Long,
    val databaseSha256: String
)

@Serializable
data class DictionaryIndex(
    val format: String = "pinecone-dictionary-index",
    val formatVersion: Int = 1,
    val activeDictionaryId: String,
    val dictionaries: List<DictionaryIndexEntry>
)

@Serializable
data class DictionaryIndexEntry(
    val dictionary: InstalledDictionary,
    val fileName: String,
    val sha256: String,
    val sourceSize: Long = 0L,
    val sourceLastModified: Long = 0L
)
