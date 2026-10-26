package com.javierreansyah.pinecone.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import com.javierreansyah.pinecone.data.model.library.FilterSortPreferences
import com.javierreansyah.pinecone.data.model.library.LayoutMode
import com.javierreansyah.pinecone.data.model.library.SortType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

@Serializable
internal data class LibraryPreferencesData(
    val globalSpaceId: String? = null,
    val screens: Map<String, FilterSortPreferences> = emptyMap(),
    val legacyMigrationComplete: Boolean = false,
)

internal val Context.libraryPreferencesStore by dataStore(
    fileName = "library_preferences.json",
    serializer = JsonPreferenceSerializer(
        LibraryPreferencesData.serializer(),
        LibraryPreferencesData()
    ),
    produceMigrations = { context ->
        listOf(object : DataMigration<LibraryPreferencesData> {
            override suspend fun shouldMigrate(currentData: LibraryPreferencesData): Boolean =
                !currentData.legacyMigrationComplete

            override suspend fun migrate(currentData: LibraryPreferencesData): LibraryPreferencesData =
                LegacyLibraryPreferences.read(
                    context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE).all,
                )

            override suspend fun cleanUp() = Unit
        })
    },
)

class LibraryPreferencesManager internal constructor(
    private val store: DataStore<LibraryPreferencesData>,
    private val readerPreferences: ReaderPreferences,
) {
    constructor(
        context: Context,
        readerPreferences: ReaderPreferences = ReaderPreferences(context)
    ) :
            this(context.libraryPreferencesStore, readerPreferences)

    fun preferences(spaceId: String?, screenKey: String): Flow<FilterSortPreferences> =
        store.data.map { data ->
            data.screens[preferenceKey(spaceId, screenKey)] ?: libraryPreferenceDefaults(screenKey)
        }.distinctUntilChanged()

    suspend fun updatePreferences(
        spaceId: String?,
        screenKey: String,
        transform: (FilterSortPreferences) -> FilterSortPreferences,
    ) {
        val key = preferenceKey(spaceId, screenKey)
        store.updateData { data ->
            val current = data.screens[key] ?: libraryPreferenceDefaults(screenKey)
            data.copy(screens = data.screens + (key to transform(current)))
        }
    }

    suspend fun setGlobalSpace(spaceId: String?) {
        store.updateData { it.copy(globalSpaceId = spaceId) }
    }

    fun getEffectiveSpaceFlow(): Flow<String?> = combine(
        store.data.map { it.globalSpaceId }.distinctUntilChanged(),
        readerPreferences.readerSettings,
    ) { spaceId, settings -> if (settings.spacesEnabled) spaceId else "_all_" }.distinctUntilChanged()

    private fun preferenceKey(spaceId: String?, screenKey: String): String =
        if (spaceId.isNullOrBlank() || spaceId == "_all_") screenKey else "${spaceId}_$screenKey"
}

fun libraryPreferenceDefaults(key: String): FilterSortPreferences = when {
    key.endsWith("shelf_detail") -> FilterSortPreferences(
        sortType = SortType.Custom,
        isAscending = true
    )

    key.endsWith("library_books") -> FilterSortPreferences(sortType = SortType.LastRead)
    key.endsWith("library_shelves") -> FilterSortPreferences(
        layoutMode = LayoutMode.BigList, sortType = SortType.Title, isAscending = true,
    )

    else -> FilterSortPreferences()
}
