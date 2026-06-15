package com.example.readerapp.ui.features.reader.components.overlay

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Arrow_forward
import com.composables.icons.materialsymbols.outlined.Book_3
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Content_copy
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Search
import com.example.readerapp.R
import com.example.readerapp.data.local.database.dictionary.DictionaryEntry
import com.example.readerapp.data.local.database.library.BookmarkEntity
import com.example.readerapp.data.local.database.library.NoteEntity
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.ui.features.reader.ReaderDictionaryViewModel
import com.example.readerapp.ui.features.reader.ReaderNavigationRouter
import com.example.readerapp.ui.features.reader.ReaderNotesViewModel
import com.example.readerapp.ui.features.reader.ReaderSearchViewModel
import com.example.readerapp.ui.features.reader.ReaderViewModel
import com.example.readerapp.ui.features.reader.SearchResultItem
import com.example.readerapp.ui.features.reader.components.ExternalLinkBottomSheet
import com.example.readerapp.ui.features.reader.components.NoteBottomSheet
import com.example.readerapp.ui.features.reader.components.ReaderBottomSheet
import com.example.readerapp.ui.features.reader.components.ReaderSearch
import com.example.readerapp.ui.features.reader.components.SortOption
import com.example.readerapp.ui.features.reader.components.dictionary.DefinitionWebView
import com.example.readerapp.ui.features.reader.components.dictionary.DictionaryFormatter
import com.example.readerapp.ui.features.reader.components.settings.ReaderSettingsContent
import com.example.readerapp.ui.features.reader.isSamePosition
import com.example.readerapp.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator

@Composable
fun ReaderOverlay(
    viewModel: ReaderViewModel,
    searchViewModel: ReaderSearchViewModel,
    notesViewModel: ReaderNotesViewModel,
    dictionaryViewModel: ReaderDictionaryViewModel,
    router: ReaderNavigationRouter,
    bookId: String,
    onNavigateToChapter: (Link) -> Unit,
    onSeekToProgression: (Double) -> Unit,
    onNavigateToLocator: (Locator) -> Unit
) {
    val bookState by viewModel.bookState.collectAsStateWithLifecycle()
    val controlsState by viewModel.controlsState.collectAsStateWithLifecycle()
    val selectionState by notesViewModel.selectionState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.searchState.collectAsStateWithLifecycle()
    val definitionState by dictionaryViewModel.definitionState.collectAsStateWithLifecycle()
    val externalLinkState by viewModel.externalLinkState.collectAsStateWithLifecycle()

    val themeColors by viewModel.themeColors.collectAsStateWithLifecycle()
    val settings by viewModel.settingsFlow.collectAsStateWithLifecycle(
        initialValue = ReaderSettings()
    )

    val readerBgColor = themeColors.backgroundColor
    val readerTextColor = themeColors.textColor

    val sortedBookmarks by notesViewModel.sortedBookmarks.collectAsStateWithLifecycle()
    val sortedNotes by notesViewModel.sortedNotes.collectAsStateWithLifecycle()
    val sortOption by notesViewModel.sortOption.collectAsStateWithLifecycle()
    val currentLocator by viewModel.currentLocator.collectAsStateWithLifecycle()
    val jumpOrigin by viewModel.jumpOrigin.collectAsStateWithLifecycle()
    val positions by viewModel.positions.collectAsStateWithLifecycle()

    // Pass TOC to NotesViewModel so it can sort correctly
    LaunchedEffect(viewModel.tableOfContents) {
        notesViewModel.updateTableOfContents(viewModel.tableOfContents)
    }

    // Determine if bookmarked manually
    val isBookmarked = remember(sortedBookmarks, currentLocator) {
        if (currentLocator == null) false
        else sortedBookmarks.any {
            val loc = try {
                Locator.fromJSON(JSONObject(it.locatorJson))
            } catch (_: Exception) {
                null
            }
            loc != null && loc.isSamePosition(currentLocator!!)
        }
    }

    val context = LocalContext.current

    LaunchedEffect(bookState.isLoading) {
        if (!bookState.isLoading && bookState.error == null) {
            launch(Dispatchers.Default) {
                // Pre-warm material icons used in the text selection control and bottom bar.
                // This prevents main-thread jank when they are first rendered.
                MaterialSymbols.Outlined.Content_copy
                MaterialSymbols.Outlined.Search
                MaterialSymbols.Outlined.Edit
                MaterialSymbols.Outlined.Book_3
                MaterialSymbols.Outlined.Delete
                MaterialSymbols.Outlined.Arrow_back
                MaterialSymbols.Outlined.Arrow_forward
                MaterialSymbols.Outlined.Close
            }
        }
    }

    BackHandler(enabled = searchState.isInNavMode) {
        searchViewModel.exitSearchNavigation()
    }

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
            isInSearchNavigationMode = searchState.isInNavMode,
            searchQuery = searchState.query,
            isBookmarked = isBookmarked,
            readerBgColor = readerBgColor,
            readerTextColor = readerTextColor,
            jumpOrigin = jumpOrigin,
            onGoBackToOriginClick = {
                jumpOrigin?.let { origin ->
                    onNavigateToLocator(origin)
                    viewModel.clearJumpOrigin()
                }
            },
            onClearJumpOriginClick = { viewModel.clearJumpOrigin() },
            onBack = { router.navigateBack() },
            onSearchClick = { viewModel.showSearch() },
            onSearchTextClick = { viewModel.showSearch() },
            onExitSearchNavigation = { searchViewModel.exitSearchNavigation() },
            onTocClick = { viewModel.showToc() },
            onSettingsClick = { viewModel.showSettings() },
            onToggleBookmark = { currentLocator?.let { notesViewModel.toggleBookmark(it) } },
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
            onSeekToProgression = { progression ->
                viewModel.recordJumpOrigin()
                onSeekToProgression(progression)
            },
            onPrevSearchResult = { searchViewModel.prevSearchResult() },
            onNextSearchResult = { searchViewModel.nextSearchResult() },
            onCopy = { highlightText ->
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("highlight", highlightText)
                clipboard.setPrimaryClip(clip)
                notesViewModel.hideSelectionMenu()
                notesViewModel.hideViewHighlight()
            },
            onSearch = { highlightText ->
                notesViewModel.hideSelectionMenu()
                notesViewModel.hideViewHighlight()
                viewModel.showSearch()
                searchViewModel.updateSearchQuery(highlightText)
                searchViewModel.performSearch(highlightText, viewModel.publication.value, positions)
            },
            onMakeNote = {
                selectionState.selectionLocator?.let { loc ->
                    notesViewModel.addNoteAndEdit(loc, loc.title ?: bookState.chapter)
                    notesViewModel.hideSelectionMenu()
                    notesViewModel.hideViewHighlight()
                } ?: selectionState.viewingHighlight?.let { note ->
                    notesViewModel.editNote(note)
                    notesViewModel.hideViewHighlight()
                }
            },
            onDefine = { highlightText ->
                dictionaryViewModel.lookupDefinition(highlightText)
                notesViewModel.hideSelectionMenu()
                notesViewModel.hideViewHighlight()
            },
            onDelete = {
                selectionState.viewingHighlight?.let { note ->
                    notesViewModel.deleteNote(note.id)
                    notesViewModel.hideViewHighlight()
                }
            },
            onColorSelected = { colorInt ->
                selectionState.selectionLocator?.let { loc ->
                    notesViewModel.addHighlight(loc, colorInt, loc.title ?: bookState.chapter)
                    notesViewModel.hideSelectionMenu()
                    notesViewModel.hideViewHighlight()
                } ?: selectionState.viewingHighlight?.let { note ->
                    notesViewModel.updateNote(note.copy(color = colorInt))
                    notesViewModel.hideViewHighlight()
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
            sortedBookmarks = sortedBookmarks,
            sortedNotes = sortedNotes,
            sortOption = sortOption,
            onSortOptionChange = { notesViewModel.setSortOption(it) },
            currentLocator = currentLocator,
            uiDarkTheme = themeColors.backgroundColorInt == 0xFF000000.toInt(),
            settings = settings,
            searchQuery = searchState.query,
            searchResults = searchState.results,
            searchLoading = searchState.isLoading,
            searchPerformed = searchState.searchPerformed,
            getPositionLabel = { viewModel.getPositionLabel(it) },
            getChapterPageLabel = { viewModel.getChapterPageLabel(it) },
            onNavigateToChapter = onNavigateToChapter,
            onNavigateToLocator = onNavigateToLocator,
            onRecordJumpOrigin = { viewModel.recordJumpOrigin() },
            onHideToc = { viewModel.hideToc() },
            onToggleControls = { viewModel.toggleControls() },
            onAddNote = { text ->
                currentLocator?.let {
                    notesViewModel.addNote(
                        it,
                        text,
                        chapterTitle = it.title ?: bookState.chapter
                    )
                }
            },
            onDeleteBookmark = { notesViewModel.deleteBookmark(it) },
            onDeleteNote = { notesViewModel.deleteNote(it) },
            onHideSettings = { viewModel.hideSettings() },
            onUpdateSettings = { viewModel.updateSettings(it) },
            onUpdateSearchQuery = { searchViewModel.updateSearchQuery(it) },
            onPerformSearch = {
                searchViewModel.performSearch(
                    it,
                    viewModel.publication.value,
                    positions
                )
            },
            onSelectSearchResult = { searchViewModel.selectSearchResult(it) },
            onHideSearch = { viewModel.hideSearch(); searchViewModel.hideSearch() },
            onUpdateNote = { notesViewModel.updateNote(it) },
            onHideEditNote = { notesViewModel.hideEditNote() },
            onHideDefinition = { dictionaryViewModel.hideDefinition() },
            showExternalLinkMenu = externalLinkState.showMenu,
            externalLinkUrl = externalLinkState.url,
            onCopyExternalLink = { url ->
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("url", url)
                clipboard.setPrimaryClip(clip)
            },
            onOpenExternalLink = { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)
                } catch (_: Exception) {
                }
            },
            onHideExternalLinkMenu = { viewModel.hideExternalLinkMenu() }
        )
    }
}

@Composable
fun ReaderTopBarSection(
    showControls: Boolean,
    showSearch: Boolean,
    isInSearchNavigationMode: Boolean,
    searchQuery: String,
    isBookmarked: Boolean,
    readerBgColor: Color,
    readerTextColor: Color,
    jumpOrigin: Locator?,
    onGoBackToOriginClick: () -> Unit,
    onClearJumpOriginClick: () -> Unit,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onSearchTextClick: () -> Unit,
    onExitSearchNavigation: () -> Unit,
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
        Crossfade(
            targetState = isInSearchNavigationMode,
            label = "ReaderTopBarMode"
        ) { searchNavMode ->
            if (searchNavMode) {
                ReaderSearchTopBar(
                    searchQuery = searchQuery,
                    onBack = onExitSearchNavigation,
                    onSearchTextClick = onSearchTextClick,
                    onCloseSearch = onExitSearchNavigation,
                    readerBgColor = readerBgColor,
                    readerTextColor = readerTextColor
                )
            } else {
                ReaderTopBar(
                    isBookmarked = isBookmarked,
                    onBack = onBack,
                    onSearchClick = onSearchClick,
                    onTocClick = onTocClick,
                    onSettingsClick = onSettingsClick,
                    onToggleBookmark = onToggleBookmark,
                    onInfoClick = onInfoClick,
                    readerBgColor = readerBgColor,
                    readerTextColor = readerTextColor,
                    jumpOrigin = jumpOrigin,
                    onGoBackToOriginClick = onGoBackToOriginClick,
                    onClearJumpOriginClick = onClearJumpOriginClick
                )
            }
        }
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
                            onPrev = onPrevSearchResult,
                            onNext = onNextSearchResult
                        )
                    }

                    BottomBarMode.TEXT_SELECTION -> {
                        val highlightText = try {
                            Locator.fromJSON(
                                JSONObject(
                                    viewingHighlight?.locatorJson ?: selectionLocator?.toJSON()
                                        ?.toString() ?: ""
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
    sortedBookmarks: List<BookmarkEntity>,
    sortedNotes: List<NoteEntity>,
    sortOption: SortOption,
    onSortOptionChange: (SortOption) -> Unit,
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
    onNavigateToLocator: (Locator) -> Unit,
    onRecordJumpOrigin: () -> Unit,
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
    onHideDefinition: () -> Unit,
    showExternalLinkMenu: Boolean,
    externalLinkUrl: String,
    onCopyExternalLink: (String) -> Unit,
    onOpenExternalLink: (String) -> Unit,
    onHideExternalLinkMenu: () -> Unit
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
                sortedBookmarks = sortedBookmarks,
                sortedNotes = sortedNotes,
                sortOption = sortOption,
                onSortOptionChange = onSortOptionChange,
                currentLocator = currentLocator,
                getPositionLabel = getPositionLabel,
                getChapterPageLabel = getChapterPageLabel,
                onChapterClick = { link ->
                    onRecordJumpOrigin()
                    onNavigateToChapter(link)
                    onHideToc()
                    onToggleControls()
                },
                onBookmarkClick = { locator ->
                    onRecordJumpOrigin()
                    onNavigateToLocator(locator)
                    onHideToc()
                    onToggleControls()
                },
                onNoteClick = { locator ->
                    onRecordJumpOrigin()
                    onNavigateToLocator(locator)
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
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            ReaderSearch(
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

        // External Link Bottom Sheet
        if (showExternalLinkMenu) {
            ExternalLinkBottomSheet(
                url = externalLinkUrl,
                onCopy = { onCopyExternalLink(externalLinkUrl) },
                onOpenInBrowser = { onOpenExternalLink(externalLinkUrl) },
                onDismiss = onHideExternalLinkMenu
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
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val maxSheetHeight = remember(windowInfo.containerSize, density) {
        with(density) { (windowInfo.containerSize.height * 0.6f).toDp() }
    }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
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
