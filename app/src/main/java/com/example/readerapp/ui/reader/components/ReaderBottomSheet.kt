package com.example.readerapp.ui.reader.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.readerapp.data.local.BookmarkEntity
import com.example.readerapp.data.local.NoteEntity
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderBottomSheet(
    tableOfContents: List<Link>,
    bookmarks: List<BookmarkEntity>,
    notes: List<NoteEntity>,
    currentLocator: Locator?,
    onChapterClick: (Link) -> Unit,
    onBookmarkClick: (Locator) -> Unit,
    onNoteClick: (Locator) -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onDismiss: () -> Unit,
    getPositionLabel: (Locator) -> String,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.9f

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxSheetHeight)
        ) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("TOC") }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Bookmarks") }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text("Notes") }
                )
            }

            @OptIn(ExperimentalFoundationApi::class)
            CompositionLocalProvider(
                LocalOverscrollConfiguration provides null
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    beyondViewportPageCount = 2
                ) { page ->
                    when (page) {
                        0 -> TocList(tableOfContents, currentLocator, onChapterClick)
                        1 -> BookmarksList(bookmarks, tableOfContents, getPositionLabel, onBookmarkClick, onDeleteBookmark)
                        2 -> NotesList(notes, tableOfContents, getPositionLabel, onNoteClick, onDeleteNote)
                    }
                }
            }
            // Add some bottom padding
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text("Add Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter your note here...") },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (noteText.isNotBlank()) {
                        onAddNote(noteText)
                    }
                    noteText = ""
                    showAddNoteDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    noteText = ""
                    showAddNoteDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TocList(
    tableOfContents: List<Link>,
    currentLocator: Locator?,
    onChapterClick: (Link) -> Unit
) {
    if (tableOfContents.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No Table of Contents")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(tableOfContents) { link ->
                val isCurrentChapter = currentLocator?.href == link.href
                ListItem(
                    headlineContent = {
                        Text(
                            text = link.title ?: link.href.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            fontWeight = if (isCurrentChapter) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.clickable { onChapterClick(link) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun BookmarksList(
    bookmarks: List<BookmarkEntity>,
    tableOfContents: List<Link>,
    getPositionLabel: (Locator) -> String,
    onBookmarkClick: (Locator) -> Unit,
    onDeleteBookmark: (Long) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No Bookmarks")
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(bookmarks) { bookmark ->
                val locator = try { Locator.fromJSON(org.json.JSONObject(bookmark.locatorJson)) } catch (e: Exception) { null }
                if (locator != null) {
                    val chapterTitle = bookmark.chapterTitle
                        ?.takeIf { it.isNotBlank() && it != "In Document" }
                        ?: tableOfContents.find { it.href.toString().substringBefore("#") == locator.href.toString().substringBefore("#") }?.title
                        ?: "In Document"
                        
                    ListItem(
                        headlineContent = { Text(chapterTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { 
                            val pct = getPositionLabel(locator)
                            if (pct.isNotBlank()) Text(pct)
                        },
                        trailingContent = {
                            IconButton(onClick = { onDeleteBookmark(bookmark.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        },
                        modifier = Modifier.clickable { onBookmarkClick(locator) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesList(
    notes: List<NoteEntity>,
    tableOfContents: List<Link>,
    getPositionLabel: (Locator) -> String,
    onNoteClick: (Locator) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("No Notes")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(notes) { note ->
                    val locator = try { Locator.fromJSON(org.json.JSONObject(note.locatorJson)) } catch (e: Exception) { null }
                    if (locator != null) {
                        val pct = getPositionLabel(locator)
                        val chapterTitle = note.chapterTitle
                            ?.takeIf { it.isNotBlank() && it != "In Document" }
                            ?: tableOfContents.find { it.href.toString().substringBefore("#") == locator.href.toString().substringBefore("#") }?.title
                            ?: "In Document"
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNoteClick(locator) }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    // Line 1: Chapter Title
                                    Text(
                                        text = chapterTitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    // Line 2: Progression
                                    if (pct.isNotBlank()) {
                                        Text(
                                            text = pct,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(onClick = { onDeleteNote(note.id) }, modifier = Modifier.padding(start = 8.dp).size(24.dp)) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            
                            Spacer(Modifier.height(8.dp))
                            
                            // Line 3: Note text
                            Text(
                                text = note.noteText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
