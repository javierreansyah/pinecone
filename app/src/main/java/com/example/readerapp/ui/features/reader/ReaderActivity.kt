package com.example.readerapp.ui.features.reader

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.*
import androidx.fragment.app.FragmentContainerView
import com.example.readerapp.ReaderApplication
import com.example.readerapp.data.local.preferences.ReaderPreferences
import com.example.readerapp.ui.theme.ReaderTheme
import com.example.readerapp.ui.features.reader.components.overlay.ReaderOverlay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import androidx.core.graphics.toColorInt
import kotlin.time.Duration.Companion.milliseconds
import androidx.core.net.toUri
import com.example.readerapp.data.local.preferences.ReaderSettings

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
        private const val NAVIGATOR_TAG = "epub_navigator"
    }

    private val viewModel: ReaderViewModel by viewModels {
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: ""
        val app = application as ReaderApplication
        ReaderViewModel.Factory(
            application = app,
            bookId = bookId,
            repository = app.libraryRepository,
            readerPreferences = ReaderPreferences(applicationContext),
            dictionaryRepository = app.dictionaryRepository
        )
    }

    private var navigator: EpubNavigatorFragment? = null
    private var navigatorContainer: FragmentContainerView? = null
    private val navigatorFlow = MutableStateFlow<EpubNavigatorFragment?>(null)
    private var currentActionMode: android.view.ActionMode? = null
    private var selectionUpdateJob: kotlinx.coroutines.Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(com.example.readerapp.R.layout.activity_reader)

        val composeView = findViewById<ComposeView>(com.example.readerapp.R.id.compose_overlay)
        navigatorContainer = findViewById(com.example.readerapp.R.id.navigator_container)

        // Ensure the navigator container doesn't shift when bars toggle or keyboard appears
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(navigatorContainer!!) { _, insets ->
            // We handle our own margins based on settings, so we consume the system bar insets
            // but keep the display cutout insets if needed.
            insets
        }

        val rootLayout = findViewById<android.widget.FrameLayout>(com.example.readerapp.R.id.reader_root)

        // Open the book
        if (savedInstanceState == null) {
            viewModel.openBook()
        }

        // Observe publication and set up navigator
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.publication.filterNotNull().collect { publication ->
                    if (navigator == null) {
                        setupNavigator(publication)
                    }
                }
            }
        }

        // Observe settings flow to dynamically update background and navigator margins
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settingsFlow.collect { settings ->
                    val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                    viewModel.systemDarkThemeFlow.value = isSystemDark

                    val color = when (settings.readerThemePreset) {
                        "Light" -> "#FFFFFF".toColorInt()
                        "Warm" -> "#FAF4E8".toColorInt()
                        "Dark" -> "#000000".toColorInt()
                        "Auto" -> {
                            val uiDarkTheme = when (settings.themeMode) {
                                "Dark" -> true
                                "Light" -> false
                                else -> isSystemDark
                            }
                            if (uiDarkTheme) "#000000".toColorInt() else "#FFFFFF".toColorInt()
                        }
                        else -> try {
                            settings.customBackgroundColor.toColorInt()
                        } catch (_: Exception) {
                            android.graphics.Color.WHITE
                        }
                    }
                    // Paint at the window level so the ENTIRE screen is one uniform
                    // color — including any margin gaps around the navigator.
                    window.decorView.setBackgroundColor(color)
                    rootLayout?.setBackgroundColor(color)

                    // In scroll mode: full screen (no margins).
                    // In pagination mode: added padding.
                    val density = resources.displayMetrics.density
                    val newTop = if (settings.scroll) 0 else (settings.verticalMargin * density).toInt()
                    val newBottom = if (settings.scroll) 0 else (maxOf(0.0, settings.verticalMargin - 16) * density).toInt()

                    navigatorContainer?.let { container ->
                        val lp = container.layoutParams as android.widget.FrameLayout.LayoutParams
                        if (lp.topMargin != newTop || lp.bottomMargin != newBottom) {
                            lp.topMargin = newTop
                            lp.bottomMargin = newBottom
                            container.layoutParams = lp
                        }
                    }

                    // Inject dynamic ::selection CSS based on the reader background color
                    injectSelectionCss(color)
                    
                    // Prevent Screen Timeout
                    if (settings.preventScreenTimeout) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    
                    // Force Orientation
                    requestedOrientation = when (settings.forceOrientation) {
                        "Portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        "Landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }

        // Observe preferences and submit to navigator reactively when either navigator or preferences change
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(navigatorFlow.filterNotNull(), viewModel.epubPreferences) { nav, prefs ->
                    nav to prefs
                }.collect { (nav, prefs) ->
                    nav.submitPreferences(prefs)
                }
            }
        }

        // Observe current locator for position saving and UI updates reactively from active navigator
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigatorFlow.collectLatest { nav ->
                    nav?.currentLocator?.collect { locator ->
                        viewModel.onLocatorChanged(locator)
                        viewModel.savePosition(locator)
                        // Re-inject ::selection CSS on every page change — the
                        // WebView reloads HTML for each new resource/chapter.
                        lifecycleScope.launch {
                            kotlinx.coroutines.delay(300.milliseconds)
                            reapplySelectionCss()
                        }
                    }
                }
            }
        }

        // Observe clear selection events
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.clearSelectionEvent.collect {
                    (navigator as? org.readium.r2.navigator.SelectableNavigator)?.clearSelection()
                }
            }
        }

        // Note: Polling loop for selection has been completely removed to prevent jitter.
        // We now rely on Android's native onDestroyActionMode to detect when selection is cleared.


        // Observe notes and highlights and apply them as permanent decorations
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(navigatorFlow.filterNotNull(), viewModel.allNotesAndHighlights) { nav, notes ->
                    nav to notes
                }.collectLatest { (nav, notes) ->
                    if (nav is DecorableNavigator) {
                        val decorations = notes.mapNotNull { note ->
                            try {
                                val locator = org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(note.locatorJson))
                                if (locator != null) {
                                    val isHighlight = note.noteText.isEmpty()
                                    val tintColor = if (note.color != -1) note.color else {
                                        if (isHighlight) "#4003A9F4".toColorInt() else "#40FFEB3B".toColorInt()
                                    }
                                    Decoration(
                                        id = "note_${note.id}",
                                        locator = locator,
                                        style = Decoration.Style.Highlight(
                                            tint = tintColor,
                                            isActive = false
                                        )
                                    )
                                } else null
                            } catch (e: Exception) {
                                null
                            }
                        }
                        nav.applyDecorations(decorations, group = "notes")
                    }
                }
            }
        }

        // Observe brightness
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.brightness.collect { brightness ->
                    val layoutParams = window.attributes
                    layoutParams.screenBrightness = brightness
                    window.attributes = layoutParams
                }
            }
        }

        // Navigate to locator emitted by search (result selection, prev/next)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigateToLocator.collectLatest { locator ->
                    // Using collectLatest to cancel any in-flight navigation if a new one comes in
                    navigator?.go(locator, animated = true)
                    
                    // Workaround for Readium race condition: when navigating backwards across chapter boundaries,
                    // the WebView may not have fully rendered the target cssSelector, causing the navigator
                    // to fall back to the end of the chapter (progression 1.0).
                    // A second go() with animated=false forces it to snap to the exact text once loaded.
                    kotlinx.coroutines.delay(100.milliseconds)
                    navigator?.go(locator, animated = false)
                    
                    // Apply a highlight decoration at the matched text location
                    applySearchHighlight(locator)
                }
            }
        }

        // Manage system bars and clear highlight decoration when the user exits search navigation
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(viewModel.uiState, viewModel.settingsFlow) { state, settings ->
                    state to settings
                }.collect { (state, settings) ->
                    val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    if (state.showControls || settings.alwaysShowStatusBar) {
                        windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    } else {
                        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    }

                    if (!state.isInSearchNavigationMode) {
                        clearSearchHighlight()
                    }
                }
            }
        }

        // Set up Compose overlay
        composeView.setContent {
            // The overlay theme is driven by the reader's own theme setting so that
            // icon/text colours always contrast with the reader background.
            val settingsState = viewModel.settingsFlow.collectAsState(
                initial = ReaderSettings()
            )
            val overlaySettings = settingsState.value
            val isSystemDark = isSystemInDarkTheme()
            val readerBgColor = when (overlaySettings.readerThemePreset) {
                "Light" -> androidx.compose.ui.graphics.Color(0xFFFFFFFF)
                "Warm" -> androidx.compose.ui.graphics.Color(0xFFFAF4E8)
                "Dark" -> androidx.compose.ui.graphics.Color(0xFF000000)
                "Auto" -> {
                    val uiDarkTheme = when (overlaySettings.themeMode) {
                        "Dark" -> true
                        "Light" -> false
                        else -> isSystemDark
                    }
                    if (uiDarkTheme) androidx.compose.ui.graphics.Color(0xFF000000) else androidx.compose.ui.graphics.Color(0xFFFFFFFF)
                }
                else -> try {
                    androidx.compose.ui.graphics.Color(overlaySettings.customBackgroundColor.toColorInt())
                } catch (_: Exception) {
                    androidx.compose.ui.graphics.Color.White
                }
            }
            ReaderTheme(readerBackgroundColor = readerBgColor) {
                ReaderOverlay(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onNavigateToChapter = { link ->
                        lifecycleScope.launch {
                            navigator?.go(link)
                        }
                    },
                    onSeekToProgression = { progression ->
                        val locator = viewModel.locatorForProgression(progression)
                        if (locator != null) {
                            lifecycleScope.launch {
                                navigator?.go(locator)
                            }
                        }
                    },
                    onInfoClick = {
                        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: ""
                        val infoIntent = Intent(Intent.ACTION_VIEW,
                            "pinecone://book_info/$bookId".toUri())
                        startActivity(infoIntent)
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun setupNavigator(publication: Publication) {
        if (supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) != null) {
            navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
            navigator?.let {
                setupNavigatorListener(it)
                navigatorFlow.value = it
            }
            return
        }

        val navigatorFactory = EpubNavigatorFactory(
            publication = publication
        )

        val initialLocator = viewModel.initialLocator
        val initialPreferences = viewModel.epubPreferences.value

        val configuration = EpubNavigatorFragment.Configuration {
            // Custom selection callback for our Compose action bar.
            selectionActionModeCallback = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                    // Clear all native menu items so the default context toolbar
                    // never renders — our custom Compose bar replaces it.
                    menu?.clear()
                    currentActionMode = mode

                    // Show our custom Compose action bar
                    val nav = navigator as? org.readium.r2.navigator.SelectableNavigator
                    if (nav != null) {
                        selectionUpdateJob?.cancel()
                        selectionUpdateJob = lifecycleScope.launch {
                            // Continuously poll while action mode is active to ensure we catch all
                            // selection updates, including delayed ones from Chromium bugs on cross-column paragraphs.
                            while (isActive && currentActionMode != null) {
                                val selection = nav.currentSelection()
                                if (selection != null) {
                                    val currentHighlight = viewModel.uiState.value.selectionLocator?.text?.highlight
                                    if (selection.locator.text.highlight != currentHighlight) {
                                        viewModel.showSelectionMenu(selection.locator)
                                    }
                                }
                                kotlinx.coroutines.delay(300.milliseconds)
                            }
                        }
                    }
                    return true
                }

                override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                    // Keep clearing — onPrepare is called on every invalidation.
                    menu?.clear()
                    return false
                }

                override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean = false

                override fun onDestroyActionMode(mode: android.view.ActionMode?) {
                    if (mode == currentActionMode) {
                        currentActionMode = null
                        // dismissSelectionBar() only hides the UI bar.
                        // It does NOT call clearSelection(), which would
                        // fight the WebView during cross-column drags.
                        viewModel.dismissSelectionBar()
                    }
                }
            }

            // Serve the fonts directory so the navigator can load custom font files.
            servedAssets += "fonts/.*"


            // Source Serif 4 — variable font, used as an explicit serif option.
            addFontFamilyDeclaration(FontFamily("Source Serif 4")) {
                addFontFace {
                    addSource("fonts/source_serif_4.ttf", preload = true)
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(200..900)
                }
                addFontFace {
                    addSource("fonts/source_serif_4_italic.ttf")
                    setFontStyle(FontStyle.ITALIC)
                    setFontWeight(200..900)
                }
            }

            // Source Sans 3 — variable font, used as the default sans-serif option.
            addFontFamilyDeclaration(FontFamily("Source Sans 3")) {
                addFontFace {
                    addSource("fonts/source_sans_3.ttf", preload = true)
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(200..900)
                }
                addFontFace {
                    addSource("fonts/source_sans_3_italic.ttf")
                    setFontStyle(FontStyle.ITALIC)
                    setFontWeight(200..900)
                }
            }

            // Literata — variable font.
            addFontFamilyDeclaration(FontFamily("Literata")) {
                addFontFace {
                    addSource("fonts/literata.ttf", preload = true)
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(200..900)
                }
                addFontFace {
                    addSource("fonts/literata_italic.ttf")
                    setFontStyle(FontStyle.ITALIC)
                    setFontWeight(200..900)
                }
            }

            // Atkinson Hyperlegible — static font family.
            addFontFamilyDeclaration(FontFamily("Atkinson Hyperlegible")) {
                addFontFace {
                    addSource("fonts/atkinson_hyperlegible.ttf", preload = true)
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(400..500)
                }
                addFontFace {
                    addSource("fonts/atkinson_hyperlegible_italic.ttf")
                    setFontStyle(FontStyle.ITALIC)
                    setFontWeight(400..500)
                }
                addFontFace {
                    addSource("fonts/atkinson_hyperlegible_bold.ttf")
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(600..700)
                }
                addFontFace {
                    addSource("fonts/atkinson_hyperlegible_bold_italic.ttf")
                    setFontStyle(FontStyle.ITALIC)
                    setFontWeight(600..700)
                }
            }

            // Source Code — variable font.
            addFontFamilyDeclaration(FontFamily("Source Code")) {
                addFontFace {
                    addSource("fonts/source_code.ttf", preload = true)
                    setFontStyle(FontStyle.NORMAL)
                    setFontWeight(200..900)
                }
                addFontFace {
                    addSource("fonts/source_code_italic.ttf")
                    setFontStyle(FontStyle.ITALIC)
                    setFontWeight(200..900)
                }
            }
        }

        supportFragmentManager.fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = initialPreferences,
            configuration = configuration
        )

        supportFragmentManager.beginTransaction()
            .replace(
                com.example.readerapp.R.id.navigator_container,
                EpubNavigatorFragment::class.java,
                Bundle(),
                NAVIGATOR_TAG
            )
            .commit()

        supportFragmentManager.executePendingTransactions()
        navigator = supportFragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
        navigator?.let {
            setupNavigatorListener(it)
            navigatorFlow.value = it
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun setupNavigatorListener(nav: EpubNavigatorFragment) {
        // 1) DirectionalNavigationAdapter — registered FIRST so it gets priority.
        //    It handles both swipe gestures and edge taps out of the box:
        //      • Swipe left / right edge tap  → goForward()  (next page or chapter)
        //      • Swipe right / left edge tap  → goBackward() (prev page or chapter)
        (nav as? org.readium.r2.navigator.OverflowableNavigator)?.let { overflowableNav ->
            nav.addInputListener(
                org.readium.r2.navigator.util.DirectionalNavigationAdapter(
                    navigator = overflowableNav,
                    animatedTransition = true
                )
            )
        }

        // 2) Tap listener — registered SECOND so edge taps consumed above never reach here.
        //    Only un-consumed taps (centre area) toggle the controls overlay.
        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                if (viewModel.uiState.value.selectionLocator != null || viewModel.uiState.value.viewingHighlight != null) {
                    viewModel.hideSelectionMenu()
                    viewModel.hideViewHighlight()
                    return true
                }
                viewModel.toggleControls()
                return true
            }
        })
        
        // 3) Decoration Observer - listen for taps on notes/highlights
        nav.addDecorationListener("notes", object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val noteIdStr = event.decoration.id.removePrefix("note_")
                val noteId = noteIdStr.toLongOrNull() ?: return false

                val note = viewModel.allNotesAndHighlights.value.find { it.id == noteId } ?: return false

                if (note.noteText.isBlank()) {
                    viewModel.viewHighlight(note)
                } else {
                    viewModel.editNote(note)
                }
                return true
            }
        })
    }

    // ── Dynamic ::selection colour injection ──────────────────────────────

    /** The last CSS colour string injected — avoids redundant JS calls. */
    private var lastSelectionCssColor: String? = null

    /**
     * Computes a darkened (for light backgrounds) or lightened (for dark
     * backgrounds) variant of [bgColor] and injects a `::selection` CSS
     * rule into the WebView.
     */
    private fun injectSelectionCss(bgColor: Int) {
        val cssBgColor = "rgba(128, 128, 128, 0.35)"
        
        // Calculate an explicit text color based on background luminance to avoid the
        // Chromium `color: inherit` cross-column jitter bug, while still forcing
        // the custom background color to be respected instead of the default blue.
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(bgColor, hsv)
        val isDark = hsv[2] < 0.5f
        val cssTextColor = if (isDark) "#ffffff" else "#000000"

        lastSelectionCssColor = "$cssBgColor|$cssTextColor"
        applySelectionCssToWebView(cssBgColor, cssTextColor)
    }

    /** Re-applies the last computed selection CSS (needed after page navigation). */
    private fun reapplySelectionCss() {
        val parts = lastSelectionCssColor?.split("|") ?: return
        if (parts.size == 2) {
            applySelectionCssToWebView(parts[0], parts[1])
        }
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun applySelectionCssToWebView(cssBgColor: String, cssTextColor: String) {
        val nav = navigator ?: return
        val js = """
            (function() {
                var id = '__pinecone_selection_style';
                var existing = document.getElementById(id);
                if (existing) existing.remove();
                var s = document.createElement('style');
                s.id = id;
                s.textContent = '\n'
                    + '*::selection { background: $cssBgColor !important; color: $cssTextColor !important; text-shadow: none !important; }\n'
                    + '*::-webkit-selection { background: $cssBgColor !important; color: $cssTextColor !important; text-shadow: none !important; }\n'
                    + '::selection { background: $cssBgColor !important; color: $cssTextColor !important; text-shadow: none !important; }\n'
                    + '::-webkit-selection { background: $cssBgColor !important; color: $cssTextColor !important; text-shadow: none !important; }\n';
                document.head.appendChild(s);
                document.documentElement.style.setProperty('--USER__selectionBackgroundColor', '$cssBgColor');
            })();
        """.trimIndent()
        lifecycleScope.launch {
            try { nav.evaluateJavascript(js) } catch (_: Exception) { }
        }
    }

    override fun onPause() {
        super.onPause()
        // Save position when leaving
        navigator?.currentLocator?.value?.let { locator ->
            lifecycleScope.launch {
                viewModel.savePosition(locator)
            }
        }
    }



    // ── Search highlight helpers ─────────────────────────────────────────────

    @OptIn(ExperimentalReadiumApi::class)
    private suspend fun applySearchHighlight(locator: org.readium.r2.shared.publication.Locator) {
        val nav = navigator as? DecorableNavigator ?: return
        val decoration = Decoration(
            id = "search_current",
            locator = locator,
            style = Decoration.Style.Highlight(
                tint = "#FFEB3B".toColorInt(), // yellow
                isActive = false
            )
        )
        nav.applyDecorations(listOf(decoration), group = "search")
    }

    @OptIn(ExperimentalReadiumApi::class)
    private fun clearSearchHighlight() {
        val nav = navigator as? DecorableNavigator ?: return
        lifecycleScope.launch {
            nav.applyDecorations(emptyList(), group = "search")
        }
    }


}
