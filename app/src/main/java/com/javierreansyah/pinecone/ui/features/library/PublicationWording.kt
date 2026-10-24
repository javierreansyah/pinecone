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
    Documents(
        R.plurals.wording_document_count,
        R.string.wording_document_tab,
        R.string.wording_document_info,
        R.string.wording_document_edit,
        R.string.wording_document_delete,
        R.string.wording_document_delete_message,
        R.string.wording_document_search,
        R.string.wording_document_shelf,
        R.string.wording_document_label,
        R.string.wording_document_merge,
        R.plurals.wording_document_delete_title,
        R.plurals.wording_document_delete_selection_message,
        R.plurals.wording_document_shelves,
        R.plurals.wording_document_labels,
    ),
    Audiobooks(
        R.plurals.wording_audiobook_count,
        R.string.wording_audiobook_tab,
        R.string.wording_audiobook_info,
        R.string.wording_audiobook_edit,
        R.string.wording_audiobook_delete,
        R.string.wording_audiobook_delete_message,
        R.string.wording_audiobook_search,
        R.string.wording_audiobook_shelf,
        R.string.wording_audiobook_label,
        R.string.wording_audiobook_merge,
        R.plurals.wording_audiobook_delete_title,
        R.plurals.wording_audiobook_delete_selection_message,
        R.plurals.wording_audiobook_shelves,
        R.plurals.wording_audiobook_labels,
    ),
    Comics(
        R.plurals.wording_comic_count,
        R.string.wording_comic_tab,
        R.string.wording_comic_info,
        R.string.wording_comic_edit,
        R.string.wording_comic_delete,
        R.string.wording_comic_delete_message,
        R.string.wording_comic_search,
        R.string.wording_comic_shelf,
        R.string.wording_comic_label,
        R.string.wording_comic_merge,
        R.plurals.wording_comic_delete_title,
        R.plurals.wording_comic_delete_selection_message,
        R.plurals.wording_comic_shelves,
        R.plurals.wording_comic_labels,
    ),
    Items(
        R.plurals.content_items_count, R.string.library_tab_items,
        R.string.publication_info_title, R.string.publication_edit_title,
        R.string.publication_delete_title, R.string.publication_delete_message,
        R.string.reader_search_placeholder, R.string.library_delete_shelf_message,
        R.string.library_delete_label_message, R.string.library_warning_merge,
        R.plurals.content_delete_items_title, R.plurals.content_delete_items_message,
        R.plurals.library_delete_shelves_message, R.plurals.library_delete_labels_message,
    ),
}

fun publicationWording(formats: Iterable<FileFilter>): PublicationWording {
    val wordings = formats.map { format ->
        when (format) {
            FileFilter.EPUB -> PublicationWording.Books
            FileFilter.PDF -> PublicationWording.Documents
            FileFilter.Audio -> PublicationWording.Audiobooks
            FileFilter.Comics -> PublicationWording.Comics
            else -> PublicationWording.Items
        }
    }
    return wordings.combinedWording()
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
    return values.firstOrNull()?.takeIf { first -> values.all { it == first } }
        ?: PublicationWording.Items
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
