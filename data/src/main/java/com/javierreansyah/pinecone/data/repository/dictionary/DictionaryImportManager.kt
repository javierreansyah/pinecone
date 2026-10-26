package com.javierreansyah.pinecone.data.repository.dictionary

import android.net.Uri
import com.javierreansyah.pinecone.data.local.database.dictionary.StardictParser
import com.javierreansyah.pinecone.data.local.preferences.InstalledDictionary
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class DictionaryImportManager @Inject constructor(
    private val preferences: ReaderPreferences,
    private val parser: StardictParser,
    private val dictionaries: com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryStore,
) {
    private val importMutex = kotlinx.coroutines.sync.Mutex()

    private val _importState = MutableStateFlow<DictionaryState>(DictionaryState.Idle)
    val importState: StateFlow<DictionaryState> = _importState.asStateFlow()

    suspend fun importDictionary(uri: Uri) {
        if (!importMutex.tryLock()) return
        var importedId: String? = null
        var installed = false
        _importState.value = DictionaryState.Loading(0)
        try {
            val (dictId, info) = parser.parseDictionary(uri) { progress ->
                _importState.value = DictionaryState.Loading(progress)
            }
            importedId = dictId
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                preferences.updateSettings { current ->
                    current.copy(
                        installedDictionaries = current.installedDictionaries + InstalledDictionary(
                            id = dictId, name = info.name, wordCount = info.wordCount,
                        ),
                        activeDictionaryId = current.activeDictionaryId.ifEmpty { dictId },
                    )
                }
                installed = true
            }

            _importState.value = DictionaryState.Success
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            _importState.value = DictionaryState.Idle
            throw cancelled
        } catch (e: Exception) {
            timber.log.Timber.e(e)
            _importState.value = DictionaryState.Error(e.message ?: "Unknown error occurred")
        } finally {
            try {
                if (!installed) importedId?.let { id ->
                    kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { dictionaries.delete(id) }
                }
            } finally {
                importMutex.unlock()
            }
        }
    }

    fun resetImportState() {
        _importState.value = DictionaryState.Idle
    }
}
