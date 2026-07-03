package com.javierreansyah.pinecone.data.model

import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import kotlinx.serialization.Serializable

@Serializable
data class DictionaryBackupPayload(
    val version: Int = 1,
    val installedDictionaries: List<InstalledDictionary>,
    val activeDictionaryId: String
)
