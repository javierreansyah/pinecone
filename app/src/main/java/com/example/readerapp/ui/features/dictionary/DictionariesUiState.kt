package com.example.readerapp.ui.features.dictionary

import com.example.readerapp.data.local.preferences.InstalledDictionary
import com.example.readerapp.data.repository.dictionary.DictionaryState

data class DictionariesUiState(
    val installedDictionaries: List<InstalledDictionary> = emptyList(),
    val importState: DictionaryState = DictionaryState.Idle,
    val restoreState: DictionaryState = DictionaryState.Idle,
    val backupState: DictionaryState = DictionaryState.Idle
)
