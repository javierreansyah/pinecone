package com.example.readerapp.ui.reader

data class ReaderUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val bookTitle: String = "",
    val showControls: Boolean = false,
    val showToc: Boolean = false,
    val showSettings: Boolean = false,
    val isBookmarked: Boolean = false,
    val currentChapter: String? = null,
    val progression: Double = 0.0,
    val currentPage: Int? = null,
    val totalPages: Int? = null,

    // Search
    val showSearch: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<SearchResultItem> = emptyList(),
    val searchLoading: Boolean = false,
    val searchPerformed: Boolean = false,
    /** Index in [searchResults] that is currently active (user tapped a result). */
    val activeSearchIndex: Int? = null,
    /** When true the bottom bar shows the ← N of M → search helper instead of the progress bar. */
    val isInSearchNavigationMode: Boolean = false
)
