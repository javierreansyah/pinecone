package com.javierreansyah.pinecone.ui.features.library.organize

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarState
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberContainedSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Forest
import com.composables.icons.materialsymbols.outlined.Mic
import com.composables.icons.materialsymbols.outlined.Search
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.SegmentedListItem
import com.javierreansyah.pinecone.ui.components.rememberVoiceSearchLauncher

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OrganizeBottomSheet(
    viewModel: OrganizeViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    val textFieldState = remember { TextFieldState() }
    val searchBarState = rememberContainedSearchBarState(initialValue = SearchBarValue.Expanded)
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchQuery = textFieldState.text.toString().trim()
    val filteredSpaces = remember(uiState.spaces, searchQuery) {
        if (searchQuery.isBlank()) uiState.spaces
        else uiState.spaces.filter { it.space.name.contains(searchQuery, ignoreCase = true) }
    }
    val filteredShelves = remember(uiState.shelves, searchQuery) {
        if (searchQuery.isBlank()) uiState.shelves
        else uiState.shelves.filter { it.shelf.name.contains(searchQuery, ignoreCase = true) }
    }

    val configuration = LocalConfiguration.current
    val sheetHeight = remember(configuration.screenHeightDp) {
        (configuration.screenHeightDp * 0.9f).dp
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onNavigateBack,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .padding(horizontal = 16.dp)
        ) {
            OrganizeSearchInputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { keyboardController?.hide() },
                onClear = { textFieldState.edit { replace(0, length, "") } }
            )

            if (!uiState.isLoading) {
                OrganizeContent(
                    searchQuery = searchQuery,
                    spaces = filteredSpaces,
                    shelves = filteredShelves,
                    allSpaces = uiState.spaces,
                    allShelves = uiState.shelves,
                    onCreateSpace = { name ->
                        viewModel.createSpace(name)
                        textFieldState.edit { replace(0, length, "") }
                        keyboardController?.hide()
                    },
                    onCreateShelf = { name ->
                        viewModel.createShelf(name)
                        textFieldState.edit { replace(0, length, "") }
                        keyboardController?.hide()
                    },
                    onToggleSpace = viewModel::toggleSpace,
                    onToggleShelf = viewModel::toggleShelf
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OrganizeSearchInputField(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val launchVoiceSearch = rememberVoiceSearchLauncher { spokenText ->
        textFieldState.edit { replace(0, length, spokenText) }
    }

    SearchBarDefaults.InputField(
        modifier = modifier.fillMaxWidth(),
        textFieldState = textFieldState,
        searchBarState = searchBarState,
        colors = SearchBarDefaults.inputFieldColors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        onSearch = { onSearch() },
        placeholder = {
            Text(
                stringResource(R.string.action_search),
                style = MaterialTheme.typography.bodyLarge
            )
        },
        leadingIcon = {
            Icon(
                MaterialSymbols.Outlined.Search,
                contentDescription = stringResource(R.string.action_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            MaterialSymbols.Outlined.Close,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(onClick = { launchVoiceSearch() }) {
                        Icon(
                            MaterialSymbols.Outlined.Mic,
                            contentDescription = stringResource(R.string.action_voice_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun OrganizeContent(
    searchQuery: String,
    spaces: List<SpaceItemState>,
    shelves: List<ShelfItemState>,
    allSpaces: List<SpaceItemState>,
    allShelves: List<ShelfItemState>,
    onCreateSpace: (String) -> Unit,
    onCreateShelf: (String) -> Unit,
    onToggleSpace: (String, ToggleableState) -> Unit,
    onToggleShelf: (String, ToggleableState) -> Unit
) {
    val showCreateSpace = searchQuery.isNotBlank() && !allSpaces.any {
        it.space.name.equals(searchQuery, ignoreCase = true)
    }
    val showCreateShelf = searchQuery.isNotBlank() && !allShelves.any {
        it.shelf.name.equals(searchQuery, ignoreCase = true)
    }
    val isSearchEmpty = searchQuery.isNotBlank() && spaces.isEmpty() && shelves.isEmpty() && !showCreateSpace && !showCreateShelf

    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val fastSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val fastSpatialSpecIntOffset = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()

    AnimatedContent(
        targetState = isSearchEmpty,
        transitionSpec = {
            fadeIn(animationSpec = fastEffectsSpec) togetherWith
                    fadeOut(animationSpec = fastEffectsSpec) using SizeTransform { _, _ ->
                fastSpatialSpec
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        label = "SearchEmptyState"
    ) { empty ->
        if (empty) {
            OrganizeEmptyResults(searchQuery = searchQuery)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                organizeCreateOptions(
                    searchQuery = searchQuery,
                    showCreateSpace = showCreateSpace,
                    showCreateShelf = showCreateShelf,
                    fastEffectsSpec = fastEffectsSpec,
                    fastSpatialSpecIntOffset = fastSpatialSpecIntOffset,
                    onCreateSpace = onCreateSpace,
                    onCreateShelf = onCreateShelf
                )

                organizeSpacesSection(
                    spaces = spaces,
                    fastEffectsSpec = fastEffectsSpec,
                    fastSpatialSpecIntOffset = fastSpatialSpecIntOffset,
                    onToggleSpace = onToggleSpace
                )

                organizeShelvesSection(
                    shelves = shelves,
                    fastEffectsSpec = fastEffectsSpec,
                    fastSpatialSpecIntOffset = fastSpatialSpecIntOffset,
                    onToggleShelf = onToggleShelf
                )
            }
        }
    }
}

@Composable
private fun OrganizeEmptyResults(
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 48.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                MaterialSymbols.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Text(
                stringResource(R.string.reader_search_no_results_for, searchQuery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun LazyListScope.organizeCreateOptions(
    searchQuery: String,
    showCreateSpace: Boolean,
    showCreateShelf: Boolean,
    fastEffectsSpec: FiniteAnimationSpec<Float>,
    fastSpatialSpecIntOffset: FiniteAnimationSpec<IntOffset>,
    onCreateSpace: (String) -> Unit,
    onCreateShelf: (String) -> Unit
) {
    if (showCreateSpace) {
        item(key = "create_space") {
            CreateOptionItem(
                label = stringResource(R.string.library_new_space) + " \"$searchQuery\"",
                icon = MaterialSymbols.Outlined.Forest,
                index = 0,
                count = if (showCreateShelf) 2 else 1,
                isTopDetached = true,
                isBottomDetached = !showCreateShelf,
                fastEffectsSpec = fastEffectsSpec,
                fastSpatialSpecIntOffset = fastSpatialSpecIntOffset,
                onClick = { onCreateSpace(searchQuery) }
            )
        }
    }

    if (showCreateShelf) {
        item(key = "create_shelf") {
            CreateOptionItem(
                label = stringResource(R.string.library_new_shelf) + " \"$searchQuery\"",
                icon = MaterialSymbols.Outlined.Folder,
                index = if (showCreateSpace) 1 else 0,
                count = if (showCreateSpace) 2 else 1,
                isTopDetached = !showCreateSpace,
                isBottomDetached = true,
                fastEffectsSpec = fastEffectsSpec,
                fastSpatialSpecIntOffset = fastSpatialSpecIntOffset,
                onClick = { onCreateShelf(searchQuery) }
            )
        }
    }

    if (showCreateSpace || showCreateShelf) {
        item(key = "create_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LazyItemScope.CreateOptionItem(
    label: String,
    icon: ImageVector,
    index: Int,
    count: Int,
    isTopDetached: Boolean,
    isBottomDetached: Boolean,
    fastEffectsSpec: FiniteAnimationSpec<Float>,
    fastSpatialSpecIntOffset: FiniteAnimationSpec<IntOffset>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SegmentedListItem(
        modifier = modifier.animateItem(
            fadeInSpec = fastEffectsSpec,
            fadeOutSpec = fastEffectsSpec,
            placementSpec = fastSpatialSpecIntOffset
        ),
        selected = false,
        onClick = onClick,
        index = index,
        count = count,
        isTopDetached = isTopDetached,
        isBottomDetached = isBottomDetached,
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        content = {
            Text(
                label,
                color = MaterialTheme.colorScheme.primary
            )
        }
    )
}

private fun LazyListScope.organizeSpacesSection(
    spaces: List<SpaceItemState>,
    fastEffectsSpec: FiniteAnimationSpec<Float>,
    fastSpatialSpecIntOffset: FiniteAnimationSpec<IntOffset>,
    onToggleSpace: (String, ToggleableState) -> Unit
) {
    if (spaces.isNotEmpty()) {
        item(key = "spaces_title") {
            Text(
                text = stringResource(R.string.library_spaces_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        itemsIndexed(spaces, key = { _, it -> "space_${it.space.id}" }) { index, spaceItem ->
            val isTopDetached = spaceItem.state == ToggleableState.On || index == 0 || (spaces.getOrNull(index - 1)?.state == ToggleableState.On)
            val isBottomDetached = spaceItem.state == ToggleableState.On || index == spaces.size - 1 || (spaces.getOrNull(index + 1)?.state == ToggleableState.On)

            SegmentedListItem(
                modifier = Modifier.animateItem(
                    fadeInSpec = fastEffectsSpec,
                    fadeOutSpec = fastEffectsSpec,
                    placementSpec = fastSpatialSpecIntOffset
                ),
                selected = spaceItem.state == ToggleableState.On,
                onClick = { onToggleSpace(spaceItem.space.id, spaceItem.state) },
                index = index,
                count = spaces.size,
                isTopDetached = isTopDetached,
                isBottomDetached = isBottomDetached,
                content = { Text(spaceItem.space.name) },
                trailingContent = {
                    TriStateCheckbox(
                        state = spaceItem.state,
                        onClick = { onToggleSpace(spaceItem.space.id, spaceItem.state) }
                    )
                }
            )
        }
        item(key = "spaces_spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

private fun LazyListScope.organizeShelvesSection(
    shelves: List<ShelfItemState>,
    fastEffectsSpec: FiniteAnimationSpec<Float>,
    fastSpatialSpecIntOffset: FiniteAnimationSpec<IntOffset>,
    onToggleShelf: (String, ToggleableState) -> Unit
) {
    if (shelves.isNotEmpty()) {
        item(key = "shelves_title") {
            Text(
                text = stringResource(R.string.library_tab_shelves),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        itemsIndexed(shelves, key = { _, it -> "shelf_${it.shelf.id}" }) { index, shelfItem ->
            val isTopDetached = shelfItem.state == ToggleableState.On || index == 0 || (shelves.getOrNull(index - 1)?.state == ToggleableState.On)
            val isBottomDetached = shelfItem.state == ToggleableState.On || index == shelves.size - 1 || (shelves.getOrNull(index + 1)?.state == ToggleableState.On)

            SegmentedListItem(
                modifier = Modifier.animateItem(
                    fadeInSpec = fastEffectsSpec,
                    fadeOutSpec = fastEffectsSpec,
                    placementSpec = fastSpatialSpecIntOffset
                ),
                selected = shelfItem.state == ToggleableState.On,
                onClick = { onToggleShelf(shelfItem.shelf.id, shelfItem.state) },
                index = index,
                count = shelves.size,
                isTopDetached = isTopDetached,
                isBottomDetached = isBottomDetached,
                content = { Text(shelfItem.shelf.name) },
                trailingContent = {
                    TriStateCheckbox(
                        state = shelfItem.state,
                        onClick = { onToggleShelf(shelfItem.shelf.id, shelfItem.state) }
                    )
                }
            )
        }
        item(key = "shelves_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
