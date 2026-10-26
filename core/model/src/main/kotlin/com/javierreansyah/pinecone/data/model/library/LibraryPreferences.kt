package com.javierreansyah.pinecone.data.model.library

import kotlinx.serialization.Serializable

const val ALL_SPACES_ID: String = "_all_"

@Serializable enum class LayoutMode { Grid, BigGrid, List, BigList }
@Serializable enum class SortType { Title, Author, LastRead, Added, Progress, Custom }
@Serializable enum class StatusFilter { NotStarted, Reading, Finished }
@Serializable enum class ShelfFilter { Shelves, Unshelved }
@Serializable enum class SearchCategory { All, Books, Authors, Shelves, Tags }
@Serializable enum class LibraryView { Both, Books, Shelves }
@Serializable enum class FileFilter { EPUB, PDF, Comics, Audio, Images, WebPublication, Other }

@Serializable
data class FilterSortPreferences(
    val layoutMode: LayoutMode = LayoutMode.Grid,
    val sortType: SortType = SortType.Added,
    val isAscending: Boolean = false,
    val selectedStatus: Set<StatusFilter> = setOf(StatusFilter.NotStarted, StatusFilter.Reading, StatusFilter.Finished),
    val selectedShelfFilter: Set<ShelfFilter> = setOf(ShelfFilter.Shelves, ShelfFilter.Unshelved),
    val selectedFormats: Set<FileFilter> = FileFilter.entries.toSet(),
    val libraryView: LibraryView = LibraryView.Both,
)
