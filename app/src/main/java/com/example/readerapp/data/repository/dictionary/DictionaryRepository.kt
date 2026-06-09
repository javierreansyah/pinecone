package com.example.readerapp.data.repository.dictionary

import android.content.Context
import com.example.readerapp.data.local.preferences.ReaderPreferences
import kotlinx.coroutines.flow.first
import com.example.readerapp.data.local.database.dictionary.DictionaryDatabase
import com.example.readerapp.data.local.database.dictionary.DictionaryEntry

class DictionaryRepository(
    private val context: Context, private val preferences: ReaderPreferences
) {
    suspend fun lookupWord(dictionaryId: String, word: String): List<DictionaryEntry> {
        if (dictionaryId.isEmpty()) return emptyList()
        val db = DictionaryDatabase.getDatabase(context, dictionaryId)
        val dao = db.dictionaryDao()

        val results = mutableListOf<DictionaryEntry>()

        // Exact match
        results.addAll(dao.getDefinitions(word))

        // Synonyms
        val synonyms = dao.getSynonyms(word)
        for (syn in synonyms) {
            val baseEntries = dao.getDefinitionsByIndex(syn.originalWordIndex)
            results.addAll(baseEntries)
        }

        // If not found, try partial/prefix
        if (results.isEmpty()) {
            results.addAll(dao.getPrefixDefinitions(word))
        }

        // Return distinct definitions to avoid duplicates
        return results.distinctBy { it.definition }
    }

    suspend fun deleteDictionary(dictionaryId: String) {
        // Remove from settings
        val currentSettings = preferences.readerSettings.first()
        val newInstalled = currentSettings.installedDictionaries.filter { it.id != dictionaryId }
        val newActiveId = if (currentSettings.activeDictionaryId == dictionaryId) {
            newInstalled.firstOrNull()?.id ?: ""
        } else {
            currentSettings.activeDictionaryId
        }
        preferences.updateSettings(
            currentSettings.copy(
                installedDictionaries = newInstalled, activeDictionaryId = newActiveId
            )
        )

        // Close database to release SQLite file locks
        DictionaryDatabase.closeDatabase(dictionaryId)

        // Delete database files
        val dbFile = context.getDatabasePath("dict_$dictionaryId.db")
        if (dbFile.exists()) dbFile.delete()
        val walFile = context.getDatabasePath("dict_$dictionaryId.db-wal")
        if (walFile.exists()) walFile.delete()
        val shmFile = context.getDatabasePath("dict_$dictionaryId.db-shm")
        if (shmFile.exists()) shmFile.delete()
    }

    suspend fun renameDictionary(dictionaryId: String, newName: String) {
        val currentSettings = preferences.readerSettings.first()
        val newInstalled = currentSettings.installedDictionaries.map {
            if (it.id == dictionaryId) it.copy(name = newName) else it
        }
        preferences.updateSettings(
            currentSettings.copy(installedDictionaries = newInstalled)
        )
    }
}
