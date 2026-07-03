package com.javierreansyah.pinecone.ui.features.library.shelf

import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.SortType

data class ShelfDetailUiState(
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.Custom)
)
