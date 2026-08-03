package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.model.Book

fun List<Book>.filterAndSort(
    prefs: FilterSortPreferences,
    crossRefs: List<ShelfBookCrossRefEntity> = emptyList()
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
    globalCollectionId: String? = null
): List<ShelfWithCovers> {
    val crossRefsByShelf = crossRefs.groupBy { it.shelfId }
    return shelvesList.map { shelfWithCovers ->
        val shelfId = shelfWithCovers.shelf.id
        val shelfCrossRefs = crossRefsByShelf[shelfId].orEmpty()
        val orderMap = shelfCrossRefs.associate { it.bookId to it.orderIndex }
        val filteredBooks = shelfWithCovers.books.filter {
            globalCollectionId == null || globalCollectionId == "_all_" || it.book.collectionId == globalCollectionId
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
    globalCollectionId: String? = null
): List<ShelfWithCovers> {
    val mappedShelves = sortShelfBooks(shelvesList, crossRefs, globalCollectionId)

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

    val shelvedBookIds = crossRefs.map { it.bookId }.toSet()
    val unshelvedBooks = allBooksEntities.filter { 
        it.book.id !in shelvedBookIds && (globalCollectionId == null || globalCollectionId == "_all_" || it.book.collectionId == globalCollectionId)
    }

    val showShelves = prefs.selectedShelfFilter.contains(ShelfFilter.Shelves)
    val showUnshelved = prefs.selectedShelfFilter.contains(ShelfFilter.Unshelved)

    val finalShelves = if (showShelves) {
        if (globalCollectionId != null && globalCollectionId != "_all_") {
            sortedShelves.filter { it.books.isNotEmpty() }
        } else {
            sortedShelves
        }
    } else {
        emptyList()
    }

    return if (showUnshelved && unshelvedBooks.isNotEmpty()) {
        val unshelvedShelf = ShelfWithCovers(
            shelf = ShelfEntity(
                id = "unshelved",
                name = unshelvedLabel,
                createdAt = 0L
            ), books = unshelvedBooks
        )
        finalShelves + unshelvedShelf
    } else {
        finalShelves
    }
}
