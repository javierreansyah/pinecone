package com.javierreansyah.pinecone.ui.features.library.main

import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.SortType

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
    val query: String = "",
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
    val isShelvesLoading: Boolean = true,
    val allSpaces: List<SpaceEntity> = emptyList(),
    val allShelves: List<ShelfEntity> = emptyList(),
    val globalSpaceId: String? = null
)
