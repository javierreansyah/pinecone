package com.javierreansyah.pinecone.ui.features.library.organize

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarValue
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.window.PopupProperties
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Forest
import com.composables.icons.materialsymbols.outlined.Search
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.SegmentedListItem

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

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val sheetHeight = remember(windowInfo.containerSize, density) {
        with(density) { (windowInfo.containerSize.height * 0.8f).toDp() }
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
            SearchBarDefaults.InputField(
                modifier = Modifier.fillMaxWidth(),
                textFieldState = textFieldState,
                searchBarState = searchBarState,
                colors = SearchBarDefaults.inputFieldColors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                onSearch = {
                    keyboardController?.hide()
                },
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
                            IconButton(onClick = {
                                textFieldState.edit { replace(0, length, "") }
                            }) {
                                Icon(
                                    MaterialSymbols.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_clear),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )

            if (!uiState.isLoading) {
                val showCreateSpace = searchQuery.isNotBlank() && !uiState.spaces.any { it.space.name.equals(searchQuery, ignoreCase = true) }
                val showCreateShelf = searchQuery.isNotBlank() && !uiState.shelves.any { it.shelf.name.equals(searchQuery, ignoreCase = true) }
                val isSearchEmpty = searchQuery.isNotBlank() && filteredSpaces.isEmpty() && filteredShelves.isEmpty() && !showCreateSpace && !showCreateShelf

                val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                val fastSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntSize>()
                val fastSpatialSpecIntOffset = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.unit.IntOffset>()

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
                        .weight(1f),
                    label = "SearchEmptyState"
                ) { empty ->
                    if (empty) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
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
                                    stringResource(
                                        R.string.reader_search_no_results_for,
                                        searchQuery
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (showCreateSpace) {
                                item(key = "create_space") {
                                    SegmentedListItem(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = fastEffectsSpec,
                                            fadeOutSpec = fastEffectsSpec,
                                            placementSpec = fastSpatialSpecIntOffset
                                        ),
                                        selected = false,
                                        onClick = { 
                                            viewModel.createSpace(searchQuery)
                                            textFieldState.edit { replace(0, length, "") }
                                            keyboardController?.hide()
                                        },
                                        index = 0,
                                        count = if (showCreateShelf) 2 else 1,
                                        isTopDetached = true,
                                        isBottomDetached = !showCreateShelf,
                                        leadingContent = {
                                            Icon(
                                                MaterialSymbols.Outlined.Forest,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        content = { 
                                            Text(
                                                stringResource(R.string.library_new_space) + " \"$searchQuery\"",
                                                color = MaterialTheme.colorScheme.primary
                                            ) 
                                        }
                                    )
                                }
                            }

                            if (showCreateShelf) {
                                item(key = "create_shelf") {
                                    SegmentedListItem(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = fastEffectsSpec,
                                            fadeOutSpec = fastEffectsSpec,
                                            placementSpec = fastSpatialSpecIntOffset
                                        ),
                                        selected = false,
                                        onClick = { 
                                            viewModel.createShelf(searchQuery)
                                            textFieldState.edit { replace(0, length, "") }
                                            keyboardController?.hide()
                                        },
                                        index = if (showCreateSpace) 1 else 0,
                                        count = if (showCreateSpace) 2 else 1,
                                        isTopDetached = !showCreateSpace,
                                        isBottomDetached = true,
                                        leadingContent = {
                                            Icon(
                                                MaterialSymbols.Outlined.Folder,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        content = { 
                                            Text(
                                                stringResource(R.string.library_new_shelf) + " \"$searchQuery\"",
                                                color = MaterialTheme.colorScheme.primary
                                            ) 
                                        }
                                    )
                                }
                            }

                            if (showCreateSpace || showCreateShelf) {
                                item(key = "create_spacer") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            if (filteredSpaces.isNotEmpty()) {
                                item(key = "spaces_title") {
                                    Text(
                                        text = stringResource(R.string.library_spaces_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                itemsIndexed(filteredSpaces, key = { _, it -> "space_${it.space.id}" }) { index, spaceItem ->
                                    val isTopDetached = spaceItem.state == ToggleableState.On || index == 0 || (filteredSpaces.getOrNull(index - 1)?.state == ToggleableState.On)
                                    val isBottomDetached = spaceItem.state == ToggleableState.On || index == filteredSpaces.size - 1 || (filteredSpaces.getOrNull(index + 1)?.state == ToggleableState.On)
                                    
                                    SegmentedListItem(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = fastEffectsSpec,
                                            fadeOutSpec = fastEffectsSpec,
                                            placementSpec = fastSpatialSpecIntOffset
                                        ),
                                        selected = spaceItem.state == ToggleableState.On,
                                        onClick = { viewModel.toggleSpace(spaceItem.space.id, spaceItem.state) },
                                        index = index,
                                        count = filteredSpaces.size,
                                        isTopDetached = isTopDetached,
                                        isBottomDetached = isBottomDetached,
                                        content = { Text(spaceItem.space.name) },
                                        trailingContent = {
                                            TriStateCheckbox(
                                                state = spaceItem.state,
                                                onClick = { viewModel.toggleSpace(spaceItem.space.id, spaceItem.state) }
                                            )
                                        }
                                    )
                                }
                                item(key = "spaces_spacer") {
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }

                            if (filteredShelves.isNotEmpty()) {
                                item(key = "shelves_title") {
                                    Text(
                                        text = stringResource(R.string.library_tab_shelves),
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                                itemsIndexed(filteredShelves, key = { _, it -> "shelf_${it.shelf.id}" }) { index, shelfItem ->
                                    val isTopDetached = shelfItem.state == ToggleableState.On || index == 0 || (filteredShelves.getOrNull(index - 1)?.state == ToggleableState.On)
                                    val isBottomDetached = shelfItem.state == ToggleableState.On || index == filteredShelves.size - 1 || (filteredShelves.getOrNull(index + 1)?.state == ToggleableState.On)
                                    
                                    SegmentedListItem(
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = fastEffectsSpec,
                                            fadeOutSpec = fastEffectsSpec,
                                            placementSpec = fastSpatialSpecIntOffset
                                        ),
                                        selected = shelfItem.state == ToggleableState.On,
                                        onClick = { viewModel.toggleShelf(shelfItem.shelf.id, shelfItem.state) },
                                        index = index,
                                        count = filteredShelves.size,
                                        isTopDetached = isTopDetached,
                                        isBottomDetached = isBottomDetached,
                                        content = { Text(shelfItem.shelf.name) },
                                        trailingContent = {
                                            TriStateCheckbox(
                                                state = shelfItem.state,
                                                onClick = { viewModel.toggleShelf(shelfItem.shelf.id, shelfItem.state) }
                                            )
                                        }
                                    )
                                }
                                item(key = "shelves_spacer") {
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}
