package com.example.readerapp.ui.features.reader.components.contents

import android.annotation.SuppressLint
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteEntity
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@SuppressLint("ConfigurationScreenWidthHeight")
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

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheetState, modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(maxSheetHeight)
                .padding(bottom = 32.dp)
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
                            bookmarks,
                            tableOfContents,
                            getPositionLabel,
                            onBookmarkClick,
                            onDeleteBookmark
                        )

                        2 -> NotesList(
                            notes, tableOfContents, getPositionLabel, onNoteClick, onDeleteNote
                        )
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

