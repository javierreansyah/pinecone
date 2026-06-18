@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.example.readerapp.ui.features.reader

import android.app.Application
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.readerapp.R
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.data.local.preferences.ReaderSettings
import com.example.readerapp.data.repository.library.LibraryRepository
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

data class ReaderThemeColors(
    val backgroundColor: Color,
    val textColor: Color,
    val backgroundColorInt: Int
)

data class BookState(
    val title: String = "",
    val chapter: String? = null,
    val progression: Double = 0.0,
    val currentPage: Int? = null,
    val totalPages: Int? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class ControlsState(
    val showControls: Boolean = false,
    val showToc: Boolean = false,
    val showSettings: Boolean = false,
    val showSearch: Boolean = false
)

data class ExternalLinkState(
    val showMenu: Boolean = false,
    val url: String = ""
)

class ReaderViewModel(
    private val application: Application,
    private val bookId: String,
    private val repository: LibraryRepository,
    private val readerPreferences: ReaderPreferences,
    initialSystemDark: Boolean = false
) : ViewModel() {

    private val _publication = MutableStateFlow<Publication?>(null)
    val publication: StateFlow<Publication?> = _publication.asStateFlow()

    private val _positions = MutableStateFlow<List<Locator>>(emptyList())
    val positions: StateFlow<List<Locator>> = _positions.asStateFlow()

    var initialLocator: Locator? = null
        private set

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

    val settingsFlow: Flow<ReaderSettings> = readerPreferences.readerSettings

    val brightness: StateFlow<Float> =
        readerPreferences.readerSettings.map { if (it.autoBrightness) -1.0f else it.brightness }
            .stateIn(viewModelScope, SharingStarted.Eagerly, -1.0f)

    val systemDarkThemeFlow = MutableStateFlow(initialSystemDark)

    val themeColors: StateFlow<ReaderThemeColors> = combine(
        settingsFlow, systemDarkThemeFlow
    ) { settings, isSystemDark ->
        val uiDarkTheme = when (settings.themeMode) {
            "Dark" -> true
            "Light" -> false
            else -> isSystemDark
        }

        val bgColorInt = when (settings.readerThemePreset) {
            "Light" -> 0xFFFFFFFF.toInt()
            "Warm" -> 0xFFFAF4E8.toInt()
            "Dark" -> 0xFF000000.toInt()
            "Auto" -> if (uiDarkTheme) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            else -> try {
                settings.customBackgroundColor.toColorInt()
            } catch (_: Exception) {
                android.graphics.Color.WHITE
            }
        }

        val textColorInt = when (settings.readerThemePreset) {
            "Light" -> 0xFF000000.toInt()
            "Warm" -> 0xFF121212.toInt()
            "Dark" -> 0xFFFFFFFF.toInt()
            "Auto" -> if (uiDarkTheme) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            else -> try {
                settings.customTextColor.toColorInt()
            } catch (_: Exception) {
                if (uiDarkTheme) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            }
        }

        ReaderThemeColors(
            backgroundColor = Color(bgColorInt),
            textColor = Color(textColorInt),
            backgroundColorInt = bgColorInt
        )
    }.stateIn(
        viewModelScope,
        WhileSubscribed(5000),
        run {
            val bgColorInt = if (initialSystemDark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            val textColorInt = if (initialSystemDark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            ReaderThemeColors(Color(bgColorInt), Color(textColorInt), bgColorInt)
        }
    )

    val epubPreferences: StateFlow<EpubPreferences> = combine(
        readerPreferences.readerSettings, systemDarkThemeFlow
    ) { settings, isDark ->
        settings.toEpubPreferences(isDark)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ReaderSettings().toEpubPreferences(initialSystemDark)
    )

    val tableOfContents: List<Link>
        get() = _publication.value?.tableOfContents ?: emptyList()

    init {
        viewModelScope.launch {
            settingsFlow.collect { settings ->
                if (settings.jumpHistoryMode == "disabled") {
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

                val pub = repository.openPublication(book)
                if (pub == null) {
                    _bookState.update { it.copy(isLoading = false, error = "Could not open book") }
                    return@launch
                }

                _publication.value = pub

                viewModelScope.launch {
                    val p = pub.positions()
                    _positions.value = p
                    _currentLocator.value?.let { onLocatorChanged(it) }
                }

                _bookState.update {
                    it.copy(
                        isLoading = false, title = pub.metadata.title ?: book.book.title
                    )
                }
            } catch (e: Exception) {
                _bookState.update {
                    it.copy(isLoading = false, error = "Error: ${e.message}")
                }
            }
        }
    }

    private var justJumped = false

    fun onLocatorChanged(locator: Locator) {
        val oldLocator = _currentLocator.value
        _currentLocator.value = locator

        if (oldLocator != null) {
            viewModelScope.launch {
                val settings = settingsFlow.first()
                if (settings.jumpHistoryMode == "disabled") {
                    repository.clearJumpOrigin(bookId)
                } else if (settings.jumpHistoryMode == "page_turn") {
                    if (justJumped) {
                        justJumped = false
                    } else {
                        val details = repository.getBook(bookId)
                        val jumpOriginJson = details?.book?.jumpOriginLocatorJson
                        if (jumpOriginJson != null) {
                            val origin = fromJSON(JSONObject(jumpOriginJson))
                            if (origin != null && !locator.isSamePosition(origin) && !locator.isSamePosition(
                                    oldLocator
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
                chapter = locator.title,
                progression = locator.locations.totalProgression ?: 0.0,
                currentPage = if (pageIndex != -1) pageIndex + 1 else null,
                totalPages = if (allPositions.isNotEmpty()) allPositions.size else null
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
                R.plurals.reader_page_num, posIndex + 1, posIndex + 1
            )

            locator.locations.totalProgression != null -> application.getString(
                R.string.reader_position_at,
                "${(locator.locations.totalProgression!! * 100).toInt()}%"
            )

            else -> ""
        }
    }

    fun getChapterPageLabel(link: Link): String {
        val allPositions = _positions.value
        if (allPositions.isEmpty()) return ""
        val linkHref = link.href.toString().substringBefore("#")
        val posIndex = allPositions.indexOfFirst {
            it.href.toString().substringBefore("#") == linkHref
        }
        return if (posIndex != -1) application.resources.getQuantityString(
            R.plurals.reader_page_num, posIndex + 1, posIndex + 1
        ) else ""
    }

    fun savePosition(locator: Locator) {
        viewModelScope.launch {
            repository.saveReadingPosition(bookId, locator)
        }
    }

    fun recordJumpOrigin() {
        val current = _currentLocator.value ?: return
        viewModelScope.launch {
            val settings = settingsFlow.first()
            if (settings.jumpHistoryMode != "disabled") {
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
            readerPreferences.updateAllSettings(settings)
        }
    }

    fun closeBook() {
        _publication.value?.close()
        _publication.value = null
    }

    override fun onCleared() {
        closeBook()
    }

    class Factory(
        private val application: Application,
        private val bookId: String,
        private val initialSystemDark: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val app = application as ReaderApplication
            return ReaderViewModel(
                application = app,
                bookId = bookId,
                repository = app.libraryRepository,
                readerPreferences = app.readerPreferences,
                initialSystemDark = initialSystemDark
            ) as T
        }
    }
}
