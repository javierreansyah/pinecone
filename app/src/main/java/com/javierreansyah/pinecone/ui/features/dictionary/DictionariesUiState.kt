package com.javierreansyah.pinecone.ui.features.dictionary

import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryState

data class DictionariesUiState(
    val installedDictionaries: List<InstalledDictionary> = emptyList(),
    val importState: DictionaryState = DictionaryState.Idle
)
