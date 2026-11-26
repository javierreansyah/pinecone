package com.javierreansyah.pinecone.ui.features.reader.components

import android.annotation.SuppressLint
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Invert_colors
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.AppBarButtonSize
import com.javierreansyah.pinecone.ui.components.TopAppBarCircleIconButton
import com.javierreansyah.pinecone.ui.components.topBarFadeBackground
import kotlinx.coroutines.launch
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.content.Content
import org.readium.r2.shared.util.AbsoluteUrl
import java.util.Locale
import android.graphics.Color as AndroidColor

sealed interface ReaderImageContent {
    data object Loading : ReaderImageContent
    data class Bitmap(val image: ImageBitmap) : ReaderImageContent
    data class Svg(val sanitizedSvg: String) : ReaderImageContent
    data object Error : ReaderImageContent
}

data class ImageViewerState(
    val content: ReaderImageContent = ReaderImageContent.Loading,
    val caption: String? = null,
    val inverted: Boolean = false,
)

private val ImageViewerInvertColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

internal data class ImageZoomTransform(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
)

internal const val IMAGE_VIEWER_MIN_SCALE = 1f
internal const val IMAGE_VIEWER_MAX_SCALE = 5f
internal const val IMAGE_VIEWER_DOUBLE_TAP_SCALE = 2.5f

internal fun isSvgLink(href: String, mediaType: String?): Boolean {
    if (mediaType?.lowercase(Locale.ROOT) == "image/svg+xml") return true
    val path = href.substringBefore('#').substringBefore('?')
    return path.endsWith(".svg", ignoreCase = true)
}

internal fun isInlineMathSvg(svg: String): Boolean {
    val doc = Jsoup.parse(svg, "", Parser.xmlParser())
    val svgElement = doc.selectFirst("svg") ?: return false
    return svgElement.hasClass("math-layout") || svgElement.hasClass("formula")
}

internal fun sanitizeSvgMarkup(rawSvg: String): String? {
    val doc = Jsoup.parse(rawSvg, "", Parser.xmlParser())
    val svg = doc.selectFirst("svg") ?: return null
    svg.select("script, iframe, object, embed, foreignObject, meta, link").remove()
    svg.allElements.forEach { element ->
        val blockedAttributes = element.attributes().asList().mapNotNull { attr ->
            val key = attr.key.lowercase(Locale.ROOT)
            val value = attr.value.trim().lowercase(Locale.ROOT)
            when {
                key.startsWith("on") -> attr.key
                (key == "href" || key == "xlink:href") &&
                        !value.startsWith("#") &&
                        !value.startsWith("data:image/") -> attr.key

                else -> null
            }
        }
        blockedAttributes.forEach { element.removeAttr(it) }
    }
    doc.outputSettings().prettyPrint(false)
    return svg.outerHtml()
}

internal fun isInternalImageHref(href: String): Boolean {
    val normalized = href.trim().lowercase(Locale.ROOT)
    return !normalized.startsWith("http://") && !normalized.startsWith("https://")
}

internal fun normalizePublicationHref(href: String): String =
    href.substringBefore('#').substringBefore('?').trimStart('/').lowercase(Locale.ROOT)

internal fun isCoverOrFirstPageImage(
    elementResourceHref: String,
    embeddedLinkHref: String?,
    embeddedLinkHasCoverRel: Boolean,
    publicationCoverHref: String?,
    firstReadingOrderHref: String?,
    firstReadingOrderPositionCount: Int,
    currentPage: Int?,
): Boolean {
    if (embeddedLinkHasCoverRel) return true
    val normalizedCoverHref = publicationCoverHref?.let(::normalizePublicationHref)
    if (normalizedCoverHref != null && embeddedLinkHref != null &&
        normalizePublicationHref(embeddedLinkHref) == normalizedCoverHref
    ) {
        return true
    }
    val normalizedElementResource = normalizePublicationHref(elementResourceHref)
    val normalizedFirstResource = firstReadingOrderHref?.let(::normalizePublicationHref)
    if (normalizedFirstResource != null && normalizedElementResource == normalizedFirstResource) {
        if (firstReadingOrderPositionCount <= 1 || currentPage == null || currentPage <= 1) {
            return true
        }
    }
    return false
}

@OptIn(ExperimentalReadiumApi::class)
internal fun shouldOpenInImageViewer(
    element: Content.Element?,
    publication: Publication? = null,
    positions: List<Locator> = emptyList(),
    currentPage: Int? = null,
): Boolean {
    val supported = when (element) {
        is Content.ImageElement -> {
            val scheme =
                (element.embeddedLink.url() as? AbsoluteUrl)?.scheme?.value?.lowercase(Locale.ROOT)
            scheme !in setOf(
                "http",
                "https"
            ) && isInternalImageHref(element.embeddedLink.href.toString())
        }

        is Content.SvgElement -> !isInlineMathSvg(element.svg)
        else -> false
    }
    if (!supported || element == null) return false

    val firstReadingOrderHref = publication?.readingOrder?.firstOrNull()?.href?.toString()
    val normalizedFirstHref = firstReadingOrderHref?.let(::normalizePublicationHref)
    val firstResourcePositions = if (normalizedFirstHref != null) {
        positions.count { normalizePublicationHref(it.href.toString()) == normalizedFirstHref }
    } else {
        0
    }
    val imageElement = element as? Content.ImageElement
    return !isCoverOrFirstPageImage(
        elementResourceHref = element.locator.href.toString(),
        embeddedLinkHref = imageElement?.embeddedLink?.href?.toString(),
        embeddedLinkHasCoverRel = imageElement?.embeddedLink?.rels?.contains("cover") == true,
        publicationCoverHref = publication?.linkWithRel("cover")?.href?.toString(),
        firstReadingOrderHref = firstReadingOrderHref,
        firstReadingOrderPositionCount = firstResourcePositions,
        currentPage = currentPage,
    )
}

/**
 * Computes a center-anchored zoom/pan transform, keeping [centroid] stationary and clamping pan
 * strictly within the fitted image's scaled edges.
 */
internal fun imageZoomTransform(
    previous: ImageZoomTransform,
    zoom: Float,
    pan: Offset,
    centroid: Offset,
    viewport: IntSize,
    imageAspectRatio: Float,
): ImageZoomTransform {
    if (viewport.width <= 0 || viewport.height <= 0 || imageAspectRatio <= 0f || !imageAspectRatio.isFinite()) {
        return ImageZoomTransform()
    }
    val scale = (previous.scale * zoom).coerceIn(IMAGE_VIEWER_MIN_SCALE, IMAGE_VIEWER_MAX_SCALE)
    if (scale <= IMAGE_VIEWER_MIN_SCALE) {
        return ImageZoomTransform(offset = Offset.Zero)
    }

    val viewportWidth = viewport.width.toFloat()
    val viewportHeight = viewport.height.toFloat()
    val center = Offset(viewportWidth / 2f, viewportHeight / 2f)
    val relativeCentroid = centroid - center
    val scaleFactor = scale / previous.scale
    val rawOffset = previous.offset * scaleFactor + relativeCentroid * (1f - scaleFactor) + pan

    val viewportAspect = viewportWidth / viewportHeight
    val fittedWidth: Float
    val fittedHeight: Float
    if (viewportAspect > imageAspectRatio) {
        fittedHeight = viewportHeight
        fittedWidth = fittedHeight * imageAspectRatio
    } else {
        fittedWidth = viewportWidth
        fittedHeight = fittedWidth / imageAspectRatio
    }

    val maxX = ((fittedWidth * scale - viewportWidth) / 2f).coerceAtLeast(0f)
    val maxY = ((fittedHeight * scale - viewportHeight) / 2f).coerceAtLeast(0f)
    return ImageZoomTransform(
        scale = scale,
        offset = Offset(
            x = rawOffset.x.coerceIn(-maxX, maxX),
            y = rawOffset.y.coerceIn(-maxY, maxY),
        ),
    )
}

internal fun imageDoubleTapTransform(
    current: ImageZoomTransform,
    tapOffset: Offset,
    viewport: IntSize,
    imageAspectRatio: Float,
): ImageZoomTransform = if (current.scale > IMAGE_VIEWER_MIN_SCALE + 0.05f) {
    ImageZoomTransform(offset = Offset.Zero)
} else {
    imageZoomTransform(
        previous = current,
        zoom = IMAGE_VIEWER_DOUBLE_TAP_SCALE / current.scale,
        pan = Offset.Zero,
        centroid = tapOffset,
        viewport = viewport,
        imageAspectRatio = imageAspectRatio,
    )
}

@Composable
fun ReaderImageViewer(
    state: ImageViewerState,
    showChrome: Boolean,
    readerBgColor: Color,
    readerTextColor: Color,
    onToggleChrome: () -> Unit,
    onToggleInvert: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(readerBgColor),
    ) {
        when (val content = state.content) {
            ReaderImageContent.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onToggleChrome() })
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }

            is ReaderImageContent.Bitmap -> {
                ZoomableBitmapSurface(
                    bitmap = content.image,
                    contentDescription = state.caption,
                    inverted = state.inverted,
                    onTap = onToggleChrome,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is ReaderImageContent.Svg -> {
                SvgViewerSurface(
                    sanitizedSvg = content.sanitizedSvg,
                    inverted = state.inverted,
                    onTap = onToggleChrome,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            ReaderImageContent.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { onToggleChrome() })
                        }
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.reader_image_load_failed),
                        color = readerTextColor,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }

        val actionsSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
        val actionsEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        val density = LocalDensity.current
        val offsetPx = with(density) { 30.dp.roundToPx() }

        AnimatedVisibility(
            visible = showChrome,
            enter = slideInVertically(
                initialOffsetY = { -offsetPx },
                animationSpec = actionsSpatialSpec,
            ) + fadeIn(animationSpec = actionsEffectsSpec),
            exit = slideOutVertically(
                targetOffsetY = { -offsetPx },
                animationSpec = actionsSpatialSpec,
            ) + fadeOut(animationSpec = actionsEffectsSpec),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .topBarFadeBackground(readerBgColor),
            ) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        TopAppBarCircleIconButton(
                            onClick = onClose,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = readerBgColor,
                                contentColor = readerTextColor,
                            ),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Arrow_back,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        FilledTonalIconToggleButton(
                            checked = state.inverted,
                            onCheckedChange = { onToggleInvert() },
                            modifier = Modifier.size(AppBarButtonSize),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconToggleButtonColors(
                                containerColor = readerBgColor,
                                contentColor = readerTextColor,
                                checkedContainerColor = readerTextColor,
                                checkedContentColor = readerBgColor,
                            ),
                        ) {
                            Icon(
                                imageVector = MaterialSymbols.Outlined.Invert_colors,
                                contentDescription = stringResource(R.string.reader_invert_image),
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        navigationIconContentColor = readerTextColor,
                        actionIconContentColor = readerTextColor,
                    ),
                )
            }
        }

        val caption = state.caption?.takeIf { it.isNotBlank() }
        if (caption != null) {
            AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically(
                    initialOffsetY = { offsetPx },
                    animationSpec = actionsSpatialSpec,
                ) + fadeIn(animationSpec = actionsEffectsSpec),
                exit = slideOutVertically(
                    targetOffsetY = { offsetPx },
                    animationSpec = actionsSpatialSpec,
                ) + fadeOut(animationSpec = actionsEffectsSpec),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to Color.Transparent,
                                    0.35f to readerBgColor.copy(alpha = 0.88f),
                                    1f to readerBgColor,
                                ),
                            ),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = caption,
                        color = readerTextColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoomableBitmapSurface(
    bitmap: ImageBitmap,
    contentDescription: String?,
    inverted: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageAspectRatio = remember(bitmap) {
        if (bitmap.height > 0) bitmap.width.toFloat() / bitmap.height.toFloat() else 1f
    }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val scaleAnim = remember(bitmap) { Animatable(IMAGE_VIEWER_MIN_SCALE) }
    val offsetAnim = remember(bitmap) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var currentScale by remember(bitmap) { mutableFloatStateOf(IMAGE_VIEWER_MIN_SCALE) }
    var currentOffset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val offsetSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Offset>()

    LaunchedEffect(scaleAnim.value, offsetAnim.value) {
        currentScale = scaleAnim.value
        currentOffset = offsetAnim.value
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { newSize ->
                viewportSize = newSize
                val clamped = imageZoomTransform(
                    previous = ImageZoomTransform(currentScale, currentOffset),
                    zoom = 1f,
                    pan = Offset.Zero,
                    centroid = Offset(newSize.width / 2f, newSize.height / 2f),
                    viewport = newSize,
                    imageAspectRatio = imageAspectRatio,
                )
                currentScale = clamped.scale
                currentOffset = clamped.offset
                scope.launch {
                    scaleAnim.snapTo(clamped.scale)
                    offsetAnim.snapTo(clamped.offset)
                }
            }
            .pointerInput(bitmap, imageAspectRatio) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        val target = imageDoubleTapTransform(
                            current = ImageZoomTransform(currentScale, currentOffset),
                            tapOffset = tapOffset,
                            viewport = viewportSize,
                            imageAspectRatio = imageAspectRatio,
                        )
                        scope.launch {
                            launch {
                                scaleAnim.animateTo(
                                    target.scale,
                                    animationSpec = spatialSpec
                                )
                            }
                            launch {
                                offsetAnim.animateTo(
                                    target.offset,
                                    animationSpec = offsetSpatialSpec
                                )
                            }
                        }
                    },
                )
            }
            .pointerInput(bitmap, imageAspectRatio) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    var totalPan = Offset.Zero
                    var transforming = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val multiTouch = event.changes.count { it.pressed } > 1
                        val pan = event.calculatePan()
                        totalPan += pan
                        if (
                            multiTouch ||
                            (currentScale > IMAGE_VIEWER_MIN_SCALE &&
                                    totalPan.getDistance() > viewConfiguration.touchSlop)
                        ) {
                            transforming = true
                        }
                        if (transforming && event.changes.any { it.pressed && it.previousPressed }) {
                            val next = imageZoomTransform(
                                previous = ImageZoomTransform(currentScale, currentOffset),
                                zoom = event.calculateZoom(),
                                pan = pan,
                                centroid = event.calculateCentroid(useCurrent = false),
                                viewport = viewportSize,
                                imageAspectRatio = imageAspectRatio,
                            )
                            currentScale = next.scale
                            currentOffset = next.offset
                            scope.launch {
                                scaleAnim.snapTo(next.scale)
                                offsetAnim.snapTo(next.offset)
                            }
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = if (inverted) ImageViewerInvertColorFilter else null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    scaleX = currentScale
                    scaleY = currentScale
                    translationX = currentOffset.x
                    translationY = currentOffset.y
                },
        )
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
private fun SvgViewerSurface(
    sanitizedSvg: String,
    inverted: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnTap by rememberUpdatedState(onTap)
    val filterRule = if (inverted) "filter: invert(1);" else ""
    val html = remember(sanitizedSvg, inverted) {
        """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
        <style>
        html, body {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
            background: transparent;
            display: flex;
            align-items: center;
            justify-content: center;
        }
        svg {
            max-width: 100vw;
            max-height: 100vh;
            width: auto;
            height: auto;
            $filterRule
        }
        </style>
        </head>
        <body>$sanitizedSvg</body>
        </html>
        """.trimIndent()
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            val tapDetector = GestureDetector(
                context,
                object : GestureDetector.SimpleOnGestureListener() {
                    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                        latestOnTap()
                        return true
                    }
                },
            )
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                isSaveEnabled = false
                setBackgroundColor(AndroidColor.TRANSPARENT)
                setOnTouchListener { _, event ->
                    tapDetector.onTouchEvent(event)
                    false
                }
                settings.apply {
                    javaScriptEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
            }
        },
        onRelease = { webView ->
            webView.destroy()
        },
    )
}
