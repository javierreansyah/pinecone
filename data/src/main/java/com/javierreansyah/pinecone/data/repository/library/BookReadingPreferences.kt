package com.javierreansyah.pinecone.data.repository.library

import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.local.preferences.decodeReadingOverrides
import com.javierreansyah.pinecone.data.local.preferences.encodeReadingOverrides
import com.javierreansyah.pinecone.data.local.preferences.withReadingPreferencesFrom
import com.javierreansyah.pinecone.data.model.ReadingSessionToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Serializes edits against the latest saved override, including edits from another reader. */
@Singleton
class BookReadingPreferences @Inject constructor(
    private val reading: ReadingRepository,
    private val global: ReaderPreferences,
) {
    private val mutex = Mutex()

    suspend fun update(
        bookId: String, key: String, session: ReadingSessionToken? = null,
        onLocalChange: (ReaderSettings?) -> Unit = {},
        transform: (ReaderSettings) -> ReaderSettings,
    ) = mutex.withLock {
        val local = decodeReadingOverrides(reading.rendition(bookId, key)?.preferencesJson)
        if (local == null) global.updateSettings(transform)
        else save(bookId, key, session, transform(effective(local)), onLocalChange)
    }

    suspend fun setEnabled(
        bookId: String, key: String, enabled: Boolean, session: ReadingSessionToken? = null,
        onLocalChange: (ReaderSettings?) -> Unit = {},
    ) = mutex.withLock {
        val settings = if (enabled) effective(
            decodeReadingOverrides(reading.rendition(bookId, key)?.preferencesJson)
        ) else null
        save(bookId, key, session, settings, onLocalChange)
    }

    private suspend fun effective(local: ReaderSettings?) =
        global.readerSettings.first().withReadingPreferencesFrom(local)

    private suspend fun save(
        bookId: String, key: String, session: ReadingSessionToken?, settings: ReaderSettings?,
        onLocalChange: (ReaderSettings?) -> Unit,
    ) {
        val local = settings?.let { ReaderSettings().withReadingPreferencesFrom(it) }
        onLocalChange(local)
        reading.setReadingOverrides(bookId, key, local?.encodeReadingOverrides() ?: "{}", session)
    }
}
