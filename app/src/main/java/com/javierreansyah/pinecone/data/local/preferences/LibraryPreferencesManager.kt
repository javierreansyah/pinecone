package com.javierreansyah.pinecone.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LibraryPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    fun getPreferences(
        screenKey: String,
        defaultLayout: LayoutMode = LayoutMode.Grid,
        defaultSort: SortType = SortType.Added,
        defaultAscending: Boolean = false
    ): FilterSortPreferences =
        getPreferences(null, screenKey, defaultLayout, defaultSort, defaultAscending)

    fun getPreferences(
        spaceId: String?,
        screenKey: String,
        defaultLayout: LayoutMode = LayoutMode.Grid,
        defaultSort: SortType = SortType.Added,
        defaultAscending: Boolean = false
    ): FilterSortPreferences {
        val fullKey = if (spaceId.isNullOrBlank()) screenKey else "${spaceId}_$screenKey"
        val layoutModeStr =
            prefs.getString("${fullKey}_layout", defaultLayout.name) ?: defaultLayout.name
        val sortTypeStr = prefs.getString("${fullKey}_sort", defaultSort.name) ?: defaultSort.name
        val isAscending = prefs.getBoolean("${fullKey}_asc", defaultAscending)
        val statusSetStr = prefs.getStringSet(
            "${fullKey}_status", setOf(
                StatusFilter.NotStarted.name, StatusFilter.Reading.name, StatusFilter.Finished.name
            )
        ) ?: setOf()
        val shelfFilterSetStr = prefs.getStringSet(
            "${fullKey}_shelf_filter", setOf(ShelfFilter.Shelves.name, ShelfFilter.Unshelved.name)
        ) ?: setOf()

        return FilterSortPreferences(
            layoutMode = try {
                LayoutMode.valueOf(layoutModeStr)
            } catch (e: Exception) {
                defaultLayout
            }, sortType = try {
                SortType.valueOf(sortTypeStr)
            } catch (e: Exception) {
                defaultSort
            }, isAscending = isAscending, selectedStatus = statusSetStr.mapNotNull {
                try {
                    StatusFilter.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            }.toSet(), selectedShelfFilter = shelfFilterSetStr.mapNotNull {
                try {
                    ShelfFilter.valueOf(it)
                } catch (e: Exception) {
                    null
                }
            }.toSet()
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

    fun savePreferences(screenKey: String, prefsObj: FilterSortPreferences) {
        savePreferences(null, screenKey, prefsObj)
    }

    fun savePreferences(spaceId: String?, screenKey: String, prefsObj: FilterSortPreferences) {
        val fullKey = if (spaceId.isNullOrBlank()) screenKey else "${spaceId}_$screenKey"
        prefs.edit().apply {
            putString("${fullKey}_layout", prefsObj.layoutMode.name)
            putString("${fullKey}_sort", prefsObj.sortType.name)
            putBoolean("${fullKey}_asc", prefsObj.isAscending)
            putStringSet("${fullKey}_status", prefsObj.selectedStatus.map { it.name }.toSet())
            putStringSet(
                "${fullKey}_shelf_filter", prefsObj.selectedShelfFilter.map { it.name }.toSet()
            )
            apply()
        }
    }
}

