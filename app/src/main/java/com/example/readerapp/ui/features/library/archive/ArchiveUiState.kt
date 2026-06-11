package com.example.readerapp.ui.features.library.archive

import com.example.readerapp.ui.features.library.FilterSortPreferences
import com.example.readerapp.ui.features.library.SortType

data class ArchiveUiState(
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.Added)
)
