package com.example.readerapp.ui.features.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Content_copy
import com.composables.icons.materialsymbols.outlined.Search
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.readerapp.ui.features.reader.ReaderViewModel
import com.example.readerapp.ui.theme.AppTheme
import org.readium.r2.shared.publication.Link
import androidx.core.graphics.toColorInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderOverlay(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onNavigateToChapter: (Link) -> Unit,
    onSeekToProgression: (Double) -> Unit,
    onInfoClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(
        initialValue = com.example.readerapp.data.local.ReaderSettings()
    )

    val uiDarkTheme = when (settings.themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    val readerBgColor = when (settings.readerThemePreset) {
        "Light" -> Color(0xFFFFFFFF)
        "Warm" -> Color(0xFFFAF4E8)
        "Dark" -> Color(0xFF000000)
        "Auto" -> if (uiDarkTheme) Color(0xFF000000) else Color(0xFFFFFFFF)
        else -> try {
            Color(settings.customBackgroundColor.toColorInt())
        } catch (_: Exception) {
            Color.White
        }
    }

    val readerTextColor = when (settings.readerThemePreset) {
        "Light" -> Color.Black
        "Warm" -> Color(0xFF121212) // Dark text for warm theme
        "Dark" -> Color.White
        "Auto" -> if (uiDarkTheme) Color.White else Color.Black
        else -> try {
            Color(settings.customTextColor.toColorInt())
        } catch (_: Exception) {
            if (uiDarkTheme) Color.White else Color.Black
        }
    }

    // Settings bottom sheet
    val settingsSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // Loading state
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Error state
        uiState.error?.let { error ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Animated controls overlay — top bar only
        AnimatedVisibility(
            visible = uiState.showControls && !uiState.showSearch,
            enter = slideInVertically(initialOffsetY = { -20 }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(targetOffsetY = { -20 }, animationSpec = tween(250)) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            ReaderTopBar(
                isBookmarked = isBookmarked,
                onBack = onBack,
                onSearchClick = { viewModel.showSearch() },
                onTocClick = { viewModel.showToc() },
                onSettingsClick = { viewModel.showSettings() },
                onToggleBookmark = { viewModel.toggleBookmark() },
                onInfoClick = onInfoClick,
                readerBgColor = readerBgColor,
                readerTextColor = readerTextColor
            )
        }

        // Bottom bar — shown when controls are visible.
        // Bottom bar — shown when controls are visible and no selection is active.
        val showBottomBar = uiState.showControls && !uiState.showSearch && uiState.selectionLocator == null && uiState.viewingHighlight == null
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { 20 }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(targetOffsetY = { 20 }, animationSpec = tween(250)) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBar(
                progression = uiState.progression,
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                readerBgColor = readerBgColor,
                readerTextColor = readerTextColor,
                onSeekToProgression = onSeekToProgression,
                isInSearchNavigationMode = uiState.isInSearchNavigationMode,
                activeSearchIndex = uiState.activeSearchIndex,
                totalSearchResults = uiState.searchResults.size,
                onExitSearch = { viewModel.exitSearchNavigation() },
                onPrevSearchResult = { viewModel.prevSearchResult() },
                onNextSearchResult = { viewModel.nextSearchResult() }
            )
        }

        // Reader Bottom Sheet
        if (uiState.showToc) {
            val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
            val notes by viewModel.allNotesAndHighlights.collectAsStateWithLifecycle()
            val currentLocator by viewModel.currentLocator.collectAsStateWithLifecycle()

            AppTheme(
                darkTheme = uiDarkTheme,
                dynamicColor = settings.colorPalette == "Dynamic"
            ) {
                ReaderBottomSheet(
                    tableOfContents = viewModel.tableOfContents,
                    bookmarks = bookmarks,
                    notes = notes,
                    currentLocator = currentLocator,
                    getPositionLabel = { viewModel.getPositionLabel(it) },
                    getChapterPageLabel = { viewModel.getChapterPageLabel(it) },
                    onChapterClick = { link ->
                        onNavigateToChapter(link)
                        viewModel.hideToc()
                        viewModel.toggleControls()
                    },
                    onBookmarkClick = { locator ->
                        onSeekToProgression(locator.locations.totalProgression ?: 0.0) // Or we could add a direct navigate to locator callback
                        viewModel.hideToc()
                        viewModel.toggleControls()
                    },
                    onNoteClick = { locator ->
                        onSeekToProgression(locator.locations.totalProgression ?: 0.0)
                        viewModel.hideToc()
                        viewModel.toggleControls()
                    },
                    onAddNote = { text ->
                        viewModel.addNote(text)
                    },
                    onDeleteBookmark = { id ->
                        viewModel.deleteBookmark(id)
                    },
                    onDeleteNote = { id ->
                        viewModel.deleteNote(id)
                    },
                    onDismiss = { viewModel.hideToc() }
                )
            }
        }


        // Settings Bottom Sheet — wrapped in its own theme that honours the UI
        // themeMode/colorPalette setting, independent of the reader theme.
        if (uiState.showSettings) {
            AppTheme(
                darkTheme = uiDarkTheme,
                dynamicColor = settings.colorPalette == "Dynamic"
            ) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.hideSettings() },
                    sheetState = settingsSheetState
                ) {
                    ReaderSettingsContent(
                        settings = settings,
                        onSettingsChange = { newSettings ->
                            viewModel.updateSettings(newSettings)
                        }
                    )
                }
            }
        }


        // ── Full-screen search overlay ─────────────────────────────────────────
        AnimatedVisibility(
            visible = uiState.showSearch,
            enter = slideInVertically(animationSpec = tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(220)) { it } + fadeOut(tween(180))
        ) {
            AppTheme(
                darkTheme = uiDarkTheme,
                dynamicColor = settings.colorPalette == "Dynamic"
            ) {
                SearchScreen(
                    query = uiState.searchQuery,
                    results = uiState.searchResults,
                    isLoading = uiState.searchLoading,
                    searchPerformed = uiState.searchPerformed,
                    onQueryChange = { q -> viewModel.updateSearchQuery(q) },
                    onSearch = { q -> viewModel.performSearch(q) },
                    onResultClick = { index -> viewModel.selectSearchResult(index) },
                    onClose = { viewModel.hideSearch() }
                )
            }
        }


        // Selection & Highlight Bottom Action Bar
        val menuLocator = uiState.selectionLocator
        val menuHighlight = uiState.viewingHighlight
        val showActionBar = menuLocator != null || menuHighlight != null
        
        AnimatedVisibility(
            visible = showActionBar,
            enter = slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(250)) + fadeIn(animationSpec = tween(250)),
            exit = slideOutVertically(targetOffsetY = { 40 }, animationSpec = tween(250)) + fadeOut(animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val highlightText = try {
                org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(
                    menuHighlight?.locatorJson ?: menuLocator?.toJSON()?.toString() ?: ""
                ))?.text?.highlight ?: ""
            } catch (_: Exception) { "" }

            val context = androidx.compose.ui.platform.LocalContext.current

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, readerBgColor),
                            startY = 0f
                        )
                    )
                    .padding(WindowInsets.navigationBars.asPaddingValues())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left side: Action Icons
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.dp)
                    ) {
                        androidx.compose.material3.IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("highlight", highlightText)
                                clipboard.setPrimaryClip(clip)
                                if (menuLocator != null) viewModel.hideSelectionMenu()
                                if (menuHighlight != null) viewModel.hideViewHighlight()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(MaterialSymbols.Outlined.Content_copy, contentDescription = "Copy",
                                tint = readerTextColor, modifier = Modifier.size(20.dp))
                        }
                        androidx.compose.material3.IconButton(
                            onClick = {
                                if (menuLocator != null) viewModel.hideSelectionMenu()
                                if (menuHighlight != null) viewModel.hideViewHighlight()
                                viewModel.showSearch()
                                viewModel.updateSearchQuery(highlightText)
                                viewModel.performSearch(highlightText)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(MaterialSymbols.Outlined.Search, contentDescription = "Search",
                                tint = readerTextColor, modifier = Modifier.size(20.dp))
                        }
                        androidx.compose.material3.IconButton(
                            onClick = {
                                if (menuLocator != null) {
                                    viewModel.addNoteAndEdit(menuLocator)
                                    viewModel.hideSelectionMenu()
                                    if (menuHighlight != null) viewModel.hideViewHighlight()
                                } else if (menuHighlight != null) {
                                    viewModel.editNote(menuHighlight)
                                    viewModel.hideViewHighlight()
                                }
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(MaterialSymbols.Outlined.Edit, contentDescription = "Make Note",
                                tint = readerTextColor, modifier = Modifier.size(20.dp))
                        }
                        if (menuHighlight != null && menuLocator == null) {
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    viewModel.deleteNote(menuHighlight.id)
                                    viewModel.hideViewHighlight()
                                },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(MaterialSymbols.Outlined.Delete, contentDescription = "Delete",
                                    tint = readerTextColor, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // Right side: Color Swatches and Close
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.foundation.layout.Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                        ) {
                            val swatches = listOf(
                                "#40fac02e".toColorInt(), // Yellow
                                "#40fd7142".toColorInt(), // Orange
                                "#408bc24a".toColorInt(), // Green
                                "#4025c6da".toColorInt()  // Blue
                            )
                            swatches.forEach { colorInt ->
                                val isSelected = menuHighlight?.color == colorInt
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(
                                            color = Color(colorInt).copy(alpha = 1f),
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) readerTextColor else Color.Transparent,
                                            shape = androidx.compose.foundation.shape.CircleShape
                                        )
                                        .clickable { 
                                            if (menuLocator != null) {
                                                viewModel.addHighlight(menuLocator, colorInt)
                                                viewModel.hideSelectionMenu()
                                                if (menuHighlight != null) viewModel.hideViewHighlight()
                                            } else if (menuHighlight != null) {
                                                viewModel.updateNote(menuHighlight.copy(color = colorInt))
                                                viewModel.hideViewHighlight()
                                            }
                                        }
                                )
                            }
                        }

                        // Close (X)
                        androidx.compose.material3.IconButton(
                            onClick = {
                                if (menuLocator != null) viewModel.hideSelectionMenu()
                                if (menuHighlight != null) viewModel.hideViewHighlight()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(MaterialSymbols.Outlined.Close, contentDescription = "Close",
                                tint = readerTextColor, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // Edit Note Bottom Sheet
        uiState.editingNote?.let { note ->
            AppTheme(
                darkTheme = uiDarkTheme,
                dynamicColor = settings.colorPalette == "Dynamic"
            ) {
                NoteBottomSheet(
                    note = note,
                    onUpdateNote = { viewModel.updateNote(it) },
                    onDeleteNote = { viewModel.deleteNote(it) },
                    onDismiss = { viewModel.hideEditNote() }
                )
            }
        }
    }
}
