package com.example.readerapp.ui.features.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.database.dictionary.DictionaryEntry
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.repository.dictionary.DictionaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DefinitionState(
    val showDefinition: Boolean = false,
    val definitionWord: String = "",
    val definitionResults: List<DictionaryEntry> = emptyList()
)

class ReaderDictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val readerPreferences: ReaderPreferences
) : ViewModel() {

    private val _definitionState = MutableStateFlow(DefinitionState())
    val definitionState: StateFlow<DefinitionState> = _definitionState.asStateFlow()

    fun lookupDefinition(word: String) {
        val cleanWord = word.trim().replace(Regex("[^\\w\\s-]"), "")
        if (cleanWord.isBlank()) return

        viewModelScope.launch {
            val activeDictId = readerPreferences.readerSettings.first().activeDictionaryId
            val results = dictionaryRepository.lookupWord(activeDictId, cleanWord)
            _definitionState.update {
                it.copy(
                    showDefinition = true, definitionWord = cleanWord, definitionResults = results
                )
            }
        }
    }

    fun hideDefinition() {
        _definitionState.update { it.copy(showDefinition = false) }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = application as ReaderApplication
            return ReaderDictionaryViewModel(
                dictionaryRepository = app.dictionaryRepository,
                readerPreferences = app.readerPreferences
            ) as T
        }
    }
}
