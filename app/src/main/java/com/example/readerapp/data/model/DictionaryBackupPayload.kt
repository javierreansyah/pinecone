package com.example.readerapp.data.model

import com.example.readerapp.data.local.preferences.InstalledDictionary
import kotlinx.serialization.Serializable

@Serializable
data class DictionaryBackupPayload(
    val version: Int = 1,
    val installedDictionaries: List<InstalledDictionary>,
    val activeDictionaryId: String
)
