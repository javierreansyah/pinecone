package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.runtime.Composable
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.features.library.LibrarySelection
import com.javierreansyah.pinecone.ui.features.library.publicationWording

@Composable
internal fun SelectedBooksBar(
    selection: LibrarySelection,
    books: List<Book>,
    allBooks: List<Book>,
    onMarkRead: (Collection<String>, Boolean) -> Unit,
    onArchive: (Collection<String>) -> Unit,
    onDelete: (Collection<String>) -> Unit,
    onInfo: (String) -> Unit,
    onOrganize: (Set<String>) -> Unit,
    isUnarchive: Boolean = false,
) {
    val ids = selection.ids
    val selected = allBooks.filter { it.id in ids }
    val markRead = selected.any { !it.isRead }
    MultiSelectAppBar(
        selectedCount = ids.size,
        isAllSelected = books.isNotEmpty() && ids.size == books.size,
        showMarkAsRead = markRead,
        wording = selected.publicationWording(),
        isUnarchive = isUnarchive,
        onCloseMultiSelect = selection::close,
        onClearSelection = selection::clear,
        onSelectAll = { selection.ids = books.mapTo(mutableSetOf()) { it.id } },
        onMarkAsReadUnread = { onMarkRead(ids, markRead); selection.close() },
        onArchive = { onArchive(ids); selection.close() },
        onDelete = { onDelete(ids); selection.close() },
        onOrganize = { selection.close(); onOrganize(ids) },
        onInfo = ids.singleOrNull()?.let { id -> { selection.close(); onInfo(id) } },
    )
}
