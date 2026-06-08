package com.example.readerapp.ui.features.dictionary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.data.local.InstalledDictionary
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.data.local.dictionary.DictionaryRepository
import com.example.readerapp.data.local.dictionary.ImportState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DictionariesViewModel(
    private val repository: DictionaryRepository,
    private val preferences: ReaderPreferences
) : ViewModel() {

    val importState: StateFlow<ImportState> = repository.importState
    val restoreState: StateFlow<ImportState> = repository.restoreState
    
    val installedDictionaries: StateFlow<List<InstalledDictionary>> = preferences.readerSettings
        .map { it.installedDictionaries }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun importDictionary(uri: Uri) {
        viewModelScope.launch {
            repository.importDictionary(uri)
        }
    }

    fun resetImportState() {
        repository.resetImportState()
    }

    fun resetRestoreState() {
        repository.resetRestoreState()
    }

    val backupState: StateFlow<ImportState> = repository.backupState

    fun resetBackupState() {
        repository.resetBackupState()
    }

    fun backupDictionaries() {
        viewModelScope.launch {
            repository.backupDictionaries()
        }
    }

    fun restoreDictionary(uri: Uri) {
        viewModelScope.launch {
            repository.restoreDictionaries(uri)
        }
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
        private val preferences: ReaderPreferences
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DictionariesViewModel(repository, preferences) as T
        }
    }
}
