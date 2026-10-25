package com.javierreansyah.pinecone.ui.features.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryEntry
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.repository.dictionary.DictionaryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
    val wordHistory: List<Pair<String, List<DictionaryEntry>>> = emptyList(),
)

@HiltViewModel
class ReaderDictionaryViewModel @Inject constructor(
    private val dictionaryRepository: DictionaryRepository,
    private val readerPreferences: ReaderPreferences,
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
                    wordHistory = newHistory,
                )
            }
        }
    }

    fun popDefinition() {
        _definitionState.update { state ->
            if (state.wordHistory.isNotEmpty()) {
                val (previousFirst, previousSecond) = state.wordHistory.last()
                state.copy(
                    definitionWord = previousFirst,
                    definitionResults = previousSecond,
                    wordHistory = state.wordHistory.dropLast(1),
                )
            } else {
                state
            }
        }
    }

    fun hideDefinition() {
        _definitionState.update { it.copy(showDefinition = false, wordHistory = emptyList()) }
    }


}
