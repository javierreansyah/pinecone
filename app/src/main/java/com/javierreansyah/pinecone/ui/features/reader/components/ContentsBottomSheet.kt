package com.javierreansyah.pinecone.ui.features.reader.components

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Sort
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.ui.features.reader.components.contents.BookmarksList
import com.javierreansyah.pinecone.ui.features.reader.components.contents.NotesList
import com.javierreansyah.pinecone.ui.features.reader.components.contents.TocList
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
)
@Composable
fun ReaderBottomSheet(
    tableOfContents: List<Link>,
    sortedBookmarks: List<BookmarkEntity>,
    sortedNotes: List<NoteEntity>,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
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
                modifier = Modifier.fillMaxSize()
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
                                getPositionLabel,
                                onBookmarkClick,
                                onDeleteBookmark
                            )

                            2 -> NotesList(
                                sortedNotes,
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
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
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
                                selected = sortOption == SortOption.CREATION_DATE_DESC,
                                text = {
                                    Text(stringResource(R.string.reader_sort_creation_date_desc))
                                },
                                onClick = {
                                    onSortOptionChange(SortOption.CREATION_DATE_DESC)
                                    showSortMenu = false
                                },
                                shapes = MenuDefaults.itemShape(0, 4)
                            )
                            DropdownMenuItem(
                                selected = sortOption == SortOption.CREATION_DATE_ASC,
                                text = {
                                    Text(stringResource(R.string.reader_sort_creation_date_asc))
                                },
                                onClick = {
                                    onSortOptionChange(SortOption.CREATION_DATE_ASC)
                                    showSortMenu = false
                                },
                                shapes = MenuDefaults.itemShape(1, 4)
                            )
                            DropdownMenuItem(
                                selected = sortOption == SortOption.BOOK_ORDER_ASC,
                                text = {
                                    Text(stringResource(R.string.reader_sort_book_order_asc))
                                },
                                onClick = {
                                    onSortOptionChange(SortOption.BOOK_ORDER_ASC)
                                    showSortMenu = false
                                },
                                shapes = MenuDefaults.itemShape(2, 4)
                            )
                            DropdownMenuItem(
                                selected = sortOption == SortOption.BOOK_ORDER_DESC,
                                text = {
                                    Text(stringResource(R.string.reader_sort_book_order_desc))
                                },
                                onClick = {
                                    onSortOptionChange(SortOption.BOOK_ORDER_DESC)
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
