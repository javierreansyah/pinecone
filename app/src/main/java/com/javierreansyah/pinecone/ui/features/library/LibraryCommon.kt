package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.model.Book

const val ALL_SPACES_ID = "_all_"

fun List<Book>.inSpace(spaceId: String?): List<Book> =
    if (spaceId == null || spaceId == ALL_SPACES_ID) filter { it.spaceIds.isNotEmpty() }
    else filter { spaceId in it.spaceIds }

fun List<Book>.filterAndSort(
    prefs: FilterSortPreferences
): List<Book> {
    return this.filter { book ->
        val status = when {
            book.isRead -> StatusFilter.Finished
            book.progress <= 0.0 -> StatusFilter.NotStarted
            else -> StatusFilter.Reading
        }
        val statusMatch = prefs.selectedStatus.contains(status)

        statusMatch

    }.let { filtered ->
        val baseComparator = when (prefs.sortType) {
            SortType.Title -> compareBy { it.title.lowercase() }
            SortType.Author -> compareBy { it.authors.firstOrNull()?.lowercase() ?: "" }
            SortType.LastRead -> compareBy { it.lastOpened ?: 0L }
            SortType.Added -> compareBy { it.addedDate }
            SortType.Progress -> compareBy { it.progress }
            SortType.Custom -> {
                val indexMap = this.withIndex().associate { it.value.id to it.index }
                compareBy<Book> { indexMap[it.id] ?: 0 }
            }
        }

        val finalComparator = if (prefs.sortType == SortType.Title) {
            if (prefs.isAscending) baseComparator else baseComparator.reversed()
        } else if (prefs.sortType == SortType.Custom) {
            if (prefs.isAscending) baseComparator else baseComparator.reversed()
        } else {
            val mainComp =
                if (prefs.isAscending) baseComparator else baseComparator.reversed()
            mainComp.thenBy { it.title.lowercase() }
        }

        filtered.sortedWith(finalComparator)
    }
}

fun sortShelfBooks(
    shelvesList: List<ShelfWithCovers>,
    crossRefs: List<ShelfBookCrossRefEntity>,
    globalSpaceId: String? = null
): List<ShelfWithCovers> {
    val crossRefsByShelf = crossRefs.groupBy { it.shelfId }
    val filteredShelves = if (globalSpaceId != null && globalSpaceId != ALL_SPACES_ID) {
        shelvesList.filter { it.shelf.spaceId == globalSpaceId }
    } else {
        shelvesList
    }

    return filteredShelves.map { shelfWithCovers ->
        val shelfId = shelfWithCovers.shelf.id
        val shelfCrossRefs = crossRefsByShelf[shelfId].orEmpty()
        val orderMap = shelfCrossRefs.associate { it.bookId to it.orderIndex }
        val filteredBooks = shelfWithCovers.books.filter {
            globalSpaceId == null || globalSpaceId == ALL_SPACES_ID || it.spaces.any { space -> space.id == globalSpaceId }
        }
        val sortedBooks = filteredBooks.sortedBy { book ->
            orderMap[book.book.id] ?: 0
        }
        shelfWithCovers.copy(books = sortedBooks)
    }
}

fun mapAndSortShelves(
    shelvesList: List<ShelfWithCovers>,
    crossRefs: List<ShelfBookCrossRefEntity>,
    allBooksEntities: List<BookWithDetails>,
    prefs: FilterSortPreferences,
    unshelvedLabel: String,
    globalSpaceId: String? = null
): List<ShelfWithCovers> {
    val mappedShelves = sortShelfBooks(shelvesList, crossRefs, globalSpaceId)

    val sortedShelves = mappedShelves.let { processedShelves ->
        val baseComparator = when (prefs.sortType) {
            SortType.Title -> compareBy { it.shelf.name.lowercase() }
            SortType.LastRead -> compareBy { shelf: ShelfWithCovers ->
                shelf.books.maxOfOrNull {
                    it.book.lastReadDate ?: 0L
                } ?: 0L
            }

            SortType.Progress -> compareBy { shelf: ShelfWithCovers ->
                if (shelf.books.isEmpty()) 0.0 else shelf.books.map { it.book.progression }
                    .average()
            }

            SortType.Added -> compareBy { it.shelf.createdAt }
            else -> compareBy { it.shelf.name.lowercase() }
        }

        val finalComparator = if (prefs.isAscending) {
            baseComparator.thenBy { it.shelf.name.lowercase() }
        } else {
            baseComparator.reversed().thenBy { it.shelf.name.lowercase() }
        }

        processedShelves.sortedWith(finalComparator)
    }

    val isSpaceSelected = globalSpaceId != null && globalSpaceId != ALL_SPACES_ID
    val currentSpaceShelves = if (isSpaceSelected) {
        shelvesList.filter { it.shelf.spaceId == globalSpaceId }
    } else {
        shelvesList
    }
    val currentSpaceShelfIds = currentSpaceShelves.map { it.shelf.id }.toSet()
    val shelvedBookIdsInCurrentSpace =
        crossRefs.filter { it.shelfId in currentSpaceShelfIds }.map { it.bookId }.toSet()

    val unshelvedBooks = allBooksEntities.filter {
        val inSpace = if (isSpaceSelected) {
            it.spaces.any { space -> space.id == globalSpaceId }
        } else {
            it.spaces.isNotEmpty()
        }
        inSpace && it.book.id !in shelvedBookIdsInCurrentSpace
    }

    val showShelves = prefs.selectedShelfFilter.contains(ShelfFilter.Shelves)
    val showUnshelved = prefs.selectedShelfFilter.contains(ShelfFilter.Unshelved)

    val finalShelves = if (showShelves) sortedShelves else emptyList()

    return if (showUnshelved && unshelvedBooks.isNotEmpty()) {
        val unshelvedShelf = ShelfWithCovers(
            shelf = ShelfEntity(
                id = "unshelved",
                spaceId = globalSpaceId ?: ALL_SPACES_ID,
                name = unshelvedLabel,
                createdAt = 0L
            ), books = unshelvedBooks
        )
        finalShelves + unshelvedShelf
    } else {
        finalShelves
    }
}

