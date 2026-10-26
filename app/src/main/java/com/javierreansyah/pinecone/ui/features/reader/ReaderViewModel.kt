package com.javierreansyah.pinecone.ui.features.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.FormatPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.local.preferences.decodeReadingOverrides
import com.javierreansyah.pinecone.data.local.preferences.encodeReadingOverrides
import com.javierreansyah.pinecone.data.local.preferences.epubImageFilterCss
import com.javierreansyah.pinecone.data.local.preferences.readingThemeIsDark
import com.javierreansyah.pinecone.data.local.preferences.withReadingPreferencesFrom
import com.javierreansyah.pinecone.data.publication.PublicationFormats
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.data.publication.conversion.GeneratedEpubLocations
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.data.repository.library.PublicationRepository
import com.javierreansyah.pinecone.data.repository.library.ReadingRepository
import com.javierreansyah.pinecone.data.repository.library.readingSessionToken
import com.javierreansyah.pinecone.reader.epub.toEpubPreferences
import com.javierreansyah.pinecone.reader.pdf.MuPdfPreferences
import com.javierreansyah.pinecone.reader.pdf.toMuPdfPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Locator.Companion.fromJSON
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.epub.pageList
import org.readium.r2.shared.publication.services.positions

data class BookState(
    val title: String = "",
    val chapter: String? = null,
    val progression: Double = 0.0,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val sourceIsPdf: Boolean = false,
    val readMode: Boolean = false,
    val converting: Boolean = false,
    val conversionId: String? = null,
    val conversionPage: Int = 0,
    val conversionTotal: Int = 0,
    val conversionError: String? = null,
    val coverPath: String? = null,
)

data class ControlsState(
    val showControls: Boolean = false,
    val showToc: Boolean = false,
    val showSettings: Boolean = false,
    val showSearch: Boolean = false,
    val contentsInitialTab: Int = 0,
)

data class ExternalLinkState(
    val showMenu: Boolean = false,
    val url: String = "",
)

@HiltViewModel(assistedFactory = ReaderViewModel.Factory::class)
class ReaderViewModel @dagger.assisted.AssistedInject constructor(
    private val application: Application,
    @dagger.assisted.Assisted private val bookId: String,
    private val repository: LibraryRepository,
    private val publications: PublicationRepository,
    private val operations: com.javierreansyah.pinecone.data.repository.library.PublicationOperations,
    private val reading: ReadingRepository,
    private val readerPreferences: ReaderPreferences,
    private val startup: com.javierreansyah.pinecone.data.StartupCoordinator,
) : ViewModel() {
    private val initialSystemDark = application.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES

    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication.asStateFlow()
    val profile: MutableStateFlow<ReaderProfile> = MutableStateFlow(ReaderProfile.EPUB)
    val activeRendition: MutableStateFlow<String> = MutableStateFlow("original")
    private var conversionJob: Job? = null
    private var currentConversionId: String? = null
    private var openJob: Job? = null

    private val _positions = MutableStateFlow<List<Locator>>(emptyList())
    val positions: StateFlow<List<Locator>> = _positions.asStateFlow()

    var initialLocator: Locator? = null
        private set
    private val retiredPublications = mutableListOf<Publication>()

    fun onNavigatorDetached() {
        retiredPublications.forEach(Publication::close)
        retiredPublications.clear()
    }

    private val _bookState = MutableStateFlow(BookState())
    val bookState: StateFlow<BookState> = _bookState.asStateFlow()

    private val _controlsState = MutableStateFlow(ControlsState())
    val controlsState: StateFlow<ControlsState> = _controlsState.asStateFlow()

    private val _externalLinkState = MutableStateFlow(ExternalLinkState())
    val externalLinkState: StateFlow<ExternalLinkState> = _externalLinkState.asStateFlow()

    private val _currentLocator = MutableStateFlow<Locator?>(null)
    val currentLocator: StateFlow<Locator?> = _currentLocator.asStateFlow()

    val jumpOrigin: StateFlow<Locator?> = repository.getBookFlow(bookId)
        .map { details ->
            val json = details?.book?.jumpOriginLocatorJson
            if (!json.isNullOrBlank()) {
                try {
                    fromJSON(JSONObject(json))
                } catch (_: Exception) {
                    null
                }
            } else null
        }
        .stateIn(viewModelScope, WhileSubscribed(5000), null)

    private val settingsMutex = Mutex()
    private val readingOverrides = MutableStateFlow<ReaderSettings?>(null)
    val useBookSettings: StateFlow<Boolean> =
        readingOverrides.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val settingsFlow: SharedFlow<ReaderSettings> =
        combine(readerPreferences.readerSettings, readingOverrides) { global, local ->
            if (local == null) global else global.withReadingPreferencesFrom(local)
        }
            .distinctUntilChanged()
            // Share the merge without emitting default settings before DataStore has loaded.
            .shareIn(viewModelScope, SharingStarted.Eagerly, replay = 1)

    val brightness: StateFlow<Float> =
        settingsFlow.map { if (it.autoBrightness) -1.0f else it.brightness }
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1.0f)

    val systemDarkThemeFlow: MutableStateFlow<Boolean> = MutableStateFlow(initialSystemDark)

    val pdfPreferences: StateFlow<MuPdfPreferences> =
        combine(settingsFlow, systemDarkThemeFlow) { settings, systemDark ->
            settings.formatPreferences.pdf.toMuPdfPreferences(settings.readingThemeIsDark(systemDark))
        }.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            FormatPreferences().pdf.toMuPdfPreferences(initialSystemDark)
        )

    val imageFilterCss: StateFlow<String> =
        combine(settingsFlow, systemDarkThemeFlow) { settings, systemDark ->
            settings.epubImageFilterCss(systemDark)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val themeSettings = settingsFlow
        .map(ReaderSettings::toReaderThemeSettings)
        .distinctUntilChanged()

    val themeColors: StateFlow<PineconeThemeColors> = combine(
        themeSettings, systemDarkThemeFlow,
    ) { settings, isSystemDark ->
        settings.resolveColors(isSystemDark)
    }.stateIn(
        viewModelScope,
        WhileSubscribed(5000),
        ReaderThemeSettings().resolveColors(initialSystemDark),
    )

    val epubPreferences: StateFlow<EpubPreferences> = combine(
        settingsFlow, systemDarkThemeFlow,
    ) { settings, isDark ->
        settings.toEpubPreferences(isDark)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ReaderSettings().toEpubPreferences(initialSystemDark),
    )

    val tableOfContents: List<Link>
        get() = _publication.value?.let { publication ->
            publication.tableOfContents.ifEmpty {
                if (profile.value == ReaderProfile.AUDIO || profile.value == ReaderProfile.IMAGE) {
                    publication.readingOrder.mapIndexed { index, link ->
                        link.copy(
                            title = link.title ?: application.getString(
                                if (profile.value == ReaderProfile.AUDIO) R.string.reader_track_label else R.string.reader_page_label,
                                index + 1
                            )
                        )
                    }
                } else emptyList()
            }
        } ?: emptyList()

    init {
        viewModelScope.launch {
            startup.awaitReady()
            var observed = false
            var restoring = false
            combine(
                repository.getBookFlow(bookId),
                operations.libraryState,
                reading.renditionFlow(bookId, "read-mode")
            ) { details, runtime, readMode ->
                ReaderIdentity(
                    runtime.first, runtime.second, details?.book?.activeRendition,
                    if (details?.book?.activeRendition == "read-mode") readMode?.sha256 else details?.book?.filePath
                )
            }.distinctUntilChanged().collect { identity ->
                if (identity.maintenance) {
                    openJob?.cancel()
                    _publication.value?.let { retiredPublications += it }
                    _publication.value = null
                    _bookState.update { it.copy(isLoading = true) }
                    restoring = true
                } else {
                    if (observed && (restoring || _publication.value != null || openJob?.isActive == true)) openBook()
                    restoring = false
                }
                observed = true
            }
        }
        viewModelScope.launch {
            startup.awaitReady()
            operations.conversion(bookId).distinctUntilChanged().collect { operation ->
                currentConversionId = operation?.id
                _bookState.update { state ->
                    state.copy(
                        converting = operation != null && !operation.state.terminal,
                        conversionId = operation?.id,
                        conversionPage = operation?.completed ?: 0,
                        conversionTotal = operation?.total ?: 0,
                        conversionError = if (operation?.state in listOf(
                                com.javierreansyah.pinecone.data.model.OperationState.FAILED,
                                com.javierreansyah.pinecone.data.model.OperationState.INPUT_REQUIRED
                            )
                        ) application.getString(R.string.reader_conversion_failed) else state.conversionError,
                    )
                }
            }
        }
        viewModelScope.launch {
            startup.awaitReady()
            settingsFlow.map { it.jumpHistoryMode }.distinctUntilChanged().collect { mode ->
                if (mode == "disabled") {
                    reading.clearJumpOrigin(bookId, _publication.value?.readingSessionToken())
                }
            }
        }
    }

    fun openBook() {
        val previous = openJob
        previous?.cancel()
        openJob = viewModelScope.launch {
            previous?.join()
            _publication.value?.let { retiredPublications += it }
            _publication.value = null
            _positions.value = emptyList()
            _currentLocator.value = null
            _bookState.update { it.copy(isLoading = true, error = null) }
            var acquired: Publication? = null
            try {
                startup.initialize()
                val (resolvedLocator, book) = coroutineScope {
                    val locatorDeferred = async { reading.getLastLocator(bookId) }
                    val bookDeferred = async { repository.getBook(bookId) }
                    locatorDeferred.await() to bookDeferred.await()
                }
                initialLocator = resolvedLocator

                if (book == null) {
                    val errorMsg = application.getString(R.string.publication_not_found)
                    _bookState.update { it.copy(isLoading = false, error = errorMsg) }
                    return@launch
                }

                activeRendition.value = book.book.activeRendition
                loadReadingOverrides(book.book.activeRendition)

                val pub = publications.openPublication(book)
                acquired = pub
                if (pub == null) {
                    _bookState.update {
                        it.copy(
                            isLoading = false,
                            error = application.getString(R.string.reader_open_failed)
                        )
                    }
                    return@launch
                }

                profile.value = PublicationFormats.profile(pub)
                _positions.value =
                    if (profile.value == ReaderProfile.AUDIO) emptyList() else pub.positions()
                _publication.value = pub
                acquired = null

                val sourceIsPdf =
                    reading.rendition(bookId, "original")?.profile == ReaderProfile.PDF.name
                _bookState.update {
                    it.copy(
                        isLoading = false, title = book.book.title,
                        sourceIsPdf = sourceIsPdf,
                        readMode = book.book.activeRendition == "read-mode",
                        coverPath = book.book.coverPath,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                _bookState.update {
                    it.copy(
                        isLoading = false,
                        error = application.getString(R.string.reader_open_failed)
                    )
                }
            } finally {
                acquired?.close()
            }
        }
    }

    private var justJumped = false

    private fun getChapterTitle(locator: Locator): String? {
        val href = locator.href.toString().substringBefore("#")
        GeneratedEpubLocations.chapterIndex(
            tableOfContents.map { it.href.toString() }, locator.href.toString(),
            locator.locations["cssSelector"] as? String, locator.locations.fragments
        )?.let { index ->
            return tableOfContents.getOrNull(index)?.title
        }
        return findTitleInLinks(href, tableOfContents)
    }

    private fun findTitleInLinks(href: String, links: List<Link>): String? {
        for ((href1, _, title, _, _, _, _, _, _, _, _, children) in links) {
            if (href1.toString().substringBefore("#") == href) {
                return title
            }
            val childTitle = findTitleInLinks(href, children)
            childTitle?.let { return it }
        }
        return null
    }

    fun onLocatorChanged(locator: Locator) {
        val oldLocator = _currentLocator.value
        _currentLocator.value = locator

        if (oldLocator != null) {
            viewModelScope.launch {
                val jumpHistoryMode = settingsFlow.first().jumpHistoryMode
                if (jumpHistoryMode == "disabled") {
                    reading.clearJumpOrigin(bookId, _publication.value?.readingSessionToken())
                } else if (jumpHistoryMode == "page_turn") {
                    if (justJumped) {
                        justJumped = false
                    } else {
                        val details = repository.getBook(bookId)
                        val jumpOriginJson = details?.book?.jumpOriginLocatorJson
                        if (jumpOriginJson != null) {
                            val origin = fromJSON(JSONObject(jumpOriginJson))
                            if (origin != null && !locator.isSamePosition(origin) && !locator.isSamePosition(
                                    oldLocator,
                                )
                            ) {
                                reading.clearJumpOrigin(
                                    bookId,
                                    _publication.value?.readingSessionToken()
                                )
                            }
                        }
                    }
                }
            }
        }

        val allPositions = _positions.value
        val pageIndex = allPositions.findIndexForLocator(locator)
        val displayedPage: Int? = if (activeRendition.value == "read-mode") readModePage(locator)
        else if (pageIndex != -1) pageIndex + 1 else null
        val displayedTotal: Int? = if (activeRendition.value == "read-mode") {
            _publication.value?.pageList?.size?.takeIf { count -> count > 0 }
        } else allPositions.size.takeIf { count -> count > 0 }

        _bookState.update {
            it.copy(
                chapter = locator.title ?: getChapterTitle(locator),
                progression = locator.locations.totalProgression ?: 0.0,
                currentPage = if (activeRendition.value == "read-mode") {
                    (displayedPage ?: it.currentPage ?: 1).coerceIn(
                        1,
                        displayedTotal ?: Int.MAX_VALUE
                    )
                } else displayedPage,
                totalPages = displayedTotal,
            )
        }
    }

    fun locatorForProgression(targetProgression: Double): Locator? =
        _positions.value.findClosestByProgression(targetProgression)

    fun getPositionLabel(locator: Locator): String {
        val posIndex =
            if (activeRendition.value == "read-mode") readModePage(locator)?.minus(1) ?: -1
            else _positions.value.findIndexForLocator(locator)

        return when {
            posIndex != -1 -> application.resources.getQuantityString(
                R.plurals.reader_page_num, posIndex + 1, posIndex + 1,
            )

            locator.locations.totalProgression != null -> application.getString(
                R.string.reader_position_at,
                "${(locator.locations.totalProgression!! * 100).toInt()}%",
            )

            else -> ""
        }
    }

    // Readium positions are byte-size estimates, not rendered screens. Inline
    // SVG glyph paths can make a single screen span many such positions.
    private fun readModePage(locator: Locator): Int? {
        if (activeRendition.value != "read-mode") return null
        val pageCount = _publication.value?.pageList?.size?.takeIf { it > 0 }
        return GeneratedEpubLocations.page(
            locator.href.toString(),
            locator.locations["cssSelector"] as? String,
            locator.locations.fragments,
        )?.takeIf { page -> page >= 1 && (pageCount == null || page <= pageCount) }
    }

    fun getChapterPageLabel(link: Link): String {
        GeneratedEpubLocations.page(link.href.toString())?.let { page ->
            return application.resources.getQuantityString(R.plurals.reader_page_num, page, page)
        }
        val allPositions = _positions.value
        if (allPositions.isEmpty()) return ""
        val linkHref = link.href.toString().substringBefore("#")
        val posIndex = allPositions.indexOfFirst { position ->
            position.href.toString().substringBefore("#") == linkHref
        }
        return if (posIndex != -1) application.resources.getQuantityString(
            R.plurals.reader_page_num, posIndex + 1, posIndex + 1,
        ) else ""
    }

    fun savePosition(
        locator: Locator, renditionKey: String = activeRendition.value,
        session: com.javierreansyah.pinecone.data.model.ReadingSessionToken? = _publication.value?.readingSessionToken()
    ) {
        viewModelScope.launch {
            reading.saveReadingPosition(bookId, locator, renditionKey, session)
        }
    }

    fun togglePdfReadMode(): Unit = preparePdfRendition(forceReparse = false)

    fun reparsePdf() {
        hideSettings()
        preparePdfRendition(forceReparse = true)
    }

    private fun preparePdfRendition(forceReparse: Boolean) {
        if (!_bookState.value.sourceIsPdf || conversionJob?.isActive == true) return
        conversionJob = viewModelScope.launch {
            try {
                _bookState.update { it.copy(conversionError = null) }
                val session = _publication.value?.readingSessionToken()
                _currentLocator.value?.let {
                    reading.saveReadingPosition(
                        bookId,
                        it,
                        activeRendition.value,
                        session
                    )
                }
                if (!forceReparse && activeRendition.value == "read-mode") {
                    reading.activateMappedRendition(bookId, "original", session)
                } else if (forceReparse || !publications.tryReuseReadMode(bookId) {
                        reading.activateMappedRendition(bookId, "read-mode", session)
                    }) {
                    currentConversionId = operations.enqueueReadMode(bookId, forceReparse)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                timber.log.Timber.e(error, "Could not request PDF mode")
                _bookState.update { it.copy(conversionError = application.getString(R.string.reader_conversion_failed)) }
            }
        }
    }

    internal fun withReadModeChapter(locator: Locator): Locator =
        if (activeRendition.value == "read-mode" && GeneratedEpubLocations.isContent(locator.href.toString())) {
            locator.copy(title = getChapterTitle(locator) ?: locator.title)
        } else locator

    fun cancelConversion() {
        currentConversionId?.let { id -> viewModelScope.launch { operations.cancel(id) } }
    }

    fun dismissConversionError() {
        _bookState.update { it.copy(conversionError = null) }
    }

    fun recordJumpOrigin() {
        val current = _currentLocator.value ?: return
        viewModelScope.launch {
            val jumpHistoryMode = settingsFlow.first().jumpHistoryMode
            if (jumpHistoryMode != "disabled") {
                reading.saveJumpOrigin(bookId, current, _publication.value?.readingSessionToken())
                justJumped = true
            }
        }
    }

    fun clearJumpOrigin() {
        viewModelScope.launch {
            reading.clearJumpOrigin(bookId, _publication.value?.readingSessionToken())
        }
    }

    fun toggleControls() {
        _controlsState.update { it.copy(showControls = !it.showControls) }
    }

    fun showExternalLinkMenu(url: String) {
        _externalLinkState.update { it.copy(showMenu = true, url = url) }
    }

    fun hideExternalLinkMenu() {
        _externalLinkState.update { it.copy(showMenu = false) }
    }

    fun showToc() {
        _controlsState.update {
            it.copy(
                showToc = true,
                showSettings = false,
                contentsInitialTab = 0
            )
        }
    }

    fun hideToc() {
        _controlsState.update { it.copy(showToc = false) }
    }

    fun showSettings() {
        _controlsState.update { it.copy(showSettings = true, showToc = false) }
    }

    fun hideSettings() {
        _controlsState.update { it.copy(showSettings = false) }
    }

    fun showSearch() {
        _controlsState.update { it.copy(showSearch = true) }
    }

    fun hideSearch() {
        _controlsState.update { it.copy(showSearch = false) }
    }

    fun updateSettings(transform: (ReaderSettings) -> ReaderSettings) {
        viewModelScope.launch {
            settingsMutex.withLock {
                if (readingOverrides.value != null) {
                    val global = readerPreferences.readerSettings.first()
                    val current =
                        global.withReadingPreferencesFrom(requireNotNull(readingOverrides.value))
                    val local = ReaderSettings().withReadingPreferencesFrom(transform(current))
                    readingOverrides.value = local
                    reading.setReadingOverrides(
                        bookId,
                        activeRendition.value,
                        local.encodeReadingOverrides(),
                        _publication.value?.readingSessionToken(),
                    )
                } else {
                    readerPreferences.updateSettings(transform)
                }
            }
        }
    }

    private suspend fun loadReadingOverrides(key: String) {
        val encoded = reading.rendition(bookId, key)?.preferencesJson
        readingOverrides.value = decodeReadingOverrides(encoded)
    }

    fun setUseBookSettings(enabled: Boolean) {
        viewModelScope.launch {
            settingsMutex.withLock {
                val global = readerPreferences.readerSettings.first()
                val current =
                    readingOverrides.value?.let { global.withReadingPreferencesFrom(it) } ?: global
                readingOverrides.value =
                    if (enabled) ReaderSettings().withReadingPreferencesFrom(current) else null
                reading.setReadingOverrides(
                    bookId,
                    activeRendition.value,
                    readingOverrides.value?.encodeReadingOverrides()
                        ?: "{}",
                    _publication.value?.readingSessionToken(),
                )
            }
        }
    }

    private fun closeBook() {
        onNavigatorDetached()
        _publication.value?.close()
        _publication.value = null
    }

    override fun onCleared() {
        openJob?.cancel()
        closeBook()
    }


    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(bookId: String): ReaderViewModel
    }

    private data class ReaderIdentity(
        val generation: Long,
        val maintenance: Boolean,
        val rendition: String?,
        val asset: String?
    )
}
