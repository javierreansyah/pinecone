package com.example.readerapp.ui.features.reader.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.readerapp.ui.theme.AppTheme
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Delete
import com.example.readerapp.data.local.BookmarkEntity
import com.example.readerapp.data.local.NoteEntity
import androidx.core.graphics.toColorInt
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import com.example.readerapp.ui.theme.spacing

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

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
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
                .padding(bottom = 32.dp)
        ) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("TOC", style = MaterialTheme.typography.titleMedium) }
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Bookmarks", style = MaterialTheme.typography.titleMedium) }
                )
                Tab(
                    selected = pagerState.currentPage == 2,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                    text = { Text("Notes", style = MaterialTheme.typography.titleMedium) }
                )
            }

            @OptIn(ExperimentalFoundationApi::class)
            CompositionLocalProvider(
                LocalOverscrollFactory provides null
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    beyondViewportPageCount = 2,
                    verticalAlignment = Alignment.Top
                ) { page ->
                    when (page) {
                        0 -> TocList(tableOfContents, currentLocator, onChapterClick)
                        1 -> BookmarksList(bookmarks, tableOfContents, getPositionLabel, onBookmarkClick, onDeleteBookmark)
                        2 -> NotesList(notes, tableOfContents, getPositionLabel, onNoteClick, onDeleteNote)
                    }
                }
            }
        }
    }

    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text("Add Note", style = MaterialTheme.typography.titleLarge) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter your note here...", style = MaterialTheme.typography.bodyLarge) },
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
                    Text("Save", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    noteText = ""
                    showAddNoteDialog = false
                }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge)
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
            Text("No Table of Contents", style = MaterialTheme.typography.bodyLarge)
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
                            style = if (isCurrentChapter) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else Color.Unspecified
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
private fun EntryHeader(
    title: String,
    positionLabel: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (positionLabel.isNotBlank()) {
                Text(
                    text = positionLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            Text("No Bookmarks", style = MaterialTheme.typography.bodyLarge)
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

@Composable
private fun NotesList(
    notes: List<NoteEntity>,
    tableOfContents: List<Link>,
    getPositionLabel: (Locator) -> String,
    onNoteClick: (Locator) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    if (notes.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No Notes & Highlights", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(notes) { note ->
                val locator = try { Locator.fromJSON(org.json.JSONObject(note.locatorJson)) } catch (e: Exception) { null }
                if (locator != null) {
                    val chapterTitle = note.chapterTitle
                        ?.takeIf { it.isNotBlank() && it != "In Document" }
                        ?: tableOfContents.find { it.href.toString().substringBefore("#") == locator.href.toString().substringBefore("#") }?.title
                        ?: "In Document"
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNoteClick(locator) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space8)
                    ) {
                        EntryHeader(
                            title = chapterTitle,
                            positionLabel = getPositionLabel(locator),
                            onDelete = { onDeleteNote(note.id) }
                        )
                        
                        // Display the highlighted text if available
                        locator.text.highlight?.takeIf { it.isNotBlank() }?.let { highlight ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    text = highlight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(8.dp),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                        
                        if (note.noteText.isNotBlank()) {
                            Text(
                                text = note.noteText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteBottomSheet(
    note: NoteEntity,
    onUpdateNote: (NoteEntity) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    var editText by remember(note.id) { mutableStateOf(note.noteText) }
    var editColor by remember(note.id) { mutableIntStateOf(note.color) }

    val swatches = listOf(
        "#40FFEB3B".toColorInt(), // Yellow
        "#40F44336".toColorInt(), // Red
        "#4003A9F4".toColorInt(), // Blue
        "#404CAF50".toColorInt()  // Green
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space16)
        ) {
            // Text field for editing note text
            OutlinedTextField(
                value = editText,
                onValueChange = {
                    editText = it
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Note text...", style = MaterialTheme.typography.bodyLarge) },
                textStyle = MaterialTheme.typography.bodyLarge,
                minLines = 2
            )

            // Color Swatches: smaller, outline and ring look, justified left
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                swatches.forEach { colorInt ->
                    val isSelected = editColor == colorInt
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .let { m ->
                                if (isSelected) {
                                    m.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                } else m
                            }
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(Color(colorInt).copy(alpha = 1f))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable {
                                editColor = colorInt
                            }
                    )
                }
            }

            // Buttons: full width side by side
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        onDeleteNote(note.id)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = ButtonDefaults.shape
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = {
                        onUpdateNote(note.copy(noteText = editText, color = editColor))
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                    shape = ButtonDefaults.shape
                ) {
                    Text("Save", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
