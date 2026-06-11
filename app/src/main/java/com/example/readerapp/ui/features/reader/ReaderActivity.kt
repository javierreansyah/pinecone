package com.example.readerapp.ui.features.reader

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.readerapp.R
import com.example.readerapp.ui.features.reader.components.overlay.ReaderOverlay
import com.example.readerapp.ui.theme.ReaderTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class ReaderActivity : AppCompatActivity(), ReaderNavigationRouter {

    companion object {
        const val EXTRA_BOOK_ID = "bookId"
    }

    private val viewModel: ReaderViewModel by viewModels {
        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: ""
        ReaderViewModel.Factory(application, bookId, isNightMode(resources.configuration))
    }

    private lateinit var navigatorController: NavigatorController
    private var navigatorContainer: FragmentContainerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        navigatorContainer = findViewById(R.id.navigator_container)
        // Fix 1: pass LifecycleOwner + FragmentManager instead of the whole activity
        navigatorController = NavigatorController(this, supportFragmentManager, viewModel)

        // Ensure the navigator container doesn't shift when bars toggle or keyboard appears
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(navigatorContainer!!) { _, insets ->
            insets
        }

        // Fix 6: initialize system dark theme now, and keep it current via onConfigurationChanged()
        viewModel.systemDarkThemeFlow.value = isNightMode(resources.configuration)

        // Open the book
        if (savedInstanceState == null) {
            viewModel.openBook()
        }

        // Observe publication and set up navigator
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.publication.filterNotNull().collect { publication ->
                    navigatorController.setupNavigator(publication)
                }
            }
        }

        // Setup system and settings observers
        observeWindowBackground()
        observeNavigatorMargins()
        observeBrightness()
        observeOrientation()
        observeScreenTimeout()
        observeSystemBars()

        // Set up Compose overlay
        val composeView = findViewById<ComposeView>(R.id.compose_overlay)
        composeView.setContent {
            val themeColorsState = viewModel.themeColors.collectAsState()
            val readerBgColor = themeColorsState.value.backgroundColor

            ReaderTheme(readerBackgroundColor = readerBgColor) {
                ReaderOverlay(
                    viewModel = viewModel,
                    router = this@ReaderActivity,
                    bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: "",
                    onNavigateToChapter = { link ->
                        navigatorController.go(link)
                    },
                    onSeekToProgression = { progression ->
                        val locator = viewModel.locatorForProgression(progression)
                        if (locator != null) {
                            navigatorController.go(locator)
                        }
                    }
                )
            }
        }
    }

    override fun navigateToBookInfo(bookId: String) {
        val infoIntent = Intent(
            Intent.ACTION_VIEW, "pinecone://book_info/$bookId".toUri()
        )
        startActivity(infoIntent)
    }

    override fun navigateBack() {
        finish()
    }

    private fun observeWindowBackground() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.themeColors.collect { colors ->
                    window.decorView.setBackgroundColor(colors.backgroundColorInt)
                }
            }
        }
    }

    private fun observeNavigatorMargins() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settingsFlow.collect { settings ->
                    val density = resources.displayMetrics.density
                    val newTop =
                        if (settings.scroll) 0 else (settings.verticalMargin * density).toInt()
                    val newBottom = if (settings.scroll) 0 else (maxOf(
                        0.0,
                        settings.verticalMargin - 16
                    ) * density).toInt()

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
    }

    private fun observeBrightness() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.brightness.collect { brightness ->
                    val layoutParams = window.attributes
                    layoutParams.screenBrightness = brightness
                    window.attributes = layoutParams
                }
            }
        }
    }

    private fun observeOrientation() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settingsFlow.collect { settings ->
                    requestedOrientation = when (settings.forceOrientation) {
                        "Portrait" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                        "Landscape" -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    }
                }
            }
        }
    }

    private fun observeScreenTimeout() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settingsFlow.collect { settings ->
                    if (settings.preventScreenTimeout) {
                        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
        }
    }

    private fun observeSystemBars() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Fix 5: searchState removed from combine; clearSearchHighlight() is now owned
                //         by NavigatorController's own observer on searchState.isInNavMode.
                combine(
                    viewModel.controlsState,
                    viewModel.settingsFlow
                ) { controls, settings ->
                    controls to settings
                }.collect { (controls, settings) ->
                    val windowInsetsController =
                        WindowCompat.getInsetsController(window, window.decorView)
                    if (controls.showControls || settings.alwaysShowStatusBar) {
                        windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    } else {
                        windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                        windowInsetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                    }
                }
            }
        }
    }

    // Fix 6: keep systemDarkThemeFlow current when the user switches the system theme at runtime
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.systemDarkThemeFlow.value = isNightMode(newConfig)
    }

    private fun isNightMode(config: Configuration): Boolean =
        (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}
