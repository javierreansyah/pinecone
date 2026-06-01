package com.example.readerapp.ui.features.library

enum class LayoutMode { Grid, List }
enum class SortType { Title, Author, LastRead, Added, Progress }
enum class StatusFilter { NotStarted, Reading, Finished }

data class LibraryUiState(
    val searchQuery: String = "",
    val layoutMode: LayoutMode = LayoutMode.Grid,
    val sortType: SortType = SortType.Added,
    val isAscending: Boolean = false,
    val selectedStatus: Set<StatusFilter> = setOf(StatusFilter.NotStarted, StatusFilter.Reading, StatusFilter.Finished),
    val isImporting: Boolean = false
)
