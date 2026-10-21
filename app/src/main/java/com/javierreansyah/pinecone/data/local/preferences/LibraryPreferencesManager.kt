package com.javierreansyah.pinecone.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.LibraryView
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class LibraryPreferencesManager(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    fun getPreferences(
        screenKey: String,
        defaultLayout: LayoutMode = LayoutMode.Grid,
        defaultSort: SortType = SortType.Added,
        defaultAscending: Boolean = false,
    ): FilterSortPreferences =
        getPreferences(null, screenKey, defaultLayout, defaultSort, defaultAscending)

    fun getPreferences(
        spaceId: String?,
        screenKey: String,
        defaultLayout: LayoutMode = LayoutMode.Grid,
        defaultSort: SortType = SortType.Added,
        defaultAscending: Boolean = false,
    ): FilterSortPreferences {
        val fullKey = preferenceKey(spaceId, screenKey)
        val layoutModeStr =
            prefs.getString("${fullKey}_layout", defaultLayout.name) ?: defaultLayout.name
        val sortTypeStr = prefs.getString("${fullKey}_sort", defaultSort.name) ?: defaultSort.name
        val isAscending = prefs.getBoolean("${fullKey}_asc", defaultAscending)
        val statusSetStr = prefs.getStringSet(
            "${fullKey}_status",
            setOf(
                StatusFilter.NotStarted.name, StatusFilter.Reading.name, StatusFilter.Finished.name,
            ),
        ) ?: setOf()
        val shelfFilterSetStr = prefs.getStringSet(
            "${fullKey}_shelf_filter", setOf(ShelfFilter.Shelves.name, ShelfFilter.Unshelved.name),
        ) ?: setOf()

        return FilterSortPreferences(
            selectedFormats = prefs.getStringSet("${fullKey}_formats", FileFilter.entries.map { it.name }.toSet())
                .orEmpty().mapNotNull { name -> FileFilter.entries.find { it.name == name } }.toSet(),
            libraryView = LibraryView.entries.find { it.name == prefs.getString("${fullKey}_show", LibraryView.Both.name) } ?: LibraryView.Both,
            layoutMode = try {
                LayoutMode.valueOf(layoutModeStr)
            } catch (_: Exception) {
                defaultLayout
            },
            sortType = try {
                SortType.valueOf(sortTypeStr)
            } catch (_: Exception) {
                defaultSort
            },
            isAscending = isAscending,
            selectedStatus = statusSetStr.asSequence().mapNotNull {
                try {
                    StatusFilter.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            }.toSet(),
            selectedShelfFilter = shelfFilterSetStr.asSequence().mapNotNull {
                try {
                    ShelfFilter.valueOf(it)
                } catch (_: Exception) {
                    null
                }
            }.toSet(),
        )
    }

    fun getGlobalSpaceFlow(): Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "global_space") {
                trySend(prefs.getString("global_space", null))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getString("global_space", null))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setGlobalSpace(spaceId: String?) {
        prefs.edit { putString("global_space", spaceId) }
    }

    fun getEffectiveSpaceFlow(): Flow<String?> = combine(
        getGlobalSpaceFlow(), ReaderPreferences(context).readerSettings,
    ) { spaceId, settings -> if (settings.spacesEnabled) spaceId else "_all_" }.distinctUntilChanged()

    private fun preferenceKey(spaceId: String?, screenKey: String): String =
        if (spaceId.isNullOrBlank() || spaceId == "_all_") screenKey else "${spaceId}_$screenKey"

    fun savePreferences(screenKey: String, prefsObj: FilterSortPreferences) {
        savePreferences(null, screenKey, prefsObj)
    }

    fun savePreferences(spaceId: String?, screenKey: String, prefsObj: FilterSortPreferences) {
        val fullKey = preferenceKey(spaceId, screenKey)
        prefs.edit().apply {
            putStringSet("${fullKey}_formats", prefsObj.selectedFormats.map { it.name }.toSet())
            putString("${fullKey}_show", prefsObj.libraryView.name)
            putString("${fullKey}_layout", prefsObj.layoutMode.name)
            putString("${fullKey}_sort", prefsObj.sortType.name)
            putBoolean("${fullKey}_asc", prefsObj.isAscending)
            putStringSet(
                "${fullKey}_status",
                prefsObj.selectedStatus.mapTo(mutableSetOf()) { it.name })
            putStringSet(
                "${fullKey}_shelf_filter",
                prefsObj.selectedShelfFilter.mapTo(mutableSetOf()) { it.name },
            )
            apply()
        }
    }
}
