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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize

internal data class PdfZoomTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero)

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

/** One transform for the whole lazy navigator, including page gaps and text overlays. */
@Composable
internal fun PdfNavigatorZoom(
    horizontal: Boolean,
    reverse: Boolean,
    navigationKey: Any,
    selectionController: PdfSelectionController,
    scrollBy: (Float) -> Float,
    content: @Composable (onDoubleTap: () -> Unit) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(navigationKey, horizontal, reverse, scrollBy) {
        // Explicit page/search jumps start at the page origin without losing document zoom.
        offset = Offset.Zero
    }
    val toggleZoom: () -> Unit = remember(selectionController) {
        {
            selectionController.clearSelection()
            scale = if (scale > 1f) 1f else 2f
            offset = Offset.Zero
        }
    }
    Box(
        Modifier.fillMaxSize().clipToBounds()
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
                        if (multiTouch || (scale > 1f &&
                                selectionController.selection.value == null &&
                                totalPan.getDistance() > viewConfiguration.touchSlop)) {
                            transforming = true
                        }
                        if (transforming && event.changes.any { it.pressed && it.previousPressed }) {
                            if (multiTouch) selectionController.clearSelection()
                            val previous = PdfZoomTransform(scale, offset)
                            val next = pdfZoomTransform(
                                previous, event.calculateZoom(), pan,
                                event.calculateCentroid(useCurrent = false), size,
                            )
                            // Once the enlarged viewport's edge is reached, continue through
                            // the document rather than trapping the gesture inside one page.
                            val overflow = pan - (next.offset - offset)
                            if (!multiTouch) {
                                val delta = if (horizontal) overflow.x else overflow.y
                                scrollBy(-delta / next.scale * if (reverse) -1f else 1f)
                            }
                            scale = next.scale
                            offset = next.offset
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = scale
            scaleY = scale
            translationX = offset.x
            translationY = offset.y
        }) {
            content(toggleZoom)
        }
    }
}
