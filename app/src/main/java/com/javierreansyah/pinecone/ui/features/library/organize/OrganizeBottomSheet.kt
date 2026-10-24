package com.javierreansyah.pinecone.ui.features.library.organize

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Forest
import com.composables.icons.materialsymbols.outlined.Mic
import com.composables.icons.materialsymbols.outlined.Search
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.SegmentedColumn
import com.javierreansyah.pinecone.ui.components.rememberVoiceSearchLauncher

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeBottomSheet(
    viewModel: OrganizeViewModel,
    onNavigateBack: () -> Unit,
) {
    val uiState = viewModel.uiState.collectAsState()

    val textFieldState = remember { TextFieldState() }
    val searchBarState = rememberContainedSearchBarState(initialValue = SearchBarValue.Expanded)
    val keyboardController = LocalSoftwareKeyboardController.current

    var shelfNameToCreate by remember { mutableStateOf<String?>(null) }

    val searchQuery = textFieldState.text.toString().trim()

    val filteredSpaces = remember(uiState.value.spaces, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.value.spaces
        } else {
            uiState.value.spaces.mapNotNull { spaceWithShelves ->
                val spaceMatches =
                    spaceWithShelves.space.name.contains(searchQuery, ignoreCase = true)
                val matchingShelves = spaceWithShelves.shelves.filter { (itShelf, _) ->
                    itShelf.name.contains(searchQuery, ignoreCase = true)
                }
                if (spaceMatches || matchingShelves.isNotEmpty()) {
                    spaceWithShelves.copy(
                        shelves = if (spaceMatches && matchingShelves.isEmpty()) spaceWithShelves.shelves else matchingShelves,
                    )
                } else {
                    null
                }
            }
        }
    }

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val sheetHeight = remember(windowInfo.containerSize, density) {
        with(density) { (windowInfo.containerSize.height * 0.9f).toDp() }
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onNavigateBack,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(sheetHeight)
                .padding(horizontal = 16.dp),
        ) {
            OrganizeSearchInputField(
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                onSearch = { keyboardController?.hide() },
                onClear = { textFieldState.edit { replace(0, length, "") } },
            )

            if (!uiState.value.isLoading) {
                OrganizeContent(
                    searchQuery = searchQuery,
                    spacesEnabled = uiState.value.spacesEnabled,
                    spaces = filteredSpaces,
                    allSpaces = uiState.value.spaces,
                    onCreateSpaceWithQuery = { name ->
                        viewModel.createSpace(name)
                        textFieldState.edit { replace(0, length, "") }
                        keyboardController?.hide()
                    },
                    onRequestCreateShelf = { name ->
                        if (uiState.value.defaultShelfSpaceId == null) shelfNameToCreate = name
                        else {
                            viewModel.createShelf(
                                uiState.value.defaultShelfSpaceId ?: "_all_",
                                name
                            )
                            textFieldState.edit { replace(0, length, "") }
                            keyboardController?.hide()
                        }
                    },
                    onToggleSpace = viewModel::toggleSpace,
                    onToggleShelf = viewModel::toggleShelf,
                )
            }
        }
    }

    shelfNameToCreate?.let { shelfName ->
        SelectSpaceForShelfDialog(
            shelfName = shelfName,
            spaces = uiState.value.spaces.map { (itSpace, _, _) -> itSpace },
            onDismiss = { shelfNameToCreate = null },
        ) { spaceIds ->
            spaceIds.forEach { spaceId ->
                viewModel.createShelf(spaceId, shelfName)
            }
            shelfNameToCreate = null
            textFieldState.edit { replace(0, length, "") }
            keyboardController?.hide()
        }
    }
}

@Composable
private fun OrganizeSearchInputField(
    textFieldState: TextFieldState,
    searchBarState: SearchBarState,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
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
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        onSearch = { onSearch() },
        placeholder = {
            Text(
                stringResource(R.string.action_search),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        leadingIcon = {
            Icon(
                MaterialSymbols.Outlined.Search,
                contentDescription = stringResource(R.string.action_search),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp),
            ) {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            MaterialSymbols.Outlined.Close,
                            contentDescription = stringResource(R.string.action_clear),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    IconButton(onClick = { launchVoiceSearch() }) {
                        Icon(
                            MaterialSymbols.Outlined.Mic,
                            contentDescription = stringResource(R.string.action_voice_search),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun OrganizeContent(
    searchQuery: String,
    spacesEnabled: Boolean,
    spaces: List<SpaceWithShelvesItemState>,
    allSpaces: List<SpaceWithShelvesItemState>,
    onCreateSpaceWithQuery: (String) -> Unit,
    onRequestCreateShelf: (String) -> Unit,
    onToggleSpace: (String, ToggleableState) -> Unit,
    onToggleShelf: (String, ToggleableState) -> Unit,
) {
    val showCreateSpace =
        spacesEnabled && searchQuery.isNotBlank() && !allSpaces.any { (itSpace2, _, _) ->
            itSpace2.name.equals(searchQuery, ignoreCase = true)
        }
    val showCreateShelf = searchQuery.isNotBlank() && allSpaces.isNotEmpty()

    val isSearchEmpty =
        searchQuery.isNotBlank() && spaces.isEmpty() && !showCreateSpace && !showCreateShelf

    val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val fastSpatialSpec =
        MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()

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
        label = "OrganizeContentState",
    ) { emptySearch ->
        if (emptySearch) {
            OrganizeEmptyResults(searchQuery = searchQuery)
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (showCreateSpace || showCreateShelf) {
                    item(key = "create_section") {
                        SegmentedColumn(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (showCreateSpace) {
                                item(
                                    key = "create_space_query",
                                    onClick = { onCreateSpaceWithQuery(searchQuery) },
                                    leadingContent = {
                                        Icon(
                                            MaterialSymbols.Outlined.Forest,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                ) {
                                    Text(
                                        text = stringResource(R.string.library_new_space) + " \"$searchQuery\"",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            if (showCreateShelf) {
                                item(
                                    key = "create_shelf_query",
                                    onClick = { onRequestCreateShelf(searchQuery) },
                                    leadingContent = {
                                        Icon(
                                            MaterialSymbols.Outlined.Folder,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                ) {
                                    Text(
                                        text = stringResource(R.string.library_new_shelf) + " \"$searchQuery\"",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }

                items(
                    spaces,
                    key = { (itSpace3, _, _) -> itSpace3.id }) { (spaceItemSpace, spaceItemState, spaceItemShelves) ->
                    val (spaceId2, spaceName, _) = spaceItemSpace
                    val spaceShelves = spaceItemShelves
                    val isSpaceSelected = spaceItemState == ToggleableState.On

                    SegmentedColumn(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Space header (top of this segmented list)
                        item(
                            key = "space_${spaceId2}",
                            selected = isSpaceSelected,
                            onClick = if (spaceId2 == "_all_" && isSpaceSelected) null else ({
                                onToggleSpace(
                                    spaceId2,
                                    spaceItemState
                                )
                            }),
                            leadingContent = {
                                Icon(
                                    if (spacesEnabled) MaterialSymbols.Outlined.Forest else MaterialSymbols.Outlined.Folder,
                                    contentDescription = null,
                                    tint = if (isSpaceSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                                )
                            },
                            content = {
                                Text(
                                    text = if (spacesEnabled) spaceName else stringResource(R.string.library_add_to_library),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            },
                            trailingContent = {
                                TriStateCheckbox(
                                    state = spaceItemState,
                                    enabled = spaceId2 != "_all_" || !isSpaceSelected,
                                    onClick = null,
                                )
                            },
                        )

                        // Shelves in this space (below the space in this segmented list)
                        spaceShelves.forEach { (shelfItemShelf, shelfItemState) ->
                            val isShelfSelected = shelfItemState == ToggleableState.On
                            item(
                                key = "shelf_${shelfItemShelf.id}",
                                selected = isShelfSelected,
                                onClick = { onToggleShelf(shelfItemShelf.id, shelfItemState) },
                                leadingContent = {
                                    Icon(
                                        MaterialSymbols.Outlined.Folder,
                                        contentDescription = null,
                                        tint = if (isShelfSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.secondary,
                                    )
                                },
                                content = {
                                    Text(
                                        text = shelfItemShelf.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                trailingContent = {
                                    TriStateCheckbox(
                                        state = shelfItemState,
                                        onClick = null,
                                    )
                                },
                            )
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun OrganizeEmptyResults(
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 48.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                MaterialSymbols.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
            Text(
                stringResource(R.string.reader_search_no_results_for, searchQuery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
