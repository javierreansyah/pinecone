package com.example.readerapp.ui.features.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.readerapp.data.local.BookmarkEntity
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@Composable
fun BookmarksList(
    bookmarks: List<BookmarkEntity>,
    tableOfContents: List<Link>,
    getPositionLabel: (Locator) -> String,
    onBookmarkClick: (Locator) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No Bookmarks", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(bookmarks) { bookmark ->
                val locator = try { Locator.fromJSON(org.json.JSONObject(bookmark.locatorJson)) } catch (_: Exception) { null }
                if (locator != null) {
                    val chapterTitle = bookmark.chapterTitle
                        ?.takeIf { it.isNotBlank() && it != "In Document" }
                        ?: tableOfContents.find { it.href.toString().substringBefore("#") == locator.href.toString().substringBefore("#") }?.title
                        ?: "In Document"
                        
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBookmarkClick(locator) }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        EntryHeader(
                            title = chapterTitle,
                            positionLabel = getPositionLabel(locator),
                            onDelete = { onDeleteBookmark(bookmark.id) }
                        )
                    }
                }
            }
        }
    }
}
