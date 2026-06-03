package com.example.readerapp.ui.features.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.material3.SearchBarValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.*
import com.example.readerapp.data.local.ShelfEntity
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.components.SegmentedButtonGroup
import com.example.readerapp.ui.features.library.SearchCategory
import com.example.readerapp.ui.features.library.SearchResults
import com.example.readerapp.ui.theme.AppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun LibrarySearchTopBar(
    searchQuery: String,
    searchCategory: SearchCategory,
    searchResults: SearchResults,
    onSearchQueryChange: (String) -> Unit,
    onSearchCategoryChange: (SearchCategory) -> Unit,
    onOpenDrawerClick: () -> Unit,
    onFilterClick: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToShelf: (String) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: SearchBarScrollBehavior = SearchBarDefaults.enterAlwaysSearchBarScrollBehavior(),
    searchBarState: SearchBarState = rememberSearchBarState()
) {
    val textFieldState = rememberTextFieldState(searchQuery)
    val scope = rememberCoroutineScope()
    val appBarWithSearchColors = SearchBarDefaults.appBarWithSearchColors()

    LaunchedEffect(textFieldState.text) {
        onSearchQueryChange(textFieldState.text.toString())
    }

    LaunchedEffect(searchBarState.currentValue) {
        if (searchBarState.currentValue == SearchBarValue.Collapsed && textFieldState.text.isNotEmpty()) {
            textFieldState.edit { replace(0, length, "") }
        }
    }

    val inputField = @Composable {
        val showIcons = searchBarState.currentValue == SearchBarValue.Expanded && searchBarState.targetValue == SearchBarValue.Expanded
        SearchBarDefaults.InputField(
            textFieldState = textFieldState,
            searchBarState = searchBarState,
            colors = appBarWithSearchColors.searchBarColors.inputFieldColors,
            onSearch = { scope.launch { searchBarState.animateToCollapsed() } },
            placeholder = { 
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = if (searchBarState.targetValue == SearchBarValue.Expanded) Alignment.CenterStart else Alignment.Center
                ) {
                    Text(modifier = Modifier.clearAndSetSemantics {}, text = "Search library") 
                }
            },
            leadingIcon = if (showIcons) {
                {
                    IconButton(onClick = { scope.launch { searchBarState.animateToCollapsed() } }) {
                        Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
                    }
                }
            } else null,
            trailingIcon = if (showIcons) {
                {
                    IconButton(onClick = { /* Microphone action */ }) {
                        Icon(MaterialSymbols.Outlined.Mic, contentDescription = "Voice Search")
                    }
                }
            } else null,
        )
    }

    Box(modifier = modifier) {
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
                IconButton(onClick = onFilterClick) {
                    Icon(MaterialSymbols.Outlined.Tune, contentDescription = "Filter")
                }
            },
        )
        ExpandedFullScreenSearchBar(
            state = searchBarState,
            inputField = inputField,
            colors = appBarWithSearchColors.searchBarColors.copy(dividerColor = Color.Transparent)
        ) {
            val isFullyExpanded = searchBarState.currentValue == SearchBarValue.Expanded && searchBarState.targetValue == SearchBarValue.Expanded
            AnimatedVisibility(
                visible = isFullyExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    val options = listOf("All", "Books", "Authors", "Shelves", "Tags")
                    SegmentedButtonGroup(
                        options = options,
                        icons = emptyList(),
                        selected = searchCategory.name,
                        onSelected = { selected -> 
                            onSearchCategoryChange(SearchCategory.valueOf(selected))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    SearchResultsContent(
                        results = searchResults,
                        onBookClick = { book ->
                            onNavigateToReader(book.id)
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                        onShelfClick = { shelf ->
                            onNavigateToShelf(shelf.id)
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                        onAuthorClick = { author ->
                            onNavigateToAuthor(author)
                            scope.launch { searchBarState.animateToCollapsed() }
                        },
                        onTagClick = { tag ->
                            onNavigateToTag(tag)
                            scope.launch { searchBarState.animateToCollapsed() }
                        }
                    )
                }
            }
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
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    results.shelves.chunked(2).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { shelf ->
                                SearchFilterItem(
                                    text = shelf.name,
                                    icon = MaterialSymbols.Outlined.Folder,
                                    onClick = { onShelfClick(shelf) },
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
        }

        if (results.authors.isNotEmpty()) {
            item {
                Text(
                    "Authors",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    results.authors.chunked(2).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { author ->
                                SearchFilterItem(
                                    text = author,
                                    icon = MaterialSymbols.Outlined.Person,
                                    onClick = { onAuthorClick(author) },
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
        }

        if (results.tags.isNotEmpty()) {
            item {
                Text(
                    "Tags",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    results.tags.chunked(2).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { tag ->
                                SearchFilterItem(
                                    text = tag,
                                    icon = MaterialSymbols.Outlined.Label,
                                    onClick = { onTagClick(tag) },
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
        }
    }
}

@Composable
private fun SearchFilterItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(AppTheme.spacing.small),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(AppTheme.spacing.itemSpacing),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
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

@Composable
private fun SearchBookItem(book: Book, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.small)
    ) {
        Card(
            shape = MaterialTheme.shapes.small,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.4f)
        ) {
            CoverImage(book = book)
        }
        Text(
            text = book.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = book.author ?: "Unknown Author",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(book.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
