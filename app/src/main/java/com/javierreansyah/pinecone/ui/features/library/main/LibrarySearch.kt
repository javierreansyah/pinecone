package com.javierreansyah.pinecone.ui.features.library.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Book
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Menu
import com.composables.icons.materialsymbols.outlined.Search
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.AppBarButtonSize
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconButton
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconToggleButton
import com.javierreansyah.pinecone.ui.components.ContainedSearchOverlay
import com.javierreansyah.pinecone.ui.components.TopAppBarActionGroup
import com.javierreansyah.pinecone.ui.components.topBarFadeBackground
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.SearchCategory

@Composable
internal fun LibraryHomeTopAppBar(
    title: String,
    onOpenDrawerClick: () -> Unit,
    onSearchClick: () -> Unit,
    onFilterClick: () -> Unit,
    showViewSwitcher: Boolean,
    isShelvesTab: Boolean,
    wording: PublicationWording,
    onToggleView: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.background
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    TopAppBar(
        title = {
            Crossfade(
                targetState = title,
                animationSpec = effectsSpec,
                label = "homeTitleText",
            ) { label ->
                Text(
                    text = label,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            FilledTonalIconButton(
                modifier = Modifier.size(AppBarButtonSize),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = backgroundColor,
                    contentColor = contentColorFor(backgroundColor),
                ),
                onClick = onOpenDrawerClick,
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Menu,
                    contentDescription = stringResource(R.string.action_menu),
                )
            }
        },
        actions = {
            TopAppBarActionGroup {
                ConnectedGroupIconButton(
                    onClick = onSearchClick,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Search,
                        contentDescription = stringResource(R.string.action_search),
                    )
                }
                ConnectedGroupIconButton(
                    onClick = onFilterClick,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Tune,
                        contentDescription = stringResource(R.string.action_filter),
                    )
                }
                if (showViewSwitcher) {
                    ConnectedGroupIconToggleButton(
                        checked = isShelvesTab,
                        onCheckedChange = { onToggleView() },
                    ) {
                        AnimatedContent(
                            targetState = isShelvesTab,
                            transitionSpec = {
                                fadeIn(animationSpec = effectsSpec) togetherWith
                                        fadeOut(animationSpec = effectsSpec)
                            },
                            label = "libraryViewSwitcherIcon",
                        ) { onShelves ->
                            Icon(
                                imageVector = if (onShelves) {
                                    MaterialSymbols.Outlined.Book
                                } else {
                                    MaterialSymbols.Outlined.Folder
                                },
                                contentDescription = stringResource(
                                    if (onShelves) wording.libraryTab else R.string.library_tab_shelves,
                                ),
                            )
                        }
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = modifier.topBarFadeBackground(),
    )
}

@Composable
internal fun LibrarySearchOverlay(
    searchQuery: String,
    searchCategory: SearchCategory,
    searchResults: SearchResults,
    onSearchQueryChange: (String) -> Unit,
    onSearchCategoryChange: (SearchCategory) -> Unit,
    onClose: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToShelf: (String, String, Int) -> Unit,
    onNavigateToAuthor: (String) -> Unit,
    onNavigateToTag: (String) -> Unit,
    onAuthorsHeaderClick: () -> Unit,
    onTagsHeaderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textFieldState = rememberTextFieldState(searchQuery)

    LaunchedEffect(textFieldState.text) {
        val currentText = textFieldState.text.toString()
        if (currentText != searchQuery) {
            onSearchQueryChange(currentText)
        }
    }

    ContainedSearchOverlay(
        textFieldState = textFieldState,
        placeholderText = stringResource(R.string.library_search_placeholder),
        onSearch = {},
        onClear = { onSearchQueryChange("") },
        onVoiceResult = onSearchQueryChange,
        onClose = onClose,
        modifier = modifier,
    ) {
        ExpandedSearchContent(
            searchCategory = searchCategory,
            searchResults = searchResults,
            onSearchCategoryChange = onSearchCategoryChange,
            onNavigateToReader = onNavigateToReader,
            onNavigateToShelf = onNavigateToShelf,
            onNavigateToAuthor = onNavigateToAuthor,
            onNavigateToTag = onNavigateToTag,
            onAuthorsHeaderClick = onAuthorsHeaderClick,
            onTagsHeaderClick = onTagsHeaderClick,
        )
    }
}
