package com.example.readerapp.ui.features.reader.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Format_list_bulleted
import com.composables.icons.materialsymbols.outlined.Sort
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.ui.components.EmptyState
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator


@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
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
    getChapterPageLabel: (Link) -> String,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val maxSheetHeight = remember(windowInfo.containerSize, density) {
        with(density) { (windowInfo.containerSize.height * 0.85f).toDp() }
    }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()

    var showAddNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    var currentSortOption by rememberSaveable { mutableStateOf(SortOption.BOOK_ORDER_ASC) }

    val sortedBookmarks = remember(bookmarks, currentSortOption, tableOfContents) {
        bookmarks.map { bookmark ->
            val locator = try {
                Locator.fromJSON(JSONObject(bookmark.locatorJson))
            } catch (_: Exception) {
                null
            }
            bookmark to locator
        }.sortedWith { pair1, pair2 ->
            val (b1, l1) = pair1
            val (b2, l2) = pair2
            when (currentSortOption) {
                SortOption.CREATION_DATE_ASC -> b1.createdAt.compareTo(b2.createdAt)
                SortOption.CREATION_DATE_DESC -> b2.createdAt.compareTo(b1.createdAt)
                SortOption.BOOK_ORDER_ASC -> {
                    if (l1 != null && l2 != null) {
                        val comp = compareLocators(l1, l2, tableOfContents)
                        if (comp != 0) comp else b1.createdAt.compareTo(b2.createdAt)
                    } else if (l1 != null) {
                        -1
                    } else if (l2 != null) {
                        1
                    } else {
                        b1.createdAt.compareTo(b2.createdAt)
                    }
                }

                SortOption.BOOK_ORDER_DESC -> {
                    if (l1 != null && l2 != null) {
                        val comp = compareLocators(l2, l1, tableOfContents)
                        if (comp != 0) comp else b2.createdAt.compareTo(b1.createdAt)
                    } else if (l1 != null) {
                        1
                    } else if (l2 != null) {
                        -1
                    } else {
                        b2.createdAt.compareTo(b1.createdAt)
                    }
                }
            }
        }.map { it.first }
    }

    val sortedNotes = remember(notes, currentSortOption, tableOfContents) {
        notes.map { note ->
            val locator = try {
                Locator.fromJSON(JSONObject(note.locatorJson))
            } catch (_: Exception) {
                null
            }
            note to locator
        }.sortedWith { pair1, pair2 ->
            val (n1, l1) = pair1
            val (n2, l2) = pair2
            when (currentSortOption) {
                SortOption.CREATION_DATE_ASC -> n1.createdAt.compareTo(n2.createdAt)
                SortOption.CREATION_DATE_DESC -> n2.createdAt.compareTo(n1.createdAt)
                SortOption.BOOK_ORDER_ASC -> {
                    if (l1 != null && l2 != null) {
                        val comp = compareLocators(l1, l2, tableOfContents)
                        if (comp != 0) comp else n1.createdAt.compareTo(n2.createdAt)
                    } else if (l1 != null) {
                        -1
                    } else if (l2 != null) {
                        1
                    } else {
                        n1.createdAt.compareTo(n2.createdAt)
                    }
                }

                SortOption.BOOK_ORDER_DESC -> {
                    if (l1 != null && l2 != null) {
                        val comp = compareLocators(l2, l1, tableOfContents)
                        if (comp != 0) comp else n2.createdAt.compareTo(n1.createdAt)
                    } else if (l1 != null) {
                        1
                    } else if (l2 != null) {
                        -1
                    } else {
                        n2.createdAt.compareTo(n1.createdAt)
                    }
                }
            }
        }.map { it.first }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxSheetHeight)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                PrimaryTabRow(
                    selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                        text = {
                            Text(
                                stringResource(R.string.reader_chapters_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        })
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(1) } },
                        text = {
                            Text(
                                stringResource(R.string.reader_bookmarks_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        })
                    Tab(
                        selected = pagerState.currentPage == 2,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(2) } },
                        text = {
                            Text(
                                stringResource(R.string.reader_notes_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                        })
                }

                @OptIn(ExperimentalFoundationApi::class) CompositionLocalProvider(
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
                            0 -> TocList(
                                tableOfContents, currentLocator, getChapterPageLabel, onChapterClick
                            )

                            1 -> BookmarksList(
                                sortedBookmarks,
                                tableOfContents,
                                getPositionLabel,
                                onBookmarkClick,
                                onDeleteBookmark
                            )

                            2 -> NotesList(
                                sortedNotes,
                                tableOfContents,
                                getPositionLabel,
                                onNoteClick,
                                onDeleteNote
                            )
                        }
                    }
                }
            }

            this@ModalBottomSheet.AnimatedVisibility(
                visible = pagerState.currentPage == 1 || pagerState.currentPage == 2,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp),
                enter = fadeIn(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
                        scaleIn(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            initialScale = 0.8f
                        ),
                exit = fadeOut(animationSpec = MaterialTheme.motionScheme.fastEffectsSpec()) +
                        scaleOut(
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            targetScale = 0.8f
                        )
            ) {
                Box {
                    var showSortMenu by remember { mutableStateOf(false) }
                    FloatingActionButton(
                        onClick = { showSortMenu = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Sort,
                            contentDescription = stringResource(R.string.action_sort)
                        )
                    }

                    DropdownMenuPopup(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        val groupInteractionSource = remember { MutableInteractionSource() }
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShape(0, 1),
                            interactionSource = groupInteractionSource
                        ) {
                            DropdownMenuItem(
                                selected = currentSortOption == SortOption.CREATION_DATE_DESC,
                                text = {
                                    Text(stringResource(R.string.reader_sort_creation_date_desc))
                                },
                                onClick = {
                                    currentSortOption = SortOption.CREATION_DATE_DESC
                                    showSortMenu = false
                                },
                                shapes = MenuDefaults.itemShape(0, 4)
                            )
                            DropdownMenuItem(
                                selected = currentSortOption == SortOption.CREATION_DATE_ASC,
                                text = {
                                    Text(stringResource(R.string.reader_sort_creation_date_asc))
                                },
                                onClick = {
                                    currentSortOption = SortOption.CREATION_DATE_ASC
                                    showSortMenu = false
                                },
                                shapes = MenuDefaults.itemShape(1, 4)
                            )
                            DropdownMenuItem(
                                selected = currentSortOption == SortOption.BOOK_ORDER_ASC,
                                text = {
                                    Text(stringResource(R.string.reader_sort_book_order_asc))
                                },
                                onClick = {
                                    currentSortOption = SortOption.BOOK_ORDER_ASC
                                    showSortMenu = false
                                },
                                shapes = MenuDefaults.itemShape(2, 4)
                            )
                            DropdownMenuItem(
                                selected = currentSortOption == SortOption.BOOK_ORDER_DESC,
                                text = {
                                    Text(stringResource(R.string.reader_sort_book_order_desc))
                                },
                                onClick = {
                                    currentSortOption = SortOption.BOOK_ORDER_DESC
                                    showSortMenu = false
                                },
                                shapes = MenuDefaults.itemShape(3, 4)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddNoteDialog) {
        AlertDialog(onDismissRequest = { showAddNoteDialog = false }, title = {
            Text(
                stringResource(R.string.reader_add_note),
                style = MaterialTheme.typography.titleLarge
            )
        }, text = {
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(R.string.reader_enter_note_placeholder),
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                minLines = 3
            )
        }, confirmButton = {
            TextButton(onClick = {
                if (noteText.isNotBlank()) {
                    onAddNote(noteText)
                }
                noteText = ""
                showAddNoteDialog = false
            }) {
                Text(
                    stringResource(R.string.action_save),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }, dismissButton = {
            TextButton(onClick = {
                noteText = ""
                showAddNoteDialog = false
            }) {
                Text(
                    stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        })
    }
}

enum class SortOption {
    CREATION_DATE_ASC,
    CREATION_DATE_DESC,
    BOOK_ORDER_ASC,
    BOOK_ORDER_DESC
}

private fun compareLocators(loc1: Locator, loc2: Locator, tableOfContents: List<Link>): Int {
    // 1. Compare totalProgression if available on both
    val tp1 = loc1.locations.totalProgression
    val tp2 = loc2.locations.totalProgression
    if (tp1 != null && tp2 != null) {
        return tp1.compareTo(tp2)
    }

    // 2. Compare based on index of href in TOC
    val href1 = loc1.href.toString().substringBefore("#")
    val href2 = loc2.href.toString().substringBefore("#")
    if (href1 != href2) {
        val idx1 = tableOfContents.indexOfFirst { it.href.toString().substringBefore("#") == href1 }
        val idx2 = tableOfContents.indexOfFirst { it.href.toString().substringBefore("#") == href2 }
        if (idx1 != -1 && idx2 != -1) {
            return idx1.compareTo(idx2)
        } else if (idx1 != -1) {
            return -1
        } else if (idx2 != -1) {
            return 1
        }
        return href1.compareTo(href2)
    }

    // 3. Same resource, compare progression
    val p1 = loc1.locations.progression
    val p2 = loc2.locations.progression
    if (p1 != null && p2 != null) {
        return p1.compareTo(p2)
    }

    // 4. Fallback to position
    val pos1 = loc1.locations.position
    val pos2 = loc2.locations.position
    if (pos1 != null && pos2 != null) {
        return pos1.compareTo(pos2)
    }

    return 0
}

@Composable
private fun TocList(
    tableOfContents: List<Link>,
    currentLocator: Locator?,
    getChapterPageLabel: (Link) -> String,
    onChapterClick: (Link) -> Unit
) {
    if (tableOfContents.isEmpty()) {
        EmptyState(
            icon = MaterialSymbols.Outlined.Format_list_bulleted,
            text = stringResource(R.string.reader_no_toc),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        )
    } else {
        val currentChapterIndex = remember(tableOfContents, currentLocator) {
            if (currentLocator == null) return@remember 0
            val currentHref = currentLocator.href.toString().substringBefore("#")
            val index = tableOfContents.indexOfFirst {
                it.href.toString().substringBefore("#") == currentHref
            }
            if (index >= 0) index else 0
        }

        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = currentChapterIndex
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(tableOfContents.size) { index ->
                val link = tableOfContents[index]
                val currentHref = currentLocator?.href?.toString()?.substringBefore("#")
                val linkHref = link.href.toString().substringBefore("#")
                val isCurrentChapter = currentHref == linkHref
                val pageLabel = getChapterPageLabel(link)
                ListItem(
                    headlineContent = {
                        Text(
                            text = link.title ?: link.href.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    },
                    supportingContent = if (pageLabel.isNotBlank()) {
                        {
                            Text(
                                text = pageLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else null,
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

@Composable
private fun NotesList(
    notes: List<NoteEntity>,
    tableOfContents: List<Link>,
    getPositionLabel: (Locator) -> String,
    onNoteClick: (Locator) -> Unit,
    onDeleteNote: (Long) -> Unit
) {
    if (notes.isEmpty()) {
        EmptyState(
            icon = MaterialSymbols.Outlined.Edit,
            text = stringResource(R.string.reader_no_notes),
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(notes) { note ->
                val locator = try {
                    Locator.fromJSON(JSONObject(note.locatorJson))
                } catch (_: Exception) {
                    null
                }
                if (locator != null) {
                    val inDocument = stringResource(R.string.reader_in_document)
                    val chapterTitle =
                        note.chapterTitle?.takeIf { it.isNotBlank() && it != inDocument }
                            ?: tableOfContents.find {
                                it.href.toString().substringBefore("#") == locator.href.toString()
                                    .substringBefore("#")
                            }?.title ?: inDocument

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNoteClick(locator) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EntryHeader(
                            title = chapterTitle,
                            positionLabel = getPositionLabel(locator),
                            onDelete = { onDeleteNote(note.id) })

                        // Display the highlighted text if available
                        locator.text.highlight?.takeIf { it.isNotBlank() }?.let { highlight ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(Color(note.color).copy(alpha = 1f))
                                )
                                Text(
                                    text = highlight,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 12.dp)
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

@Composable
private fun EntryHeader(
    title: String, positionLabel: String, onDelete: () -> Unit, modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
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
            onClick = onDelete, modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        "#40fac02e".toColorInt(), // Yellow
        "#40fd7142".toColorInt(), // Orange
        "#408bc24a".toColorInt(), // Green
        "#4025c6da".toColorInt()  // Blue
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Text field for editing note text
            OutlinedTextField(
                value = editText, onValueChange = {
                    editText = it
                }, modifier = Modifier.fillMaxWidth(), placeholder = {
                    Text(
                        stringResource(R.string.reader_note_text_placeholder),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }, textStyle = MaterialTheme.typography.bodyLarge, minLines = 2
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
                            })
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
                    }, modifier = Modifier.weight(1f), shape = ButtonDefaults.shape
                ) {
                    Text(
                        stringResource(R.string.action_delete),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                Button(
                    onClick = {
                        onUpdateNote(note.copy(noteText = editText, color = editColor))
                        onDismiss()
                    }, modifier = Modifier.weight(1f), shape = ButtonDefaults.shape
                ) {
                    Text(
                        stringResource(R.string.action_save),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}



