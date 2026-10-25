package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.model.Book

const val ALL_SPACES_ID: String = "_all_"

fun List<Book>.inSpace(spaceId: String?): List<Book> =
    filter { !it.isUnsorted && (spaceId == null || spaceId == ALL_SPACES_ID || spaceId in it.spaceIds) }

fun Book.fileFormat(): FileFilter = publicationFileFormat(mediaType, originalFilename, filePath)

fun BookEntity.fileFormat(): FileFilter =
    publicationFileFormat(mediaType, originalFilename, filePath)

private fun publicationFileFormat(
    mediaType: String?,
    originalFilename: String,
    filePath: String
): FileFilter {
    val mime = mediaType.orEmpty().lowercase()
    val extension = originalFilename.ifBlank { filePath }.substringAfterLast('.', "").lowercase()
    return when {
        mime == "application/epub+zip" || extension == "epub" -> FileFilter.EPUB
        mime == "application/pdf" || extension == "pdf" -> FileFilter.PDF
        "comicbook" in mime || "cbz" in mime || extension in setOf(
            "cbz",
            "divina"
        ) || "divina" in mime -> FileFilter.Comics

        mime.startsWith("audio/") || "audiobook" in mime || extension in setOf(
            "audiobook",
            "zab",
            "mp3",
            "m4a",
            "m4b",
            "aac",
            "ogg",
            "oga",
            "opus",
            "flac",
            "wav",
            "mp4",
            "aif",
            "aiff",
            "webm"
        ) -> FileFilter.Audio

        mime.startsWith("image/") || extension in setOf(
            "jpg",
            "jpeg",
            "png",
            "webp",
            "gif",
            "bmp",
            "avif",
            "tif",
            "tiff",
            "jxl"
        ) -> FileFilter.Images

        "webpub" in mime || extension == "webpub" -> FileFilter.WebPublication
        mime == "application/zip" || extension == "zip" -> FileFilter.Comics
        else -> FileFilter.Other
    }
}

fun List<Book>.filterAndSort(
    prefs: FilterSortPreferences,
): List<Book> {
    return filter { book ->
        val bookProgress = book.progress
        val bookIsRead = book.isRead
        val status = when {
            bookIsRead -> StatusFilter.Finished
            bookProgress <= 0.0 -> StatusFilter.NotStarted
            else -> StatusFilter.Reading
        }
        val statusMatch = status in prefs.selectedStatus

        statusMatch && book.fileFormat() in prefs.selectedFormats

    }.let { filtered ->
        val baseComparator = when (prefs.sortType) {
            SortType.Title -> compareBy { it.metadata.sortTitle.ifBlank { it.title }.lowercase() }
            SortType.Author -> compareBy { it.authors.firstOrNull()?.lowercase().orEmpty() }
            SortType.LastRead -> compareBy { it.lastOpened ?: 0L }
            SortType.Added -> compareBy { it.addedDate }
            SortType.Progress -> compareBy { it.progress }
            SortType.Custom -> {
                val indexMap = asSequence().withIndex().associateBy(
                    keySelector = { (_, itValue) -> itValue.id },
                    valueTransform = IndexedValue<Book>::index,
                )
                compareBy<Book> { book ->
                    indexMap[book.id] ?: 0
                }
            }
        }

        val finalComparator =
            if (prefs.sortType == SortType.Title || prefs.sortType == SortType.Custom) {
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
    globalSpaceId: String? = null,
): List<ShelfWithCovers> {
    val crossRefsByShelf = crossRefs.groupBy { it.shelfId }
    val filteredShelves = if (globalSpaceId != null && globalSpaceId != ALL_SPACES_ID) {
        shelvesList.filter { (itShelf, _) -> itShelf.spaceId == globalSpaceId }
    } else {
        shelvesList
    }

    return filteredShelves.map { shelfWithCovers ->
        val shelfId = shelfWithCovers.shelf.id
        val shelfCrossRefs = crossRefsByShelf[shelfId].orEmpty()
        val refsByBook = shelfCrossRefs.associateBy(ShelfBookCrossRefEntity::bookId)
        val filteredBooks = shelfWithCovers.books.filter { details ->
            !details.book.isUnsorted && (globalSpaceId == null || globalSpaceId == ALL_SPACES_ID || details.spaces.any { it.id == globalSpaceId })
        }
        // Older entries can share position zero; their shelf-added time preserves insertion order.
        val sortedBooks = filteredBooks.sortedWith(
            compareBy<BookWithDetails> { refsByBook[it.book.id]?.orderIndex ?: 0 }
                .thenBy { refsByBook[it.book.id]?.addedAt ?: 0L }
                .thenBy { it.book.id },
        )
        shelfWithCovers.copy(books = sortedBooks)
    }
}

fun mapAndSortShelves(
    shelvesList: List<ShelfWithCovers>,
    crossRefs: List<ShelfBookCrossRefEntity>,
    allBooksEntities: List<BookWithDetails>,
    prefs: FilterSortPreferences,
    unshelvedLabel: String,
    globalSpaceId: String? = null,
): List<ShelfWithCovers> {
    val mappedShelves = sortShelfBooks(shelvesList, crossRefs, globalSpaceId).map { shelf ->
        shelf.copy(books = shelf.books.filter { it.book.fileFormat() in prefs.selectedFormats })
    }.filter { it.books.isNotEmpty() }

    val sortedShelves = mappedShelves.let { processedShelves ->
        val baseComparator = when (prefs.sortType) {
            SortType.Title -> compareBy { (itShelf2, _) -> itShelf2.name.lowercase() }
            SortType.LastRead -> compareBy { shelf: ShelfWithCovers ->
                shelf.books.maxOfOrNull { details ->
                    details.book.lastReadDate ?: 0L
                } ?: 0L
            }

            SortType.Progress -> compareBy { shelf: ShelfWithCovers ->
                if (shelf.books.isEmpty()) 0.0 else shelf.books.asSequence()
                    .map { details -> details.book.progression }
                    .average()
            }

            SortType.Added -> compareBy { (itShelf3, _) -> itShelf3.createdAt }
            else -> compareBy { (itShelf4, _) -> itShelf4.name.lowercase() }
        }

        val finalComparator = if (prefs.isAscending) {
            baseComparator.thenBy { (itShelf5, _) -> itShelf5.name.lowercase() }
        } else {
            baseComparator.reversed().thenBy { it.shelf.name.lowercase() }
        }

        processedShelves.sortedWith(finalComparator)
    }

    val isSpaceSelected = globalSpaceId != null && globalSpaceId != ALL_SPACES_ID
    val currentSpaceShelves = if (isSpaceSelected) {
        shelvesList.filter { (itShelf6, _) -> itShelf6.spaceId == globalSpaceId }
    } else {
        shelvesList
    }
    val currentSpaceShelfIds =
        currentSpaceShelves.asSequence().map { (itShelf7, _) -> itShelf7.id }.toSet()
    val shelvedBookIdsInCurrentSpace =
        crossRefs.asSequence()
            .filter { it.shelfId in currentSpaceShelfIds }
            .map { (_, itBookId2, _, _) -> itBookId2 }.toSet()

    val unshelvedBooks = allBooksEntities.filter { details ->
        val itBook3 = details.book
        val itSpaces2 = details.spaces
        val inSpace = if (isSpaceSelected) {
            itSpaces2.any { (spaceId3, _, _) -> spaceId3 == globalSpaceId }
        } else {
            itSpaces2.isNotEmpty()
        }
        inSpace && !itBook3.isUnsorted && itBook3.id !in shelvedBookIdsInCurrentSpace
                && details.book.fileFormat() in prefs.selectedFormats
    }

    val showShelves = ShelfFilter.Shelves in prefs.selectedShelfFilter
    val showUnshelved = ShelfFilter.Unshelved in prefs.selectedShelfFilter

    val finalShelves = if (showShelves) sortedShelves else emptyList()

    return if (showUnshelved && unshelvedBooks.isNotEmpty()) {
        val unshelvedShelf = ShelfWithCovers(
            shelf = ShelfEntity(
                id = "unshelved",
                spaceId = globalSpaceId ?: ALL_SPACES_ID,
                name = unshelvedLabel,
                createdAt = 0L,
            ),
            books = unshelvedBooks,
        )
        finalShelves + unshelvedShelf
    } else {
        finalShelves
    }
}
