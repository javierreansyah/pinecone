package com.example.readerapp.ui.features.reader.components.contents

import android.annotation.SuppressLint
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Sort
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteEntity
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator


@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
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

            androidx.compose.animation.AnimatedVisibility(
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



