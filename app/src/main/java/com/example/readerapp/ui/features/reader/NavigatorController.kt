package com.example.readerapp.ui.features.reader

import android.graphics.Color.colorToHSV
import android.os.Bundle
import android.view.ActionMode
import android.view.Menu
import androidx.core.graphics.toColorInt
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.readerapp.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.util.DirectionalNavigationAdapter
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import kotlin.time.Duration.Companion.milliseconds

// Fix 1: Accept LifecycleOwner + FragmentManager instead of the whole AppCompatActivity.
//         This prevents leaking the activity reference through configuration changes.
@OptIn(ExperimentalReadiumApi::class)
class NavigatorController(
    private val lifecycleOwner: LifecycleOwner,
    private val fragmentManager: FragmentManager,
    private val viewModel: ReaderViewModel
) : DefaultLifecycleObserver {

    companion object {
        private const val NAVIGATOR_TAG = "epub_navigator"
    }

    // Fix 3: navigator is fully private; the public API (go, clearSelection, etc.) is sufficient.
    private var navigator: EpubNavigatorFragment? = null

    // Fix 2: navigatorFlow is a properly encapsulated StateFlow.
    private val _navigatorFlow = MutableStateFlow<EpubNavigatorFragment?>(null)

    private var currentActionMode: ActionMode? = null

    // Fix 1: use lifecycleOwner.lifecycleScope, not activity.lifecycleScope
    private val lifecycleScope: LifecycleCoroutineScope
        get() = lifecycleOwner.lifecycleScope

    private val selectionPoller = SelectionPoller(lifecycleOwner.lifecycleScope) { locator ->
        val currentHighlight = viewModel.selectionState.value.selectionLocator?.text?.highlight
        if (locator.text.highlight != currentHighlight) {
            viewModel.showSelectionMenu(locator)
        }
    }

    private var lastSelectionCssColor: String? = null

    // Fix 4: setupObservers() is NOT called from init; it is deferred until setupNavigator()
    //         so observers are never live before a navigator can possibly exist.
    private var observersStarted = false

    init {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    fun setupNavigator(publication: Publication) {
        if (fragmentManager.findFragmentByTag(NAVIGATOR_TAG) != null) {
            navigator = fragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
            navigator?.let {
                setupNavigatorListener(it)
                _navigatorFlow.value = it
            }
            // Fix 4: start observers exactly once, even on the config-change path
            startObserversOnce()
            return
        }

        val navigatorFactory = EpubNavigatorFactory(publication = publication)
        val initialLocator = viewModel.initialLocator
        val initialPreferences = viewModel.epubPreferences.value

        val configuration = EpubNavigatorFragment.Configuration {
            selectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    menu?.clear()
                    currentActionMode = mode
                    val nav = navigator as? SelectableNavigator
                    if (nav != null) {
                        selectionPoller.start(nav) { currentActionMode }
                    }
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
                    menu?.clear()
                    return false
                }

                override fun onActionItemClicked(
                    mode: ActionMode?,
                    item: android.view.MenuItem?
                ): Boolean = false

                override fun onDestroyActionMode(mode: ActionMode?) {
                    if (mode == currentActionMode) {
                        currentActionMode = null
                        selectionPoller.stop()
                        viewModel.dismissSelectionBar()
                    }
                }
            }

            // Register custom fonts via the Registry object
            configureFonts()
        }

        fragmentManager.fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = initialPreferences,
            configuration = configuration
        )

        fragmentManager.beginTransaction().replace(
            R.id.navigator_container,
            EpubNavigatorFragment::class.java,
            Bundle(),
            NAVIGATOR_TAG
        ).commit()

        fragmentManager.executePendingTransactions()
        navigator = fragmentManager.findFragmentByTag(NAVIGATOR_TAG) as? EpubNavigatorFragment
        navigator?.let {
            setupNavigatorListener(it)
            _navigatorFlow.value = it
        }

        // Fix 4: start observers exactly once, after the navigator is committed
        startObserversOnce()
    }

    // Fix 4: guards against being called multiple times (config change re-enters setupNavigator)
    private fun startObserversOnce() {
        if (observersStarted) return
        observersStarted = true
        setupObservers()
    }

    private fun setupNavigatorListener(nav: EpubNavigatorFragment) {
        (nav as? OverflowableNavigator)?.let { overflowableNav ->
            nav.addInputListener(
                DirectionalNavigationAdapter(
                    navigator = overflowableNav, animatedTransition = true
                )
            )
        }

        nav.addInputListener(object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                if (viewModel.selectionState.value.selectionLocator != null || viewModel.selectionState.value.viewingHighlight != null) {
                    viewModel.hideSelectionMenu()
                    viewModel.hideViewHighlight()
                    return true
                }
                viewModel.toggleControls()
                return true
            }
        })

        nav.addDecorationListener("notes", object : DecorableNavigator.Listener {
            override fun onDecorationActivated(event: DecorableNavigator.OnActivatedEvent): Boolean {
                val noteIdStr = event.decoration.id.removePrefix("note_")
                val noteId = noteIdStr.toLongOrNull() ?: return false
                val note =
                    viewModel.allNotesAndHighlights.value.find { it.id == noteId } ?: return false

                if (note.noteText.isBlank()) {
                    viewModel.viewHighlight(note)
                } else {
                    viewModel.editNote(note)
                }
                return true
            }
        })
    }

    private fun setupObservers() {
        // Observe themeColors flow to dynamically update CSS selection
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.themeColors.collect { colors ->
                    injectSelectionCss(colors.backgroundColorInt)
                }
            }
        }

        // Observe preferences and submit to navigator reactively
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(_navigatorFlow.filterNotNull(), viewModel.epubPreferences) { nav, prefs ->
                    nav to prefs
                }.collect { (nav, prefs) ->
                    nav.submitPreferences(prefs)
                }
            }
        }

        // Observe current locator for position saving and UI updates reactively from active navigator
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                _navigatorFlow.collectLatest { nav ->
                    nav?.currentLocator?.collect { locator ->
                        viewModel.onLocatorChanged(locator)
                        viewModel.savePosition(locator)
                        lifecycleScope.launch {
                            delay(300.milliseconds)
                            reapplySelectionCss()
                        }
                    }
                }
            }
        }

        // Observe clear selection events
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.clearSelectionEvent.collect {
                    (navigator as? SelectableNavigator)?.clearSelection()
                }
            }
        }

        // Observe notes and highlights and apply them as permanent decorations
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    _navigatorFlow.filterNotNull(), viewModel.allNotesAndHighlights
                ) { nav, notes ->
                    nav to notes
                }.collectLatest { (nav, notes) ->
                    val decorations = notes.mapNotNull { note ->
                        try {
                            val locator = Locator.fromJSON(
                                org.json.JSONObject(note.locatorJson)
                            )
                            if (locator != null) {
                                val isHighlight = note.noteText.isEmpty()
                                val tintColor = if (note.color != -1) note.color else {
                                    if (isHighlight) "#4003A9F4".toColorInt() else "#40FFEB3B".toColorInt()
                                }
                                Decoration(
                                    id = "note_${note.id}",
                                    locator = locator,
                                    style = Decoration.Style.Highlight(
                                        tint = tintColor, isActive = false
                                    )
                                )
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                    nav.applyDecorations(decorations, group = "notes")
                }
            }
        }

        // Navigate to locator emitted by search (result selection, prev/next)
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigateToLocator.collectLatest { locator ->
                    navigator?.go(locator, animated = true)
                    delay(100.milliseconds)
                    navigator?.go(locator, animated = false)
                    applySearchHighlight(locator)
                }
            }
        }

        // Fix 5: Own the search-highlight-clear reaction here, where it belongs.
        //         Clear the search decoration only when isInNavMode transitions false → not as a
        //         side-effect of every emission from the combined controlsState/searchState/settings
        //         observer in ReaderActivity.
        lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.searchState
                    .map { it.isInNavMode }
                    .distinctUntilChanged()
                    .collect { isInNavMode ->
                        if (!isInNavMode) clearSearchHighlight()
                    }
            }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        saveCurrentPosition()
    }

    fun saveCurrentPosition() {
        navigator?.currentLocator?.value?.let { locator ->
            lifecycleScope.launch {
                viewModel.savePosition(locator)
            }
        }
    }

    fun go(locator: Locator) {
        lifecycleScope.launch {
            navigator?.go(locator)
        }
    }

    fun go(link: Link) {
        lifecycleScope.launch {
            navigator?.go(link)
        }
    }

    private fun injectSelectionCss(bgColor: Int) {
        val cssBgColor = "rgba(128, 128, 128, 0.35)"
        val hsv = FloatArray(3)
        colorToHSV(bgColor, hsv)
        val isDark = hsv[2] < 0.5f
        val cssTextColor = if (isDark) "#ffffff" else "#000000"

        lastSelectionCssColor = "$cssBgColor|$cssTextColor"
        applySelectionCssToWebView(cssBgColor, cssTextColor)
    }

    private fun reapplySelectionCss() {
        val parts = lastSelectionCssColor?.split("|") ?: return
        if (parts.size == 2) {
            applySelectionCssToWebView(parts[0], parts[1])
        }
    }

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
            try {
                nav.evaluateJavascript(js)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun applySearchHighlight(locator: Locator) {
        val nav = navigator as? DecorableNavigator ?: return
        val decoration = Decoration(
            id = "search_current", locator = locator, style = Decoration.Style.Highlight(
                tint = "#FFEB3B".toColorInt(),
                isActive = false
            )
        )
        nav.applyDecorations(listOf(decoration), group = "search")
    }

    fun clearSearchHighlight() {
        val nav = navigator as? DecorableNavigator ?: return
        lifecycleScope.launch {
            nav.applyDecorations(emptyList(), group = "search")
        }
    }
}
