package com.javierreansyah.pinecone.ui.features.dictionary

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryImportManager
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryRepository
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DictionariesViewModel @Inject constructor(
    private val repository: DictionaryRepository,
    private val importManager: DictionaryImportManager,
    preferences: ReaderPreferences,
) : ViewModel() {

    val importState: StateFlow<DictionaryState> = importManager.importState

    val installedDictionaries: StateFlow<List<InstalledDictionary>> = preferences.readerSettings
        .map { settings -> settings.installedDictionaries }
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


}
