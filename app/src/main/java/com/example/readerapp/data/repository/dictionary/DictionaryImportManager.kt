package com.example.readerapp.data.repository.dictionary

import android.content.Context
import android.net.Uri
import com.example.readerapp.data.local.database.dictionary.StardictParser
import com.example.readerapp.data.local.preferences.InstalledDictionary
import com.example.readerapp.data.local.preferences.ReaderPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

class DictionaryImportManager(
    private val context: Context,
    private val preferences: ReaderPreferences
) {
    private val parser = StardictParser(context)

    private val _importState = MutableStateFlow<DictionaryState>(DictionaryState.Idle)
    val importState: StateFlow<DictionaryState> = _importState.asStateFlow()

    suspend fun importDictionary(uri: Uri) {
        _importState.value = DictionaryState.Loading(0)
        try {
            val (dictId, info) = parser.parseDictionary(uri) { progress ->
                _importState.value = DictionaryState.Loading(progress)
            }

            // Update installed dictionaries
            val currentSettings = preferences.readerSettings.first()
            val newInstalled = currentSettings.installedDictionaries + InstalledDictionary(
                id = dictId,
                name = info.name,
                wordCount = info.wordCount
            )
            
            // Set as active if it's the first one
            val newActiveId = currentSettings.activeDictionaryId.ifEmpty { dictId }

            preferences.updateSettings(
                currentSettings.copy(
                    installedDictionaries = newInstalled,
                    activeDictionaryId = newActiveId
                )
            )

            _importState.value = DictionaryState.Success
        } catch (e: Exception) {
            e.printStackTrace()
            _importState.value = DictionaryState.Error(e.message ?: "Unknown error occurred")
        }
    }

    fun resetImportState() {
        _importState.value = DictionaryState.Idle
    }
}
