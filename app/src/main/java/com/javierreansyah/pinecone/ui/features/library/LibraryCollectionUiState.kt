package com.javierreansyah.pinecone.ui.features.library

/** Preferences shared by the archive and unsorted collection screens. */
data class LibraryCollectionUiState(
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(),
)
