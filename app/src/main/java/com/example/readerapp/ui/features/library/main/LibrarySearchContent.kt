package com.example.readerapp.ui.features.library.main

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Chevron_forward
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Label
import com.composables.icons.materialsymbols.outlined.Person
import com.example.readerapp.R
import com.example.readerapp.data.local.database.library.ShelfEntity
import com.example.readerapp.data.model.Book
import com.example.readerapp.ui.components.SegmentedButtonGroup
import com.example.readerapp.ui.features.library.SearchCategory
import com.example.readerapp.ui.features.library.components.book.BookItem

@Composable
internal fun ExpandedSearchContent(
    isSearchEmpty: Boolean,
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
            .background(MaterialTheme.colorScheme.surface)
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
            isSearchEmpty = isSearchEmpty,
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
    isSearchEmpty: Boolean,
    searchCategory: SearchCategory,
    results: SearchResults,
    onBookClick: (Book) -> Unit,
    onShelfClick: (ShelfEntity) -> Unit,
    onAuthorClick: (String) -> Unit,
    onTagClick: (String) -> Unit,
    onAuthorsHeaderClick: () -> Unit,
    onTagsHeaderClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        val isAll = searchCategory == SearchCategory.All
        val isPreview = isAll && isSearchEmpty

        val maxBooks = if (isPreview) 6 else Int.MAX_VALUE
        val maxShelves = if (isPreview) 4 else Int.MAX_VALUE
        val maxAuthors = if (isPreview) 8 else Int.MAX_VALUE
        val maxTags = if (isPreview) 8 else Int.MAX_VALUE

        val booksToShow = if (isPreview) {
            results.books.sortedByDescending { it.lastOpened ?: 0L }.take(maxBooks)
        } else if (isAll) {
            results.books.take(maxBooks)
        } else {
            results.books
        }

        val shelvesToShow = if (isPreview) {
            results.shelves.sortedBy { it.name }.take(maxShelves)
        } else if (isAll) {
            results.shelves.take(maxShelves)
        } else {
            results.shelves
        }

        val authorsToShow = if (isPreview) {
            results.authors.sortedBy { it }.take(maxAuthors)
        } else if (isAll) {
            results.authors.take(maxAuthors)
        } else {
            results.authors
        }

        val tagsToShow = if (isPreview) {
            results.tags.sortedBy { it }.take(maxTags)
        } else if (isAll) {
            results.tags.take(maxTags)
        } else {
            results.tags
        }

        val showOnlyBooksGrid = !isAll && searchCategory == SearchCategory.Books

        if (showOnlyBooksGrid) {
            BookCollection(
                books = booksToShow,
                onBookClick = { id ->
                    val book = booksToShow.find { it.id == id }
                    if (book != null) onBookClick(book)
                },
                onBookLongClick = {},
                scrollKey = results,
                headerContent = {
                    SectionHeader(
                        title = stringResource(R.string.library_tab_books),
                        onHeaderClick = null,
                        isBooksSection = true,
                        paddingValues = PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 0.dp
                        )
                    )
                }
            )
        } else {
            val scrollState = rememberScrollState()

            androidx.compose.runtime.LaunchedEffect(results) {
                scrollState.scrollTo(0)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (booksToShow.isNotEmpty() && isAll) {
                    BooksSection(
                        books = booksToShow,
                        onBookClick = { onBookClick(it) }
                    )
                }

                if (shelvesToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Shelves)) {
                    GridFilterSection(
                        title = stringResource(R.string.library_tab_shelves),
                        items = shelvesToShow,
                        icon = MaterialSymbols.Outlined.Folder,
                        nameSelector = { it.name },
                        onClick = { onShelfClick(it) },
                        onHeaderClick = null
                    )
                }

                if (authorsToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Authors)) {
                    GridFilterSection(
                        title = stringResource(R.string.library_authors_title),
                        items = authorsToShow,
                        icon = MaterialSymbols.Outlined.Person,
                        nameSelector = { it },
                        onClick = { onAuthorClick(it) },
                        onHeaderClick = { onAuthorsHeaderClick() })
                }

                if (tagsToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Tags)) {
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
}

@Composable
private fun BooksSection(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SectionHeader(
            title = stringResource(R.string.library_tab_books),
            onHeaderClick = null,
            isBooksSection = true
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
}

@Composable
private fun <T> GridFilterSection(
    modifier: Modifier = Modifier,
    title: String,
    items: List<T>,
    icon: ImageVector,
    nameSelector: (T) -> String,
    onClick: (T) -> Unit,
    onHeaderClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        SectionHeader(
            title = title,
            onHeaderClick = onHeaderClick
        )

        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
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
}

@Composable
private fun SectionHeader(
    title: String,
    onHeaderClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isBooksSection: Boolean = false,
    paddingValues: PaddingValues = PaddingValues(
        start = 16.dp,
        end = 16.dp,
        top = 8.dp,
        bottom = if (isBooksSection) 0.dp else 8.dp
    )
) {
    val clickableModifier = Modifier
        .fillMaxWidth()
        .then(if (onHeaderClick != null) Modifier.clickable { onHeaderClick() } else Modifier)
        .padding(paddingValues)

    Row(
        modifier = clickableModifier.then(modifier),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
        color = MaterialTheme.colorScheme.surfaceContainer,
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

@Composable
private fun BookCollection(
    books: List<Book>,
    onBookClick: (String) -> Unit,
    onBookLongClick: (String) -> Unit,
    scrollKey: Any? = null,
    headerContent: @Composable (() -> Unit)? = null
) {
    key(scrollKey) {
        val itemWidth = 100.dp
        val horizontalPadding = 8.dp

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = itemWidth),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding)
        ) {
            if (headerContent != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        headerContent()
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
            items(
                items = books,
                key = { "${it.id}-Grid" },
                contentType = { "Grid" }
            ) { book ->
                BookItem(
                    book = book,
                    onClick = { onBookClick(book.id) },
                    onLongClick = { onBookLongClick(book.id) },
                )
            }
        }
    }
}
