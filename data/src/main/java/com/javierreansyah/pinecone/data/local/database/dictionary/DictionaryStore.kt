package com.javierreansyah.pinecone.data.local.database.dictionary

import android.content.Context
import androidx.room.Room
import com.javierreansyah.pinecone.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** A DAO is usable only inside its lease. File replacement waits for every current lease. */
@Singleton
class DictionaryStore @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val mutex = Mutex()
    private val databases = mutableMapOf<String, DictionaryDatabase>()

    suspend fun <T> withDictionary(id: String, block: suspend (DictionaryDao) -> T): T? =
        withContext(ioDispatcher) {
            mutex.withLock {
                if (!context.getDatabasePath(fileName(id)).isFile) return@withLock null
                block(database(id).dictionaryDao())
            }
        }

    internal suspend fun <T> create(id: String, block: suspend (DictionaryDatabase) -> T): T =
        withContext(ioDispatcher) { mutex.withLock { block(database(id)) } }

    /** The block may access files directly, but must not acquire a dictionary lease recursively. */
    suspend fun <T> withExclusiveFiles(block: suspend () -> T): T = withContext(ioDispatcher) {
        mutex.withLock {
            databases.values.forEach { it.close() }
            databases.clear()
            block()
        }
    }

    suspend fun delete(id: String) = withExclusiveFiles {
        context.deleteDatabase(fileName(id))
    }

    private fun database(id: String): DictionaryDatabase = databases.getOrPut(id) {
        Room.databaseBuilder(context, DictionaryDatabase::class.java, fileName(id))
            .addMigrations(DictionaryDatabase.MIGRATION_1_2)
            .build()
    }

    private fun fileName(id: String): String {
        require(validDictionaryId(id))
        return "dict_$id.db"
    }
}

internal fun validDictionaryId(id: String): Boolean =
    id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
