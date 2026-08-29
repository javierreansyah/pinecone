package com.javierreansyah.pinecone.ui.features.library.unsorted

import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.SortType

data class UnsortedUiState(
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.Added)
)
