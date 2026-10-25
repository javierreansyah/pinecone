package com.javierreansyah.pinecone.ui.features.reader

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.FormatPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.local.preferences.epubImageFilterCss
import com.javierreansyah.pinecone.data.local.preferences.readingThemeIsDark
import com.javierreansyah.pinecone.data.local.preferences.withReadingPreferencesFrom
import com.javierreansyah.pinecone.data.publication.PublicationFormats
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.data.publication.conversion.GeneratedEpubLocations
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.reader.audio.AudioReadingSession
import com.javierreansyah.pinecone.reader.epub.toEpubPreferences
import com.javierreansyah.pinecone.reader.pdf.MuPdfPreferences
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch
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
    private val readerPreferences: ReaderPreferences,
    val audioSession: AudioReadingSession,
) : ViewModel() {
    private val initialSystemDark = application.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES

    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication.asStateFlow()
    val profile: MutableStateFlow<ReaderProfile> = MutableStateFlow(ReaderProfile.EPUB)
    val activeRendition: MutableStateFlow<String> = MutableStateFlow("original")
    private var conversionJob: Job? = null
    private var audioLocatorJob: Job? = null

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
            settings.formatPreferences.pdf.resolveTheme(settings.readingThemeIsDark(systemDark))
        }.stateIn(
            viewModelScope, SharingStarted.Eagerly,
            FormatPreferences().pdf.resolveTheme(initialSystemDark)
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
            settingsFlow.map { it.jumpHistoryMode }.distinctUntilChanged().collect { mode ->
                if (mode == "disabled") {
                    repository.clearJumpOrigin(bookId)
                }
            }
        }
    }

    fun openBook() {
        viewModelScope.launch {
            _bookState.update { it.copy(isLoading = true, error = null) }

            try {
                val (resolvedLocator, book) = coroutineScope {
                    val locatorDeferred = async { repository.getLastLocator(bookId) }
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

                val pub = repository.openPublication(book)
                if (pub == null) {
                    _bookState.update {
                        it.copy(
                            isLoading = false,
                            error = application.getString(R.string.reader_open_failed)
                        )
                    }
                    return@launch
                }

                _publication.value = pub
                profile.value = PublicationFormats.profile(pub)

                viewModelScope.launch {
                    val p =
                        if (profile.value == ReaderProfile.AUDIO) emptyList() else pub.positions()
                    _positions.value = p
                    _currentLocator.value?.let { onLocatorChanged(it) }
                }

                val sourceIsPdf =
                    repository.rendition(bookId, "original")?.profile == ReaderProfile.PDF.name
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
        for (link in links) {
            if (link.href.toString().substringBefore("#") == href) {
                return link.title
            }
            val childTitle = findTitleInLinks(href, link.children)
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
                    repository.clearJumpOrigin(bookId)
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
                                repository.clearJumpOrigin(bookId)
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

    fun savePosition(locator: Locator, renditionKey: String = activeRendition.value) {
        viewModelScope.launch {
            repository.saveReadingPosition(bookId, locator, renditionKey)
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
            val old = _publication.value ?: return@launch
            var source = _currentLocator.value
            val destination =
                if (forceReparse || activeRendition.value == "original") "read-mode" else "original"
            _bookState.update { it.copy(conversionError = null) }
            var next: Publication? = null
            try {
                source?.let { repository.saveReadingPosition(bookId, it, activeRendition.value) }
                if (destination == "read-mode") {
                    repository.createReadMode(
                        bookId,
                        forceReparse = forceReparse,
                        onConversionStart = {
                            _bookState.update {
                                it.copy(
                                    converting = true,
                                    conversionPage = 0,
                                    conversionTotal = 0
                                )
                            }
                        },
                    ) { page, total ->
                        _bookState.update {
                            it.copy(
                                conversionPage = page,
                                conversionTotal = total
                            )
                        }
                    }
                }
                // The PDF remains interactive during conversion. Map the position the
                // reader has reached now, rather than the position where it started.
                source = _currentLocator.value ?: source
                source?.let { repository.saveReadingPosition(bookId, it, activeRendition.value) }
                repository.activateRendition(bookId, destination)
                val item = requireNotNull(repository.getBook(bookId))
                next = requireNotNull(repository.openPublication(item))
                val target = requireNotNull(repository.rendition(bookId, destination))
                val sourceMap = repository.rendition(bookId, "read-mode")?.sourceMapJson?.let {
                    kotlinx.serialization.json.Json.decodeFromString<List<com.javierreansyah.pinecone.data.publication.conversion.SourcePage>>(
                        it
                    )
                }.orEmpty()

                fun mappedPage(locator: Locator): Int =
                    if (locator.mediaType.toString() == "application/pdf") {
                        val page = sourcePage(locator)
                        sourceMap.firstOrNull { it.sourceHref == locator.href.toString() && it.sourcePage == page }?.page
                            ?: page
                    } else sourcePage(locator)

                val sourcePage = source?.let(::mappedPage) ?: 1
                val stored = target.lastLocatorJson?.let { fromJSON(JSONObject(it)) }
                val nextLocator = stored?.takeIf { mappedPage(it) == sourcePage }
                    ?: if (destination == "read-mode") {
                        val resource =
                            sourceMap.firstOrNull { it.page == sourcePage }?.href?.substringBefore(
                                '#'
                            )
                        next.readingOrder.find {
                            if (resource != null) GeneratedEpubLocations.sameContentResource(
                                it.href.toString(),
                                resource
                            )
                            else GeneratedEpubLocations.isContent(it.href.toString())
                        }?.let(next::locatorFromLink)?.copyWithLocations(
                            fragments = listOf("source-page-$sourcePage"),
                        )
                    } else {
                        val mapping = sourceMap.firstOrNull { it.page == sourcePage }
                        val link =
                            next.readingOrder.find { it.href.toString() == mapping?.sourceHref }
                                ?: next.readingOrder.firstOrNull()
                        link?.let(next::locatorFromLink)?.copyWithLocations(
                            fragments = listOf("page=${mapping?.sourcePage ?: sourcePage}"),
                            position = sourcePage,
                        )
                    }
                val nextPositions = next.positions()
                loadReadingOverrides(destination)
                initialLocator = nextLocator
                activeRendition.value = destination
                profile.value = PublicationFormats.profile(next)
                _currentLocator.value = null
                _positions.value = nextPositions
                retiredPublications += old
                _publication.value = next
                next = null
                _bookState.update {
                    it.copy(
                        readMode = destination == "read-mode",
                        converting = false
                    )
                }
            } catch (cancelled: CancellationException) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                    repository.activateRendition(
                        bookId,
                        activeRendition.value
                    )
                }
                throw cancelled
            } catch (failure: Exception) {
                repository.activateRendition(bookId, activeRendition.value)
                timber.log.Timber.e(failure, "PDF conversion failed")
                _bookState.update { it.copy(conversionError = application.getString(R.string.reader_conversion_failed)) }
            } finally {
                next?.close()
                _bookState.update { it.copy(converting = false) }
            }
        }
    }

    private fun sourcePage(locator: Locator): Int =
        GeneratedEpubLocations.page(
            locator.href.toString(), locator.locations["cssSelector"] as? String,
            locator.locations.fragments
        )
            ?: locator.locations.fragments.firstNotNullOfOrNull {
                it.removePrefix("page=").toIntOrNull()
            } ?: locator.locations.position ?: 1

    internal fun withReadModeChapter(locator: Locator): Locator =
        if (activeRendition.value == "read-mode" && GeneratedEpubLocations.isContent(locator.href.toString())) {
            locator.copy(title = getChapterTitle(locator) ?: locator.title)
        } else locator

    fun cancelConversion() {
        conversionJob?.cancel()
    }

    fun dismissConversionError() {
        _bookState.update { it.copy(conversionError = null) }
    }

    fun startAudio() {
        application.startService(
            android.content.Intent(
                application,
                com.javierreansyah.pinecone.reader.audio.AudioPlaybackService::class.java
            )
                .putExtra("bookId", bookId)
        )
        _controlsState.update { it.copy(showControls = true) }
        audioLocatorJob?.cancel()
        audioLocatorJob = viewModelScope.launch {
            audioSession.locator.collect { locator ->
                if (locator != null && audioSession.state.value.bookId == bookId) onLocatorChanged(
                    locator
                )
            }
        }
    }

    fun audioGo(locator: Locator) {
        audioSession.go(locator)
    }

    fun audioGo(link: Link) {
        audioSession.go(link)
    }

    fun recordJumpOrigin() {
        val current = _currentLocator.value ?: return
        viewModelScope.launch {
            val jumpHistoryMode = settingsFlow.first().jumpHistoryMode
            if (jumpHistoryMode != "disabled") {
                repository.saveJumpOrigin(bookId, current)
                justJumped = true
            }
        }
    }

    fun clearJumpOrigin() {
        viewModelScope.launch {
            repository.clearJumpOrigin(bookId)
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

    fun showBookmarks() {
        _controlsState.update {
            it.copy(
                showToc = true,
                showSettings = false,
                contentsInitialTab = 1
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
                    val current = global.withReadingPreferencesFrom(requireNotNull(readingOverrides.value))
                    val local = ReaderSettings().withReadingPreferencesFrom(transform(current))
                    readingOverrides.value = local
                    repository.setReadingOverrides(
                        bookId,
                        activeRendition.value,
                        kotlinx.serialization.json.Json.encodeToString(local),
                    )
                } else {
                    readerPreferences.updateSettings(transform)
                }
            }
        }
    }

    private suspend fun loadReadingOverrides(key: String) {
        val encoded = repository.rendition(bookId, key)?.preferencesJson
        readingOverrides.value = encoded?.takeUnless { it == "{}" }
            ?.let { kotlinx.serialization.json.Json.decodeFromString<ReaderSettings>(it) }
    }

    fun setUseBookSettings(enabled: Boolean) {
        viewModelScope.launch {
            settingsMutex.withLock {
                val global = readerPreferences.readerSettings.first()
                val current = readingOverrides.value?.let { global.withReadingPreferencesFrom(it) } ?: global
                readingOverrides.value = if (enabled) ReaderSettings().withReadingPreferencesFrom(current) else null
                repository.setReadingOverrides(
                    bookId,
                    activeRendition.value,
                    readingOverrides.value?.let { kotlinx.serialization.json.Json.encodeToString(it) } ?: "{}",
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
        closeBook()
    }


    @dagger.assisted.AssistedFactory
    interface Factory {
        fun create(bookId: String): ReaderViewModel
    }
}
