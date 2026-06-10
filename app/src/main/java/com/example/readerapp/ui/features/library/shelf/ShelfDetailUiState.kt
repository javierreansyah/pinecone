package com.example.readerapp.ui.features.library.shelf

import com.example.readerapp.ui.features.library.FilterSortPreferences
import com.example.readerapp.ui.features.library.SortType

data class ShelfDetailUiState(
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.Custom)
)
