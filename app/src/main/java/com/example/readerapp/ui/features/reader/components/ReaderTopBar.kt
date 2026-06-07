package com.example.readerapp.ui.features.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.composables.icons.materialsymbols.outlined.List
import com.composables.icons.materialsymbols.outlined.Match_case
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Search

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderTopBar(
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onInfoClick: () -> Unit,
    readerBgColor: Color,
    readerTextColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(readerBgColor, Color.Transparent)
                )
            )
    ) {
        TopAppBar(
            title = { },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MaterialSymbols.Outlined.Arrow_back, contentDescription = "Back")
                }
            },
            actions = {
                IconButton(onClick = onSearchClick) {
                    Icon(MaterialSymbols.Outlined.Search, contentDescription = "Search")
                }
                IconButton(onClick = onTocClick) {
                    Icon(MaterialSymbols.Outlined.List, contentDescription = "Table of Contents", modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(MaterialSymbols.Outlined.Match_case, contentDescription = "Typography", modifier = Modifier.size(28.dp))
                }

                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(MaterialSymbols.Outlined.More_vert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isBookmarked) "Remove Bookmark" else "Add Bookmark", style = MaterialTheme.typography.titleMedium) },
                            onClick = {
                                onToggleBookmark()
                                showMoreMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    MaterialSymbols.Outlined.Bookmark,
                                    contentDescription = null
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Book Info", style = MaterialTheme.typography.titleMedium) },
                            onClick = {
                                onInfoClick()
                                showMoreMenu = false
                            },
                            leadingIcon = {
                                Icon(
                                    MaterialSymbols.Outlined.Info,
                                    contentDescription = null
                                )
                            }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                navigationIconContentColor = readerTextColor,
                actionIconContentColor = readerTextColor
            )
        )
    }
}
