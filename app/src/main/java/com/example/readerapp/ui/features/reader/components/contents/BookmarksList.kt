package com.example.readerapp.ui.features.reader.components.contents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.ui.components.EmptyState
import org.readium.r2.shared.publication.Locator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookmarksList(
    bookmarks: List<BookmarkEntity>,
    getPositionLabel: (Locator) -> String,
    onBookmarkClick: (Locator) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    val context = LocalContext.current
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
                    Locator.fromJSON(org.json.JSONObject(bookmark.locatorJson))
                } catch (_: Exception) {
                    null
                }
                if (locator != null) {
                    val inDocument = stringResource(R.string.reader_in_document)
                    val chapterTitle =
                        bookmark.chapterTitle?.takeIf { it.isNotBlank() && it != inDocument }
                            ?: inDocument

                    val positionLabel = getPositionLabel(locator)
                    val formattedDate = remember(bookmark.createdAt, context) {
                        val date = Date(bookmark.createdAt)
                        val locale = Locale.getDefault()
                        val dateStr = if (locale.language == "en") {
                            SimpleDateFormat("MM/dd/yyyy", locale).format(date)
                        } else {
                            android.text.format.DateFormat.getDateFormat(context).format(date)
                        }
                        val timeStr =
                            android.text.format.DateFormat.getTimeFormat(context).format(date)
                        "created $dateStr $timeStr"
                    }
                    val subtitle = if (positionLabel.isNotBlank()) {
                        "$positionLabel | $formattedDate"
                    } else {
                        formattedDate
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookmarkClick(locator) }
                            .padding(horizontal = 20.dp, vertical = 12.dp)) {
                        EntryHeader(
                            title = chapterTitle,
                            positionLabel = subtitle,
                            onDelete = { onDeleteBookmark(bookmark.id) })
                    }
                }
            }
        }
    }
}
