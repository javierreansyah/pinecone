package com.example.readerapp.ui.features.library.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Chevron_forward
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Label
import com.composables.icons.materialsymbols.outlined.Menu
import com.composables.icons.materialsymbols.outlined.Mic
import com.composables.icons.materialsymbols.outlined.Person
import com.composables.icons.materialsymbols.outlined.Tune
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.ShelfEntity
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.components.SegmentedButtonGroup
import com.example.readerapp.ui.components.rememberVoiceSearchLauncher
import com.example.readerapp.ui.features.library.SearchCategory
import com.example.readerapp.ui.features.library.components.book.BookItem
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun LibrarySearchTopBar(
    modifier: Modifier = Modifier,
    searchQuery: String,
    searchCategory: SearchCategory,
    searchResults: SearchResults,
    onSearchQueryChange: (String) -> Unit,
    onSearchCategoryChange: (SearchCategory) -> Unit,
    onOpenDrawerClick: () -> Unit,
    onFilterClick: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
    onAuthorsHeaderClick: () -> Unit = {},
    onTagsHeaderClick: () -> Unit = {},
    scrollBehavior: SearchBarScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(),
    searchBarState: SearchBarState = rememberSearchBarState()
) {
    val textFieldState = rememberTextFieldState(searchQuery)
    val focusRequester = remember { FocusRequester() }
    var isRestoring by remember {
        mutableStateOf(searchBarState.currentValue == SearchBarValue.Expanded)
    }

    // Sync state to parent
    LaunchedEffect(textFieldState.text) {
        onSearchQueryChange(textFieldState.text.toString())
    }

    // Handle focus and keyboard based on search bar state
    HandleSearchBarStateChanges(
        searchBarState = searchBarState,
        textFieldState = textFieldState,
        focusRequester = focusRequester,
        onRestoringChange = { isRestoring = it }
    )

    // Colors
    val isExpandedTarget = searchBarState.targetValue == SearchBarValue.Expanded
    val searchBarContainerColor by animateColorAsState(
        targetValue = if (isExpandedTarget) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "searchBarColor"
    )

    val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors(
        searchBarColors = SearchBarDefaults.colors(containerColor = searchBarContainerColor),
        scrolledSearchBarContainerColor = searchBarContainerColor,
        appBarContainerColor = MaterialTheme.colorScheme.surface,
        scrolledAppBarContainerColor = MaterialTheme.colorScheme.surface
    )

    val scope = rememberCoroutineScope()

    Box(modifier = modifier) {
        AppBarWithSearch(
            scrollBehavior = scrollBehavior,
            state = searchBarState,
            colors = appBarWithSearchColors,
            inputField = {
                SearchInputField(
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    focusRequester = focusRequester,
                    isRestoring = isRestoring,
                    onRestoringChange = { isRestoring = it },
                    colors = appBarWithSearchColors.searchBarColors.inputFieldColors,
                    onSearchQueryChange = onSearchQueryChange
                )
            },
            navigationIcon = {
                IconButton(onClick = onOpenDrawerClick) {
                    Icon(
                        MaterialSymbols.Outlined.Menu,
                        contentDescription = stringResource(R.string.action_menu)
                    )
                }
            },
            actions = {
                IconButton(onClick = onFilterClick) {
                    Icon(
                        MaterialSymbols.Outlined.Tune,
                        contentDescription = stringResource(R.string.action_filter)
                    )
                }
            },
        )

        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = {
                SearchInputField(
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    focusRequester = focusRequester,
                    isRestoring = isRestoring,
                    onRestoringChange = { isRestoring = it },
                    colors = appBarWithSearchColors.searchBarColors.inputFieldColors,
                    onSearchQueryChange = onSearchQueryChange
                )
            },
            colors = appBarWithSearchColors.searchBarColors.copy(dividerColor = Color.Transparent)
        ) {
            // Always compose the content so Compose can pre-measure and pre-layout
            // the entire tree while the bar is still collapsed. We hide it with a
            // graphicsLayer alpha fade instead of conditional composition, which
            // eliminates the cold-compose jank on first open.
            val isExpanded = searchBarState.targetValue == SearchBarValue.Expanded
            val contentAlpha by animateFloatAsState(
                targetValue = if (isExpanded) 1f else 0f,
                animationSpec = tween(durationMillis = 180, delayMillis = 60),
                label = "searchContentAlpha"
            )

            // Wrapper that collapses the search bar before navigating so the
            // Library destination appears in its normal (non-expanded) state
            // during predictive back and enter transitions.
            val navigateAfterCollapse = { action: () -> Unit ->
                scope.launch {
                    searchBarState.animateToCollapsed()
                    action()
                }
                Unit
            }

            Box(modifier = Modifier.graphicsLayer { alpha = contentAlpha }) {
                ExpandedSearchContent(
                    searchCategory = searchCategory,
                    searchResults = searchResults,
                    onSearchCategoryChange = onSearchCategoryChange,
                    onNavigateToReader = { bookId ->
                        navigateAfterCollapse {
                            onNavigateToReader(
                                bookId
                            )
                        }
                    },
                    onNavigateToShelf = { shelfId, name, count ->
                        navigateAfterCollapse {
                            onNavigateToShelf(
                                shelfId,
                                name,
                                count
                            )
                        }
                    },
                    onNavigateToAuthor = { author ->
                        navigateAfterCollapse {
                            onNavigateToAuthor(
                                author
                            )
                        }
                    },
                    onNavigateToTag = { tag -> navigateAfterCollapse { onNavigateToTag(tag) } },
                    onAuthorsHeaderClick = { navigateAfterCollapse { onAuthorsHeaderClick() } },
                    onTagsHeaderClick = { navigateAfterCollapse { onTagsHeaderClick() } }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HandleSearchBarStateChanges(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    focusRequester: FocusRequester,
    onRestoringChange: (Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var previousValue by remember { mutableStateOf<SearchBarValue?>(null) }

    // Listen to lifecycle resume events to clear focus and keyboard when returning to the screen
    val lifecycle = lifecycleOwner.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (searchBarState.currentValue == SearchBarValue.Expanded) {
                    onRestoringChange(true)
                    scope.launch {
                        // Small delay/yield to let Compose focus restoration complete first
                        kotlinx.coroutines.yield()
                        focusManager.clearFocus(force = true)
                        keyboardController?.hide()
                        // Reset the restoring flag to false once the restoration pass is complete
                        onRestoringChange(false)
                    }
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(searchBarState.currentValue) {
        if (searchBarState.currentValue == SearchBarValue.Collapsed) {
            onRestoringChange(false) // Reset flag when collapsed
            focusManager.clearFocus()
            keyboardController?.hide()
            if (textFieldState.text.isNotEmpty()) {
                textFieldState.edit { replace(0, length, "") }
            }
        } else if (searchBarState.currentValue == SearchBarValue.Expanded) {
            // Only request focus/keyboard if we transitioned from Collapsed to Expanded (user clicked search)
            if (previousValue == SearchBarValue.Collapsed) {
                onRestoringChange(false) // Reset flag when explicitly expanding
                lifecycleOwner.lifecycle.withResumed {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            }
        }
        previousValue = searchBarState.currentValue
    }

    DisposableEffect(Unit) {
        onDispose {
            if (searchBarState.currentValue == SearchBarValue.Expanded) {
                focusManager.clearFocus()
                keyboardController?.hide()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchInputField(
    searchBarState: SearchBarState,
    textFieldState: TextFieldState,
    focusRequester: FocusRequester,
    isRestoring: Boolean,
    onRestoringChange: (Boolean) -> Unit,
    colors: TextFieldColors,
    onSearchQueryChange: (String) -> Unit
) {
    val isExpanded = searchBarState.targetValue == SearchBarValue.Expanded
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val alignmentBias by animateFloatAsState(
        targetValue = if (isExpanded) -1f else 0f, label = "placeholderAlignment"
    )

    val launchVoiceSearch = rememberVoiceSearchLauncher { spokenText ->
        textFieldState.edit { replace(0, length, spokenText) }
        onSearchQueryChange(spokenText)
    }

    SearchBarDefaults.InputField(
        modifier = Modifier
            .focusRequester(focusRequester)
            .focusProperties {
                canFocus = searchBarState.currentValue == SearchBarValue.Expanded
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused && isRestoring) {
                    onRestoringChange(false)
                    focusManager.clearFocus(force = true)
                    keyboardController?.hide()
                }
            },
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        colors = colors,
        onSearch = { focusManager.clearFocus() },
        placeholder = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = BiasAlignment(horizontalBias = alignmentBias, verticalBias = 0f)
            ) {
                Text(
                    modifier = Modifier.clearAndSetSemantics {},
                    text = stringResource(R.string.library_search_placeholder),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        },
        leadingIcon = {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 50))
            ) {
                IconButton(onClick = {
                    focusManager.clearFocus()
                    scope.launch { searchBarState.animateToCollapsed() }
                }) {
                    Icon(
                        MaterialSymbols.Outlined.Arrow_back,
                        contentDescription = stringResource(R.string.action_back)
                    )
                }
            }
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(animationSpec = tween(durationMillis = 150, delayMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 50))
            ) {
                IconButton(onClick = { launchVoiceSearch() }) {
                    Icon(
                        MaterialSymbols.Outlined.Mic,
                        contentDescription = stringResource(R.string.action_voice_search)
                    )
                }
            }
        },
    )
}

@Composable
private fun ExpandedSearchContent(
    searchCategory: SearchCategory,
    searchResults: SearchResults,
    onSearchCategoryChange: (SearchCategory) -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
    onAuthorsHeaderClick: () -> Unit,
    onTagsHeaderClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        val categoryLabels = mapOf(
            SearchCategory.All to stringResource(R.string.action_all),
            SearchCategory.Books to stringResource(R.string.library_tab_books),
            SearchCategory.Shelves to stringResource(R.string.library_tab_shelves),
            SearchCategory.Authors to stringResource(R.string.library_authors_title),
            SearchCategory.Tags to stringResource(R.string.library_tags_title)
        )

        SegmentedButtonGroup(
            options = SearchCategory.entries.map { categoryLabels[it] ?: it.name },
            icons = emptyList(),
            selected = categoryLabels[searchCategory] ?: searchCategory.name,
            onSelected = { selectedLabel ->
                val category = categoryLabels.entries.find { it.value == selectedLabel }?.key
                    ?: SearchCategory.All
                onSearchCategoryChange(category)
            },
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
        )

        SearchResultsContent(
            searchCategory = searchCategory,
            results = searchResults,
            onBookClick = { book -> onNavigateToReader(book.id) },
            onShelfClick = { shelf -> onNavigateToShelf(shelf.id, shelf.name, 0) },
            onAuthorClick = { author -> onNavigateToAuthor(author) },
            onTagClick = { tag -> onNavigateToTag(tag) },
            onAuthorsHeaderClick = onAuthorsHeaderClick,
            onTagsHeaderClick = onTagsHeaderClick
        )
    }
}

@Composable
private fun SearchResultsContent(
    searchCategory: SearchCategory,
    results: SearchResults,
    onBookClick: (Book) -> Unit,
    onShelfClick: (ShelfEntity) -> Unit,
    onAuthorClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onAuthorsHeaderClick: () -> Unit,
    onTagsHeaderClick: () -> Unit
) {
    val isAll = searchCategory == SearchCategory.All
    val maxItemForAll = 8

    val booksToShow = if (isAll) results.books.take(maxItemForAll) else results.books
    val shelvesToShow = if (isAll) results.shelves.take(maxItemForAll) else results.shelves
    val authorsToShow = if (isAll) results.authors.take(maxItemForAll) else results.authors
    val tagsToShow = if (isAll) results.tags.take(maxItemForAll) else results.tags

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (booksToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Books)) {
            item {
                BooksSection(
                    books = booksToShow,
                    onBookClick = { onBookClick(it) }
                )
            }
        }

        if (shelvesToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Shelves)) {
            item {
                GridFilterSection(
                    title = stringResource(R.string.library_tab_shelves),
                    items = shelvesToShow,
                    icon = MaterialSymbols.Outlined.Folder,
                    nameSelector = { it.name },
                    onClick = { onShelfClick(it) },
                    onHeaderClick = null
                )
            }
        }

        if (authorsToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Authors)) {
            item {
                GridFilterSection(
                    title = stringResource(R.string.library_authors_title),
                    items = authorsToShow,
                    icon = MaterialSymbols.Outlined.Person,
                    nameSelector = { it },
                    onClick = { onAuthorClick(it) },
                    onHeaderClick = { onAuthorsHeaderClick() })
            }
        }

        if (tagsToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Tags)) {
            item {
                GridFilterSection(
                    title = stringResource(R.string.library_tags_title),
                    items = tagsToShow,
                    icon = MaterialSymbols.Outlined.Label,
                    nameSelector = { it },
                    onClick = { onTagClick(it) },
                    onHeaderClick = { onTagsHeaderClick() })
            }
        }
    }
}

@Composable
private fun BooksSection(
    books: List<Book>, onBookClick: (Book) -> Unit
) {
    SectionHeader(
        title = stringResource(R.string.library_tab_books),
        onHeaderClick = null,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookItem(
                book = book, onClick = { onBookClick(book) }, modifier = Modifier.width(120.dp)
            )
        }
    }
}

@Composable
private fun <T> GridFilterSection(
    title: String,
    items: List<T>,
    icon: ImageVector,
    nameSelector: (T) -> String,
    onClick: (T) -> Unit,
    onHeaderClick: (() -> Unit)? = null
) {
    SectionHeader(
        title = title,
        onHeaderClick = onHeaderClick,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
    )

    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.chunked(2).forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chunk.forEach { item ->
                    SearchFilterItem(
                        text = nameSelector(item),
                        icon = icon,
                        onClick = { onClick(item) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (chunk.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String, onHeaderClick: (() -> Unit)?, modifier: Modifier = Modifier
) {
    val clickableModifier = if (onHeaderClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable { onHeaderClick() }
            .then(modifier)
    } else {
        Modifier
            .fillMaxWidth()
            .then(modifier)
    }

    Row(
        modifier = clickableModifier,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title, style = MaterialTheme.typography.titleMedium
        )
        if (onHeaderClick != null) {
            Icon(
                imageVector = MaterialSymbols.Outlined.Chevron_forward,
                contentDescription = stringResource(R.string.library_view_all, title),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchFilterItem(
    text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
