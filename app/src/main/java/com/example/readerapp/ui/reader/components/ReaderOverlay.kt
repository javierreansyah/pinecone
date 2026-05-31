package com.example.readerapp.ui.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.readerapp.ui.components.ReaderSettingsContent
import com.example.readerapp.ui.reader.ReaderViewModel
import com.example.readerapp.ui.theme.ReaderAppTheme
import org.readium.r2.shared.publication.Link

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderOverlay(
    viewModel: ReaderViewModel,
    onBack: () -> Unit,
    onNavigateToChapter: (Link) -> Unit,
    onSeekToProgression: (Double) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(
        initialValue = com.example.readerapp.data.local.ReaderSettings()
    )

    val readerBgColor = when (settings.readerThemePreset) {
        "Light" -> Color(0xFFFFFFFF)
        "Warm" -> Color(0xFFFAF4E8)
        "Dark" -> Color(0xFF000000)
        "Auto" -> if (isSystemInDarkTheme()) Color(0xFF000000) else Color(0xFFFFFFFF)
        else -> try {
            Color(android.graphics.Color.parseColor(settings.customBackgroundColor))
        } catch (e: Exception) {
            Color.White
        }
    }

    // Settings bottom sheet
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ReaderTopBar(
                    isBookmarked = isBookmarked,
                    onBack = onBack,
                    onSearchClick = { viewModel.showSearch() },
                    onTocClick = { viewModel.showToc() },
                    onSettingsClick = { viewModel.showSettings() },
                    onToggleBookmark = { viewModel.toggleBookmark() },
                    readerBgColor = readerBgColor,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        // Bottom bar — shown when controls are visible.
        // It shows either the normal progress bar or the search helper bar (if in search nav mode).
        val showBottomBar = uiState.showControls && !uiState.showSearch
        AnimatedVisibility(
            visible = showBottomBar,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ReaderBottomBar(
                    progression = uiState.progression,
                    currentPage = uiState.currentPage,
                    totalPages = uiState.totalPages,
                    readerBgColor = readerBgColor,
                    onSeekToProgression = onSeekToProgression,
                    isInSearchNavigationMode = uiState.isInSearchNavigationMode,
                    activeSearchIndex = uiState.activeSearchIndex,
                    totalSearchResults = uiState.searchResults.size,
                    onExitSearch = { viewModel.exitSearchNavigation() },
                    onPrevSearchResult = { viewModel.prevSearchResult() },
                    onNextSearchResult = { viewModel.nextSearchResult() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }

        // Reader Bottom Sheet
        if (uiState.showToc) {
            val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
            val notes by viewModel.notes.collectAsStateWithLifecycle()
            val currentLocator by viewModel.currentLocator.collectAsStateWithLifecycle()

            ReaderBottomSheet(
                tableOfContents = viewModel.tableOfContents,
                bookmarks = bookmarks,
                notes = notes,
                currentLocator = currentLocator,
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

        // Settings Bottom Sheet — wrapped in its own theme that honours the UI
        // themeMode/colorPalette setting, independent of the reader theme.
        if (uiState.showSettings) {
            val uiDarkTheme = when (settings.themeMode) {
                "Dark" -> true
                "Light" -> false
                else -> isSystemInDarkTheme()
            }
            ReaderAppTheme(darkTheme = uiDarkTheme, colorPalette = settings.colorPalette) {
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
}
