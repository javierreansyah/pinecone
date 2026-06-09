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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.readerapp.R
import com.example.readerapp.data.local.database.dictionary.DictionaryEntry
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.ui.features.dictionary.utils.DefinitionWebView
import com.example.readerapp.ui.features.dictionary.utils.DictionaryFormatter
import com.example.readerapp.ui.features.reader.ReaderViewModel
import com.example.readerapp.ui.features.reader.ReaderNavigationRouter
import com.example.readerapp.ui.features.reader.SearchResultItem
import com.example.readerapp.ui.features.reader.components.SearchScreen
import com.example.readerapp.ui.features.reader.components.contents.NoteBottomSheet
import com.example.readerapp.ui.features.reader.components.contents.ReaderBottomSheet
import com.example.readerapp.ui.features.reader.components.settings.ReaderSettingsContent
import com.example.readerapp.ui.theme.AppTheme
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@Composable
fun ReaderOverlay(
    viewModel: ReaderViewModel,
    router: ReaderNavigationRouter,
    bookId: String,
    onNavigateToChapter: (Link) -> Unit,
    onSeekToProgression: (Double) -> Unit
) {
    val bookState by viewModel.bookState.collectAsStateWithLifecycle()
    val controlsState by viewModel.controlsState.collectAsStateWithLifecycle()
    val selectionState by viewModel.selectionState.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val definitionState by viewModel.definitionState.collectAsStateWithLifecycle()

    val themeColors by viewModel.themeColors.collectAsStateWithLifecycle()
    val isBookmarked by viewModel.isBookmarked.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(
        initialValue = ReaderSettings()
    )

    val uiDarkTheme = when (settings.themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemInDarkTheme()
    }

    val readerBgColor = themeColors.backgroundColor
    val readerTextColor = themeColors.textColor

    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val notes by viewModel.allNotesAndHighlights.collectAsStateWithLifecycle()
    val currentLocator by viewModel.currentLocator.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Loading state
        if (bookState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        // Error state
        bookState.error?.let { error ->
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

        // Top Bar Section
        ReaderTopBarSection(
            modifier = Modifier.align(Alignment.TopCenter),
            showControls = controlsState.showControls,
            showSearch = controlsState.showSearch,
            isBookmarked = isBookmarked,
            readerBgColor = readerBgColor,
            readerTextColor = readerTextColor,
            onBack = { router.navigateBack() },
            onSearchClick = { viewModel.showSearch() },
            onTocClick = { viewModel.showToc() },
            onSettingsClick = { viewModel.showSettings() },
            onToggleBookmark = { viewModel.toggleBookmark() },
            onInfoClick = { router.navigateToBookInfo(bookId) }
        )

        // Bottom Bar Section
        ReaderBottomBarSection(
            modifier = Modifier.align(Alignment.BottomCenter),
            showControls = controlsState.showControls,
            showSearch = controlsState.showSearch,
            isInSearchNavigationMode = searchState.isInNavMode,
            activeSearchIndex = searchState.activeIndex,
            searchResultsSize = searchState.results.size,
            selectionLocator = selectionState.selectionLocator,
            viewingHighlight = selectionState.viewingHighlight,
            progression = bookState.progression,
            currentPage = bookState.currentPage,
            totalPages = bookState.totalPages,
            readerBgColor = readerBgColor,
            readerTextColor = readerTextColor,
            onSeekToProgression = onSeekToProgression,
            onExitSearchNavigation = { viewModel.exitSearchNavigation() },
            onPrevSearchResult = { viewModel.prevSearchResult() },
            onNextSearchResult = { viewModel.nextSearchResult() },
            onCopy = { highlightText ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("highlight", highlightText)
                clipboard.setPrimaryClip(clip)
                viewModel.hideSelectionMenu()
                viewModel.hideViewHighlight()
            },
            onSearch = { highlightText ->
                viewModel.hideSelectionMenu()
                viewModel.hideViewHighlight()
                viewModel.showSearch()
                viewModel.updateSearchQuery(highlightText)
                viewModel.performSearch(highlightText)
            },
            onMakeNote = {
                selectionState.selectionLocator?.let { loc ->
                    viewModel.addNoteAndEdit(loc)
                    viewModel.hideSelectionMenu()
                    viewModel.hideViewHighlight()
                } ?: selectionState.viewingHighlight?.let { note ->
                    viewModel.editNote(note)
                    viewModel.hideViewHighlight()
                }
            },
            onDefine = { highlightText ->
                viewModel.lookupDefinition(highlightText)
                viewModel.hideSelectionMenu()
                viewModel.hideViewHighlight()
            },
            onDelete = {
                selectionState.viewingHighlight?.let { note ->
                    viewModel.deleteNote(note.id)
                    viewModel.hideViewHighlight()
                }
            },
            onColorSelected = { colorInt ->
                selectionState.selectionLocator?.let { loc ->
                    viewModel.addHighlight(loc, colorInt)
                    viewModel.hideSelectionMenu()
                    viewModel.hideViewHighlight()
                } ?: selectionState.viewingHighlight?.let { note ->
                    viewModel.updateNote(note.copy(color = colorInt))
                    viewModel.hideViewHighlight()
                }
            }
        )

        // Sheets Layer
        ReaderSheetsLayer(
            showToc = controlsState.showToc,
            showSettings = controlsState.showSettings,
            showSearch = controlsState.showSearch,
            showDefinition = definitionState.showDefinition,
            editingNote = selectionState.editingNote,
            definitionWord = definitionState.definitionWord,
            definitionResults = definitionState.definitionResults,
            tableOfContents = viewModel.tableOfContents,
            bookmarks = bookmarks,
            notes = notes,
            currentLocator = currentLocator,
            uiDarkTheme = uiDarkTheme,
            settings = settings,
            searchQuery = searchState.query,
            searchResults = searchState.results,
            searchLoading = searchState.isLoading,
            searchPerformed = searchState.searchPerformed,
            getPositionLabel = { viewModel.getPositionLabel(it) },
            getChapterPageLabel = { viewModel.getChapterPageLabel(it) },
            onNavigateToChapter = onNavigateToChapter,
            onSeekToProgression = onSeekToProgression,
            onHideToc = { viewModel.hideToc() },
            onToggleControls = { viewModel.toggleControls() },
            onAddNote = { viewModel.addNote(it) },
            onDeleteBookmark = { viewModel.deleteBookmark(it) },
            onDeleteNote = { viewModel.deleteNote(it) },
            onHideSettings = { viewModel.hideSettings() },
            onUpdateSettings = { viewModel.updateSettings(it) },
            onUpdateSearchQuery = { viewModel.updateSearchQuery(it) },
            onPerformSearch = { viewModel.performSearch(it) },
            onSelectSearchResult = { viewModel.selectSearchResult(it) },
            onHideSearch = { viewModel.hideSearch() },
            onUpdateNote = { viewModel.updateNote(it) },
            onHideEditNote = { viewModel.hideEditNote() },
            onHideDefinition = { viewModel.hideDefinition() }
        )
    }
}

@Composable
fun ReaderTopBarSection(
    showControls: Boolean,
    showSearch: Boolean,
    isBookmarked: Boolean,
    readerBgColor: Color,
    readerTextColor: Color,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = showControls && !showSearch,
        enter = slideInVertically(
            initialOffsetY = { -20 }, animationSpec = tween(250)
        ) + fadeIn(animationSpec = tween(250)),
        exit = slideOutVertically(
            targetOffsetY = { -20 }, animationSpec = tween(250)
        ) + fadeOut(animationSpec = tween(250)),
        modifier = modifier
    ) {
        ReaderTopBar(
            isBookmarked = isBookmarked,
            onBack = onBack,
            onSearchClick = onSearchClick,
            onTocClick = onTocClick,
            onSettingsClick = onSettingsClick,
            onToggleBookmark = onToggleBookmark,
            onInfoClick = onInfoClick,
            readerBgColor = readerBgColor,
            readerTextColor = readerTextColor
        )
    }
}

@Composable
fun ReaderBottomBarSection(
    showControls: Boolean,
    showSearch: Boolean,
    isInSearchNavigationMode: Boolean,
    activeSearchIndex: Int?,
    searchResultsSize: Int,
    selectionLocator: Locator?,
    viewingHighlight: NoteEntity?,
    progression: Double,
    currentPage: Int?,
    totalPages: Int?,
    readerBgColor: Color,
    readerTextColor: Color,
    onSeekToProgression: (Double) -> Unit,
    onExitSearchNavigation: () -> Unit,
    onPrevSearchResult: () -> Unit,
    onNextSearchResult: () -> Unit,
    onCopy: (String) -> Unit,
    onSearch: (String) -> Unit,
    onMakeNote: () -> Unit,
    onDefine: (String) -> Unit,
    onDelete: () -> Unit,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelectionActive = selectionLocator != null || viewingHighlight != null
    val showBottomBar = (showControls && !showSearch) || isSelectionActive

    AnimatedVisibility(
        visible = showBottomBar,
        enter = slideInVertically(initialOffsetY = { 40 }, animationSpec = tween(250)) + fadeIn(
            animationSpec = tween(250)
        ),
        exit = slideOutVertically(targetOffsetY = { 40 }, animationSpec = tween(250)) + fadeOut(
            animationSpec = tween(250)
        ),
        modifier = modifier
    ) {
        ReaderBottomBarContainer(
            modifier = Modifier.navigationBarsPadding(),
            readerBgColor = readerBgColor
        ) {
            val currentMode = when {
                isSelectionActive -> BottomBarMode.TEXT_SELECTION
                isInSearchNavigationMode -> BottomBarMode.SEARCH_NAV
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
                            progression = progression,
                            currentPage = currentPage,
                            totalPages = totalPages,
                            readerTextColor = readerTextColor,
                            onSeekToProgression = onSeekToProgression
                        )
                    }

                    BottomBarMode.SEARCH_NAV -> {
                        ReaderSearchNavigator(
                            activeIndex = activeSearchIndex,
                            totalResults = searchResultsSize,
                            textColor = readerTextColor,
                            onExit = onExitSearchNavigation,
                            onPrev = onPrevSearchResult,
                            onNext = onNextSearchResult
                        )
                    }

                    BottomBarMode.TEXT_SELECTION -> {
                        val highlightText = try {
                            Locator.fromJSON(
                                JSONObject(
                                    viewingHighlight?.locatorJson ?: selectionLocator?.toJSON()?.toString() ?: ""
                                )
                            )?.text?.highlight ?: ""
                        } catch (_: Exception) {
                            ""
                        }

                        val selectedColorInt = viewingHighlight?.color

                        ReaderTextSelectionControl(
                            selectedColorInt = selectedColorInt,
                            readerTextColor = readerTextColor,
                            showDeleteOption = viewingHighlight != null && selectionLocator == null,
                            onCopy = { onCopy(highlightText) },
                            onSearch = { onSearch(highlightText) },
                            onMakeNote = onMakeNote,
                            onDefine = { onDefine(highlightText) },
                            onDelete = onDelete,
                            onColorSelected = onColorSelected
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSheetsLayer(
    showToc: Boolean,
    showSettings: Boolean,
    showSearch: Boolean,
    showDefinition: Boolean,
    editingNote: NoteEntity?,
    definitionWord: String,
    definitionResults: List<DictionaryEntry>,
    tableOfContents: List<Link>,
    bookmarks: List<BookmarkEntity>,
    notes: List<NoteEntity>,
    currentLocator: Locator?,
    uiDarkTheme: Boolean,
    settings: ReaderSettings,
    searchQuery: String,
    searchResults: List<SearchResultItem>,
    searchLoading: Boolean,
    searchPerformed: Boolean,
    getPositionLabel: (Locator) -> String,
    getChapterPageLabel: (Link) -> String,
    onNavigateToChapter: (Link) -> Unit,
    onSeekToProgression: (Double) -> Unit,
    onHideToc: () -> Unit,
    onToggleControls: () -> Unit,
    onAddNote: (String) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onHideSettings: () -> Unit,
    onUpdateSettings: (ReaderSettings) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onPerformSearch: (String) -> Unit,
    onSelectSearchResult: (Int) -> Unit,
    onHideSearch: () -> Unit,
    onUpdateNote: (NoteEntity) -> Unit,
    onHideEditNote: () -> Unit,
    onHideDefinition: () -> Unit
) {
    val settingsSheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ReaderThemedContent(uiDarkTheme = uiDarkTheme, settings = settings) {
        // Table of Contents Sheet
        if (showToc) {
            ReaderBottomSheet(
                tableOfContents = tableOfContents,
                bookmarks = bookmarks,
                notes = notes,
                currentLocator = currentLocator,
                getPositionLabel = getPositionLabel,
                getChapterPageLabel = getChapterPageLabel,
                onChapterClick = { link ->
                    onNavigateToChapter(link)
                    onHideToc()
                    onToggleControls()
                },
                onBookmarkClick = { locator ->
                    onSeekToProgression(locator.locations.totalProgression ?: 0.0)
                    onHideToc()
                    onToggleControls()
                },
                onNoteClick = { locator ->
                    onSeekToProgression(locator.locations.totalProgression ?: 0.0)
                    onHideToc()
                    onToggleControls()
                },
                onAddNote = onAddNote,
                onDeleteBookmark = onDeleteBookmark,
                onDeleteNote = onDeleteNote,
                onDismiss = onHideToc
            )
        }

        // Settings Bottom Sheet
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = onHideSettings,
                sheetState = settingsSheetState
            ) {
                ReaderSettingsContent(
                    settings = settings,
                    onSettingsChange = onUpdateSettings
                )
            }
        }

        // Full-screen search overlay
        AnimatedVisibility(
            visible = showSearch,
            enter = slideInVertically(animationSpec = tween(280)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(animationSpec = tween(220)) { it } + fadeOut(tween(180))
        ) {
            SearchScreen(
                query = searchQuery,
                results = searchResults,
                isLoading = searchLoading,
                searchPerformed = searchPerformed,
                onQueryChange = onUpdateSearchQuery,
                onSearch = onPerformSearch,
                onResultClick = onSelectSearchResult,
                onClose = onHideSearch
            )
        }

        // Edit Note Bottom Sheet
        editingNote?.let { note ->
            NoteBottomSheet(
                note = note,
                onUpdateNote = onUpdateNote,
                onDeleteNote = onDeleteNote,
                onDismiss = onHideEditNote
            )
        }

        // Definition Bottom Sheet
        if (showDefinition) {
            ReaderDefinitionBottomSheet(
                definitionWord = definitionWord,
                definitionResults = definitionResults,
                onDismiss = onHideDefinition
            )
        }
    }
}

@Composable
fun ReaderThemedContent(
    uiDarkTheme: Boolean,
    settings: ReaderSettings,
    content: @Composable () -> Unit
) {
    AppTheme(
        darkTheme = uiDarkTheme,
        colorPalette = settings.colorPalette,
        themeContrast = settings.themeContrast,
        content = content
    )
}

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderDefinitionBottomSheet(
    definitionWord: String,
    definitionResults: List<DictionaryEntry>,
    onDismiss: () -> Unit
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
