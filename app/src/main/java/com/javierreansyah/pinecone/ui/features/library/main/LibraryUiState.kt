package com.javierreansyah.pinecone.ui.features.library.main

import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.publicationWording

data class LibraryUiState(
    val searchQuery: String = "",
    val searchCategory: SearchCategory = SearchCategory.All,
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.LastRead),
    val shelvesPreferences: FilterSortPreferences = FilterSortPreferences(
        layoutMode = LayoutMode.BigList, sortType = SortType.Title, isAscending = true,
    ),
)

data class SearchResults(
    val query: String = "",
    val books: List<Book> = emptyList(),
    val shelves: List<ShelfEntity> = emptyList(),
    val authors: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val contentWording: PublicationWording = PublicationWording.Items,
)

data class LibraryScreenUiState(
    val searchQuery: String = "",
    val searchCategory: SearchCategory = SearchCategory.All,
    val bookPreferences: FilterSortPreferences = FilterSortPreferences(sortType = SortType.LastRead),
    val shelvesPreferences: FilterSortPreferences = FilterSortPreferences(
        layoutMode = LayoutMode.BigList, sortType = SortType.Title, isAscending = true,
    ),
    val filteredBooks: List<Book> = emptyList(),
    val shelves: List<ShelfWithCovers> = emptyList(),
    val allBooks: List<Book> = emptyList(),
    val searchResults: SearchResults = SearchResults(),
    val isBooksLoading: Boolean = true,
    val isShelvesLoading: Boolean = true,
    val allSpaces: List<SpaceEntity> = emptyList(),
    val allShelves: List<ShelfEntity> = emptyList(),
    val globalSpaceId: String? = null,
    val hasUnsortedBooks: Boolean = false,
    val showAllSpaces: Boolean = true,
    val spacesEnabled: Boolean = true,
    val showLibraryBottomNavigation: Boolean = true,
)

/** Search keeps source ordering and derives wording from the whole visible collection. */
internal fun searchLibrary(
    books: List<Book>,
    shelves: List<ShelfEntity>,
    query: String,
    category: SearchCategory,
): SearchResults {
    val blank = query.isBlank()
    fun includes(value: SearchCategory) = category == SearchCategory.All || category == value
    fun names(values: Sequence<String>) = values.distinct()
        .filter { blank || it.contains(query, ignoreCase = true) }.toList()
    return SearchResults(
        query = query,
        contentWording = books.publicationWording(),
        books = if (includes(SearchCategory.Books)) {
            if (blank) books else books.filter { it.matchesLibraryQuery(query) }
        } else emptyList(),
        shelves = if (includes(SearchCategory.Shelves)) {
            if (blank) shelves else shelves.filter { it.name.contains(query, ignoreCase = true) }
        } else emptyList(),
        authors = if (includes(SearchCategory.Authors)) names(books.asSequence().flatMap { it.authors }) else emptyList(),
        tags = if (includes(SearchCategory.Tags)) names(books.asSequence().flatMap { it.tags }) else emptyList(),
    )
}
