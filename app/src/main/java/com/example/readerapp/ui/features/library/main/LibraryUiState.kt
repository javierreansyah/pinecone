package com.example.readerapp.ui.features.library.main

import com.example.readerapp.data.local.database.library.ShelfEntity
import com.example.readerapp.data.local.database.library.ShelfWithCovers
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.FilterSortPreferences
import com.example.readerapp.ui.features.library.LayoutMode
import com.example.readerapp.ui.features.library.SearchCategory
import com.example.readerapp.ui.features.library.SortType

data class LibraryUiState(
    val searchQuery: String = "",
    val searchCategory: SearchCategory = SearchCategory.All,
    val isImporting: Boolean = false,
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.LastRead),
    val shelvesPreferences: FilterSortPreferences = FilterSortPreferences(
        layoutMode = LayoutMode.BigList, sortType = SortType.Title, isAscending = true
    )
)

data class SearchResults(
    val books: List<Book> = emptyList(),
    val shelves: List<ShelfEntity> = emptyList(),
    val authors: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

data class LibraryScreenUiState(
    val searchQuery: String = "",
    val searchCategory: SearchCategory = SearchCategory.All,
    val isImporting: Boolean = false,
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.LastRead),
    val shelvesPreferences: FilterSortPreferences = FilterSortPreferences(
        layoutMode = LayoutMode.BigList, sortType = SortType.Title, isAscending = true
    ),
    val filteredBooks: List<Book> = emptyList(),
    val shelves: List<ShelfWithCovers> = emptyList(),
    val allBooks: List<Book> = emptyList(),
    val searchResults: SearchResults = SearchResults(),
    val isBooksLoading: Boolean = true,
    val isShelvesLoading: Boolean = true
)
