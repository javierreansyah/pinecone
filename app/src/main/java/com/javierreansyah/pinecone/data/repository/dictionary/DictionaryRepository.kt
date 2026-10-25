package com.javierreansyah.pinecone.data.repository.dictionary

import android.content.Context
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryDatabase
import com.javierreansyah.pinecone.data.local.database.dictionary.DictionaryEntry
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryRepository @Inject constructor(
    @ApplicationContext private val context: Context, private val preferences: ReaderPreferences,
) {
    suspend fun lookupWord(dictionaryId: String, word: String): List<DictionaryEntry> {
        if (dictionaryId.isEmpty()) return emptyList()
        val db = DictionaryDatabase.getDatabase(context, dictionaryId)
        val dao = db.dictionaryDao()

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

        return results.distinctBy { it.definition }
    }

    suspend fun deleteDictionary(dictionaryId: String) {

        preferences.updateSettings { current ->
            val installed = current.installedDictionaries.filter { it.id != dictionaryId }
            current.copy(
                installedDictionaries = installed,
                activeDictionaryId = if (current.activeDictionaryId == dictionaryId) {
                    installed.firstOrNull()?.id.orEmpty()
                } else current.activeDictionaryId,
            )
        }

        DictionaryDatabase.closeDatabase(dictionaryId)

        val dbFile = context.getDatabasePath("dict_$dictionaryId.db")
        if (dbFile.exists()) dbFile.delete()
        val walFile = context.getDatabasePath("dict_$dictionaryId.db-wal")
        if (walFile.exists()) walFile.delete()
        val shmFile = context.getDatabasePath("dict_$dictionaryId.db-shm")
        if (shmFile.exists()) shmFile.delete()
    }

    suspend fun renameDictionary(dictionaryId: String, newName: String) {
        preferences.updateSettings { current ->
            current.copy(installedDictionaries = current.installedDictionaries.map {
                if (it.id == dictionaryId) it.copy(name = newName) else it
            })
        }
    }
}
