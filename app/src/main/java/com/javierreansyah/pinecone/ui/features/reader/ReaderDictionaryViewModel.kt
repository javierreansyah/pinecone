package com.javierreansyah.pinecone.ui.features.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryEntry
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DefinitionState(
    val showDefinition: Boolean = false,
    val definitionWord: String = "",
    val definitionResults: List<DictionaryEntry> = emptyList(),
    val wordHistory: List<Pair<String, List<DictionaryEntry>>> = emptyList()
)

class ReaderDictionaryViewModel(
    private val dictionaryRepository: DictionaryRepository,
    private val readerPreferences: ReaderPreferences
) : ViewModel() {

    private val _definitionState = MutableStateFlow(DefinitionState())
    val definitionState: StateFlow<DefinitionState> = _definitionState.asStateFlow()

    fun lookupDefinition(word: String, clearHistory: Boolean = false) {
        val cleanWord = word.trim().replace(Regex("[^\\w\\s-]"), "")
        if (cleanWord.isBlank()) return

        viewModelScope.launch {
            val activeDictId = readerPreferences.readerSettings.first().activeDictionaryId
            val results = dictionaryRepository.lookupWord(activeDictId, cleanWord)
            _definitionState.update { state ->
                val newHistory = if (clearHistory) {
                    emptyList()
                } else if (state.showDefinition && state.definitionWord.isNotBlank() && cleanWord != state.definitionWord) {
                    state.wordHistory + (state.definitionWord to state.definitionResults)
                } else {
                    state.wordHistory
                }

                state.copy(
                    showDefinition = true,
                    definitionWord = cleanWord,
                    definitionResults = results,
                    wordHistory = newHistory
                )
            }
        }
    }

    fun popDefinition() {
        _definitionState.update { state ->
            if (state.wordHistory.isNotEmpty()) {
                val previous = state.wordHistory.last()
                state.copy(
                    definitionWord = previous.first,
                    definitionResults = previous.second,
                    wordHistory = state.wordHistory.dropLast(1)
                )
            } else {
                state
            }
        }
    }

    fun hideDefinition() {
        _definitionState.update { it.copy(showDefinition = false, wordHistory = emptyList()) }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = application as PineconeApplication
            return ReaderDictionaryViewModel(
                dictionaryRepository = app.dictionaryRepository,
                readerPreferences = app.readerPreferences
            ) as T
        }
    }
}
