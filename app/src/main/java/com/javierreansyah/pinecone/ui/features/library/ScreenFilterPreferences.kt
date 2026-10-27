package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.local.preferences.LibraryPreferencesManager
import com.javierreansyah.pinecone.data.local.preferences.libraryPreferenceDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
internal class ScreenFilterPreferences(
    private val manager: LibraryPreferencesManager,
    private val scope: CoroutineScope,
    private val screenKey: String,
    private val space: StateFlow<String?> = MutableStateFlow(null),
) {
    val preferences = space.flatMapLatest { manager.preferences(it, screenKey) }
        .stateIn(scope, SharingStarted.Eagerly, libraryPreferenceDefaults(screenKey))

    private fun update(transform: (FilterSortPreferences) -> FilterSortPreferences) {
        val spaceId = space.value
        scope.launch { manager.updatePreferences(spaceId, screenKey, transform) }
    }

    fun onLayoutModeChange(mode: LayoutMode) = update { it.copy(layoutMode = mode) }
    fun onSortTypeChange(sort: SortType) = update { it.selectSort(sort) }
    fun toggleFileFilter(format: FileFilter) = update { it.toggleFormat(format) }
    fun toggleStatusFilter(status: StatusFilter) = update { it.toggleStatus(status) }
}
