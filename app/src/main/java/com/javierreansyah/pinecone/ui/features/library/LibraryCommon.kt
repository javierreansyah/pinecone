package com.javierreansyah.pinecone.ui.features.library

import com.javierreansyah.pinecone.data.local.database.library.BookEntity
import com.javierreansyah.pinecone.data.local.database.library.BookWithDetails
import com.javierreansyah.pinecone.data.local.database.library.ShelfBookCrossRefEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.local.database.library.ShelfWithCovers
import com.javierreansyah.pinecone.data.model.Book

const val ALL_SPACES_ID: String = "_all_"

fun List<Book>.inSpace(spaceId: String?): List<Book> =
    filter { !it.isUnsorted && ((spaceId == null) || (spaceId == ALL_SPACES_ID) || spaceId in it.spaceIds) }

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
    return this.filter { book ->
        val bookProgress = book.progress
        val bookIsRead = book.isRead
        val status = when {
            bookIsRead -> StatusFilter.Finished
            bookProgress <= 0.0 -> StatusFilter.NotStarted
            else -> StatusFilter.Reading
        }
        val statusMatch = prefs.selectedStatus.contains(status)

        statusMatch && book.fileFormat() in prefs.selectedFormats

    }.let { filtered ->
        val baseComparator = when (prefs.sortType) {
            SortType.Title -> compareBy { it.metadata.sortTitle.ifBlank { it.title }.lowercase() }
            SortType.Author -> compareBy { (_, _, itAuthors) ->
                itAuthors.firstOrNull()?.lowercase() ?: ""
            }

            SortType.LastRead -> compareBy { (_, _, _, _, _, itLastOpened) -> itLastOpened ?: 0L }
            SortType.Added -> compareBy { (_, _, _, _, _, _, _, itAddedDate) -> itAddedDate }
            SortType.Progress -> compareBy { (_, _, _, _, itProgress) -> itProgress }
            SortType.Custom -> {
                val indexMap = asSequence().withIndex().associateBy(
                    keySelector = { (_, itValue) -> itValue.id },
                    valueTransform = IndexedValue<Book>::index,
                )
                compareBy<Book> { (itId) -> indexMap[itId] ?: 0 }
            }
        }

        val finalComparator = if (prefs.sortType == SortType.Title) {
            if (prefs.isAscending) baseComparator else baseComparator.reversed()
        } else if (prefs.sortType == SortType.Custom) {
            if (prefs.isAscending) baseComparator else baseComparator.reversed()
        } else {
            val mainComp =
                if (prefs.isAscending) baseComparator else baseComparator.reversed()
            mainComp.thenBy { (_, itTitle2) -> itTitle2.lowercase() }
        }

        filtered.sortedWith(finalComparator)
    }
}

fun sortShelfBooks(
    shelvesList: List<ShelfWithCovers>,
    crossRefs: List<ShelfBookCrossRefEntity>,
    globalSpaceId: String? = null,
): List<ShelfWithCovers> {
    val crossRefsByShelf = crossRefs.groupBy { (itShelfId) -> itShelfId }
    val filteredShelves = if ((globalSpaceId != null) && (globalSpaceId != ALL_SPACES_ID)) {
        shelvesList.filter { (itShelf) -> itShelf.spaceId == globalSpaceId }
    } else {
        shelvesList
    }

    return filteredShelves.map { shelfWithCovers ->
        val shelfId = shelfWithCovers.shelf.id
        val shelfCrossRefs = crossRefsByShelf[shelfId].orEmpty()
        val orderMap = shelfCrossRefs.associateBy(
            ShelfBookCrossRefEntity::bookId,
            ShelfBookCrossRefEntity::orderIndex,
        )
        val filteredBooks = shelfWithCovers.books.filter { details ->
            !details.book.isUnsorted && ((globalSpaceId == null) || (globalSpaceId == ALL_SPACES_ID) || details.spaces.any { it.id == globalSpaceId })
        }
        val sortedBooks = filteredBooks.sortedBy { (bookBook) ->
            orderMap[bookBook.id] ?: 0
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
    globalSpaceId: String? = null,
): List<ShelfWithCovers> {
    val formatsFiltered = prefs.selectedFormats != FileFilter.entries.toSet()
    val mappedShelves = sortShelfBooks(shelvesList, crossRefs, globalSpaceId).map { shelf ->
        shelf.copy(books = shelf.books.filter { it.book.fileFormat() in prefs.selectedFormats })
    }.filter { !formatsFiltered || it.books.isNotEmpty() }

    val sortedShelves = mappedShelves.let { processedShelves ->
        val baseComparator = when (prefs.sortType) {
            SortType.Title -> compareBy { (itShelf2) -> itShelf2.name.lowercase() }
            SortType.LastRead -> compareBy { shelf: ShelfWithCovers ->
                shelf.books.maxOfOrNull { (itBook) ->
                    itBook.lastReadDate ?: 0L
                } ?: 0L
            }

            SortType.Progress -> compareBy { shelf: ShelfWithCovers ->
                if (shelf.books.isEmpty()) 0.0 else shelf.books.asSequence()
                    .map { (itBook2) -> itBook2.progression }
                    .average()
            }

            SortType.Added -> compareBy { (itShelf3) -> itShelf3.createdAt }
            else -> compareBy { (itShelf4) -> itShelf4.name.lowercase() }
        }

        val finalComparator = if (prefs.isAscending) {
            baseComparator.thenBy { (itShelf5) -> itShelf5.name.lowercase() }
        } else {
            baseComparator.reversed().thenBy { it.shelf.name.lowercase() }
        }

        processedShelves.sortedWith(finalComparator)
    }

    val isSpaceSelected = (globalSpaceId != null) && (globalSpaceId != ALL_SPACES_ID)
    val currentSpaceShelves = if (isSpaceSelected) {
        shelvesList.filter { (itShelf6) -> itShelf6.spaceId == globalSpaceId }
    } else {
        shelvesList
    }
    val currentSpaceShelfIds =
        currentSpaceShelves.asSequence().map { (itShelf7) -> itShelf7.id }.toSet()
    val shelvedBookIdsInCurrentSpace =
        crossRefs.asSequence().filter { (itShelfId2) -> itShelfId2 in currentSpaceShelfIds }
            .map { (_, itBookId2) -> itBookId2 }.toSet()

    val unshelvedBooks = allBooksEntities.filter { details ->
        val itBook3 = details.book
        val itSpaces2 = details.spaces
        val inSpace = if (isSpaceSelected) {
            itSpaces2.any { (spaceId3) -> spaceId3 == globalSpaceId }
        } else {
            itSpaces2.isNotEmpty()
        }
        inSpace && !itBook3.isUnsorted && (itBook3.id !in shelvedBookIdsInCurrentSpace)
                && details.book.fileFormat() in prefs.selectedFormats
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
                createdAt = 0L,
            ),
            books = unshelvedBooks,
        )
        finalShelves + unshelvedShelf
    } else {
        finalShelves
    }
}
