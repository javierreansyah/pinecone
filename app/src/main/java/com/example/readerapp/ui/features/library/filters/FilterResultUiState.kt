package com.example.readerapp.ui.features.library.filters

import com.example.readerapp.ui.features.library.FilterSortPreferences
import com.example.readerapp.ui.features.library.SortType

data class FilterResultUiState(
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.Added)
)
