package com.javierreansyah.pinecone.ui.features.library.filters

import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.SortType

data class FilterResultUiState(
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.Added)
)
