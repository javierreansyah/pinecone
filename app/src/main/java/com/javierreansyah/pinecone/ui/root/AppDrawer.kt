package com.javierreansyah.pinecone.ui.root

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Add
import com.composables.icons.materialsymbols.outlined.Archive
import com.composables.icons.materialsymbols.outlined.Book_3
import com.composables.icons.materialsymbols.outlined.Folder
import com.composables.icons.materialsymbols.outlined.Forest
import com.composables.icons.materialsymbols.outlined.Inbox
import com.composables.icons.materialsymbols.outlined.Menu_open
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Upload
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.SpaceEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDrawer(
    drawerState: WideNavigationRailState,
    allSpaces: List<SpaceEntity> = emptyList(),
    selectedSpaceId: String? = null,
    onSpaceSelected: (String?) -> Unit = {},
    hasUnsortedBooks: Boolean = false,
    showAllSpaces: Boolean = true,
    onNavigateToUnsorted: () -> Unit = {},
    onNavigateToAllSpaces: () -> Unit,
    onNavigateToArchives: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onImportFilesClick: () -> Unit,
    onScanFolderClick: () -> Unit,
    onNavigateToDictionaries: () -> Unit
) {
    val scope = rememberCoroutineScope()

    ModalWideNavigationRail(
        state = drawerState,
        hideOnCollapse = true,
        modifier = Modifier.fillMaxHeight(),
        expandedHeaderTopPadding = 0.dp,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            IconButton(
                onClick = {
                    scope.launch { drawerState.collapse() }
                },
                modifier = Modifier
                    .padding(start = 8.dp, top = 8.dp)
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Menu_open,
                    contentDescription = stringResource(R.string.action_close),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)
                    .align(Alignment.Start)
            ) {
                var menuExpanded by remember { mutableStateOf(false) }

                SmallExtendedFloatingActionButton(
                    onClick = { menuExpanded = true },
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 0.dp,
                        pressedElevation = 0.dp,
                        hoveredElevation = 0.dp,
                        focusedElevation = 0.dp
                    ),
                    icon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Add,
                            contentDescription = null
                        )
                    },
                    text = { Text(text = stringResource(R.string.action_import)) }
                )

                DropdownMenuPopup(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    val groupInteractionSource = remember { MutableInteractionSource() }
                    DropdownMenuGroup(
                        shapes = MenuDefaults.groupShape(0, 1),
                        interactionSource = groupInteractionSource
                    ) {
                        DropdownMenuItem(
                            selected = false,
                            text = { Text(stringResource(R.string.nav_import_files)) },
                            shapes = MenuDefaults.itemShape(0, 2),
                            leadingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Upload,
                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onImportFilesClick()
                                scope.launch { drawerState.collapse() }
                            }
                        )
                        DropdownMenuItem(
                            selected = false,
                            text = { Text(stringResource(R.string.nav_scan_folder)) },
                            shapes = MenuDefaults.itemShape(1, 2),
                            leadingIcon = {
                                Icon(
                                    imageVector = MaterialSymbols.Outlined.Folder,
                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onScanFolderClick()
                                scope.launch { drawerState.collapse() }
                            }
                        )
                    }
                }
            }

            if (hasUnsortedBooks) {
                WideNavigationRailItem(
                    railExpanded = true,
                    icon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Inbox,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.library_unsorted_title)) },
                    selected = false,
                    onClick = {
                        onNavigateToUnsorted()
                        scope.launch { drawerState.collapse() }
                    }
                )
            }

            WideNavigationRailItem(
                railExpanded = true,
                icon = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Forest,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.library_spaces_title)) },
                selected = false,
                onClick = {
                    onNavigateToAllSpaces()
                    scope.launch { drawerState.collapse() }
                }
            )

            WideNavigationRailItem(
                railExpanded = true,
                icon = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Archive,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.library_archives_title)) },
                selected = false,
                onClick = {
                    onNavigateToArchives()
                    scope.launch { drawerState.collapse() }
                }
            )

            WideNavigationRailItem(
                railExpanded = true,
                icon = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Book_3,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.dictionaries_title)) },
                selected = false,
                onClick = {
                    onNavigateToDictionaries()
                    scope.launch { drawerState.collapse() }
                }
            )

            WideNavigationRailItem(
                railExpanded = true,
                icon = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Settings,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.settings_title)) },
                selected = false,
                onClick = {
                    onNavigateToSettings()
                    scope.launch { drawerState.collapse() }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.library_spaces_title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (showAllSpaces) {
                WideNavigationRailItem(
                    railExpanded = true,
                    icon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Forest,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.action_all)) },
                    selected = selectedSpaceId == null || selectedSpaceId == "_all_",
                    onClick = {
                        onSpaceSelected("_all_")
                        scope.launch { drawerState.collapse() }
                    }
                )
            }

            allSpaces.forEach { space ->
                WideNavigationRailItem(
                    railExpanded = true,
                    icon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Forest,
                            contentDescription = null
                        )
                    },
                    label = { Text(space.name) },
                    selected = selectedSpaceId == space.id,
                    onClick = {
                        onSpaceSelected(space.id)
                        scope.launch { drawerState.collapse() }
                    }
                )
            }
        }
    }
}
