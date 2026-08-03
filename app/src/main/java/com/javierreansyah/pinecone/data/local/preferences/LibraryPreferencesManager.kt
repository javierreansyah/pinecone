package com.javierreansyah.pinecone.data.local.preferences

import android.content.Context
import android.content.SharedPreferences
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.Flow

class LibraryPreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)

    fun getPreferences(
        screenKey: String,
        defaultLayout: LayoutMode = LayoutMode.Grid,
        defaultSort: SortType = SortType.Added,
        defaultAscending: Boolean = false
    ): FilterSortPreferences {
        val layoutModeStr =
            prefs.getString("${screenKey}_layout", defaultLayout.name) ?: defaultLayout.name
        val sortTypeStr = prefs.getString("${screenKey}_sort", defaultSort.name) ?: defaultSort.name
        val isAscending = prefs.getBoolean("${screenKey}_asc", defaultAscending)
        val statusSetStr = prefs.getStringSet(
            "${screenKey}_status", setOf(
                StatusFilter.NotStarted.name, StatusFilter.Reading.name, StatusFilter.Finished.name
            )
        ) ?: setOf()
        val shelfFilterSetStr = prefs.getStringSet(
            "${screenKey}_shelf_filter", setOf(ShelfFilter.Shelves.name, ShelfFilter.Unshelved.name)
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

    fun getGlobalCollectionFlow(): Flow<String?> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "global_collection") {
                trySend(prefs.getString("global_collection", null))
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs.getString("global_collection", null))
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun setGlobalCollection(collectionId: String?) {
        prefs.edit().putString("global_collection", collectionId).apply()
    }

    fun savePreferences(screenKey: String, prefsObj: FilterSortPreferences) {
        prefs.edit().apply {
            putString("${screenKey}_layout", prefsObj.layoutMode.name)
            putString("${screenKey}_sort", prefsObj.sortType.name)
            putBoolean("${screenKey}_asc", prefsObj.isAscending)
            putStringSet("${screenKey}_status", prefsObj.selectedStatus.map { it.name }.toSet())
            putStringSet(
                "${screenKey}_shelf_filter", prefsObj.selectedShelfFilter.map { it.name }.toSet()
            )
            apply()
        }
    }
}