package com.javierreansyah.pinecone.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ReaderPreferences internal constructor(private val store: DataStore<ReaderPreferencesData>) {
    constructor(context: Context) : this(context.readerPreferencesStore)

    val readerSettings: Flow<ReaderSettings> = store.data.map { it.settings }.distinctUntilChanged()

    /** Each edit is applied to the latest durable state, including concurrent backup updates. */
    suspend fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        store.updateData { it.copy(settings = transform(it.settings).normalized()) }
    }

    /** Full replacement is reserved for restoring a complete backup. */
    suspend fun updateAllSettings(settings: ReaderSettings) = updateSettings { settings }

    suspend fun markBackup(timestamp: Long, revision: Long) = updateSettings {
        it.copy(lastBackupTime = timestamp, lastBackupRevision = revision)
    }
}

internal fun ReaderSettings.normalized(): ReaderSettings = copy(
    fontSize = (fontSize * 100.0).roundToInt() / 100.0,
    lineHeight = (lineHeight * 10.0).roundToInt() / 10.0,
    paragraphSpacing = (paragraphSpacing * 100.0).roundToInt() / 100.0,
    paragraphIndent = (paragraphIndent * 100.0).roundToInt() / 100.0,
    wordSpacing = (wordSpacing * 100.0).roundToInt() / 100.0,
    letterSpacing = (letterSpacing * 1000.0).roundToInt() / 1000.0,
    pageMargins = (pageMargins * 100.0).roundToInt() / 100.0,
    typeScale = (typeScale * 100.0).roundToInt() / 100.0,
    verticalMargin = (verticalMargin * 10.0).roundToInt() / 10.0,
)
