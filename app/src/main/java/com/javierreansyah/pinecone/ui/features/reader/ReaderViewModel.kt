package com.javierreansyah.pinecone.ui.features.reader

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.FormatPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderPreferences
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.local.preferences.withReadingPreferencesFrom
import com.javierreansyah.pinecone.data.publication.PublicationFormats
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.data.repository.library.LibraryRepository
import com.javierreansyah.pinecone.reader.audio.AudioReadingSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Locator.Companion.fromJSON
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions

data class PineconeThemeColors(
    val backgroundColor: Color,
    val textColor: Color,
    val backgroundColorInt: Int,
    val isDarkTheme: Boolean,
)

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
)

data class ExternalLinkState(
    val showMenu: Boolean = false,
    val url: String = "",
)

class ReaderViewModel(
    private val application: Application,
    private val bookId: String,
    private val repository: LibraryRepository,
    private val readerPreferences: ReaderPreferences,
    initialSystemDark: Boolean = false,
) : ViewModel() {

    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication.asStateFlow()
    val profile: MutableStateFlow<ReaderProfile> = MutableStateFlow(ReaderProfile.EPUB)
    val activeRendition: MutableStateFlow<String> = MutableStateFlow("original")
    val audioSession: AudioReadingSession get() = (application as PineconeApplication).audioSession
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

    private val readingOverrides = MutableStateFlow<ReaderSettings?>(null)
    val useBookSettings: StateFlow<Boolean> =
        readingOverrides.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val settingsFlow: Flow<ReaderSettings> =
        combine(readerPreferences.readerSettings, readingOverrides) { global, local ->
            if (local == null) global else global.withReadingPreferencesFrom(local)
        }
    val formatSettings: StateFlow<FormatPreferences> = settingsFlow.map { it.formatPreferences }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FormatPreferences())

    val brightness: StateFlow<Float> =
        settingsFlow.map { if (it.autoBrightness) -1.0f else it.brightness }
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1.0f)

    val systemDarkThemeFlow: MutableStateFlow<Boolean> = MutableStateFlow(initialSystemDark)

    val themeColors: StateFlow<PineconeThemeColors> = combine(
        settingsFlow, systemDarkThemeFlow,
    ) { (settingsThemeMode, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, settingsPineconeThemePreset, settingsCustomBackgroundColor, settingsCustomTextColor, settingsCustomThemes), isSystemDark ->
        val customTheme =
            settingsCustomThemes.find { (itName) -> itName == settingsPineconeThemePreset }
        val uiThemeSetting = customTheme?.uiTheme ?: "Setting"
        val effectiveThemeMode =
            if ((uiThemeSetting == "Setting") || (uiThemeSetting == "setting")) settingsThemeMode else uiThemeSetting

        val uiDarkTheme = when (effectiveThemeMode) {
            "Dark" -> true
            "Light" -> false
            else -> isSystemDark
        }

        val bgColorInt = when (settingsPineconeThemePreset) {
            "Light" -> 0xFFFFFFFF.toInt()
            "Warm" -> 0xFFFAF4E8.toInt()
            "Dark" -> 0xFF000000.toInt()
            "Auto" -> if (uiDarkTheme) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            else -> try {
                settingsCustomBackgroundColor.toColorInt()
            } catch (_: Exception) {
                android.graphics.Color.WHITE
            }
        }

        val textColorInt = when (settingsPineconeThemePreset) {
            "Light" -> 0xFF000000.toInt()
            "Warm" -> 0xFF121212.toInt()
            "Dark" -> 0xFFFFFFFF.toInt()
            "Auto" -> if (uiDarkTheme) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            else -> try {
                settingsCustomTextColor.toColorInt()
            } catch (_: Exception) {
                if (uiDarkTheme) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }

        PineconeThemeColors(
            backgroundColor = Color(bgColorInt),
            textColor = Color(textColorInt),
            backgroundColorInt = bgColorInt,
            isDarkTheme = uiDarkTheme,
        )
    }.stateIn(
        viewModelScope,
        WhileSubscribed(5000),
        run {
            val bgColorInt = if (initialSystemDark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            val textColorInt = if (initialSystemDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            PineconeThemeColors(
                Color(bgColorInt),
                Color(textColorInt),
                bgColorInt,
                initialSystemDark,
            )
        },
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
            settingsFlow.collect { (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, settingsJumpHistoryMode) ->
                if (settingsJumpHistoryMode == "disabled") {
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
                    val errorMsg = application.getString(R.string.book_not_found)
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
        return findTitleInLinks(href, tableOfContents)
    }

    private fun findTitleInLinks(href: String, links: List<Link>): String? {
        for ((linkHref2, _, linkTitle, _, _, _, _, _, _, _, _, linkChildren) in links) {
            if (linkHref2.toString().substringBefore("#") == href) {
                return linkTitle
            }
            val childTitle = findTitleInLinks(href, linkChildren)
            childTitle?.let { return it }
        }
        return null
    }

    fun onLocatorChanged(locator: Locator) {
        val oldLocator = _currentLocator.value
        _currentLocator.value = locator

        if (oldLocator != null) {
            viewModelScope.launch {
                val (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, settingsJumpHistoryMode2) = settingsFlow.first()
                if (settingsJumpHistoryMode2 == "disabled") {
                    repository.clearJumpOrigin(bookId)
                } else if (settingsJumpHistoryMode2 == "page_turn") {
                    if (justJumped) {
                        justJumped = false
                    } else {
                        val details = repository.getBook(bookId)
                        val jumpOriginJson = details?.book?.jumpOriginLocatorJson
                        if (jumpOriginJson != null) {
                            val origin = fromJSON(JSONObject(jumpOriginJson))
                            if ((origin != null) && !locator.isSamePosition(origin) && !locator.isSamePosition(
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

        _bookState.update {
            it.copy(
                chapter = locator.title ?: getChapterTitle(locator),
                progression = locator.locations.totalProgression ?: 0.0,
                currentPage = if (pageIndex != -1) pageIndex + 1 else null,
                totalPages = if (allPositions.isNotEmpty()) allPositions.size else null,
            )
        }
    }

    fun locatorForProgression(targetProgression: Double): Locator? {
        return _positions.value.findClosestByProgression(targetProgression)
    }

    fun getPositionLabel(locator: Locator): String {
        val posIndex = _positions.value.findIndexForLocator(locator)

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

    fun getChapterPageLabel(link: Link): String {
        val allPositions = _positions.value
        if (allPositions.isEmpty()) return ""
        val linkHref = link.href.toString().substringBefore("#")
        val posIndex = allPositions.indexOfFirst { (itHref) ->
            itHref.toString().substringBefore("#") == linkHref
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

    fun togglePdfReadMode() {
        if (!_bookState.value.sourceIsPdf || conversionJob?.isActive == true) return
        conversionJob = viewModelScope.launch {
            val old = _publication.value ?: return@launch
            val source = _currentLocator.value
            val destination = if (activeRendition.value == "original") "read-mode" else "original"
            _bookState.update { it.copy(converting = true, conversionError = null) }
            var next: Publication? = null
            try {
                source?.let { repository.saveReadingPosition(bookId, it, activeRendition.value) }
                if (destination == "read-mode") repository.createReadMode(bookId) { page, total ->
                    _bookState.update { it.copy(conversionPage = page, conversionTotal = total) }
                }
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
                        next.readingOrder.find {
                            it.href.toString().endsWith("text/page-$sourcePage.xhtml")
                        }?.let(next::locatorFromLink)
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
        Regex("page-(\\d+)\\.xhtml").find(locator.href.toString())?.groupValues?.get(1)
            ?.toIntOrNull()
            ?: locator.locations.fragments.firstNotNullOfOrNull {
                it.removePrefix("page=").toIntOrNull()
            }
            ?: locator.locations.position ?: 1

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
            val (_, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, settingsJumpHistoryMode3) = settingsFlow.first()
            if (settingsJumpHistoryMode3 != "disabled") {
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
        _controlsState.update { it.copy(showToc = true) }
    }

    fun hideToc() {
        _controlsState.update { it.copy(showToc = false) }
    }

    fun showSettings() {
        _controlsState.update { it.copy(showSettings = true) }
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

    fun updateSettings(settings: ReaderSettings) {
        viewModelScope.launch {
            if (readingOverrides.value != null) {
                val local = ReaderSettings().withReadingPreferencesFrom(settings)
                readingOverrides.value = local
                repository.setReadingOverrides(
                    bookId,
                    activeRendition.value,
                    kotlinx.serialization.json.Json.encodeToString(local)
                )
            } else readerPreferences.updateAllSettings(settings)
        }
    }

    private suspend fun loadReadingOverrides(key: String) {
        val encoded = repository.rendition(bookId, key)?.preferencesJson
        readingOverrides.value = encoded?.takeUnless { it == "{}" }
            ?.let { kotlinx.serialization.json.Json.decodeFromString<ReaderSettings>(it) }
    }

    fun setUseBookSettings(enabled: Boolean) {
        viewModelScope.launch {
            readingOverrides.value =
                if (enabled) ReaderSettings().withReadingPreferencesFrom(settingsFlow.first()) else null
            repository.setReadingOverrides(
                bookId, activeRendition.value,
                readingOverrides.value?.let { kotlinx.serialization.json.Json.encodeToString(it) }
                    ?: "{}")
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

    class Factory(
        private val application: Application,
        private val bookId: String,
        private val initialSystemDark: Boolean = false,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = application as PineconeApplication
            return ReaderViewModel(
                application = app,
                bookId = bookId,
                repository = app.libraryRepository,
                readerPreferences = app.readerPreferences,
                initialSystemDark = initialSystemDark,
            ) as T
        }
    }
}
