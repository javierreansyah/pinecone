package com.example.readerapp.ui.features.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.*
import androidx.compose.material3.SearchBarValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.*
import com.example.readerapp.data.local.ShelfEntity
import com.example.readerapp.data.local.ShelfWithCovers
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.features.library.components.*
import com.example.readerapp.ui.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    onNavigateToReader: (String) -> Unit,
    onOpenDrawerClick: () -> Unit,
    onNavigateToShelf: (String) -> Unit,
    onNavigateToAuthor: (String) -> Unit = {},
    onNavigateToTag: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: LibraryViewModel = viewModel(
        factory = object : ViewModelProvider.AndroidViewModelFactory(context.applicationContext as android.app.Application) {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                    @Suppress("UNCHECKED_CAST")
                    return LibraryViewModel(context.applicationContext as android.app.Application) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    )
    
    val uiState by viewModel.uiState.collectAsState()
    val filteredBooks by viewModel.filteredBooks.collectAsState()
    val shelves by viewModel.shelves.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var showFilterSheet by remember { mutableStateOf(false) }

    val textFieldState = rememberTextFieldState(uiState.searchQuery)
    val searchBarState = rememberContainedSearchBarState()
    val scope = rememberCoroutineScope()
    val scrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior()
    val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors(
        searchBarColors = SearchBarDefaults.containedColors(state = searchBarState)
    )

    LaunchedEffect(textFieldState.text) {
        viewModel.onSearchQueryChange(textFieldState.text.toString())
    }

    // Context Menu State
    var selectedBookForMenu by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showCreateShelfDialog by remember { mutableStateOf(false) }
    var showAddToShelfDialog by remember { mutableStateOf(false) }
    var newShelfName by remember { mutableStateOf("") }

    val pagerState = rememberPagerState(pageCount = { 2 })

    val inputField = @Composable {
        val isExpanded = searchBarState.targetValue == SearchBarValue.Expanded
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            colors = appBarWithSearchColors.searchBarColors.inputFieldColors,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { Text("Search books, authors, tags...") },
            leadingIcon = if (isExpanded) {
                {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
                    }
                }
            } else null,
            trailingIcon = if (isExpanded) {
                {
                    IconButton(onClick = { /* Microphone action */ }) {
                        Icon(MaterialSymbols.Outlined.Mic, contentDescription = "Voice Search")
                    }
                }
            } else null,
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AppBarWithSearch(
                scrollBehavior = scrollBehavior,
                state = searchBarState,
                colors = appBarWithSearchColors,
                inputField = inputField,
                navigationIcon = {
                    IconButton(onClick = onOpenDrawerClick) {
                        Icon(MaterialSymbols.Outlined.Menu, contentDescription = "Menu")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(MaterialSymbols.Outlined.Tune, contentDescription = "Filter")
                    }
                },
            )
            ExpandedFullScreenContainedSearchBar(
                state = searchBarState,
                inputField = inputField,
                colors = appBarWithSearchColors.searchBarColors,
            ) {
                Column {
                    HorizontalDivider(modifier = Modifier.fillMaxWidth())
                    SearchResultsContent(
                        results = searchResults,
                        onBookClick = { book: Book ->
                            onNavigateToReader(book.id)
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                        onShelfClick = { shelf: ShelfEntity ->
                            onNavigateToShelf(shelf.id)
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                        onAuthorClick = { author: String ->
                            onNavigateToAuthor(author)
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                        onTagClick = { tag: String ->
                            onNavigateToTag(tag)
                            scope.launch { searchBarState.animateToCollapsed() }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(innerPadding).fillMaxSize()
        ) { page ->
            when (page) {
                0 -> {
                    // Books Page
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (uiState.layoutMode == LayoutMode.Grid) {
                            BookGrid(
                                books = filteredBooks,
                                onBookClick = onNavigateToReader,
                                onBookLongClick = { selectedBookForMenu = it }
                            )
                        } else {
                            BookList(
                                books = filteredBooks,
                                onBookClick = onNavigateToReader,
                                onBookLongClick = { selectedBookForMenu = it }
                            )
                        }

                        if (uiState.isImporting) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Surface(
                                    modifier = Modifier.size(150.dp),
                                    shape = MaterialTheme.shapes.large,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    tonalElevation = 8.dp
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.medium, Alignment.CenterVertically),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Text("Importing...")
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Shelves Page
                    ShelvesPage(
                        shelves = shelves,
                        onShelfClick = onNavigateToShelf,
                        onBookClick = onNavigateToReader
                    )
                }
            }
        }

        if (showFilterSheet) {
            FilterSortBottomSheet(
                uiState = uiState,
                onLayoutModeChange = viewModel::onLayoutModeChange,
                onSortTypeChange = viewModel::onSortTypeChange,
                onStatusToggle = viewModel::toggleStatusFilter,
                onDismiss = { showFilterSheet = false }
            )
        }

        // Context Menu
        if (selectedBookForMenu != null) {
            ModalBottomSheet(onDismissRequest = { selectedBookForMenu = null }) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                    Text("Options", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                    HorizontalDivider()
                    ListItem(
                        headlineContent = { Text("Info") },
                        modifier = Modifier.clickable {
                            val intent = android.content.Intent(context, com.example.readerapp.ui.features.info.BookInfoActivity::class.java).apply {
                                putExtra(com.example.readerapp.ui.features.info.BookInfoActivity.EXTRA_BOOK_ID, selectedBookForMenu!!)
                            }
                            context.startActivity(intent)
                            selectedBookForMenu = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Archive") },
                        modifier = Modifier.clickable {
                            viewModel.toggleArchive(selectedBookForMenu!!)
                            selectedBookForMenu = null
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Add to Shelf") },
                        modifier = Modifier.clickable {
                            showAddToShelfDialog = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Create New Shelf") },
                        modifier = Modifier.clickable {
                            showCreateShelfDialog = true
                        }
                    )
                    ListItem(
                        headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        modifier = Modifier.clickable {
                            showDeleteConfirmation = true
                        }
                    )
                }
            }
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Book") },
                text = { Text("Are you sure you want to delete this book? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteBook(selectedBookForMenu!!)
                            showDeleteConfirmation = false
                            selectedBookForMenu = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Add to Shelf Dialog
        if (showAddToShelfDialog) {
            AlertDialog(
                onDismissRequest = { showAddToShelfDialog = false },
                title = { Text("Add to Shelf") },
                text = {
                    if (shelves.isEmpty()) {
                        Text("No shelves available.")
                    } else {
                        LazyColumn {
                            items(shelves) { shelfWithCovers ->
                                ListItem(
                                    headlineContent = { Text(shelfWithCovers.shelf.name) },
                                    modifier = Modifier.clickable {
                                        viewModel.addBookToShelf(shelfWithCovers.shelf.id, selectedBookForMenu!!)
                                        showAddToShelfDialog = false
                                        selectedBookForMenu = null
                                    }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAddToShelfDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Create Shelf Dialog
        if (showCreateShelfDialog) {
            AlertDialog(
                onDismissRequest = { showCreateShelfDialog = false },
                title = { Text("Create New Shelf") },
                text = {
                    OutlinedTextField(
                        value = newShelfName,
                        onValueChange = { newShelfName = it },
                        label = { Text("Shelf Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (newShelfName.isNotBlank()) {
                            viewModel.createShelfAndAddBook(newShelfName, selectedBookForMenu)
                            newShelfName = ""
                            showCreateShelfDialog = false
                            selectedBookForMenu = null
                        }
                    }) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreateShelfDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SearchResultsContent(
    results: SearchResults,
    onBookClick: (Book) -> Unit,
    onShelfClick: (ShelfEntity) -> Unit,
    onAuthorClick: (String) -> Unit,
    onTagClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (results.books.isNotEmpty()) {
            item {
                Text(
                    "Books",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(results.books) { book ->
                        SearchBookItem(book = book, onClick = { onBookClick(book) })
                    }
                }
            }
        }

        if (results.shelves.isNotEmpty()) {
            item {
                Text(
                    "Shelves",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(results.shelves) { shelf ->
                ListItem(
                    headlineContent = { Text(shelf.name) },
                    leadingContent = { Icon(MaterialSymbols.Outlined.Folder, contentDescription = null) },
                    modifier = Modifier.clickable { onShelfClick(shelf) }
                )
            }
        }

        if (results.authors.isNotEmpty()) {
            item {
                Text(
                    "Authors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            val authorChunks = results.authors.chunked(2)
            items(authorChunks) { chunk ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    chunk.forEach { author ->
                        ListItem(
                            headlineContent = { Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(MaterialSymbols.Outlined.Person, contentDescription = null) },
                            modifier = Modifier.weight(1f).clickable { onAuthorClick(author) }
                        )
                    }
                    if (chunk.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (results.tags.isNotEmpty()) {
            item {
                Text(
                    "Tags",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            val tagChunks = results.tags.chunked(2)
            items(tagChunks) { chunk ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    chunk.forEach { tag ->
                        ListItem(
                            headlineContent = { Text(tag, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(MaterialSymbols.Outlined.Label, contentDescription = null) },
                            modifier = Modifier.weight(1f).clickable { onTagClick(tag) }
                        )
                    }
                    if (chunk.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBookItem(book: Book, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = book.coverPath,
            contentDescription = null,
            modifier = Modifier
                .aspectRatio(2 / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = book.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = book.author ?: "Unknown",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { book.progress.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(4.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}
