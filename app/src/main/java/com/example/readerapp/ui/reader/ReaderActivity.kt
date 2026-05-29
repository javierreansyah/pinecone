package com.example.readerapp.ui.reader

import android.os.Bundle
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
        super.onCreate(savedInstanceState)
        setContentView(com.example.readerapp.R.layout.activity_reader)

        val composeView = findViewById<ComposeView>(com.example.readerapp.R.id.compose_overlay)
        navigatorContainer = findViewById(com.example.readerapp.R.id.navigator_container)

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(navigatorContainer!!) { _, _ ->
            androidx.core.view.WindowInsetsCompat.CONSUMED
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
                    val topMarginPx = if (settings.scroll) 0 else (58 * density).toInt()
                    val bottomMarginPx = if (settings.scroll) 0 else (40 * density).toInt()

                    navigatorContainer?.let { container ->
                        val lp = container.layoutParams as android.widget.FrameLayout.LayoutParams
                        lp.topMargin = topMarginPx
                        lp.bottomMargin = bottomMarginPx
                        container.layoutParams = lp
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

        supportFragmentManager.fragmentFactory = navigatorFactory.createFragmentFactory(
            initialLocator = initialLocator,
            initialPreferences = initialPreferences
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
                viewModel.toggleControls()
                return true
            }
        })
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


}
