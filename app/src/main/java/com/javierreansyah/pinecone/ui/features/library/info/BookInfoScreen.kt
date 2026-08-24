@file:Suppress("unused", "RedundantSuppression")

package com.javierreansyah.pinecone.ui.features.library.info

import android.app.Application
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.composables.icons.materialsymbols.outlined.Check_circle
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Create_new_folder
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Format_list_bulleted
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Radio_button_unchecked
import com.composables.icons.materialsymbols.outlined.Replay
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.BookmarkEntity
import com.javierreansyah.pinecone.data.local.database.library.NoteEntity
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.EmptyState
import com.javierreansyah.pinecone.ui.components.SegmentedColumn
import com.javierreansyah.pinecone.ui.features.reader.ReaderActivity
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.mediatype.MediaType
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BookInfoScreen(
    bookId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
    onNavigateToOrganize: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: BookInfoViewModel = viewModel(
        key = bookId, factory = BookInfoViewModel.Factory(application, bookId)
    )

    val uiState by viewModel.uiState.collectAsState()
    val book = uiState.book
    val isLoading = uiState.isLoading

    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = innerPadding.calculateStartPadding(LocalLayoutDirection.current),
                    end = innerPadding.calculateEndPadding(LocalLayoutDirection.current),
                    bottom = innerPadding.calculateBottomPadding()
                )
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (book == null) {
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.book_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                BookInfoContent(
                    book = book,
                    bookId = bookId,
                    viewModel = viewModel,
                    onNavigateBack = onNavigateBack,
                    onNavigateToEdit = onNavigateToEdit,
                    onNavigateToTag = onNavigateToTag,
                    onOrganizeClick = { onNavigateToOrganize(bookId) },
                    showDeleteConfirm = { showDeleteConfirm = true })
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(onDismissRequest = { showDeleteConfirm = false }, title = {
            Text(
                stringResource(R.string.book_delete_title),
                style = MaterialTheme.typography.titleLarge
            )
        }, text = {
            Text(
                stringResource(R.string.book_delete_message),
                style = MaterialTheme.typography.bodyMedium
            )
        }, confirmButton = {
            TextButton(
                onClick = {
                    viewModel.deleteBook {
                        showDeleteConfirm = false
                        onNavigateBack()
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(
                    stringResource(R.string.action_delete),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }, dismissButton = {
            TextButton(onClick = { showDeleteConfirm = false }) {
                Text(
                    stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        })
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookInfoContent(
    book: Book,
    bookId: String,
    viewModel: BookInfoViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
    onOrganizeClick: () -> Unit,
    showDeleteConfirm: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val maxTabHeight = remember(windowInfo.containerSize, density) {
        with(density) { (windowInfo.containerSize.height * 0.8f).toDp() }
    }

    val bookmarks by viewModel.bookmarks.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val tableOfContents by viewModel.tableOfContents.collectAsState()
    val positions by viewModel.positions.collectAsState()

    val currentLocator = remember(book.lastLocatorJson) {
        book.lastLocatorJson?.let {
            try {
                Locator.fromJSON(JSONObject(it))
            } catch (_: Exception) {
                null
            }
        }
    }

    val furthestLocator = remember(book.furthestLocatorJson) {
        book.furthestLocatorJson?.let {
            try {
                Locator.fromJSON(JSONObject(it))
            } catch (_: Exception) {
                null
            }
        }
    }

    val navigateToLocator: (Locator) -> Unit = { locator ->
        viewModel.saveReadingPosition(locator) {
            val intent = Intent(context, ReaderActivity::class.java).apply {
                putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
            }
            context.startActivity(intent)
        }
    }

    val jumpToLocator: (Locator) -> Unit = { locator ->
        viewModel.saveJumpReadingPosition(locator) {
            val intent = Intent(context, ReaderActivity::class.java).apply {
                putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
            }
            context.startActivity(intent)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BookBackgroundBanner(
            book = book, scrollState = scrollState
        )

        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    val delta = available.y
                    return if (delta < 0) {
                        val parentConsumed = scrollState.dispatchRawDelta(-delta)
                        Offset(0f, -parentConsumed)
                    } else {
                        Offset.Zero
                    }
                }

                override fun onPostScroll(
                    consumed: Offset, available: Offset, source: NestedScrollSource
                ): Offset {
                    val delta = available.y
                    return if (delta > 0) {
                        val parentConsumed = scrollState.dispatchRawDelta(-delta)
                        Offset(0f, -parentConsumed)
                    } else {
                        Offset.Zero
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .verticalScroll(scrollState), horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BookInfoHeader(
                book = book,
                onNavigateToTag = onNavigateToTag,
                onReadClick = {
                    val intent = Intent(context, ReaderActivity::class.java).apply {
                        putExtra(ReaderActivity.EXTRA_BOOK_ID, book.id)
                    }
                    context.startActivity(intent)
                },
                onReadFurthestClick = {
                    furthestLocator?.let { jumpToLocator(it) }
                },
                onReadFromStartClick = {
                    val firstPos =
                        positions.firstOrNull() ?: tableOfContents.firstOrNull()?.let { link ->
                            Locator(
                                href = link.url(),
                                mediaType = link.mediaType ?: MediaType.XHTML,
                                title = link.title
                            )
                        }
                    firstPos?.let { jumpToLocator(it) }
                },
                onToggleReadStatus = { viewModel.toggleReadStatus() },
                onOrganizeClick = onOrganizeClick,
                onToggleArchive = { viewModel.toggleArchive() },
                onDeleteClick = showDeleteConfirm
            )

            val pagerState = rememberPagerState(pageCount = { 4 })
            val coroutineScope = rememberCoroutineScope()
            val tabs = listOf(
                stringResource(R.string.book_info_title),
                stringResource(R.string.reader_chapters_title),
                stringResource(R.string.reader_bookmarks_title),
                stringResource(R.string.reader_notes_title)
            )

            SecondaryScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                edgePadding = 16.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = pagerState.currentPage == index, onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    }, text = {
                        Text(
                            text = title, style = MaterialTheme.typography.titleMedium
                        )
                    })
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .animateContentSize(),
                verticalAlignment = Alignment.Top
            ) { page ->
                when (page) {
                    0 -> AboutTabContent(
                        book = book, maxTabHeight = maxTabHeight
                    )

                    1 -> ChaptersTabContent(
                        tableOfContents = tableOfContents,
                        currentLocator = currentLocator,
                        positions = positions,
                        onChapterClick = jumpToLocator,
                        maxTabHeight = maxTabHeight
                    )

                    2 -> BookmarksTabContent(
                        bookmarks = bookmarks,
                        currentLocator = currentLocator,
                        furthestLocator = furthestLocator,
                        positions = positions,
                        tableOfContents = tableOfContents,
                        onNavigateToLocator = navigateToLocator,
                        onJumpToLocator = jumpToLocator,
                        onDeleteBookmark = { viewModel.deleteBookmark(it) },
                        onDeleteFurthestPosition = { viewModel.deleteFurthestPosition() },
                        maxTabHeight = maxTabHeight
                    )

                    3 -> NotesTabContent(
                        notes = notes,
                        tableOfContents = tableOfContents,
                        positions = positions,
                        onNoteClick = jumpToLocator,
                        onDeleteNote = { viewModel.deleteNote(it) },
                        maxTabHeight = maxTabHeight
                    )
                }
            }
        }

        BookInfoTopButtons(
            bookId = bookId,
            onNavigateBack = onNavigateBack,
            onNavigateToEdit = onNavigateToEdit,
            modifier = Modifier.statusBarsPadding()
        )
    }
}

@Composable
private fun BookBackgroundBanner(
    book: Book, scrollState: ScrollState, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(540.dp)
            .graphicsLayer {
                translationY = -scrollState.value.toFloat()
            }
            .clipToBounds()) {
        if (book.coverPath != null) {
            val coverModel = remember(book.coverPath) { File(book.coverPath) }
            AsyncImage(
                model = coverModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = scrollState.value * 0.5f
                    }
                    .blur(radius = 32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .scale(1.2f))
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                            )
                        )
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        )
    }
}

@Composable
private fun AboutTabContent(
    book: Book, maxTabHeight: Dp, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxTabHeight)
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BookProgress(progress = book.furthestProgression)

        if (!book.description.isNullOrBlank()) {
            BookDescription(description = book.description)
        }

        BookMetadata(book = book)
    }
}

@Composable
private fun ChaptersTabContent(
    tableOfContents: List<Link>,
    currentLocator: Locator?,
    positions: List<Locator>,
    onChapterClick: (Locator) -> Unit,
    maxTabHeight: Dp,
    modifier: Modifier = Modifier
) {
    if (tableOfContents.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(maxTabHeight)
        ) {
            EmptyState(
                icon = MaterialSymbols.Outlined.Format_list_bulleted,
                text = stringResource(R.string.reader_no_toc),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .height(maxTabHeight),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            val currentHref = currentLocator?.href?.toString()?.substringBefore("#")
            items(tableOfContents) { link ->
                val linkHref = link.href.toString().substringBefore("#")
                val isCurrentChapter = currentHref == linkHref
                val pageLabel = getChapterPageLabel(link, positions)
                ListItem(
                    modifier = Modifier.clickable {
                        val targetLocator = positions.firstOrNull {
                            it.href.toString().substringBefore("#") == link.href.toString()
                                .substringBefore("#")
                        } ?: Locator(
                            href = link.url(),
                            mediaType = link.mediaType ?: positions.firstOrNull()?.mediaType
                            ?: MediaType.XHTML,
                            title = link.title
                        )
                        onChapterClick(targetLocator)
                    },
                    leadingContent = null,
                    trailingContent = null,
                    overlineContent = null,
                    supportingContent = if (pageLabel.isNotBlank()) {
                        {
                            Text(
                                text = pageLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else null,
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    elevation = ListItemDefaults.elevation(),
                    content = {
                        Text(
                            text = link.title ?: link.href.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isCurrentChapter) MaterialTheme.colorScheme.primary else Color.Unspecified
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun BookmarksTabContent(
    bookmarks: List<BookmarkEntity>,
    currentLocator: Locator?,
    furthestLocator: Locator?,
    positions: List<Locator>,
    tableOfContents: List<Link>,
    onNavigateToLocator: (Locator) -> Unit,
    onJumpToLocator: (Locator) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteFurthestPosition: () -> Unit,
    maxTabHeight: Dp,
    modifier: Modifier = Modifier
) {
    val hasBookmarks = bookmarks.isNotEmpty() || currentLocator != null || furthestLocator != null
    if (!hasBookmarks) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(maxTabHeight)
        ) {
            EmptyState(
                icon = MaterialSymbols.Outlined.Bookmark,
                text = stringResource(R.string.reader_no_bookmarks),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .height(maxTabHeight),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            currentLocator?.let { locator ->
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            onNavigateToLocator(locator)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = null,
                        overlineContent = null,
                        supportingContent = {
                            val label = getPositionLabel(locator, positions)
                            Text(
                                text = label.ifBlank { stringResource(R.string.reader_in_document) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        elevation = ListItemDefaults.elevation(),
                        content = {
                            Text(
                                text = stringResource(R.string.book_info_current_position),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                    )
                }
            }

            furthestLocator?.let { locator ->
                item {
                    ListItem(
                        modifier = Modifier.clickable {
                            onJumpToLocator(locator)
                        },
                        leadingContent = {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        },
                        trailingContent = {
                            androidx.compose.material3.IconButton(
                                onClick = onDeleteFurthestPosition
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        overlineContent = null,
                        supportingContent = {
                            val label = getPositionLabel(locator, positions)
                            Text(
                                text = label.ifBlank { stringResource(R.string.reader_in_document) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        elevation = ListItemDefaults.elevation(),
                        content = {
                            Text(
                                text = stringResource(R.string.book_info_furthest_position),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                    )
                }
            }

            if (bookmarks.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.reader_bookmarks_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = 16.dp, vertical = 8.dp
                        )
                    )
                }

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
                                    it.href.toString()
                                        .substringBefore("#") == locator.href.toString()
                                        .substringBefore("#")
                                }?.title ?: inDocument

                        ListItem(
                            modifier = Modifier.clickable {
                                onJumpToLocator(locator)
                            },
                            leadingContent = null,
                            trailingContent = {
                                androidx.compose.material3.IconButton(
                                    onClick = { onDeleteBookmark(bookmark.id) }) {
                                    Icon(
                                        imageVector = MaterialSymbols.Outlined.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            overlineContent = null,
                            supportingContent = {
                                val label = getPositionLabel(locator, positions)
                                Text(
                                    text = label.ifBlank { inDocument },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            elevation = ListItemDefaults.elevation(),
                            content = {
                                Text(
                                    text = chapterTitle,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotesTabContent(
    notes: List<NoteEntity>,
    tableOfContents: List<Link>,
    positions: List<Locator>,
    onNoteClick: (Locator) -> Unit,
    onDeleteNote: (Long) -> Unit,
    maxTabHeight: Dp,
    modifier: Modifier = Modifier
) {
    if (notes.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(maxTabHeight)
        ) {
            EmptyState(
                icon = MaterialSymbols.Outlined.Edit,
                text = stringResource(R.string.reader_no_notes),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .padding(top = 32.dp, start = 16.dp, end = 16.dp)
            )
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxWidth()
                .height(maxTabHeight),
            contentPadding = PaddingValues(vertical = 8.dp)
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 16.dp)
                            ) {
                                Text(
                                    text = chapterTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val label = getPositionLabel(locator, positions)
                                if (label.isNotBlank()) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            androidx.compose.material3.IconButton(
                                onClick = { onDeleteNote(note.id) }, modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

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
                                        .background(
                                            Color(note.color).copy(
                                                alpha = 1f
                                            )
                                        )
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
private fun BookInfoHeader(
    book: Book,
    onNavigateToTag: (String) -> Unit,
    onReadClick: () -> Unit,
    onReadFurthestClick: () -> Unit,
    onReadFromStartClick: () -> Unit,
    onToggleReadStatus: () -> Unit,
    onOrganizeClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 64.dp, start = 16.dp, end = 16.dp, bottom = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BookCoverImage(
            coverPath = book.coverPath, title = book.title
        )

        BookHeaderDetails(
            title = book.title,
            authors = book.authors,
            spaces = book.spaces.map { it.name },
            tags = book.tags,
            onNavigateToTag = onNavigateToTag
        )

        BookButtonGroup(
            book = book,
            onReadClick = onReadClick,
            onReadFurthestClick = onReadFurthestClick,
            onReadFromStartClick = onReadFromStartClick,
            onToggleReadStatus = onToggleReadStatus,
            onOrganizeClick = onOrganizeClick,
            onToggleArchive = onToggleArchive,
            onDeleteClick = onDeleteClick
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BookButtonGroup(
    book: Book,
    onReadClick: () -> Unit,
    onReadFurthestClick: () -> Unit,
    onReadFromStartClick: () -> Unit,
    onToggleReadStatus: () -> Unit,
    onOrganizeClick: () -> Unit,
    onToggleArchive: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        var splitMenuExpanded by remember { mutableStateOf(false) }

        SplitButtonLayout(leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onReadClick,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Book,
                    contentDescription = null,
                    modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.action_read))
            }
        }, trailingButton = {
            val description = stringResource(R.string.action_more)
            Box {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                    tooltip = {
                        PlainTooltip { Text(description) }
                    },
                    state = rememberTooltipState()
                ) {
                    SplitButtonDefaults.TrailingButton(
                        checked = splitMenuExpanded,
                        onCheckedChange = { splitMenuExpanded = it },
                        modifier = Modifier.semantics {
                            stateDescription = if (splitMenuExpanded) "Expanded" else "Collapsed"
                            contentDescription = description
                        }) {
                        val rotation: Float by animateFloatAsState(
                            targetValue = if (splitMenuExpanded) 180f else 0f,
                            label = "Trailing Icon Rotation"
                        )
                        Icon(
                            MaterialSymbols.Outlined.Arrow_drop_down,
                            modifier = Modifier
                                .size(SplitButtonDefaults.TrailingIconSize)
                                .graphicsLayer { rotationZ = rotation },
                            contentDescription = description
                        )
                    }
                }

                DropdownMenuPopup(
                    expanded = splitMenuExpanded,
                    onDismissRequest = { splitMenuExpanded = false }) {
                    val groupInteractionSource = remember { MutableInteractionSource() }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(0, 1),
                        interactionSource = groupInteractionSource
                    ) {
                        DropdownMenuItem(
                            selected = false,
                            text = { Text(stringResource(R.string.book_read_furthest)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Book,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                splitMenuExpanded = false
                                onReadFurthestClick()
                            },
                            shapes = MenuDefaults.itemShape(0, 2)
                        )
                        DropdownMenuItem(
                            selected = false,
                            text = { Text(stringResource(R.string.book_read_from_start)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Replay,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                splitMenuExpanded = false
                                onReadFromStartClick()
                            },
                            shapes = MenuDefaults.itemShape(1, 2)
                        )
                    }
                }
            }
        })

        Box(
            modifier = Modifier
                .height(24.dp)
                .width(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        FilledTonalButton(
            onClick = onToggleReadStatus,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = if (book.isRead) MaterialSymbols.Outlined.Radio_button_unchecked else MaterialSymbols.Outlined.Check_circle,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (book.isRead) stringResource(R.string.book_mark_as_unread)
                else stringResource(R.string.book_mark_as_read)
            )
        }

        FilledTonalButton(
            onClick = onOrganizeClick,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Create_new_folder,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_organize))
        }

        FilledTonalIconButton(
            onClick = onToggleArchive, colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (book.isArchived) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (book.isArchived) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Archive,
                contentDescription = if (book.isArchived) stringResource(R.string.book_unarchive) else stringResource(
                    R.string.book_archive
                )
            )
        }

        FilledTonalIconButton(
            onClick = onDeleteClick, colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Delete,
                contentDescription = stringResource(R.string.action_delete)
            )
        }
    }
}

@Composable
private fun BookCoverImage(
    coverPath: String?, title: String, modifier: Modifier = Modifier
) {
    if (coverPath != null) {
        val coverModel = remember(coverPath) { File(coverPath) }
        AsyncImage(
            model = coverModel,
            contentDescription = stringResource(R.string.book_info_title),
            modifier = modifier
                .height(300.dp)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.Fit
        )
    } else {
        Box(
            modifier = modifier
                .height(300.dp)
                .aspectRatio(1f / 1.5f)
                .shadow(8.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                ), contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.take(1).uppercase(),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BookHeaderDetails(
    title: String,
    authors: List<String>,
    spaces: List<String>,
    tags: List<String>,
    onNavigateToTag: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val showMoreChip = tags.size > 5 && !isExpanded

    val tagsToShow = if (isExpanded) {
        tags
    } else if (tags.size > 5) {
        tags.take(4)
    } else {
        tags
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (authors.isNotEmpty()) authors.joinToString(", ") else stringResource(
                R.string.book_unknown_author
            ),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (spaces.isNotEmpty()) {
            Text(
                text = spaces.joinToString(", "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (tags.isNotEmpty()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    tagsToShow.forEach { tag ->
                        SuggestionChip(
                            onClick = { onNavigateToTag(tag) }, label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }, colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ), border = null
                        )
                    }

                    if (showMoreChip) {
                        SuggestionChip(
                            onClick = { isExpanded = true }, label = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.More_vert,
                                    contentDescription = stringResource(R.string.book_show_more_tags),
                                    modifier = Modifier.size(16.dp)
                                )
                            }, colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ), border = null
                        )
                    } else if (isExpanded && tags.size > 5) {
                        SuggestionChip(
                            onClick = { isExpanded = false }, label = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Close,
                                    contentDescription = stringResource(R.string.book_show_less_tags),
                                    modifier = Modifier.size(16.dp)
                                )
                            }, colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ), border = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookProgress(
    progress: Double, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.book_reading_progress),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LinearProgressIndicator(
            progress = { progress.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun BookDescription(
    description: String, modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val annotatedDescription = remember(description) {
        val spanned = HtmlCompat.fromHtml(
            description, HtmlCompat.FROM_HTML_MODE_COMPACT
        )

        val trimmed = spanned.toString().trimEnd()
        androidx.compose.ui.text.AnnotatedString(trimmed)
    }

    Column(
        modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.book_description),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = annotatedDescription,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (isExpanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
        )
        if (description.length > 200) {
            val labelText =
                if (isExpanded) stringResource(R.string.book_read_less) else stringResource(
                    R.string.book_read_more
                )
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() }, indication = null
                ) {
                    isExpanded = !isExpanded
                })
        }
    }
}

@Composable
private fun BookMetadata(
    book: Book, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.book_publication_details),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        val notAvailable = stringResource(R.string.book_not_available)
        SegmentedColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.book_publisher),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = book.publisher ?: notAvailable,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.book_published_date),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatPublishedDate(book.published, notAvailable),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.book_language),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = book.language ?: notAvailable,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.book_identifier),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = book.identifier ?: notAvailable,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.book_format),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = parseBookFormat(
                            book.mediaType, stringResource(R.string.book_unknown)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookInfoTopButtons(
    bookId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = onNavigateBack,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = 0.8f
                )
            )
        ) {
            Icon(
                MaterialSymbols.Outlined.Arrow_back,
                contentDescription = stringResource(R.string.action_back)
            )
        }

        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            onClick = { onNavigateToEdit(bookId) },
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                    alpha = 0.8f
                )
            )
        ) {
            Icon(
                MaterialSymbols.Outlined.Edit,
                contentDescription = stringResource(R.string.action_edit),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatPublishedDate(dateString: String?, defaultValue: String): String {
    if (dateString.isNullOrBlank()) return defaultValue
    return try {
        val odt = OffsetDateTime.parse(dateString)
        odt.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
    } catch (_: Exception) {
        try {
            val ld = LocalDate.parse(dateString.substringBefore('T'))
            ld.format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.getDefault()))
        } catch (_: Exception) {
            dateString
        }
    }
}

private fun parseBookFormat(mediaType: String?, defaultUnknown: String): String {
    if (mediaType.isNullOrBlank()) return defaultUnknown

    val key = "fileextention="
    val altKey = "fileextension="
    val lowerMediaType = mediaType.lowercase()

    val index = when {
        lowerMediaType.contains(key) -> lowerMediaType.indexOf(key) + key.length
        lowerMediaType.contains(altKey) -> lowerMediaType.indexOf(altKey) + altKey.length
        else -> -1
    }

    if (index != -1) {
        val substring = mediaType.substring(index)
        val word = substring.takeWhile { it.isLetterOrDigit() }
        if (word.isNotBlank()) {
            return word.uppercase()
        }
        return substring.replace(")", "").trim().uppercase()
    }

    val mime = mediaType.trim().lowercase()
    return when {
        mime == "application/epub+zip" -> "EPUB"
        mime == "application/x-cbz" -> "CBZ"
        mime == "application/x-cbr" -> "CBR"
        mime == "application/pdf" -> "PDF"
        mime.contains("/") -> {
            val lastSegment = mime.substringAfterLast('/')
            val clean = lastSegment.removePrefix("x-").substringBefore('+').uppercase()
            clean.ifBlank { lastSegment.uppercase() }
        }

        else -> mime.uppercase()
    }
}

@Composable
private fun getPositionLabel(locator: Locator, positions: List<Locator>): String {
    val posIndex = locator.locations.totalProgression?.let { target ->
        positions.indexOfLast { (it.locations.totalProgression ?: -1.0) <= target }
    }?.takeIf { it != -1 } ?: positions.indexOfLast { pos ->
        pos.href == locator.href && (pos.locations.progression
            ?: 0.0) <= (locator.locations.progression ?: 0.0)
    }.takeIf { it != -1 }

    return when {
        posIndex != null -> pluralStringResource(
            R.plurals.reader_page_num, posIndex + 1, posIndex + 1
        )

        locator.locations.totalProgression != null -> stringResource(
            R.string.reader_position_at, "${(locator.locations.totalProgression!! * 100).toInt()}%"
        )

        else -> ""
    }
}

@Composable
private fun getChapterPageLabel(link: Link, positions: List<Locator>): String {
    if (positions.isEmpty()) return ""
    val linkHref = link.href.toString().substringBefore("#")
    val posIndex = positions.indexOfFirst {
        it.href.toString().substringBefore("#") == linkHref
    }
    return if (posIndex != -1) {
        pluralStringResource(
            R.plurals.reader_page_num, posIndex + 1, posIndex + 1
        )
    } else ""
}
