@file:OptIn(
    org.readium.r2.shared.ExperimentalReadiumApi::class,
)

package com.javierreansyah.pinecone.reader.pdf

import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.theme.PineconeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfDocumentFragment
import org.readium.r2.navigator.pdf.PdfDocumentFragmentInput
import org.readium.r2.shared.util.data.ReadError
import kotlin.time.Duration.Companion.milliseconds

private val PdfInvertColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

class MuPdfDocumentFragment(
    private val input: PdfDocumentFragmentInput<MuPdfSettings>,
    private val searchHighlights: StateFlow<PdfSearchHighlights> = MutableStateFlow(
        PdfSearchHighlights()
    ),
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
                    val prefs by settings.collectAsStateWithLifecycle()
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
                                            } finally {
                                                page.destroy()
                                            }
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
    private fun Pages(
        pdf: MuPdfDocument,
        preferences: MuPdfPreferences,
        aspectRatios: List<Float>
    ) {
        val request by requestedPage.collectAsStateWithLifecycle()
        val highlights by searchHighlights.collectAsStateWithLifecycle()
        val matchesByPage = remember(highlights.matches) {
            highlights.matches.filter { it.href == input.href.toString() }.groupBy { it.pageIndex }
        }
        val initial =
            currentPage.collectAsStateWithLifecycle().value.coerceIn(
                0,
                (pdf.pageCount - 1).coerceAtLeast(0)
            )
        val pager = rememberPagerState(initialPage = initial) { pdf.pageCount }
        val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
        LaunchedEffect(
            request,
            preferences.scroll,
            preferences.horizontal,
            preferences.rightToLeft
        ) {
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
        val background = if (preferences.invert == true) Color.Black else Color.White
        val modifier = Modifier
            .fillMaxSize()
            .background(background)
        val spacing = preferences.pageSpacing.dp
        val reverse = preferences.horizontal && preferences.rightToLeft
        val topInset = WindowInsets.statusBarsIgnoringVisibility
            .union(WindowInsets.displayCutout)
            .asPaddingValues()
            .calculateTopPadding() * 2
        val scrollPage = preferences.fitWidth && (!preferences.scroll || preferences.horizontal)
        val scrollBy: (Float) -> Float = remember(list, pager, preferences.scroll) {
            { delta ->
                if (preferences.scroll) list.dispatchRawDelta(delta)
                else pager.dispatchRawDelta(delta)
            }
        }
        val containerWidthPx = LocalWindowInfo.current.containerSize.width
        val containerWidthDp = with(LocalDensity.current) { containerWidthPx.toDp() }
        val baseRenderWidth = if (containerWidthPx > 0) {
            containerWidthPx.coerceIn(720, 1800)
        } else {
            1400
        }
        PdfNavigatorZoom(
            horizontal = preferences.horizontal,
            reverse = reverse,
            navigationKey = request,
            selectionController = selectionController,
            scrollBy = scrollBy,
        ) { zoomState, onDoubleTap ->
            if (preferences.scroll && preferences.horizontal) {
                LazyRow(
                    modifier,
                    state = list,
                    reverseLayout = reverse,
                    contentPadding = if (scrollPage) PaddingValues(0.dp) else PaddingValues(top = topInset),
                    horizontalArrangement = Arrangement.spacedBy(spacing)
                ) {
                    items(pdf.pageCount) { index ->
                        PageImage(
                            pdf,
                            index,
                            preferences,
                            Modifier.width(containerWidthDp),
                            aspectRatios[index],
                            matchesByPage[index].orEmpty(), highlights.focusedIndex,
                            baseRenderWidth,
                            zoomState,
                            onDoubleTap,
                            topInset = if (scrollPage) topInset else 0.dp,
                        )
                    }
                }
            } else if (preferences.scroll) {
                LazyColumn(
                    modifier,
                    state = list,
                    verticalArrangement = Arrangement.spacedBy(spacing),
                    contentPadding = PaddingValues(top = topInset, bottom = spacing)
                ) {
                    items(pdf.pageCount) { index ->
                        PageImage(
                            pdf,
                            index,
                            preferences,
                            Modifier.fillMaxWidth(),
                            aspectRatios[index],
                            matchesByPage[index].orEmpty(), highlights.focusedIndex,
                            baseRenderWidth,
                            zoomState,
                            onDoubleTap,
                        )
                    }
                }
            } else if (preferences.horizontal) {
                HorizontalPager(
                    pager,
                    modifier,
                    contentPadding = if (scrollPage) PaddingValues(0.dp) else PaddingValues(top = topInset),
                    reverseLayout = reverse,
                    pageSpacing = spacing
                ) { index ->
                    PageImage(
                        pdf, index, preferences, Modifier.fillMaxSize(), aspectRatios[index],
                        matchesByPage[index].orEmpty(), highlights.focusedIndex,
                        baseRenderWidth, zoomState, onDoubleTap,
                        topInset = if (scrollPage) topInset else 0.dp,
                    )
                }
            } else {
                VerticalPager(
                    pager,
                    modifier,
                    contentPadding = if (scrollPage) PaddingValues(0.dp) else PaddingValues(top = topInset),
                    pageSpacing = spacing
                ) { index ->
                    PageImage(
                        pdf, index, preferences, Modifier.fillMaxSize(), aspectRatios[index],
                        matchesByPage[index].orEmpty(), highlights.focusedIndex,
                        baseRenderWidth, zoomState, onDoubleTap,
                        topInset = if (scrollPage) topInset else 0.dp,
                    )
                }
            }
        }
    }

    private data class RenderedPdfTile(
        val imageBitmap: ImageBitmap,
        val region: PdfTileRegion,
        val fullPageWidthPx: Float,
    )

    @Composable
    private fun PageImage(
        pdf: MuPdfDocument,
        index: Int,
        preferences: MuPdfPreferences,
        modifier: Modifier,
        aspectRatio: Float,
        matches: List<PdfSearchMatch>,
        focusedIndex: Int?,
        baseRenderWidth: Int,
        zoomState: PdfZoomViewportState,
        onDoubleTap: () -> Unit,
        topInset: Dp = 0.dp,
    ) {
        var error by remember(index) { mutableStateOf<String?>(null) }
        val bitmap by produceState<android.graphics.Bitmap?>(null, pdf, index, baseRenderWidth) {
            try {
                value = pdf.render(index, width = baseRenderWidth)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                timber.log.Timber.e(failure, "Could not render PDF page")
                error = getString(R.string.reader_page_render_failed)
            }
        }
        var renderedTile by remember(pdf, index) { mutableStateOf<RenderedPdfTile?>(null) }
        var boxBoundsInContent by remember(index) { mutableStateOf<Rect?>(null) }

        LaunchedEffect(pdf, index, bitmap, zoomState) {
            val baseBitmap = bitmap ?: run {
                renderedTile = null
                return@LaunchedEffect
            }
            snapshotFlow {
                val boxBounds = boxBoundsInContent
                val transform = zoomState.transform
                val viewportSize = zoomState.viewportSize
                if (boxBounds == null) {
                    null
                } else {
                    val imageViewport = pdfImageViewport(
                        boxBounds.width,
                        boxBounds.height,
                        baseBitmap.width,
                        baseBitmap.height,
                    )
                    val pageBoundsInContent = Rect(
                        left = boxBounds.left + imageViewport.left,
                        top = boxBounds.top + imageViewport.top,
                        right = boxBounds.left + imageViewport.left + imageViewport.width,
                        bottom = boxBounds.top + imageViewport.top + imageViewport.height,
                    )
                    calculatePdfTilePlan(
                        pageBoundsInContent = pageBoundsInContent,
                        viewportSize = viewportSize,
                        transform = transform,
                        baseBitmapWidth = baseBitmap.width,
                    )
                }
            }.distinctUntilChanged().collectLatest { plan ->
                if (plan == null) {
                    renderedTile = null
                    return@collectLatest
                }
                val current = renderedTile
                if (
                    current != null &&
                    shouldReusePdfTile(current.region, current.fullPageWidthPx, plan)
                ) {
                    return@collectLatest
                }
                delay(120.milliseconds)
                try {
                    val region = plan.request.region
                    val tileBitmap = pdf.renderRegion(
                        index = index,
                        left = region.left,
                        top = region.top,
                        right = region.right,
                        bottom = region.bottom,
                        width = plan.request.widthPx,
                        height = plan.request.heightPx,
                    )
                    renderedTile = RenderedPdfTile(
                        imageBitmap = tileBitmap.asImageBitmap(),
                        region = region,
                        fullPageWidthPx = plan.request.fullPageWidthPx,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    timber.log.Timber.w(failure, "Could not render zoomed PDF tile")
                }
            }
        }

        val imageModifier = if (preferences.scroll && !preferences.horizontal) {
            modifier.aspectRatio(aspectRatio)
        } else modifier
        Box(
            imageModifier
                .clipToBounds()
                .pointerInput(index, onDoubleTap) {
                    detectTapGestures(
                        onTap = { input.inputListener?.onTap(TapEvent(PointF(it.x, it.y))) },
                        onDoubleTap = { onDoubleTap() },
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            bitmap?.let {
                val baseImage = remember(it) { it.asImageBitmap() }
                val filter = if (preferences.invert == true) PdfInvertColorFilter else null
                val scrollPage =
                    preferences.fitWidth && (!preferences.scroll || preferences.horizontal)
                val viewport = if (scrollPage) Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()) else Modifier.fillMaxSize()
                Box(viewport, contentAlignment = Alignment.TopCenter) {
                    val image = if (scrollPage) Modifier
                        .padding(top = topInset)
                        .fillMaxWidth()
                        .aspectRatio(it.width.toFloat() / it.height) else Modifier.fillMaxSize()
                    val pageBorderColor = if (preferences.invert == true) {
                        Color.White.copy(alpha = 0.14f)
                    } else {
                        Color.Black.copy(alpha = 0.12f)
                    }
                    // The navigator transforms the bitmap and overlays together.
                    // Keep yellow outside the bitmap's inversion filter for dark PDF mode.
                    Box(
                        image
                            .onGloballyPositioned { coords ->
                                val contentCoords = zoomState.contentCoordinates
                                boxBoundsInContent = if (
                                    contentCoords != null &&
                                    contentCoords.isAttached &&
                                    coords.isAttached
                                ) {
                                    contentCoords.localBoundingBoxOf(coords, clipBounds = false)
                                } else {
                                    null
                                }
                            }
                            .drawWithContent {
                                drawContent()
                                val viewport =
                                    pdfImageViewport(size.width, size.height, it.width, it.height)
                                val strokeWidth = 1.dp.toPx()
                                val halfStroke = strokeWidth / 2f
                                drawRect(
                                    color = pageBorderColor,
                                    topLeft = Offset(
                                        viewport.left + halfStroke,
                                        viewport.top + halfStroke,
                                    ),
                                    size = Size(
                                        (viewport.width - strokeWidth).coerceAtLeast(0f),
                                        (viewport.height - strokeWidth).coerceAtLeast(0f),
                                    ),
                                    style = Stroke(width = strokeWidth),
                                )
                            },
                    ) {
                        Image(
                            baseImage,
                            stringResource(R.string.reader_page_label, index + 1),
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            colorFilter = filter,
                        )
                        renderedTile?.let { tile ->
                            Canvas(Modifier.matchParentSize()) {
                                val viewport =
                                    pdfImageViewport(size.width, size.height, it.width, it.height)
                                val dstLeft = viewport.left + tile.region.left * viewport.width
                                val dstTop = viewport.top + tile.region.top * viewport.height
                                val dstWidth =
                                    (tile.region.right - tile.region.left) * viewport.width
                                val dstHeight =
                                    (tile.region.bottom - tile.region.top) * viewport.height
                                if (dstWidth > 0f && dstHeight > 0f) {
                                    clipRect(
                                        viewport.left,
                                        viewport.top,
                                        viewport.left + viewport.width,
                                        viewport.top + viewport.height,
                                    ) {
                                        withTransform({
                                            translate(left = dstLeft, top = dstTop)
                                            scale(
                                                scaleX = dstWidth / tile.imageBitmap.width.toFloat(),
                                                scaleY = dstHeight / tile.imageBitmap.height.toFloat(),
                                                pivot = Offset.Zero,
                                            )
                                        }) {
                                            drawImage(
                                                image = tile.imageBitmap,
                                                colorFilter = filter,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        PdfTextOverlay(
                            pdf = pdf,
                            publication = input.publication,
                            href = input.href.toString(),
                            pageIndex = index,
                            imageWidth = it.width,
                            imageHeight = it.height,
                            inverted = preferences.invert == true,
                            controller = selectionController,
                            onTap = { point ->
                                input.inputListener?.onTap(
                                    TapEvent(
                                        PointF(
                                            point.x,
                                            point.y
                                        )
                                    )
                                )
                            },
                            onDoubleTap = onDoubleTap,
                        )
                        if (matches.isNotEmpty()) Canvas(Modifier.matchParentSize()) {
                            val viewport =
                                pdfImageViewport(size.width, size.height, it.width, it.height)

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
                                drawPath(
                                    path, Color.Yellow.copy(alpha = if (focused) .55f else .20f),
                                    blendMode = if (preferences.invert == true) BlendMode.Screen else BlendMode.Multiply
                                )
                            }
                            clipRect(
                                viewport.left,
                                viewport.top,
                                viewport.left + viewport.width,
                                viewport.top + viewport.height
                            ) {
                                matches.forEach { match ->
                                    if (match.resultIndex != focusedIndex) drawMatch(
                                        match,
                                        false
                                    )
                                }
                                matches.firstOrNull { match -> match.resultIndex == focusedIndex }
                                    ?.let { match -> drawMatch(match, true) }
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
        if (index !in 0..<count) return false
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
        val layoutChanged =
            before.scroll != after.scroll || before.horizontal != after.horizontal ||
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
