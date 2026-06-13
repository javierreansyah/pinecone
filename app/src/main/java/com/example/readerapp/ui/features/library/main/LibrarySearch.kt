package com.example.readerapp.ui.features.library.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AppBarWithSearch
import androidx.compose.material3.ExpandedFullScreenContainedSearchBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarScrollBehavior
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withResumed
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Chevron_backward
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Menu
import com.composables.icons.materialsymbols.outlined.Mic
import com.composables.icons.materialsymbols.outlined.Tune
import com.example.readerapp.R
import com.example.readerapp.ui.components.rememberVoiceSearchLauncher
import com.example.readerapp.ui.features.library.SearchCategory
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
    searchBarState: SearchBarState = rememberContainedSearchBarState()
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
        onRestoringChange = { isRestoring = it })

    // Colors
    val searchBarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh

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

        val expandedSearchBarColors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
            dividerColor = Color.Transparent,
            inputFieldColors = SearchBarDefaults.inputFieldColors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )

        ExpandedFullScreenContainedSearchBar(
            state = searchBarState,
            inputField = {
                SearchInputField(
                    searchBarState = searchBarState,
                    textFieldState = textFieldState,
                    focusRequester = focusRequester,
                    isRestoring = isRestoring,
                    onRestoringChange = { isRestoring = it },
                    colors = expandedSearchBarColors.inputFieldColors,
                    onSearchQueryChange = onSearchQueryChange
                )
            },
            colors = expandedSearchBarColors
        ) {
            // Track whether the expansion animation has ever settled so we can
            // defer the expensive search-content composition until the bar is
            // fully open. This eliminates cold-start jank by keeping the UI
            // thread free during the expansion animation.
            var hasEverExpanded by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                snapshotFlow { searchBarState.currentValue }.collect { value ->
                    if (value == SearchBarValue.Expanded) {
                        hasEverExpanded = true
                    }
                }
            }

            // After the first expansion the flag stays true, so subsequent
            // opens never need to re-compose from scratch.
            val showContent by remember {
                derivedStateOf {
                    hasEverExpanded || searchBarState.currentValue == SearchBarValue.Expanded
                }
            }

            // Wrapper that collapses the search bar before navigating so the
            // Library destination appears in its normal (non-expanded) state
            // during predictive back and enter transitions.
            val navigateAfterCollapse = remember(scope, searchBarState) {
                { action: () -> Unit ->
                    scope.launch {
                        searchBarState.animateToCollapsed()
                        action()
                    }
                    Unit
                }
            }

            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = androidx.compose.animation.ExitTransition.None
            ) {
                ExpandedSearchContent(
                    isSearchEmpty = textFieldState.text.isEmpty(),
                    searchCategory = searchCategory,
                    searchResults = searchResults,
                    onSearchCategoryChange = onSearchCategoryChange,
                    onNavigateToReader = { bookId ->
                        navigateAfterCollapse { onNavigateToReader(bookId) }
                    },
                    onNavigateToShelf = { shelfId, name, count ->
                        navigateAfterCollapse { onNavigateToShelf(shelfId, name, count) }
                    },
                    onNavigateToAuthor = { author ->
                        navigateAfterCollapse { onNavigateToAuthor(author) }
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
                        MaterialSymbols.Outlined.Chevron_backward,
                        contentDescription = stringResource(R.string.action_back),
                        modifier = Modifier.size(28.dp)
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
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldState.edit { replace(0, length, "") }
                    }) {
                        Icon(
                            MaterialSymbols.Outlined.Close,
                            contentDescription = stringResource(R.string.action_clear)
                        )
                    }
                } else {
                    IconButton(onClick = { launchVoiceSearch() }) {
                        Icon(
                            MaterialSymbols.Outlined.Mic,
                            contentDescription = stringResource(R.string.action_voice_search)
                        )
                    }
                }
            }
        },
    )
}


