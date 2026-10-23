package com.javierreansyah.pinecone.reader.pdf

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R
import kotlinx.coroutines.CancellationException
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private data class LoadedTextLayer(val page: PdfTextPage, val base: Locator)

/** Lives in the bitmap's coordinate/zoom layer; no screen-coordinate guesses are involved. */
@Composable
internal fun PdfTextOverlay(
    pdf: MuPdfDocument,
    publication: Publication,
    href: String,
    pageIndex: Int,
    imageWidth: Int,
    imageHeight: Int,
    inverted: Boolean,
    controller: PdfSelectionController,
    onTap: (Offset) -> Unit,
    onDoubleTap: () -> Unit,
) {
    val loaded by produceState<LoadedTextLayer?>(null, pdf, href, pageIndex) {
        try {
            val text = pdf.textPage(pageIndex)
            val link = publication.readingOrder.firstOrNull {
                samePdfResource(
                    it.href.toString(),
                    href
                ) || publication.locatorFromLink(it)?.href?.toString() == href
            }
            val base = publication.positions().firstOrNull {
                samePdfResource(
                    it.href.toString(),
                    href
                ) && "page=${pageIndex + 1}" in it.locations.fragments
            } ?: link?.let(publication::locatorFromLink) ?: Locator(
                requireNotNull(Url(href)) { "Invalid PDF resource URL: $href" },
                MediaType.PDF
            )
            value = LoadedTextLayer(text, base)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            timber.log.Timber.w(error, "Could not load PDF selection text")
        }
    }
    val selectedLocator by controller.selection.collectAsState()
    val annotations by controller.annotations.collectAsState()
    val selected = remember(selectedLocator, href, pageIndex) {
        selectedLocator?.takeIf { samePdfResource(it.href.toString(), href) }
            ?.let(::pdfSelectionAnchor)
            ?.takeIf { it.pageIndex == pageIndex }
    }
    val pageAnnotations = remember(annotations, href, pageIndex) {
        annotations.mapNotNull { annotation ->
            annotation.locator.takeIf { samePdfResource(it.href.toString(), href) }
                ?.let(::pdfSelectionAnchor)
                ?.takeIf { it.pageIndex == pageIndex }?.let { annotation to it }
        }
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val viewport = pdfImageViewport(
        viewportSize.width.toFloat(),
        viewportSize.height.toFloat(),
        imageWidth,
        imageHeight
    )

    fun normalized(point: Offset) = PdfSearchPoint(
        (point.x - viewport.left) / viewport.width.coerceAtLeast(1f),
        (point.y - viewport.top) / viewport.height.coerceAtLeast(1f),
    )

    fun publish(range: PdfTextRange): Boolean {
        val layer = loaded ?: return false
        val anchor = pdfSelectionAnchor(pageIndex, layer.page, range) ?: return false
        controller.select(pdfSelectionLocator(layer.base, layer.page, anchor))
        return true
    }

    val haptic = LocalHapticFeedback.current
    val pageLabel = stringResource(R.string.reader_page_label, pageIndex + 1)

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { viewportSize = it }
            .semantics {
                contentDescription = pageLabel
                loaded?.let { layer ->
                    text = AnnotatedString(layer.page.text)
                    selected?.let { textSelectionRange = TextRange(it.start, it.end) }
                    setSelection { start, end, _ ->
                        if (start == end) {
                            controller.clearSelection()
                            true
                        } else if (min(start, end) >= 0 && max(
                                start,
                                end
                            ) <= layer.page.text.length
                        ) {
                            publish(PdfTextRange(min(start, end), max(start, end)))
                        } else false
                    }
                }
            }
            .pointerInput(pdf, pageIndex, loaded, viewportSize) {
                var initialRange: PdfTextRange? = null
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        initialRange = loaded?.let { pdfWordAt(it.page, normalized(point)) }
                        initialRange?.let {
                            publish(it)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onDrag = { change, _ ->
                        val initial = initialRange
                        val word = loaded?.let {
                            pdfWordAt(
                                it.page,
                                normalized(change.position),
                                nearest = true
                            )
                        }
                        if (initial != null && word != null) {
                            change.consume()
                            publish(
                                PdfTextRange(
                                    min(initial.start, word.start),
                                    max(initial.end, word.end)
                                )
                            )
                        }
                    },
                    onDragEnd = { initialRange = null },
                    onDragCancel = { initialRange = null },
                )
            }
            .pointerInput(pageAnnotations, viewportSize, selectedLocator) {
                detectTapGestures(
                    onTap = { point ->
                        if (controller.selection.value != null) {
                            controller.clearSelection()
                        } else {
                            val annotation =
                                pageAnnotations.asReversed().firstOrNull { (_, anchor) ->
                                    anchor.quads.any { it.contains(normalized(point)) }
                                }
                            if (annotation != null) controller.activateAnnotation(annotation.first.id) else onTap(
                                point
                            )
                        }
                    },
                    onDoubleTap = { controller.clearSelection(); onDoubleTap() },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun drawQuads(quads: List<PdfSearchQuad>, color: Color) {
                val path = Path()
                quads.forEach { quad ->
                    path.moveTo(
                        viewport.left + quad.topLeft.x * viewport.width,
                        viewport.top + quad.topLeft.y * viewport.height
                    )
                    listOf(quad.topRight, quad.bottomRight, quad.bottomLeft).forEach {
                        path.lineTo(
                            viewport.left + it.x * viewport.width,
                            viewport.top + it.y * viewport.height
                        )
                    }
                    path.close()
                }
                drawPath(
                    path,
                    color,
                    blendMode = if (inverted) BlendMode.Screen else BlendMode.Multiply
                )
            }
            clipRect(
                viewport.left,
                viewport.top,
                viewport.left + viewport.width,
                viewport.top + viewport.height
            ) {
                pageAnnotations.forEach { (annotation, anchor) ->
                    drawQuads(
                        anchor.quads,
                        Color(if (annotation.color == -1) 0x4003A9F4 else annotation.color)
                    )
                }
                selected?.let { drawQuads(it.quads, Color(0x663399FF)) }
            }
        }
        selected?.let { anchor ->
            listOf(true, false).forEach { start ->
                val point =
                    if (start) anchor.quads.first().bottomLeft else anchor.quads.last().bottomRight
                val center = Offset(
                    viewport.left + point.x * viewport.width,
                    viewport.top + point.y * viewport.height
                )
                val radiusPx = with(LocalDensity.current) { 18.dp.toPx() }
                val label =
                    stringResource(if (start) R.string.pdf_selection_start else R.string.pdf_selection_end)
                Canvas(
                    Modifier
                        .offset {
                            IntOffset(
                                (center.x - radiusPx).roundToInt(),
                                (center.y - radiusPx).roundToInt()
                            )
                        }
                        .size(36.dp)
                        .semantics { contentDescription = label }
                        .pointerInput(pdf, pageIndex, start, loaded, viewportSize) {
                            var dragPoint = Offset.Zero
                            var fixedBoundary = 0
                            detectDragGestures(
                                onDragStart = { touch ->
                                    val current =
                                        controller.selection.value?.let(::pdfSelectionAnchor)
                                    fixedBoundary =
                                        if (start) current?.end ?: anchor.end else current?.start
                                            ?: anchor.start
                                    val p =
                                        if (start) current?.quads?.first()?.bottomLeft else current?.quads?.last()?.bottomRight
                                    val handleCenter = p?.let {
                                        Offset(
                                            viewport.left + it.x * viewport.width,
                                            viewport.top + it.y * viewport.height
                                        )
                                    } ?: center
                                    dragPoint = handleCenter - Offset(radiusPx, radiusPx) + touch
                                },
                                onDrag = { change, delta ->
                                    change.consume()
                                    dragPoint += delta
                                    val glyph = loaded?.let {
                                        pdfGlyphAt(
                                            it.page,
                                            normalized(dragPoint),
                                            nearest = true
                                        )
                                    }
                                    if (glyph != null) {
                                        val boundary = if (start) glyph.start else glyph.end
                                        publish(
                                            PdfTextRange(
                                                min(boundary, fixedBoundary),
                                                max(boundary, fixedBoundary)
                                            )
                                        )
                                    }
                                },
                            )
                        },
                ) {
                    drawCircle(Color(0xFF1976D2), radius = 6.dp.toPx())
                }
            }
        }
    }
}
