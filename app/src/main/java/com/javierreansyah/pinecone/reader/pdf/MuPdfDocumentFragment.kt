@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.reader.pdf

import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.theme.PineconeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfDocumentFragment
import org.readium.r2.navigator.pdf.PdfDocumentFragmentInput
import org.readium.r2.shared.util.data.ReadError

class MuPdfDocumentFragment(
    private val input: PdfDocumentFragmentInput<MuPdfSettings>,
    private val searchHighlights: StateFlow<PdfSearchHighlights> = MutableStateFlow(PdfSearchHighlights()),
    private val selectionController: PdfSelectionController = PdfSelectionController(),
) :
    PdfDocumentFragment<MuPdfSettings>() {
    private data class PageRequest(val index: Int, val sequence: Long = 0)
    private data class LoadedPdf(val document: MuPdfDocument, val aspectRatios: List<Float>)

    private val currentPage = MutableStateFlow(input.pageIndex)
    override val pageIndex: StateFlow<Int> = currentPage
    private val settings = MutableStateFlow(input.settings)
    private var document: MuPdfDocument? = null
    private val requestedPage = MutableStateFlow(PageRequest(input.pageIndex))
    private var navigationPending = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PineconeTheme(readerBackgroundColor = Color(0xFFE8E8E8)) {
                    val prefs by settings.collectAsState()
                    val pdf by produceState<LoadedPdf?>(null) {
                        val resource = input.publication.get(input.href) ?: return@produceState
                        val opened = MuPdfDocumentFactory().open(resource, null)
                        val loaded = opened.getOrNull()
                        if (loaded == null) {
                            input.navigatorListener?.onResourceLoadFailed(
                                input.href,
                                ReadError.Decoding(IllegalStateException("Cannot open PDF"))
                            )
                        }
                        if (loaded != null) {
                            try {
                                // Reserve each page's actual size before lazy layout/navigation.
                                // Small loading placeholders otherwise clamp distant jumps to an
                                // earlier page and keep that page anchored as bitmaps arrive.
                                val ratios = withContext(Dispatchers.Default) {
                                    val context = currentCoroutineContext()
                                    loaded.access { native ->
                                        List(loaded.pageCount) { index ->
                                            context.ensureActive()
                                            val page = native.loadPage(index)
                                            try {
                                                val bounds = page.bounds
                                                ((bounds.x1 - bounds.x0) / (bounds.y1 - bounds.y0))
                                                    .takeIf { it.isFinite() && it > 0f } ?: 1f
                                            } finally { page.destroy() }
                                        }
                                    }
                                }
                                document = loaded
                                value = LoadedPdf(loaded, ratios)
                                awaitCancellation()
                            } finally {
                                if (document === loaded) document = null
                                // Cleanup stays inside the owning producer coroutine and survives
                                // cancellation without blocking the UI on an in-flight render.
                                withContext(NonCancellable + Dispatchers.Default) { loaded.close() }
                            }
                        }
                    }
                    pdf?.let { Pages(it.document, prefs.preferences, it.aspectRatios) }
                        ?: Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                }
            }
        }

    @Composable
    private fun Pages(pdf: MuPdfDocument, preferences: MuPdfPreferences, aspectRatios: List<Float>) {
        val request by requestedPage.collectAsState()
        val highlights by searchHighlights.collectAsState()
        val matchesByPage = remember(highlights.matches) {
            highlights.matches.filter { it.href == input.href.toString() }.groupBy { it.pageIndex }
        }
        val initial =
            currentPage.collectAsState().value.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))
        val pager = rememberPagerState(initialPage = initial) { pdf.pageCount }
        val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
        LaunchedEffect(request, preferences.scroll, preferences.horizontal, preferences.rightToLeft) {
            if (preferences.scroll) list.scrollToItem(request.index) else pager.scrollToPage(request.index)
            if (requestedPage.value == request) {
                currentPage.value = request.index
                navigationPending = false
            }
        }
        LaunchedEffect(preferences.scroll, preferences.horizontal) {
            snapshotFlow { if (preferences.scroll) list.firstVisibleItemIndex else pager.currentPage }
                .collect {
                    if (!navigationPending && currentPage.value != it) {
                        selectionController.clearSelection()
                        currentPage.value = it
                    }
                }
        }
        val background = if (preferences.invert == true) Color(0xFF171717) else Color(0xFFE8E8E8)
        val modifier = Modifier
            .fillMaxSize()
            .background(background)
        val spacing = preferences.pageSpacing.dp
        val reverse = preferences.horizontal && preferences.rightToLeft
        if (preferences.scroll && preferences.horizontal) {
            LazyRow(
                modifier,
                state = list,
                reverseLayout = reverse,
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                items(pdf.pageCount) { index ->
                    PageImage(
                        pdf,
                        index,
                        preferences,
                        Modifier.width((LocalWindowInfo.current.containerSize.width / resources.displayMetrics.density).dp),
                        aspectRatios[index],
                        matchesByPage[index].orEmpty(), highlights.focusedIndex,
                    )
                }
            }
        } else if (preferences.scroll) {
            LazyColumn(
                modifier,
                state = list,
                verticalArrangement = Arrangement.spacedBy(spacing),
                contentPadding = PaddingValues(vertical = spacing)
            ) {
                items(pdf.pageCount) { index ->
                    PageImage(
                        pdf,
                        index,
                        preferences,
                        Modifier.fillMaxWidth(),
                        aspectRatios[index],
                        matchesByPage[index].orEmpty(), highlights.focusedIndex,
                    )
                }
            }
        } else if (preferences.horizontal) {
            HorizontalPager(
                pager,
                modifier,
                reverseLayout = reverse,
                pageSpacing = spacing
            ) { index ->
                PageImage(pdf, index, preferences, Modifier.fillMaxSize(), aspectRatios[index],
                    matchesByPage[index].orEmpty(), highlights.focusedIndex)
            }
        } else {
            VerticalPager(pager, modifier, pageSpacing = spacing) { index ->
                PageImage(pdf, index, preferences, Modifier.fillMaxSize(), aspectRatios[index],
                    matchesByPage[index].orEmpty(), highlights.focusedIndex)
            }
        }
    }

    @Composable
    private fun PageImage(
        pdf: MuPdfDocument,
        index: Int,
        preferences: MuPdfPreferences,
        modifier: Modifier,
        aspectRatio: Float,
        matches: List<PdfSearchMatch>,
        focusedIndex: Int?,
    ) {
        var error by remember(index) { mutableStateOf<String?>(null) }
        val bitmap by produceState<android.graphics.Bitmap?>(null, pdf, index) {
            try {
                value = pdf.render(index)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                timber.log.Timber.e(failure, "Could not render PDF page")
                error = getString(R.string.reader_page_render_failed)
            }
        }
        var zoom by remember(index) { mutableFloatStateOf(1f) }
        var offset by remember(index) { mutableStateOf(Offset.Zero) }
        val imageModifier = if (preferences.scroll && !preferences.horizontal) {
            modifier.aspectRatio(aspectRatio)
        } else modifier
        Box(imageModifier
            .clipToBounds()
            .pointerInput(index) {
                detectTapGestures(
                    onTap = { input.inputListener?.onTap(TapEvent(PointF(it.x, it.y))) },
                    onDoubleTap = { zoom = if (zoom > 1f) 1f else 2f; offset = Offset.Zero },
                )
            }
            .pointerInput(index) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val multiTouch = event.changes.count { it.pressed } > 1
                        if (multiTouch || (zoom > 1f && selectionController.selection.value == null)) {
                            if (multiTouch) selectionController.clearSelection()
                            val pan = event.calculatePan()
                            zoom = (zoom * event.calculateZoom()).coerceIn(1f, 4f)
                            offset = if (zoom > 1f) {
                                val maxX = size.width * (zoom - 1) / 2
                                val maxY = size.height * (zoom - 1) / 2
                                Offset(
                                    (offset.x + pan.x).coerceIn(-maxX, maxX),
                                    (offset.y + pan.y).coerceIn(-maxY, maxY)
                                )
                            } else Offset.Zero
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }, contentAlignment = Alignment.Center) {
            bitmap?.let {
                val filter = if (preferences.invert == true) ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            -1f,
                            0f,
                            0f,
                            0f,
                            255f,
                            0f,
                            -1f,
                            0f,
                            0f,
                            255f,
                            0f,
                            0f,
                            -1f,
                            0f,
                            255f,
                            0f,
                            0f,
                            0f,
                            1f,
                            0f,
                        )
                    )
                ) else null
                val scrollPage =
                    preferences.fitWidth && (!preferences.scroll || preferences.horizontal)
                val viewport = if (scrollPage) Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
                Box(viewport, contentAlignment = Alignment.TopCenter) {
                    val image = if (scrollPage) Modifier
                        .fillMaxWidth()
                        .aspectRatio(it.width.toFloat() / it.height) else Modifier.fillMaxSize()
                    // The bitmap and search overlay share sizing, scrolling, zoom and pan.
                    // Keep yellow outside the bitmap's inversion filter for dark PDF mode.
                    Box(image.graphicsLayer {
                            scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY =
                            offset.y
                        }) {
                        Image(
                            it.asImageBitmap(),
                            stringResource(R.string.reader_page_label, index + 1),
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = filter,
                        )
                        PdfTextOverlay(
                            pdf = pdf,
                            publication = input.publication,
                            href = input.href.toString(),
                            pageIndex = index,
                            imageWidth = it.width,
                            imageHeight = it.height,
                            inverted = preferences.invert == true,
                            controller = selectionController,
                            onTap = { point -> input.inputListener?.onTap(TapEvent(PointF(point.x, point.y))) },
                            onDoubleTap = { zoom = if (zoom > 1f) 1f else 2f; offset = Offset.Zero },
                        )
                        if (matches.isNotEmpty()) Canvas(Modifier.matchParentSize()) {
                            val viewport = pdfImageViewport(size.width, size.height, it.width, it.height)
                            fun Path.point(point: PdfSearchPoint, first: Boolean = false) {
                                val x = viewport.left + point.x * viewport.width
                                val y = viewport.top + point.y * viewport.height
                                if (first) moveTo(x, y) else lineTo(x, y)
                            }
                            // Focus draws last so overlapping hits keep the stronger marker.
                            fun drawMatch(match: PdfSearchMatch, focused: Boolean) {
                                val path = Path().apply {
                                    match.quads.forEach { quad ->
                                        point(quad.topLeft, first = true)
                                        point(quad.topRight); point(quad.bottomRight); point(quad.bottomLeft)
                                        close()
                                    }
                                }
                                drawPath(path, Color.Yellow.copy(alpha = if (focused) .55f else .20f),
                                    blendMode = if (preferences.invert == true) BlendMode.Screen else BlendMode.Multiply)
                            }
                            clipRect(viewport.left, viewport.top, viewport.left + viewport.width, viewport.top + viewport.height) {
                                matches.forEach { match -> if (match.resultIndex != focusedIndex) drawMatch(match, false) }
                                matches.firstOrNull { match -> match.resultIndex == focusedIndex }?.let { match -> drawMatch(match, true) }
                            }
                        }
                    }
                }
            } ?: if (error != null) Text(
                error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(24.dp)
            )
            else CircularProgressIndicator()
        }
    }

    override fun goToPageIndex(index: Int, animated: Boolean): Boolean {
        val count = document?.pageCount ?: return false
        if (index !in 0 until count) return false
        selectionController.clearSelection()
        requestPage(index)
        currentPage.value = index
        return true
    }

    private fun requestPage(index: Int) {
        navigationPending = true
        // A repeated target is still a command: the user may have swiped since the last jump.
        requestedPage.value = PageRequest(index, requestedPage.value.sequence + 1)
    }

    override fun applySettings(settings: MuPdfSettings) {
        // A late settings emission must not replace an outstanding search/page jump.
        val before = this.settings.value.preferences
        val after = settings.preferences
        val layoutChanged = before.scroll != after.scroll || before.horizontal != after.horizontal ||
            before.rightToLeft != after.rightToLeft
        if (!navigationPending && layoutChanged) requestPage(currentPage.value)
        this.settings.value = settings
    }

    override fun onDestroyView() {
        selectionController.clearSelection()
        document = null
        super.onDestroyView()
    }
}
