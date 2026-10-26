package com.javierreansyah.pinecone.data.repository.dictionary

import android.content.Context
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryEntry
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryStore
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepository @Inject constructor(
    @ApplicationContext private val context: Context, private val preferences: ReaderPreferences,
    private val dictionaries: DictionaryStore,
) {
    suspend fun lookupWord(dictionaryId: String, word: String): List<DictionaryEntry> {
        if (dictionaryId.isEmpty()) return emptyList()
        return dictionaries.withDictionary(dictionaryId) { dao ->
            val results = mutableListOf<DictionaryEntry>()

            results.addAll(dao.getDefinitions(word))

            val synonyms = dao.getSynonyms(word)
            for ((_, _, synOriginalWordIndex) in synonyms) {
                val baseEntries = dao.getDefinitionsByIndex(synOriginalWordIndex)
                results.addAll(baseEntries)
            }

            if (results.isEmpty()) {
                results.addAll(dao.getPrefixDefinitions(word))
            }

            results.distinctBy { it.definition }
        }.orEmpty()
    }

    suspend fun deleteDictionary(dictionaryId: String) = dictionaries.withExclusiveFiles {

        preferences.updateSettings { current ->
            val installed = current.installedDictionaries.filter { it.id != dictionaryId }
            current.copy(
                installedDictionaries = installed,
                activeDictionaryId = if (current.activeDictionaryId == dictionaryId) {
                    installed.firstOrNull()?.id.orEmpty()
                } else current.activeDictionaryId,
            )
        }

        context.deleteDatabase("dict_$dictionaryId.db")
    }

    suspend fun renameDictionary(dictionaryId: String, newName: String) {
        preferences.updateSettings { current ->
            current.copy(installedDictionaries = current.installedDictionaries.map {
                if (it.id == dictionaryId) it.copy(name = newName) else it
            })
        }
    }
}
