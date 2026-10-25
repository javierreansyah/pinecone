package com.javierreansyah.pinecone.data.local.preferences

import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.LibraryView
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter

/** Read-only decoder for the original SharedPreferences keys. */
internal object LegacyLibraryPreferences {
    fun read(values: Map<String, *>): LibraryPreferencesData {
        val suffixes = listOf("_layout", "_sort", "_asc", "_status", "_shelf_filter", "_formats", "_show")
        val keys = values.keys.mapNotNull { key ->
            suffixes.firstOrNull(key::endsWith)?.let { key.removeSuffix(it) }
        }.toSet()
        return LibraryPreferencesData(
            globalSpaceId = values["global_space"] as? String,
            screens = keys.associateWith { decode(values, it) },
            legacyMigrationComplete = true,
        )
    }

    private fun decode(values: Map<String, *>, fullKey: String): FilterSortPreferences {
        val defaults = libraryPreferenceDefaults(fullKey)
        val defaultLayout = defaults.layoutMode
        val defaultSort = defaults.sortType
        val defaultAscending = defaults.isAscending
        fun string(key: String, default: String): String = values[key] as? String ?: default
        fun boolean(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
        fun strings(key: String, default: Set<String>): Set<String> =
            (values[key] as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: default
        val layoutModeStr =
            string("${fullKey}_layout", defaultLayout.name)
        val sortTypeStr = string("${fullKey}_sort", defaultSort.name)
        val isAscending = boolean("${fullKey}_asc", defaultAscending)
        val statusSetStr = strings(
            "${fullKey}_status",
            setOf(
                StatusFilter.NotStarted.name, StatusFilter.Reading.name, StatusFilter.Finished.name,
            ),
        )
        val shelfFilterSetStr = strings(
            "${fullKey}_shelf_filter", setOf(ShelfFilter.Shelves.name, ShelfFilter.Unshelved.name),
        )

        return FilterSortPreferences(
            selectedFormats = strings(
                "${fullKey}_formats",
                FileFilter.entries.map { it.name }.toSet()
            )
                .orEmpty().mapNotNull { name -> FileFilter.entries.find { it.name == name } }
                .toSet(),
            libraryView = LibraryView.entries.find {
                it.name == string(
                    "${fullKey}_show",
                    LibraryView.Both.name
                )
            } ?: LibraryView.Both,
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

}
