package com.javierreansyah.pinecone.ui.features.library

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.model.Book

/** UI vocabulary follows original file formats, never a generated reading rendition. */
enum class PublicationWording(
    @param:PluralsRes val count: Int,
    @param:StringRes val libraryTab: Int,
    @param:StringRes val infoTitle: Int,
    @param:StringRes val editTitle: Int,
    @param:StringRes val deleteTitle: Int,
    @param:StringRes val deleteMessage: Int,
    @param:StringRes val searchHint: Int,
    @param:StringRes val deleteShelfMessage: Int,
    @param:StringRes val deleteLabelMessage: Int,
    @param:StringRes val mergeWarning: Int,
    @param:PluralsRes val deleteSelectionTitle: Int,
    @param:PluralsRes val deleteSelectionMessage: Int,
    @param:PluralsRes val deleteShelvesMessage: Int,
    @param:PluralsRes val deleteLabelsMessage: Int,
) {
    Books(
        R.plurals.library_books_count, R.string.content_books,
        R.string.content_book_info, R.string.content_edit_book,
        R.string.content_delete_book, R.string.content_delete_book_message,
        R.string.content_search_book, R.string.content_delete_book_shelf,
        R.string.content_delete_book_label, R.string.content_merge_book_label,
        R.plurals.content_delete_books_title, R.plurals.content_delete_books_message,
        R.plurals.content_delete_book_shelves, R.plurals.content_delete_book_labels,
    ),
    Items(
        R.plurals.content_items_count, R.string.library_tab_books,
        R.string.book_info_title, R.string.book_edit_title,
        R.string.book_delete_title, R.string.book_delete_message,
        R.string.reader_search_in_book, R.string.library_delete_shelf_message,
        R.string.library_delete_item_message, R.string.library_warning_merge,
        R.plurals.content_delete_items_title, R.plurals.content_delete_items_message,
        R.plurals.library_delete_shelves_message, R.plurals.library_delete_items_message,
    ),
}

fun publicationWording(formats: Iterable<FileFilter>): PublicationWording {
    val iterator = formats.iterator()
    if (!iterator.hasNext()) return PublicationWording.Items
    while (iterator.hasNext()) {
        if (iterator.next() != FileFilter.EPUB) return PublicationWording.Items
    }
    return PublicationWording.Books
}

val Book.publicationWording: PublicationWording
    get() = publicationWording(listOf(fileFormat()))

@JvmName("publicationWordingForItems")
fun Iterable<Book>.publicationWording(): PublicationWording =
    publicationWording(map { it.fileFormat() })

fun Iterable<Book>.inNamedCollection(type: String, name: String): List<Book> = filter { book ->
    when (type) {
        "author" -> name in book.authors
        "space" -> book.spaces.any { it.name == name }
        else -> name in book.tags
    }
}

fun Iterable<PublicationWording>.combinedWording(): PublicationWording {
    val values = toList()
    return if (values.isNotEmpty() && values.all { it == PublicationWording.Books }) {
        PublicationWording.Books
    } else PublicationWording.Items
}

@StringRes
fun deleteCollectionTitle(type: String): Int = when (type) {
    "author" -> R.string.content_delete_author
    "space" -> R.string.content_delete_space
    else -> R.string.content_delete_tag
}

@Composable
fun publicationCountLabel(count: Int, wording: PublicationWording): String =
    pluralStringResource(wording.count, count, count)
