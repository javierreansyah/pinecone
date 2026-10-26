package com.javierreansyah.pinecone.data.local.preferences

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

internal val PreferenceJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

@Serializable
internal data class ReaderPreferencesData(
    val settings: ReaderSettings = ReaderSettings(),
    val legacyMigrationComplete: Boolean = false,
)

/** A single delegate per file prevents competing DataStore instances. Legacy files stay read-only. */
private val Context.legacyReaderPreferences by preferencesDataStore(name = "reader_settings")

internal val Context.readerPreferencesStore by dataStore(
    fileName = "reader_preferences.json",
    serializer = JsonPreferenceSerializer(
        ReaderPreferencesData.serializer(),
        ReaderPreferencesData()
    ),
    produceMigrations = { context ->
        listOf(ReaderPreferencesMigration { context.legacyReaderPreferences.data.first() })
    },
)

internal class ReaderPreferencesMigration(
    private val readLegacy: suspend () -> androidx.datastore.preferences.core.Preferences,
) : DataMigration<ReaderPreferencesData> {
    override suspend fun shouldMigrate(currentData: ReaderPreferencesData): Boolean =
        !currentData.legacyMigrationComplete

    override suspend fun migrate(currentData: ReaderPreferencesData): ReaderPreferencesData =
        currentData.copy(
            settings = LegacyReaderSettings.read(readLegacy()),
            legacyMigrationComplete = true
        )

    // Keep the original file for recovery; the durable marker prevents reimporting it.
    override suspend fun cleanUp() = Unit
}

internal class JsonPreferenceSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T = try {
        PreferenceJson.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (error: SerializationException) {
        throw CorruptionException("Cannot decode saved preferences", error)
    } catch (error: IllegalArgumentException) {
        throw CorruptionException("Invalid saved preferences", error)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(PreferenceJson.encodeToString(serializer, t).encodeToByteArray())
    }
}
