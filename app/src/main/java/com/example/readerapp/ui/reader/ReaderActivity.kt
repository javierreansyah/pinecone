package com.example.readerapp.ui.reader

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
import com.example.readerapp.data.local.ReaderPreferences
import com.example.readerapp.ui.theme.ReaderAppTheme
import com.example.readerapp.ui.reader.components.ReaderOverlay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import androidx.core.graphics.toColorInt

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
        private const val NAVIGATOR_TAG = "epub_navigator"
    }

    private val viewModel: ReaderViewModel by viewModels {
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: ""
        val app = application as ReaderApplication
        ReaderViewModel.Factory(
            bookId = bookId,
            repository = app.bookRepository,
            readerPreferences = ReaderPreferences(applicationContext)
        )
    }

    private var navigator: EpubNavigatorFragment? = null
    private var navigatorContainer: FragmentContainerView? = null
    private val navigatorFlow = MutableStateFlow<EpubNavigatorFragment?>(null)

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
                    val color = when (settings.readerThemePreset) {
                        "Light" -> "#FFFFFF".toColorInt()
                        "Warm" -> "#FAF4E8".toColorInt()
                        "Dark" -> "#000000".toColorInt()
                        "Auto" -> {
                            val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                            if (isSystemDark) "#000000".toColorInt() else "#FFFFFF".toColorInt()
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
                    val newTop = if (settings.scroll) 0 else (32 * density).toInt()
                    val newBottom = if (settings.scroll) 0 else (16 * density).toInt()

                    navigatorContainer?.let { container ->
                        val lp = container.layoutParams as android.widget.FrameLayout.LayoutParams
                        if (lp.topMargin != newTop || lp.bottomMargin != newBottom) {
                            lp.topMargin = newTop
                            lp.bottomMargin = newBottom
                            container.layoutParams = lp
                        }
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
                        // Inject custom selection CSS with a small delay to ensure WebView is ready
                        lifecycleScope.launch {
                            kotlinx.coroutines.delay(300)
                            val css = "::selection { background-color: rgba(128, 128, 128, 0.35) !important; color: inherit !important; }"
                            val script = """
                                (function() {
                                    var style = document.getElementById('custom-selection-style');
                                    if (!style) {
                                        style = document.createElement('style');
                                        style.id = 'custom-selection-style';
                                        style.type = 'text/css';
                                        document.head.appendChild(style);
                                    }
                                    style.innerHTML = '$css';
                                })();
                            """
                            (nav as? org.readium.r2.navigator.epub.EpubNavigatorFragment)?.evaluateJavascript(script)
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

        // Poll for native selection changes to dismiss our menu when the user natively taps outside to drop selection
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    kotlinx.coroutines.delay(200)
                    if (viewModel.uiState.value.selectionLocator != null) {
                        val nav = navigator as? org.readium.r2.navigator.SelectableNavigator
                        if (nav != null) {
                            val currentSel = nav.currentSelection()
                            if (currentSel == null) {
                                viewModel.hideSelectionMenu()
                            } else {
                                // Keep our locator up-to-date with handle drags!
                                if (currentSel.locator != viewModel.uiState.value.selectionLocator) {
                                    viewModel.showSelectionMenu(currentSel.locator)
                                }
                            }
                        }
                    }
                }
            }
        }

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
                                        if (isHighlight) android.graphics.Color.parseColor("#4003A9F4") else android.graphics.Color.parseColor("#40FFEB3B")
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
                    kotlinx.coroutines.delay(100)
                    navigator?.go(locator, animated = false)
                    
                    // Apply a highlight decoration at the matched text location
                    applySearchHighlight(locator)
                }
            }
        }

        // Clear highlight decoration when the user exits search navigation
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
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
                initial = com.example.readerapp.data.local.ReaderSettings()
            )
            val overlaySettings = settingsState.value
            val isSystemDark = isSystemInDarkTheme()
            val overlayDarkTheme = when (overlaySettings.readerThemePreset) {
                "Dark" -> true
                "Light", "Warm" -> false
                "Auto" -> isSystemDark
                else -> {
                    try {
                        val colorInt = overlaySettings.customBackgroundColor.toColorInt()
                        androidx.core.graphics.ColorUtils.calculateLuminance(colorInt) < 0.5
                    } catch (_: Exception) {
                        isSystemDark
                    }
                }
            }
            ReaderAppTheme(darkTheme = overlayDarkTheme, colorPalette = overlaySettings.colorPalette) {
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
                    }
                )
            }
        }
    }

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

        val configuration = EpubNavigatorFragment.Configuration(
            selectionActionModeCallback = object : android.view.ActionMode.Callback {
                override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                    menu?.clear()
                    
                    val nav = navigator as? org.readium.r2.navigator.SelectableNavigator
                    if (nav != null) {
                        lifecycleScope.launch {
                            val selection = nav.currentSelection()
                            if (selection != null) {
                                viewModel.showSelectionMenu(selection.locator)
                            }
                            // Close the native action mode immediately so the default bar disappears
                            mode?.finish()
                        }
                    }
                    // Return true initially to prevent the default menu from showing up fully before we finish it
                    return true
                }

                override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                    menu?.clear()
                    return false
                }

                override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean {
                    return false
                }

                override fun onDestroyActionMode(mode: android.view.ActionMode?) {}
            }
        )

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
        if (nav is DecorableNavigator) {
            nav.addDecorationListener("notes", object : org.readium.r2.navigator.DecorableNavigator.Listener {
                override fun onDecorationActivated(event: org.readium.r2.navigator.DecorableNavigator.OnActivatedEvent): Boolean {
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

    override fun onDestroy() {
        super.onDestroy()
        viewModel.closeBook()
    }

    // ── Search highlight helpers ─────────────────────────────────────────────

    @OptIn(ExperimentalReadiumApi::class)
    private suspend fun applySearchHighlight(locator: org.readium.r2.shared.publication.Locator) {
        val nav = navigator as? DecorableNavigator ?: return
        val decoration = Decoration(
            id = "search_current",
            locator = locator,
            style = Decoration.Style.Highlight(
                tint = android.graphics.Color.parseColor("#FFEB3B"), // yellow
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
