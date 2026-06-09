package com.example.readerapp.ui.features.reader.components.contents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.BookmarkEntity
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.example.readerapp.ui.components.EmptyState

@Composable
fun BookmarksList(
    bookmarks: List<BookmarkEntity>,
    tableOfContents: List<Link>,
    getPositionLabel: (Locator) -> String,
    onBookmarkClick: (Locator) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    if (bookmarks.isEmpty()) {
        EmptyState(
            icon = MaterialSymbols.Outlined.Bookmark,
            text = stringResource(R.string.reader_no_bookmarks),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(bookmarks) { bookmark ->
                val locator = try {
                    Locator.fromJSON(JSONObject(bookmark.locatorJson))
                } catch (_: Exception) {
                    null
                }
                if (locator != null) {
                    val inDocument = stringResource(R.string.reader_in_document)
                    val chapterTitle =
                        bookmark.chapterTitle?.takeIf { it.isNotBlank() && it != inDocument }
                            ?: tableOfContents.find {
                                it.href.toString().substringBefore("#") == locator.href.toString()
                                    .substringBefore("#")
                            }?.title ?: inDocument

                    Column(
                        modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookmarkClick(locator) }
                        .padding(horizontal = 20.dp, vertical = 12.dp)) {
                        EntryHeader(
                            title = chapterTitle,
                            positionLabel = getPositionLabel(locator),
                            onDelete = { onDeleteBookmark(bookmark.id) })
                    }
                }
            }
        }
    }
}
