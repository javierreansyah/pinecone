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
    val totalPages: Int? = null
)
