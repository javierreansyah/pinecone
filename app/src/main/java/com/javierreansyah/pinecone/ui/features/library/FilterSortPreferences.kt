package com.javierreansyah.pinecone.ui.features.library

@kotlinx.serialization.Serializable
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

/** Selecting the current sort reverses it; a new sort starts ascending except last-read. */
fun FilterSortPreferences.selectSort(sort: SortType): FilterSortPreferences =
    if (sortType == sort) copy(isAscending = !isAscending)
    else copy(sortType = sort, isAscending = sort != SortType.LastRead)

fun FilterSortPreferences.toggleFormat(format: FileFilter): FilterSortPreferences =
    copy(selectedFormats = selectedFormats.toggled(format))

fun FilterSortPreferences.toggleStatus(status: StatusFilter): FilterSortPreferences =
    copy(selectedStatus = selectedStatus.toggled(status))

fun FilterSortPreferences.toggleShelf(filter: ShelfFilter): FilterSortPreferences =
    copy(selectedShelfFilter = selectedShelfFilter.toggled(filter))

private fun <T> Set<T>.toggled(value: T): Set<T> =
    if (value in this) this - value else this + value
