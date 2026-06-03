package com.example.readerapp.ui.features.library

import com.example.readerapp.data.local.ShelfEntity
import com.example.readerapp.data.model.Book

enum class LayoutMode { Grid, List }
enum class SortType { Title, Author, LastRead, Added, Progress }
enum class StatusFilter { NotStarted, Reading, Finished }

enum class SearchCategory { All, Books, Authors, Shelves, Tags }

data class LibraryUiState(
    val searchQuery: String = "",
    val searchCategory: SearchCategory = SearchCategory.All,
    val layoutMode: LayoutMode = LayoutMode.Grid,
    val sortType: SortType = SortType.Added,
    val isAscending: Boolean = false,
    val selectedStatus: Set<StatusFilter> = setOf(StatusFilter.NotStarted, StatusFilter.Reading, StatusFilter.Finished),
    val isImporting: Boolean = false
)

data class SearchResults(
    val books: List<Book> = emptyList(),
    val shelves: List<ShelfEntity> = emptyList(),
    val authors: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)
