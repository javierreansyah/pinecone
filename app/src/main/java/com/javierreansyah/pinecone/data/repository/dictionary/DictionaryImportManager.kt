package com.javierreansyah.pinecone.data.repository.dictionary

import android.content.Context
import android.net.Uri
import com.javierreansyah.pinecone.data.local.database.dictionary.StardictParser
import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DictionaryImportManager @Inject constructor(
    @ApplicationContext context: Context, private val preferences: ReaderPreferences,
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

            preferences.updateSettings { current ->
                current.copy(
                    installedDictionaries = current.installedDictionaries + InstalledDictionary(
                        id = dictId, name = info.name, wordCount = info.wordCount,
                    ),
                    activeDictionaryId = current.activeDictionaryId.ifEmpty { dictId },
                )
            }

            _importState.value = DictionaryState.Success
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            timber.log.Timber.e(e)
            _importState.value = DictionaryState.Error(e.message ?: "Unknown error occurred")
        }
    }

    fun resetImportState() {
        _importState.value = DictionaryState.Idle
    }
}
