package com.javierreansyah.pinecone.ui.root

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.composables.icons.materialsymbols.outlined.Menu_open
import com.composables.icons.materialsymbols.outlined.Settings
import com.composables.icons.materialsymbols.outlined.Upload
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.database.library.CollectionEntity
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppDrawer(
    drawerState: WideNavigationRailState,
    allCollections: List<CollectionEntity> = emptyList(),
    selectedCollectionId: String? = null,
    onCollectionSelected: (String?) -> Unit = {},
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
        Column(modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
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

            // FAB for imports with dropdown menu
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
                    text = { Text(text = "Import") }
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

            // Normal WideNavigationRailItems below the FAB
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
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Collections",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            WideNavigationRailItem(
                railExpanded = true,
                icon = {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Book_3,
                        contentDescription = null
                    )
                },
                label = { Text(stringResource(R.string.action_all)) },
                selected = selectedCollectionId == null || selectedCollectionId == "_all_",
                onClick = {
                    onCollectionSelected("_all_")
                    scope.launch { drawerState.collapse() }
                }
            )

            allCollections.forEach { collection ->
                WideNavigationRailItem(
                    railExpanded = true,
                    icon = {
                        Icon(
                            imageVector = MaterialSymbols.Outlined.Folder,
                            contentDescription = null
                        )
                    },
                    label = { Text(collection.name) },
                    selected = selectedCollectionId == collection.id,
                    onClick = {
                        onCollectionSelected(collection.id)
                        scope.launch { drawerState.collapse() }
                    }
                )
            }
        }
    }
}
