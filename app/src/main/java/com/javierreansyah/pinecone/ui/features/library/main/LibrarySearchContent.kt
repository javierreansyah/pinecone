package com.javierreansyah.pinecone.ui.features.library.main

import androidx.compose.animation.core.FiniteAnimationSpec
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Chevron_forward
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Label
import com.composables.icons.materialsymbols.outlined.Person
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.ShelfEntity
import com.javierreansyah.pinecone.data.model.Book
import com.javierreansyah.pinecone.ui.components.SegmentedButtonGroup
import com.javierreansyah.pinecone.ui.features.library.SearchCategory
import com.javierreansyah.pinecone.ui.features.library.components.book.BookItem

@Composable
internal fun ExpandedSearchContent(
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
    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val fastSpatialSpecIntOffset = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()

    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        val isSearchEmpty = results.query.isEmpty()
        val isAll = searchCategory == SearchCategory.All
        val isPreview = isAll && isSearchEmpty

        val maxBooks = if (isPreview) 4 else Int.MAX_VALUE
        val maxShelves = if (isPreview) 4 else Int.MAX_VALUE
        val maxAuthors = if (isPreview) 4 else Int.MAX_VALUE
        val maxTags = if (isPreview) 4 else Int.MAX_VALUE

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
                fastEffectsSpec = fastEffectsSpec,
                fastSpatialSpecIntOffset = fastSpatialSpecIntOffset,
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
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (booksToShow.isNotEmpty() && isAll) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        BooksSection(
                            books = booksToShow,
                            onBookClick = { onBookClick(it) },
                            fastEffectsSpec = fastEffectsSpec,
                            fastSpatialSpecIntOffset = fastSpatialSpecIntOffset
                        )
                    }
                }

                if (shelvesToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Shelves)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = stringResource(R.string.library_tab_shelves),
                            onHeaderClick = null,
                            paddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    itemsIndexed(shelvesToShow, key = { _, it -> "shelf_${it.id}" }) { index, shelf ->
                        SearchFilterItem(
                            text = shelf.name,
                            icon = MaterialSymbols.Outlined.Folder,
                            onClick = { onShelfClick(shelf) },
                            modifier = Modifier
                                .padding(
                                    start = if (index % 2 == 0) 16.dp else 0.dp,
                                    end = if (index % 2 == 1) 16.dp else 0.dp
                                )
                                .animateItem(
                                    fadeInSpec = fastEffectsSpec,
                                    fadeOutSpec = fastEffectsSpec,
                                    placementSpec = fastSpatialSpecIntOffset
                                )
                        )
                    }
                }

                if (authorsToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Authors)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = stringResource(R.string.library_authors_title),
                            onHeaderClick = { onAuthorsHeaderClick() },
                            paddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    itemsIndexed(authorsToShow, key = { _, it -> "author_$it" }) { index, author ->
                        SearchFilterItem(
                            text = author,
                            icon = MaterialSymbols.Outlined.Person,
                            onClick = { onAuthorClick(author) },
                            modifier = Modifier
                                .padding(
                                    start = if (index % 2 == 0) 16.dp else 0.dp,
                                    end = if (index % 2 == 1) 16.dp else 0.dp
                                )
                                .animateItem(
                                    fadeInSpec = fastEffectsSpec,
                                    fadeOutSpec = fastEffectsSpec,
                                    placementSpec = fastSpatialSpecIntOffset
                                )
                        )
                    }
                }

                if (tagsToShow.isNotEmpty() && (isAll || searchCategory == SearchCategory.Tags)) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SectionHeader(
                            title = stringResource(R.string.library_tags_title),
                            onHeaderClick = { onTagsHeaderClick() },
                            paddingValues = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                        )
                    }
                    itemsIndexed(tagsToShow, key = { _, it -> "tag_$it" }) { index, tag ->
                        SearchFilterItem(
                            text = tag,
                            icon = MaterialSymbols.Outlined.Label,
                            onClick = { onTagClick(tag) },
                            modifier = Modifier
                                .padding(
                                    start = if (index % 2 == 0) 16.dp else 0.dp,
                                    end = if (index % 2 == 1) 16.dp else 0.dp
                                )
                                .animateItem(
                                    fadeInSpec = fastEffectsSpec,
                                    fadeOutSpec = fastEffectsSpec,
                                    placementSpec = fastSpatialSpecIntOffset
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BooksSection(
    books: List<Book>,
    onBookClick: (Book) -> Unit,
    fastEffectsSpec: FiniteAnimationSpec<Float>? = null,
    fastSpatialSpecIntOffset: FiniteAnimationSpec<IntOffset>? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

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
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(books, key = { it.id }) { book ->
                val animateModifier = if (fastEffectsSpec != null && fastSpatialSpecIntOffset != null) {
                    Modifier.animateItem(
                        fadeInSpec = fastEffectsSpec,
                        fadeOutSpec = fastEffectsSpec,
                        placementSpec = fastSpatialSpecIntOffset
                    )
                } else Modifier
                BookItem(
                    book = book, onClick = { onBookClick(book) }, modifier = animateModifier.width(120.dp)
                )
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
    onBookLongClick: ((String) -> Unit)? = null,
    fastEffectsSpec: FiniteAnimationSpec<Float>? = null,
    fastSpatialSpecIntOffset: FiniteAnimationSpec<IntOffset>? = null,
    headerContent: @Composable (() -> Unit)? = null
) {
    val gridState = rememberLazyGridState()

    val itemWidth = 100.dp
    val horizontalPadding = 8.dp

    LazyVerticalGrid(
        state = gridState,
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
                val animateModifier = if (fastEffectsSpec != null && fastSpatialSpecIntOffset != null) {
                    Modifier.animateItem(
                        fadeInSpec = fastEffectsSpec,
                        fadeOutSpec = fastEffectsSpec,
                        placementSpec = fastSpatialSpecIntOffset
                    )
                } else Modifier

                BookItem(
                    book = book,
                    onClick = { onBookClick(book.id) },
                    onLongClick = onBookLongClick?.let { { it(book.id) } },
                    modifier = animateModifier
                )
            }
        }
    }
