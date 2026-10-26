package com.javierreansyah.pinecone.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.get
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.javierreansyah.pinecone.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap

@RunWith(AndroidJUnit4::class)
class ScreenNavDisplayTest {
    @Test
    fun consecutiveNestedBackGesturesKeepEachOutgoingScreenAboveItsParent() {
        val probe = Probe(listOf("first", "second", "third"))
        launch(probe).use { scenario ->
            await { probe.models["third"] != null }
            val thirdModel = probe.models.getValue("third")
            scenario.onActivity { probe.dispatcher.onBack() }
            await { probe.stack.size == 2 && thirdModel.cleared }

            scenario.onActivity {
                it.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
                it.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.6f))
            }
            await { "first" in probe.composed }
            // Verify drawing order, not just the back stack: the second page must still cover
            // the center while its parent is visible through the gap around its scaled surface.
            await {
                val screenshot =
                    InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                try {
                    screenshot[screenshot.width / 2, screenshot.height / 2] == Color.Green.toArgb() &&
                            screenshot[1, screenshot.height / 2] == Color.Red.toArgb()
                } finally {
                    screenshot.recycle()
                }
            }
            val secondModel = probe.models.getValue("second")
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await { probe.stack.size == 1 && secondModel.cleared }
        }
    }

    @Test
    fun cancelledPreviewRetainsTheEntryAndCommittedBackClearsItAfterTheFlight() {
        val probe = Probe()
        launch(probe).use { scenario ->
            await { probe.models["second"] != null }
            val model = probe.models.getValue("second")
            scenario.onActivity {
                it.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f))
                it.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.6f))
            }
            await { probe.models["first"] != null }
            assertEquals(listOf("first", "second"), probe.stack.toList())
            assertFalse(model.cleared)

            scenario.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
            await { "first" !in probe.composed }
            assertSame(model, probe.models["second"])
            assertFalse(model.cleared)

            scenario.onActivity {
                it.onBackPressedDispatcher.dispatchOnBackStarted(backEvent(0f, BackEventCompat.EDGE_RIGHT))
                it.onBackPressedDispatcher.dispatchOnBackProgressed(backEvent(0.6f, BackEventCompat.EDGE_RIGHT))
            }
            await { "first" in probe.composed }
            scenario.onActivity {
                it.onBackPressedDispatcher.onBackPressed()
                assertEquals(2, probe.stack.size)
                assertFalse(model.cleared)
            }
            await { probe.stack.size == 1 && model.cleared }
            assertEquals("first", probe.stack.single())
        }
    }

    @Test
    fun localBackWinsAndRepeatedToolbarBackDoesNotPopTwice() {
        val probe = Probe()
        launch(probe).use { scenario ->
            await { probe.models["second"] != null }
            scenario.onActivity { probe.intercept.value = true }
            await { probe.localBackReady }
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            await { probe.intercepted == 1 }
            assertEquals(2, probe.stack.size)

            scenario.onActivity {
                probe.dispatcher.onBack()
                probe.dispatcher.onBack()
                probe.dispatcher.onBack()
            }
            await { probe.stack.size == 1 }
            assertEquals(1, probe.pops)
        }
    }

    private fun launch(probe: Probe): ActivityScenario<MainActivity> =
        ActivityScenario.launch(MainActivity::class.java).also { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        ScreenNavDisplay(
                            backStack = probe.stack,
                            backDispatcher = probe.dispatcher,
                            onBack = {
                                probe.pops++
                                if (probe.stack.size > 1) probe.stack.removeAt(probe.stack.lastIndex)
                            },
                            entryDecorators = listOf(
                                rememberSaveableStateHolderNavEntryDecorator(),
                                rememberViewModelStoreNavEntryDecorator(),
                            ),
                        ) { key ->
                            NavEntry(key) { entryKey ->
                                val model: EntryModel = viewModel(factory = EntryModel.Factory)
                                probe.models[entryKey] = model
                                DisposableEffect(entryKey) {
                                    probe.composed.add(entryKey)
                                    onDispose { probe.composed.remove(entryKey) }
                                }
                                BackHandler(probe.intercept.value && entryKey == "second") {
                                    probe.intercepted++
                                }
                                SideEffect {
                                    if (entryKey == "second") probe.localBackReady =
                                        probe.intercept.value
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            when (entryKey) {
                                                "first" -> Color.Red
                                                "second" -> Color.Green
                                                else -> Color.Blue
                                            }
                                        )
                                ) {
                                    Text(entryKey)
                                }
                            }
                        }
                    }
                }
            }
        }

    private fun backEvent(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT) =
        BackEventCompat(0f, 500f, progress, edge)

    private fun await(condition: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 5_000L
        while (!condition() && android.os.SystemClock.uptimeMillis() < deadline) Thread.sleep(20)
        assertTrue("Navigation did not reach the expected state", condition())
    }

    private class Probe(initialStack: List<String> = listOf("first", "second")) {
        val stack = mutableStateListOf(*initialStack.toTypedArray())
        val dispatcher = ScreenBackDispatcher()
        val intercept = mutableStateOf(false)
        val models = ConcurrentHashMap<String, EntryModel>()
        val composed: MutableSet<String> = ConcurrentHashMap.newKeySet()

        @Volatile
        var intercepted = 0

        @Volatile
        var pops = 0

        @Volatile
        var localBackReady = false
    }

    private class EntryModel : ViewModel() {
        @Volatile
        var cleared = false
        override fun onCleared() {
            cleared = true
        }

        object Factory : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return EntryModel() as T
            }
        }
    }
}
