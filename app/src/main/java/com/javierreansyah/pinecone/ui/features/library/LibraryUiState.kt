package com.javierreansyah.pinecone.ui.features.library

enum class LayoutMode { Grid, BigGrid, List, BigList }
enum class SortType { Title, Author, LastRead, Added, Progress, Custom }
enum class StatusFilter { NotStarted, Reading, Finished }
enum class ShelfFilter { Shelves, Unshelved }

enum class SearchCategory { All, Books, Authors, Shelves, Tags }

enum class LibraryView { Both, Books, Shelves }

enum class FileFilter { EPUB, PDF, Comics, Audio, Images, WebPublication, Other }

data class FilterSortPreferences(
    val layoutMode: LayoutMode = LayoutMode.Grid,
    val sortType: SortType = SortType.Added,
    val isAscending: Boolean = false,
    val selectedStatus: Set<StatusFilter> = setOf(
        StatusFilter.NotStarted, StatusFilter.Reading, StatusFilter.Finished,
    ),
    val selectedShelfFilter: Set<ShelfFilter> = setOf(ShelfFilter.Shelves, ShelfFilter.Unshelved),
    val selectedFormats: Set<FileFilter> = FileFilter.entries.toSet(),
    val libraryView: LibraryView = LibraryView.Both,
)
