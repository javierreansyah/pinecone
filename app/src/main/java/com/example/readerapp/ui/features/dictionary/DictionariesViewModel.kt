package com.example.readerapp.ui.features.dictionary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.data.local.preferences.InstalledDictionary
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.repository.dictionary.DictionaryImportManager
import com.example.readerapp.data.repository.dictionary.DictionaryRepository
import com.example.readerapp.data.repository.dictionary.DictionaryState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DictionariesViewModel(
    private val repository: DictionaryRepository,
    private val importManager: DictionaryImportManager,
    preferences: ReaderPreferences
) : ViewModel() {

    val importState: StateFlow<DictionaryState> = importManager.importState

    val installedDictionaries: StateFlow<List<InstalledDictionary>> = preferences.readerSettings
        .map { it.installedDictionaries }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun importDictionary(uri: Uri) {
        viewModelScope.launch {
            importManager.importDictionary(uri)
        }
    }

    fun resetImportState() {
        importManager.resetImportState()
    }


    fun deleteDictionary(id: String) {
        viewModelScope.launch {
            repository.deleteDictionary(id)
        }
    }

    fun renameDictionary(id: String, newName: String) {
        viewModelScope.launch {
            repository.renameDictionary(id, newName)
        }
    }

    class Factory(
        private val repository: DictionaryRepository,
        private val importManager: DictionaryImportManager,
        private val preferences: ReaderPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DictionariesViewModel(repository, importManager, preferences) as T
        }
    }
}
