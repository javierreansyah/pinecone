package com.javierreansyah.pinecone.ui.features.library.archive

import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.SortType

data class ArchiveUiState(
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.Added)
)
