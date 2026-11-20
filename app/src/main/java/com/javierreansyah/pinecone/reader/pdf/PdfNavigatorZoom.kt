package com.javierreansyah.pinecone.reader.pdf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal data class PdfZoomTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero)

internal data class PdfTileRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(other: PdfTileRegion): Boolean =
        left <= other.left && top <= other.top && right >= other.right && bottom >= other.bottom
}

internal data class PdfTileRequest(
    val region: PdfTileRegion,
    val widthPx: Int,
    val heightPx: Int,
    val fullPageWidthPx: Float,
)

internal data class PdfTilePlan(
    val visibleRegion: PdfTileRegion,
    val request: PdfTileRequest,
)

@Stable
internal class PdfZoomViewportState {
    var transform by mutableStateOf(PdfZoomTransform())
        internal set
    var viewportSize by mutableStateOf(IntSize.Zero)
        internal set
    var contentCoordinates: LayoutCoordinates? by mutableStateOf(null)
        internal set
}

/** Keep the content under the fingers stationary, then prevent panning beyond the document. */
internal fun pdfZoomTransform(
    previous: PdfZoomTransform,
    zoom: Float,
    pan: Offset,
    centroid: Offset,
    viewport: IntSize,
): PdfZoomTransform {
    val scale = (previous.scale * zoom).coerceIn(1f, 4f)
    val offset = centroid - (centroid - previous.offset) * (scale / previous.scale) + pan
    return PdfZoomTransform(
        scale,
        Offset(
            offset.x.coerceIn(-viewport.width * (scale - 1f), 0f),
            offset.y.coerceIn(-viewport.height * (scale - 1f), 0f),
        ),
    )
}

/**
 * Compute the high-resolution visible tile for a page when zoomed past its base bitmap resolution.
 * Returns null if the base bitmap is already sharp enough or the page is outside the viewport.
 */
internal fun calculatePdfTilePlan(
    pageBoundsInContent: Rect,
    viewportSize: IntSize,
    transform: PdfZoomTransform,
    baseBitmapWidth: Int,
    overscanFraction: Float = 0.12f,
    minUpscaleRatio: Float = 1.15f,
    maxTileDimensionPx: Int = 3200,
    maxTilePixels: Int = 4_500_000,
): PdfTilePlan? {
    if (
        pageBoundsInContent.width <= 0f ||
        pageBoundsInContent.height <= 0f ||
        viewportSize.width <= 0 ||
        viewportSize.height <= 0 ||
        transform.scale <= 0f ||
        baseBitmapWidth <= 0
    ) {
        return null
    }
    val desiredFullPageWidth = pageBoundsInContent.width * transform.scale
    if (desiredFullPageWidth <= baseBitmapWidth * minUpscaleRatio) return null

    val visLeft = -transform.offset.x / transform.scale
    val visTop = -transform.offset.y / transform.scale
    val visRight = (viewportSize.width - transform.offset.x) / transform.scale
    val visBottom = (viewportSize.height - transform.offset.y) / transform.scale

    val interLeft = maxOf(visLeft, pageBoundsInContent.left)
    val interTop = maxOf(visTop, pageBoundsInContent.top)
    val interRight = minOf(visRight, pageBoundsInContent.right)
    val interBottom = minOf(visBottom, pageBoundsInContent.bottom)
    if (interRight <= interLeft || interBottom <= interTop) return null

    val visibleRegion = PdfTileRegion(
        left = ((interLeft - pageBoundsInContent.left) / pageBoundsInContent.width).coerceIn(0f, 1f),
        top = ((interTop - pageBoundsInContent.top) / pageBoundsInContent.height).coerceIn(0f, 1f),
        right = ((interRight - pageBoundsInContent.left) / pageBoundsInContent.width).coerceIn(0f, 1f),
        bottom = ((interBottom - pageBoundsInContent.top) / pageBoundsInContent.height).coerceIn(0f, 1f),
    )

    val padX = (interRight - interLeft) * overscanFraction
    val padY = (interBottom - interTop) * overscanFraction
    val tileLeft = (interLeft - padX).coerceAtLeast(pageBoundsInContent.left)
    val tileTop = (interTop - padY).coerceAtLeast(pageBoundsInContent.top)
    val tileRight = (interRight + padX).coerceAtMost(pageBoundsInContent.right)
    val tileBottom = (interBottom + padY).coerceAtMost(pageBoundsInContent.bottom)

    val region = PdfTileRegion(
        left = ((tileLeft - pageBoundsInContent.left) / pageBoundsInContent.width).coerceIn(0f, 1f),
        top = ((tileTop - pageBoundsInContent.top) / pageBoundsInContent.height).coerceIn(0f, 1f),
        right = ((tileRight - pageBoundsInContent.left) / pageBoundsInContent.width).coerceIn(0f, 1f),
        bottom = ((tileBottom - pageBoundsInContent.top) / pageBoundsInContent.height).coerceIn(0f, 1f),
    )

    val rawWidth = (region.right - region.left) * pageBoundsInContent.width * transform.scale
    val rawHeight = (region.bottom - region.top) * pageBoundsInContent.height * transform.scale
    if (rawWidth < 1f || rawHeight < 1f) return null

    val maxDim = maxOf(rawWidth, rawHeight)
    val dimScale = if (maxDim > maxTileDimensionPx) maxTileDimensionPx / maxDim else 1f
    val scaledArea = rawWidth * rawHeight * dimScale * dimScale
    val areaScale = if (scaledArea > maxTilePixels) sqrt(maxTilePixels / scaledArea) else 1f
    val downscale = dimScale * areaScale

    val widthPx = (rawWidth * downscale).roundToInt().coerceIn(1, maxTileDimensionPx)
    val heightPx = (rawHeight * downscale).roundToInt().coerceIn(1, maxTileDimensionPx)

    return PdfTilePlan(
        visibleRegion = visibleRegion,
        request = PdfTileRequest(
            region = region,
            widthPx = widthPx,
            heightPx = heightPx,
            fullPageWidthPx = desiredFullPageWidth * downscale,
        ),
    )
}

internal fun shouldReusePdfTile(
    currentRegion: PdfTileRegion,
    currentFullPageWidthPx: Float,
    plan: PdfTilePlan,
): Boolean {
    if (!currentRegion.contains(plan.visibleRegion)) return false
    val targetWidth = plan.request.fullPageWidthPx
    return targetWidth in (currentFullPageWidthPx * 0.85f)..(currentFullPageWidthPx * 1.15f)
}

/** One transform for the whole lazy navigator, including page gaps and text overlays. */
@Composable
internal fun PdfNavigatorZoom(
    horizontal: Boolean,
    reverse: Boolean,
    navigationKey: Any,
    selectionController: PdfSelectionController,
    scrollBy: (Float) -> Float,
    content: @Composable (zoomState: PdfZoomViewportState, onDoubleTap: () -> Unit) -> Unit,
) {
    val zoomState = remember { PdfZoomViewportState() }
    LaunchedEffect(navigationKey, horizontal, reverse, scrollBy) {
        // Explicit page/search jumps start at the page origin without losing document zoom.
        zoomState.transform = zoomState.transform.copy(offset = Offset.Zero)
    }
    val toggleZoom: () -> Unit = remember(selectionController, zoomState) {
        {
            selectionController.clearSelection()
            val nextScale = if (zoomState.transform.scale > 1f) 1f else 2f
            zoomState.transform = PdfZoomTransform(scale = nextScale, offset = Offset.Zero)
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { zoomState.viewportSize = it }
            .pointerInput(horizontal, reverse, scrollBy) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var totalPan = Offset.Zero
                    var transforming = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val multiTouch = event.changes.count { it.pressed } > 1
                        val pan = event.calculatePan()
                        totalPan += pan
                        if (multiTouch || zoomState.transform.scale > 1f &&
                            selectionController.selection.value == null && totalPan.getDistance() > viewConfiguration.touchSlop
                        ) {
                            transforming = true
                        }
                        if (transforming && event.changes.any { it.pressed && it.previousPressed }) {
                            if (multiTouch) selectionController.clearSelection()
                            val previous = zoomState.transform
                            val next = pdfZoomTransform(
                                previous, event.calculateZoom(), pan,
                                event.calculateCentroid(useCurrent = false), size,
                            )
                            // Once the enlarged viewport's edge is reached, continue through
                            // the document rather than trapping the gesture inside one page.
                            val overflow = pan - (next.offset - previous.offset)
                            if (!multiTouch) {
                                val delta = if (horizontal) overflow.x else overflow.y
                                scrollBy(-delta / next.scale * if (reverse) -1f else 1f)
                            }
                            zoomState.transform = next
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val current = zoomState.transform
                    transformOrigin = TransformOrigin(0f, 0f)
                    scaleX = current.scale
                    scaleY = current.scale
                    translationX = current.offset.x
                    translationY = current.offset.y
                }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { zoomState.contentCoordinates = it }
            ) {
                content(zoomState, toggleZoom)
            }
        }
    }
}
