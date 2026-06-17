package com.example.readerapp.ui.features.library.info

import android.app.Application
import android.content.Intent
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.composables.icons.materialsymbols.outlined.Bookmark_add
import com.composables.icons.materialsymbols.outlined.Check_circle
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Format_list_bulleted
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Radio_button_unchecked
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.components.EmptyState
import com.example.readerapp.ui.components.HtmlPreset
import com.example.readerapp.ui.components.HtmlWebView
import com.example.readerapp.ui.components.HtmlWebViewConfig
import com.example.readerapp.ui.components.SegmentedColumn
import com.example.readerapp.ui.features.library.components.ShelfListItem
import com.example.readerapp.ui.features.reader.ReaderActivity
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
    onNavigateToTag: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: BookInfoViewModel = viewModel(
        key = bookId,
        factory = BookInfoViewModel.Factory(application, bookId)
    )

    val uiState by viewModel.uiState.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    val book = uiState.book
    val isLoading = uiState.isLoading

    var showShelfDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var newShelfName by remember { mutableStateOf("") }

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
                    showShelfDialog = { showShelfDialog = true },
                    showDeleteConfirm = { showDeleteConfirm = true }
                )
            }
        }
    }

    if (showShelfDialog) {
        Dialog(
            onDismissRequest = { showShelfDialog = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.library_select_shelf_title)) },
                            navigationIcon = {
                                FilledTonalIconButton(
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ),
                                    onClick = { showShelfDialog = false }
                                ) {
                                    Icon(
                                        MaterialSymbols.Outlined.Arrow_back,
                                        contentDescription = stringResource(R.string.action_back)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                scrolledContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = { showCreateShelfDialog = true }
                        ) {
                            Icon(
                                MaterialSymbols.Outlined.Add,
                                contentDescription = stringResource(R.string.action_create)
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .padding(paddingValues)
                            .fillMaxSize()
                    ) {
                        val validShelves = shelves.filter { it.shelf.id != "unshelved" }
                        if (validShelves.isEmpty()) {
                            EmptyState(
                                icon = MaterialSymbols.Outlined.Folder,
                                text = stringResource(R.string.library_empty_shelves),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(validShelves) { shelfWithCovers ->
                                    ShelfListItem(
                                        shelfWithCovers = shelfWithCovers,
                                        onClick = {
                                            viewModel.addBookToShelf(shelfWithCovers.shelf.id)
                                            showShelfDialog = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateShelfDialog) {
        AlertDialog(
            onDismissRequest = { showCreateShelfDialog = false },
            title = {
                Text(
                    stringResource(R.string.library_create_shelf_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                OutlinedTextField(
                    value = newShelfName,
                    onValueChange = { newShelfName = it },
                    label = {
                        Text(
                            stringResource(R.string.library_shelf_name_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newShelfName.isNotBlank()) {
                            viewModel.createShelfAndAddBook(newShelfName)
                            newShelfName = ""
                            showCreateShelfDialog = false
                            showShelfDialog = false
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.action_create),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateShelfDialog = false }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    stringResource(R.string.book_delete_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    stringResource(R.string.book_delete_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
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
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        stringResource(R.string.action_cancel),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        )
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
    showShelfDialog: () -> Unit,
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
            book = book,
            scrollState = scrollState
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
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource
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
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
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
                onToggleReadStatus = { viewModel.toggleReadStatus() },
                onAddToShelfClick = showShelfDialog,
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
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    )
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
                        book = book,
                        maxTabHeight = maxTabHeight
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
    book: Book,
    scrollState: ScrollState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(540.dp)
            .graphicsLayer {
                translationY = -scrollState.value.toFloat()
            }
            .clipToBounds()
    ) {
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
                    .scale(1.2f)
            )
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
    book: Book,
    maxTabHeight: Dp,
    modifier: Modifier = Modifier
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
                    modifier = Modifier.clickable {
                        val targetLocator = positions.firstOrNull {
                            it.href.toString()
                                .substringBefore("#") == link.href.toString()
                                .substringBefore("#")
                        } ?: Locator(
                            href = link.url(),
                            mediaType = link.mediaType
                                ?: positions.firstOrNull()?.mediaType
                                ?: MediaType.XHTML,
                            title = link.title
                        )
                        onChapterClick(targetLocator)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.book_info_current_position),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            val label = getPositionLabel(locator, positions)
                            Text(
                                text = label.ifBlank { stringResource(R.string.reader_in_document) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Book,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable {
                            onNavigateToLocator(locator)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            furthestLocator?.let { locator ->
                item {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.book_info_furthest_position),
                                style = MaterialTheme.typography.titleMedium
                            )
                        },
                        supportingContent = {
                            val label = getPositionLabel(locator, positions)
                            Text(
                                text = label.ifBlank { stringResource(R.string.reader_in_document) },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Bookmark,
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
                        modifier = Modifier.clickable {
                            onJumpToLocator(locator)
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                            horizontal = 16.dp,
                            vertical = 8.dp
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
                            headlineContent = {
                                Text(
                                    text = chapterTitle,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            },
                            supportingContent = {
                                val label = getPositionLabel(locator, positions)
                                Text(
                                    text = label.ifBlank { inDocument },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                androidx.compose.material3.IconButton(
                                    onClick = { onDeleteBookmark(bookmark.id) }
                                ) {
                                    Icon(
                                        imageVector = MaterialSymbols.Outlined.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                onJumpToLocator(locator)
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                                onClick = { onDeleteNote(note.id) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        locator.text.highlight?.takeIf { it.isNotBlank() }
                            ?.let { highlight ->
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
    onToggleReadStatus: () -> Unit,
    onAddToShelfClick: () -> Unit,
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
            coverPath = book.coverPath,
            title = book.title
        )

        BookHeaderDetails(
            title = book.title,
            authors = book.authors,
            tags = book.tags,
            onNavigateToTag = onNavigateToTag
        )

        BookButtonGroup(
            book = book,
            onReadClick = onReadClick,
            onToggleReadStatus = onToggleReadStatus,
            onAddToShelfClick = onAddToShelfClick,
            onToggleArchive = onToggleArchive,
            onDeleteClick = onDeleteClick
        )
    }
}

@Composable
private fun BookButtonGroup(
    book: Book,
    onReadClick: () -> Unit,
    onToggleReadStatus: () -> Unit,
    onAddToShelfClick: () -> Unit,
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
        Button(
            onClick = onReadClick,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Book,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.action_read))
        }

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
            onClick = onAddToShelfClick,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Bookmark_add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.library_label_add_to_shelf))
        }

        FilledTonalIconButton(
            onClick = onToggleArchive,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
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
            onClick = onDeleteClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
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
    coverPath: String?,
    title: String,
    modifier: Modifier = Modifier
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
                            onClick = { onNavigateToTag(tag) },
                            label = {
                                Text(
                                    text = tag,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null
                        )
                    }

                    if (showMoreChip) {
                        SuggestionChip(
                            onClick = { isExpanded = true },
                            label = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.More_vert,
                                    contentDescription = "Show more tags",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null
                        )
                    } else if (isExpanded && tags.size > 5) {
                        SuggestionChip(
                            onClick = { isExpanded = false },
                            label = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Close,
                                    contentDescription = "Show less tags",
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookProgress(
    progress: Double,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
    description: String,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.book_description),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        // WebView renders the raw publisher HTML properly — handles <BR>, mixed
        // <b><i> preambles, bare <div>/<p> blocks, &amp; entities, etc.
        // Expand/collapse is driven by animateContentSize on the wrapper Box.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (!isExpanded) Modifier.heightIn(max = 120.dp) else Modifier)
                .animateContentSize()
        ) {
            HtmlWebView(
                htmlContent = description,
                config = HtmlWebViewConfig(preset = HtmlPreset.Description),
                baseFontSize = MaterialTheme.typography.bodyMedium.fontSize
            )
        }
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
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isExpanded = !isExpanded
                }
            )
        }
    }
}

@Composable
private fun BookMetadata(
    book: Book,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            book.mediaType,
                            stringResource(R.string.book_unknown)
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

    // Fallback parsing for traditional mime types
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
            R.string.reader_position_at,
            "${(locator.locations.totalProgression!! * 100).toInt()}%"
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
