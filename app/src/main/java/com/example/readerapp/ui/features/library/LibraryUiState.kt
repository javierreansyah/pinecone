package com.example.readerapp.ui.features.library

import com.example.readerapp.data.local.ShelfEntity
import com.example.readerapp.data.model.Book

enum class LayoutMode { Grid, BigGrid, List, BigList }
enum class SortType { Title, Author, LastRead, Added, Progress, Custom }
enum class StatusFilter { NotStarted, Reading, Finished }

enum class SearchCategory { All, Books, Authors, Shelves, Tags }

data class FilterSortPreferences(
    val layoutMode: LayoutMode = LayoutMode.Grid,
    val sortType: SortType = SortType.Added,
    val isAscending: Boolean = false,
    val selectedStatus: Set<StatusFilter> = setOf(StatusFilter.NotStarted, StatusFilter.Reading, StatusFilter.Finished)
)

data class LibraryUiState(
    val searchQuery: String = "",
    val searchCategory: SearchCategory = SearchCategory.All,
    val isImporting: Boolean = false,
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.LastRead),
    val shelvesPreferences: FilterSortPreferences = FilterSortPreferences(layoutMode = LayoutMode.BigList, sortType = SortType.Title, isAscending = true)
)

data class SearchResults(
    val books: List<Book> = emptyList(),
    val shelves: List<ShelfEntity> = emptyList(),
    val authors: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)
