package com.example.readerapp.ui.features.reader.components.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.data.local.database.dictionary.DictionaryEntry
import com.example.readerapp.ui.features.dictionary.utils.DefinitionWebView
import com.example.readerapp.ui.features.dictionary.utils.DictionaryFormatter
import com.example.readerapp.ui.features.reader.ReaderViewModel
import com.example.readerapp.ui.features.reader.components.contents.NoteBottomSheet
import com.example.readerapp.ui.features.reader.components.contents.ReaderBottomSheet
import com.example.readerapp.ui.features.reader.components.settings.ReaderSettingsContent
import com.example.readerapp.ui.features.reader.components.SearchScreen
import com.example.readerapp.ui.theme.AppTheme
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.json.JSONObject

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
        initialValue = ReaderSettings()
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
        "Warm" -> Color(0xFF121212)
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
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Error state
        uiState.error?.let { error ->
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        // Animated controls overlay — top bar
        AnimatedVisibility(
            visible = uiState.showControls && !uiState.showSearch, enter = slideInVertically(
                initialOffsetY = { -20 }, animationSpec = tween(250)
            ) + fadeIn(animationSpec = tween(250)), exit = slideOutVertically(
                targetOffsetY = { -20 }, animationSpec = tween(250)
            ) + fadeOut(animationSpec = tween(250)), modifier = Modifier.align(Alignment.TopCenter)
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

        // Animated controls overlay — unified bottom bar
        val menuLocator = uiState.selectionLocator
        val menuHighlight = uiState.viewingHighlight
        val isSelectionActive = menuLocator != null || menuHighlight != null
        val showBottomBar = (uiState.showControls && !uiState.showSearch) || isSelectionActive

        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(250)) + fadeIn(
                animationSpec = tween(250)
            ),
            exit = slideOutVertically(targetOffsetY = { 40 }, animationSpec = tween(250)) + fadeOut(
                animationSpec = tween(250)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            ReaderBottomBarContainer(
                modifier = Modifier.navigationBarsPadding(), readerBgColor = readerBgColor
            ) {
                val currentMode = when {
                    isSelectionActive -> BottomBarMode.TEXT_SELECTION
                    uiState.isInSearchNavigationMode -> BottomBarMode.SEARCH_NAV
                    else -> BottomBarMode.PROGRESS
                }

                val modeArray = remember { arrayOf(currentMode) }
                if (showBottomBar) {
                    modeArray[0] = currentMode
                }

                Crossfade(targetState = modeArray[0], label = "BottomBarMode") { mode ->
                    when (mode) {
                        BottomBarMode.PROGRESS -> {
                            ReaderProgressTracker(
                                progression = uiState.progression,
                                currentPage = uiState.currentPage,
                                totalPages = uiState.totalPages,
                                readerTextColor = readerTextColor,
                                onSeekToProgression = onSeekToProgression
                            )
                        }

                        BottomBarMode.SEARCH_NAV -> {
                            ReaderSearchNavigator(
                                activeIndex = uiState.activeSearchIndex,
                                totalResults = uiState.searchResults.size,
                                textColor = readerTextColor,
                                onExit = { viewModel.exitSearchNavigation() },
                                onPrev = { viewModel.prevSearchResult() },
                                onNext = { viewModel.nextSearchResult() })
                        }

                        BottomBarMode.TEXT_SELECTION -> {
                            val highlightText = try {
                                Locator.fromJSON(
                                    JSONObject(
                                        menuHighlight?.locatorJson ?: menuLocator?.toJSON()
                                            ?.toString() ?: ""
                                    )
                                )?.text?.highlight ?: ""
                            } catch (_: Exception) {
                                ""
                            }

                            val context = LocalContext.current
                            val selectedColorInt = menuHighlight?.color

                            ReaderTextSelectionControl(
                                selectedColorInt = selectedColorInt,
                                readerTextColor = readerTextColor,
                                showDeleteOption = menuHighlight != null && menuLocator == null,
                                onCopy = {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("highlight", highlightText)
                                    clipboard.setPrimaryClip(clip)
                                    if (menuLocator != null) viewModel.hideSelectionMenu()
                                    if (menuHighlight != null) viewModel.hideViewHighlight()
                                },
                                onSearch = {
                                    if (menuLocator != null) viewModel.hideSelectionMenu()
                                    if (menuHighlight != null) viewModel.hideViewHighlight()
                                    viewModel.showSearch()
                                    viewModel.updateSearchQuery(highlightText)
                                    viewModel.performSearch(highlightText)
                                },
                                onMakeNote = {
                                    if (menuLocator != null) {
                                        viewModel.addNoteAndEdit(menuLocator)
                                        viewModel.hideSelectionMenu()
                                        if (menuHighlight != null) viewModel.hideViewHighlight()
                                    } else if (menuHighlight != null) {
                                        viewModel.editNote(menuHighlight)
                                        viewModel.hideViewHighlight()
                                    }
                                },
                                onDefine = {
                                    viewModel.lookupDefinition(highlightText)
                                    if (menuLocator != null) viewModel.hideSelectionMenu()
                                    if (menuHighlight != null) viewModel.hideViewHighlight()
                                },
                                onDelete = {
                                    if (menuHighlight != null) {
                                        viewModel.deleteNote(menuHighlight.id)
                                        viewModel.hideViewHighlight()
                                    }
                                },
                                onColorSelected = { colorInt ->
                                    if (menuLocator != null) {
                                        viewModel.addHighlight(menuLocator, colorInt)
                                        viewModel.hideSelectionMenu()
                                        if (menuHighlight != null) viewModel.hideViewHighlight()
                                    } else if (menuHighlight != null) {
                                        viewModel.updateNote(menuHighlight.copy(color = colorInt))
                                        viewModel.hideViewHighlight()
                                    }
                                })
                        }
                    }
                }
            }
        }

        // Reader Bottom Sheet (TOC)
        if (uiState.showToc) {
            val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
            val notes by viewModel.allNotesAndHighlights.collectAsStateWithLifecycle()
            val currentLocator by viewModel.currentLocator.collectAsStateWithLifecycle()

            AppTheme(
                darkTheme = uiDarkTheme,
                colorPalette = settings.colorPalette,
                themeContrast = settings.themeContrast
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
                        onSeekToProgression(locator.locations.totalProgression ?: 0.0)
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
                    onDismiss = { viewModel.hideToc() })
            }
        }

        // Settings Bottom Sheet
        if (uiState.showSettings) {
            AppTheme(
                darkTheme = uiDarkTheme,
                colorPalette = settings.colorPalette,
                themeContrast = settings.themeContrast
            ) {
                ModalBottomSheet(
                    onDismissRequest = { viewModel.hideSettings() }, sheetState = settingsSheetState
                ) {
                    ReaderSettingsContent(
                        settings = settings, onSettingsChange = { newSettings ->
                            viewModel.updateSettings(newSettings)
                        })
                }
            }
        }

        // Full-screen search overlay
        AnimatedVisibility(
            visible = uiState.showSearch,
            enter = slideInVertically(animationSpec = tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(220)) { it } + fadeOut(tween(180))) {
            AppTheme(
                darkTheme = uiDarkTheme,
                colorPalette = settings.colorPalette,
                themeContrast = settings.themeContrast
            ) {
                SearchScreen(
                    query = uiState.searchQuery,
                    results = uiState.searchResults,
                    isLoading = uiState.searchLoading,
                    searchPerformed = uiState.searchPerformed,
                    onQueryChange = { q -> viewModel.updateSearchQuery(q) },
                    onSearch = { q -> viewModel.performSearch(q) },
                    onResultClick = { index -> viewModel.selectSearchResult(index) },
                    onClose = { viewModel.hideSearch() })
            }
        }

        // Edit Note Bottom Sheet
        uiState.editingNote?.let { note ->
            AppTheme(
                darkTheme = uiDarkTheme,
                colorPalette = settings.colorPalette,
                themeContrast = settings.themeContrast
            ) {
                NoteBottomSheet(
                    note = note,
                    onUpdateNote = { viewModel.updateNote(it) },
                    onDeleteNote = { viewModel.deleteNote(it) },
                    onDismiss = { viewModel.hideEditNote() })
            }
        }

        // Definition Bottom Sheet
        if (uiState.showDefinition) {
            AppTheme(
                darkTheme = uiDarkTheme,
                colorPalette = settings.colorPalette,
                themeContrast = settings.themeContrast
            ) {
                ReaderDefinitionBottomSheet(
                    definitionWord = uiState.definitionWord,
                    definitionResults = uiState.definitionResults,
                    onDismiss = { viewModel.hideDefinition() })
            }
        }
    }
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderDefinitionBottomSheet(
    definitionWord: String, definitionResults: List<DictionaryEntry>, onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxSheetHeight = configuration.screenHeightDp.dp * 0.6f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            if (definitionResults.isEmpty()) {
                Text(
                    text = stringResource(R.string.reader_no_definition),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            } else {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {
                    val sortedResults =
                        definitionResults.sortedWith(compareBy<DictionaryEntry> { entry ->
                            when {
                                entry.word == definitionWord -> 0
                                entry.word.firstOrNull()?.isLowerCase() == true -> 1
                                else -> 2
                            }
                        }.thenBy { it.word })

                    val combinedHtmlContent =
                        DictionaryFormatter.prepareHtmlForMultipleEntries(sortedResults)
                    DefinitionWebView(
                        htmlContent = combinedHtmlContent, modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
