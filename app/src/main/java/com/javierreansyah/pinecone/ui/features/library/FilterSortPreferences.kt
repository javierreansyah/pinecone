package com.javierreansyah.pinecone.ui.features.library

typealias FilterSortPreferences = com.javierreansyah.pinecone.data.model.library.FilterSortPreferences

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
