@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.ui.features.reader

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.graphics.get
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.artifex.mupdf.fitz.Document
import com.javierreansyah.pinecone.PineconeApplication
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocument
import com.javierreansyah.pinecone.reader.pdf.MuPdfDocumentFragment
import com.javierreansyah.pinecone.reader.pdf.MuPdfPreferences
import com.javierreansyah.pinecone.reader.pdf.MuPdfSettings
import com.javierreansyah.pinecone.reader.pdf.PdfSearchMatch
import com.javierreansyah.pinecone.reader.pdf.pdfImageViewport
import com.javierreansyah.pinecone.reader.pdf.pdfSelectionAnchor
import com.javierreansyah.pinecone.testing.InstrumentationLoggingRule
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.shared.publication.Locator
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@RunWith(AndroidJUnit4::class)
class PdfSearchNavigationTest {
    @get:Rule
    val logging: InstrumentationLoggingRule = InstrumentationLoggingRule()

    @Test
    fun longPressSelectsTextAndSavedHighlightCanBeReopenedAfterRecreation(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PineconeApplication
        val source = File.createTempFile("pdf-selection-", ".pdf", app.cacheDir)
        source.writeBytes(searchablePdf())
        val book = requireNotNull(app.libraryRepository.importBook(Uri.fromFile(source)))
        try {
            val native = MuPdfDocument(Document.openDocument(source.absolutePath))
            val glyphs = try {
                native.textPage(0).glyphs
            } finally {
                native.close()
            }
            val glyph = glyphs.first().quad
            ActivityScenario.launch<ReaderActivity>(
                Intent(app, ReaderActivity::class.java)
                    .putExtra(ReaderActivity.EXTRA_BOOK_ID, book.book.id)
            ).use { scenario ->
                awaitPage(scenario, 1)
                scenario.onActivity { navigator(it).submitPreferences(MuPdfPreferences(invert = false)) }
                await(scenario) {
                    navigator(it).settings.value.preferences == MuPdfPreferences(
                        invert = false
                    )
                }
                fun containsText(node: AccessibilityNodeInfo?): Boolean = node != null &&
                        (node.text?.contains("Other content") == true ||
                                (0..<node.childCount).any { containsText(node.getChild(it)) })

                suspend fun awaitTextLayer() {
                    withTimeout(20_000.milliseconds) {
                        while (!containsText(instrumentation.uiAutomation.rootInActiveWindow)) delay(
                            25.milliseconds
                        )
                    }
                }

                fun pointOnWord(quad: com.javierreansyah.pinecone.reader.pdf.PdfSearchQuad = glyph): Pair<Float, Float> {
                    var result = 0f to 0f
                    scenario.onActivity { activity ->
                        val fragment = navigator(activity).childFragmentManager.fragments
                            .filterIsInstance<MuPdfDocumentFragment>().first()
                        val view = fragment.requireView()
                        val origin = IntArray(2).also { view.getLocationOnScreen(it) }
                        val viewport =
                            pdfImageViewport(view.width.toFloat(), view.height.toFloat(), 600, 800)
                        result =
                            origin[0] + viewport.left + (quad.topLeft.x + quad.bottomRight.x) / 2 * viewport.width to origin[1] + viewport.top + (quad.topLeft.y + quad.bottomRight.y) / 2 * viewport.height
                    }
                    return result
                }

                fun touch(action: Int, down: Long, point: Pair<Float, Float>) {
                    val event = MotionEvent.obtain(
                        down,
                        SystemClock.uptimeMillis(),
                        action,
                        point.first,
                        point.second,
                        0
                    )
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    try {
                        assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                    } finally {
                        event.recycle()
                    }
                }
                awaitTextLayer()
                Timber.tag("PdfSearchNavigationTest").i("PDF_SELECTION: text layer ready")
                val point = pointOnWord()
                val down = SystemClock.uptimeMillis()
                touch(MotionEvent.ACTION_DOWN, down, point)
                delay((ViewConfiguration.getLongPressTimeout().toLong() + 150).milliseconds)
                touch(MotionEvent.ACTION_UP, down, point)
                Timber.tag("PdfSearchNavigationTest")
                    .i("PDF_SELECTION: long press sent at %s", point)
                await(scenario) {
                    ViewModelProvider(it)[ReaderNotesViewModel::class.java]
                        .selectionState.value.selectionLocator?.text?.highlight == "Other"
                }
                Timber.tag("PdfSearchNavigationTest").i("PDF_SELECTION: word selected")
                val handleLabel = app.getString(R.string.reader_pdf_selection_end)
                fun findHandle(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
                    if (node == null) return null
                    if (node.contentDescription?.toString() == handleLabel && node.isVisibleToUser) return node
                    return (0..<node.childCount).firstNotNullOfOrNull {
                        findHandle(
                            node.getChild(
                                it
                            )
                        )
                    }
                }
                withTimeout(10_000.milliseconds) {
                    while (findHandle(instrumentation.uiAutomation.rootInActiveWindow) == null) delay(
                        25.milliseconds
                    )
                }
                val handleBounds = Rect().also {
                    requireNotNull(findHandle(instrumentation.uiAutomation.rootInActiveWindow)).getBoundsInScreen(
                        it
                    )
                }
                val handleStart = handleBounds.exactCenterX() to handleBounds.exactCenterY()
                val handleEnd = pointOnWord(glyphs.first { it.start == 12 }.quad)
                val dragDown = SystemClock.uptimeMillis()
                touch(MotionEvent.ACTION_DOWN, dragDown, handleStart)
                repeat(12) { step ->
                    delay(25.milliseconds)
                    val fraction = (step + 1) / 12f
                    touch(
                        MotionEvent.ACTION_MOVE, dragDown,
                        handleStart.first + (handleEnd.first - handleStart.first) * fraction to handleStart.second + (handleEnd.second - handleStart.second) * fraction
                    )
                }
                touch(MotionEvent.ACTION_UP, dragDown, handleEnd)
                await(scenario) {
                    ViewModelProvider(it)[ReaderNotesViewModel::class.java]
                        .selectionState.value.selectionLocator?.text?.highlight == "Other content"
                }
                Timber.tag("PdfSearchNavigationTest").i("PDF_SELECTION: handle extended the range")
                scenario.onActivity { activity ->
                    val notes = ViewModelProvider(activity)[ReaderNotesViewModel::class.java]
                    val locator = requireNotNull(notes.selectionState.value.selectionLocator)
                    assertEquals(0, requireNotNull(pdfSelectionAnchor(locator)).pageIndex)
                    notes.addHighlight(locator, 0x40FAC02E, "Page 1")
                    notes.hideSelectionMenu()
                }
                val note = withTimeout(10_000.milliseconds) {
                    app.libraryRepository.getNotes(book.book.id).first { it.isNotEmpty() }.single()
                }
                val saved = requireNotNull(Locator.fromJSON(JSONObject(note.locatorJson)))
                Timber.tag("PdfSearchNavigationTest").i("PDF_SELECTION: highlight saved")
                assertEquals("Other content", saved.text.highlight)
                assertTrue(requireNotNull(pdfSelectionAnchor(saved)).quads.isNotEmpty())
                scenario.recreate()
                awaitPage(scenario, 1)
                awaitTextLayer()
                await(scenario) { ViewModelProvider(it)[ReaderNotesViewModel::class.java].allNotesAndHighlights.value.size == 1 }
                val reopenedPoint = pointOnWord()
                val tapDown = SystemClock.uptimeMillis()
                touch(MotionEvent.ACTION_DOWN, tapDown, reopenedPoint)
                touch(MotionEvent.ACTION_UP, tapDown, reopenedPoint)
                await(scenario) {
                    ViewModelProvider(it)[ReaderNotesViewModel::class.java]
                        .selectionState.value.viewingHighlight?.id == note.id
                }
            }
        } finally {
            app.libraryRepository.deleteBook(book.book.id)
            source.delete()
        }
    }

    @Test
    fun focusedMatchIsStrongerYellowAndFocusMovesBetweenMatchesOnTheSamePage(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PineconeApplication
        val source = File.createTempFile("pdf-search-highlights-", ".pdf", app.cacheDir)
        source.writeBytes(searchablePdf(repeatedMatches = true))
        val book = requireNotNull(app.libraryRepository.importBook(Uri.fromFile(source)))
        try {
            ActivityScenario.launch<ReaderActivity>(
                Intent(app, ReaderActivity::class.java)
                    .putExtra(ReaderActivity.EXTRA_BOOK_ID, book.book.id)
            ).use { scenario ->
                awaitPage(scenario, 1)
                val preferences = MuPdfPreferences(invert = false)
                scenario.onActivity { navigator(it).submitPreferences(preferences) }
                await(scenario) { navigator(it).settings.value.preferences == preferences }
                search(scenario, expectedCount = 4)
                scenario.onActivity { activity ->
                    val matches =
                        searchModel(activity).searchState.value.results.map { requireNotNull(it.pdfMatch) }
                    assertEquals(listOf(1, 1, 3, 5), matches.map { it.pageIndex })
                    matches.forEach { match ->
                        assertTrue(match.quads.isNotEmpty())
                        match.quads.forEach { quad ->
                            listOf(
                                quad.topLeft,
                                quad.topRight,
                                quad.bottomRight,
                                quad.bottomLeft
                            ).forEach { point ->
                                assertTrue(point.x in 0f..1f && point.y in 0f..1f)
                            }
                        }
                    }
                }
                select(scenario, 0)
                awaitPage(scenario, 2)
                awaitMarkers(scenario, focused = 0, screenshot = "pdf-search-focus-first.png")
                scenario.onActivity { searchModel(it).nextSearchResult() }
                awaitPage(scenario, 2)
                awaitMarkers(scenario, focused = 1, screenshot = "pdf-search-focus-next.png")
                scenario.onActivity { searchModel(it).prevSearchResult() }
                awaitMarkers(scenario, focused = 0)
                scenario.onActivity { navigator(it).submitPreferences(preferences.copy(invert = true)) }
                await(scenario) { navigator(it).settings.value.preferences.invert == true }
                awaitMarkers(
                    scenario,
                    focused = 0,
                    inverted = true,
                    screenshot = "pdf-search-focus-dark.png"
                )
                scenario.onActivity { navigator(it).submitPreferences(preferences) }
                awaitMarkers(scenario, focused = 0)
                scenario.onActivity { searchModel(it).exitSearchNavigation() }
                awaitMarkers(scenario, focused = null, cleared = true)
            }
        } finally {
            app.libraryRepository.deleteBook(book.book.id)
            source.delete()
        }
    }

    @Test
    fun selectingAndSteppingThroughResultsMovesTheVisiblePdfPage(): Unit = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PineconeApplication
        val source = File.createTempFile("pdf-search-navigation-", ".pdf", app.cacheDir)
        source.writeBytes(searchablePdf())
        val book = requireNotNull(app.libraryRepository.importBook(Uri.fromFile(source)))
        try {
            val intent = Intent(app, ReaderActivity::class.java)
                .putExtra(ReaderActivity.EXTRA_BOOK_ID, book.book.id)
            ActivityScenario.launch<ReaderActivity>(intent).use { scenario ->
                awaitPage(scenario, 1)
                search(scenario)
                val layouts = listOf(
                    MuPdfPreferences(),
                    MuPdfPreferences(horizontal = true),
                    MuPdfPreferences(),
                    MuPdfPreferences(horizontal = true, rightToLeft = true),
                )
                for (preferences in layouts) {
                    scenario.onActivity { activity ->
                        navigator(activity).submitPreferences(
                            preferences
                        )
                    }
                    await(scenario) { navigator(it).settings.value.preferences == preferences }
                    select(scenario, 1)
                    awaitPage(scenario, 4)
                    scenario.onActivity { searchModel(it).nextSearchResult() }
                    awaitPage(scenario, 6)
                    scenario.onActivity { searchModel(it).prevSearchResult() }
                    awaitPage(scenario, 4)
                    scenario.onActivity { searchModel(it).prevSearchResult() }
                    awaitPage(scenario, 2)
                    scenario.onActivity {
                        assertEquals(
                            0,
                            searchModel(it).searchState.value.activeIndex
                        )
                    }
                }

                // The old activity's collector must be disposed and the replacement subscribed.
                scenario.recreate()
                awaitPage(scenario, 2)
                search(scenario)
                select(scenario, 1)
                awaitPage(scenario, 4)
                scenario.onActivity { searchModel(it).nextSearchResult() }
                awaitPage(scenario, 6)
            }
        } finally {
            app.libraryRepository.deleteBook(book.book.id)
            source.delete()
        }
    }

    private fun searchModel(activity: ReaderActivity): ReaderSearchViewModel =
        ViewModelProvider(activity)[ReaderSearchViewModel::class.java]

    @Suppress("UNCHECKED_CAST")
    private fun navigator(activity: ReaderActivity) =
        activity.supportFragmentManager.findFragmentByTag("pdf_navigator") as PdfNavigatorFragment<MuPdfSettings, MuPdfPreferences>

    private suspend fun search(scenario: ActivityScenario<ReaderActivity>, expectedCount: Int = 3) {
        Timber.tag("PdfSearchNavigationTest").i("PDF_SEARCH_NAV: searching")
        scenario.onActivity { activity ->
            val reader = ViewModelProvider(activity)[ReaderViewModel::class.java]
            searchModel(activity).performSearch(
                "pdfsearchneedle",
                reader.publication.value,
                reader.positions.value
            )
        }
        await(scenario) { activity ->
            val state = searchModel(activity).searchState.value
            !state.isLoading && state.results.size == expectedCount
        }
    }

    private fun select(scenario: ActivityScenario<ReaderActivity>, index: Int) {
        scenario.onActivity { searchModel(it).selectSearchResult(index) }
    }

    private suspend fun await(
        scenario: ActivityScenario<ReaderActivity>,
        condition: (ReaderActivity) -> Boolean
    ) {
        withTimeout(20_000.milliseconds) {
            while (true) {
                var ready = false
                scenario.onActivity { ready = condition(it) }
                if (ready) break
                delay(25.milliseconds)
            }
        }
    }

    private suspend fun awaitPage(scenario: ActivityScenario<ReaderActivity>, page: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val label = instrumentation.targetContext.getString(R.string.reader_page_label, page)
        Timber.tag("PdfSearchNavigationTest").i("PDF_SEARCH_NAV: waiting for visible %s", label)
        var reported: Int? = null
        try {
            withTimeout(20_000.milliseconds) {
                while (true) {
                    var current = false
                    scenario.onActivity { activity ->
                        val fragment =
                            activity.supportFragmentManager.findFragmentByTag("pdf_navigator")
                                ?.childFragmentManager?.findFragmentByTag("documentFragment") as? MuPdfDocumentFragment
                        reported = fragment?.pageIndex?.value
                        current = reported == page - 1
                    }
                    if (current && visiblePage(
                            instrumentation.uiAutomation.rootInActiveWindow,
                            label
                        )
                    ) break
                    delay(25.milliseconds)
                }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("Expected visible $label, reported page index=$reported", timeout)
        }
    }

    private fun visiblePage(node: AccessibilityNodeInfo?, label: String): Boolean {
        if (node == null) return false
        if (node.isVisibleToUser && node.contentDescription?.toString() == label) return true
        return (0..<node.childCount).any { visiblePage(node.getChild(it), label) }
    }

    private var markerRegions: List<Rect> = emptyList()
    private var backgroundPixel = 0 to 0

    private suspend fun awaitMarkers(
        scenario: ActivityScenario<ReaderActivity>, focused: Int?, cleared: Boolean = false,
        inverted: Boolean = false, screenshot: String? = null,
    ) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        if (!cleared) scenario.onActivity { activity ->
            val fragment = requireNotNull(
                navigator(activity).childFragmentManager.fragments.filterIsInstance<MuPdfDocumentFragment>()
                    .firstOrNull()
            )
            val view = fragment.requireView()
            val origin = IntArray(2).also { view.getLocationOnScreen(it) }
            val viewport = pdfImageViewport(view.width.toFloat(), view.height.toFloat(), 600, 800)
            backgroundPixel = (origin[0] + viewport.left + viewport.width * .8f).toInt() to
                    (origin[1] + viewport.top + viewport.height * .8f).toInt()
            markerRegions = searchModel(activity).searchState.value.results.take(2).map { result ->
                val match: PdfSearchMatch = requireNotNull(result.pdfMatch)
                val points = match.quads.flatMap {
                    listOf(
                        it.topLeft,
                        it.topRight,
                        it.bottomRight,
                        it.bottomLeft
                    )
                }
                Rect(
                    (origin[0] + viewport.left + points.minOf { it.x } * viewport.width).toInt() + 2,
                    (origin[1] + viewport.top + points.minOf { it.y } * viewport.height).toInt() + 2,
                    (origin[0] + viewport.left + points.maxOf { it.x } * viewport.width).toInt() - 2,
                    (origin[1] + viewport.top + points.maxOf { it.y } * viewport.height).toInt() - 2,
                )
            }
        }
        withTimeout(10_000.milliseconds) {
            while (true) {
                val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                try {
                    val background = image[backgroundPixel.first, backgroundPixel.second]
                    val backgroundReady =
                        if (inverted) Color.red(background) < 20 && Color.green(background) < 20
                        else Color.red(background) > 240 && Color.green(background) > 240
                    val valid = backgroundReady && markerRegions.mapIndexed { index, region ->
                        var strong = 0
                        var faint = 0
                        for (y in region.top..<region.bottom) for (x in region.left..<region.right) {
                            val pixel = image[x, y]
                            if (inverted && Color.blue(pixel) < 20) {
                                if (Color.red(pixel) in 120..165 && Color.green(pixel) in 120..165) strong++
                                if (Color.red(pixel) in 35..70 && Color.green(pixel) in 35..70) faint++
                            } else if (!inverted && Color.red(pixel) > 240 && Color.green(pixel) > 240) {
                                if (Color.blue(pixel) in 80..160) strong++
                                if (Color.blue(pixel) in 180..230) faint++
                            }
                        }
                        when {
                            cleared -> strong == 0 && faint == 0
                            index == focused -> strong > 100 && faint == 0
                            else -> faint > 100 && strong == 0
                        }
                    }.all { it }
                    if (valid) {
                        if (screenshot != null) File(
                            instrumentation.targetContext.cacheDir,
                            screenshot
                        ).outputStream().use {
                            image.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                        break
                    }
                } finally {
                    image.recycle()
                }
                delay(50.milliseconds)
            }
        }
    }

    private fun searchablePdf(repeatedMatches: Boolean = false): ByteArray {
        val pages = 8
        val objects = mutableListOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [${(0..<pages).joinToString(" ") { "${4 + it * 2} 0 R" }}] /Count $pages >>",
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        )
        val unique = UUID.randomUUID().toString()
        repeat(pages) { index ->
            val text = if (index in listOf(
                    1,
                    3,
                    5
                )
            ) "pdfsearchneedle on page ${index + 1}" else "Other content $unique"
            val content = "BT /F1 18 Tf 50 700 Td ($text) Tj ET\n" +
                    if (repeatedMatches && index == 1) "BT /F1 18 Tf 50 600 Td (PDFSEARCHNEEDLE again on this page) Tj ET\n" else ""
            objects += "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 600 800] " +
                    "/Resources << /Font << /F1 3 0 R >> >> /Contents ${5 + index * 2} 0 R >>"
            objects += "<< /Length ${content.length} >>\nstream\n${content}endstream"
        }
        val output = ByteArrayOutputStream()
        fun write(value: String) = output.write(value.toByteArray(Charsets.US_ASCII))
        write("%PDF-1.4\n")
        val offsets = objects.mapIndexed { index, value ->
            output.size().also { write("${index + 1} 0 obj\n$value\nendobj\n") }
        }
        val xref = output.size()
        write("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { write(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        write("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return output.toByteArray()
    }
}
