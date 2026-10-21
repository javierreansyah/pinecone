@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.javierreansyah.pinecone.reader.pdf

import android.graphics.PointF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfDocumentFragment
import org.readium.r2.navigator.pdf.PdfDocumentFragmentInput
import org.readium.r2.shared.util.data.ReadError

class MuPdfDocumentFragment(private val input: PdfDocumentFragmentInput<MuPdfSettings>) :
    PdfDocumentFragment<MuPdfSettings>() {
    private val currentPage = MutableStateFlow(input.pageIndex)
    override val pageIndex: StateFlow<Int> = currentPage
    private val settings = MutableStateFlow(input.settings)
    private var document: MuPdfDocument? = null
    private val requestedPage = MutableStateFlow(input.pageIndex)

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
                    val pdf by produceState<MuPdfDocument?>(null) {
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
                                document = loaded
                                value = loaded
                                awaitCancellation()
                            } finally {
                                if (document === loaded) document = null
                                // Cleanup stays inside the owning producer coroutine and survives
                                // cancellation without blocking the UI on an in-flight render.
                                withContext(NonCancellable + Dispatchers.Default) { loaded.close() }
                            }
                        }
                    }
                    pdf?.let { Pages(it, prefs.preferences) }
                        ?: Box(
                            Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                }
            }
        }

    @Composable
    private fun Pages(pdf: MuPdfDocument, preferences: MuPdfPreferences) {
        val request by requestedPage.collectAsState()
        val initial =
            currentPage.collectAsState().value.coerceIn(0, (pdf.pageCount - 1).coerceAtLeast(0))
        val pager = rememberPagerState(initialPage = initial) { pdf.pageCount }
        val list = rememberLazyListState(initialFirstVisibleItemIndex = initial)
        LaunchedEffect(request, preferences.scroll, preferences.horizontal) {
            if (preferences.scroll) list.scrollToItem(request) else pager.scrollToPage(request)
        }
        LaunchedEffect(preferences.scroll, preferences.horizontal) {
            snapshotFlow { if (preferences.scroll) list.firstVisibleItemIndex else pager.currentPage }
                .collect { currentPage.value = it }
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
                        Modifier.width((LocalWindowInfo.current.containerSize.width / resources.displayMetrics.density).dp)
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
                        Modifier.fillMaxWidth()
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
                PageImage(pdf, index, preferences, Modifier.fillMaxSize())
            }
        } else {
            VerticalPager(pager, modifier, pageSpacing = spacing) { index ->
                PageImage(pdf, index, preferences, Modifier.fillMaxSize())
            }
        }
    }

    @Composable
    private fun PageImage(
        pdf: MuPdfDocument,
        index: Int,
        preferences: MuPdfPreferences,
        modifier: Modifier
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
            bitmap?.let { modifier.aspectRatio(it.width.toFloat() / it.height) }
                ?: modifier.heightIn(min = 200.dp)
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
                        if (event.changes.count { it.pressed } > 1 || zoom > 1f) {
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
                    Image(
                        it.asImageBitmap(),
                        stringResource(R.string.reader_page_label, index + 1),
                        image.graphicsLayer {
                            scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY =
                            offset.y
                        },
                        contentScale = ContentScale.Fit,
                        colorFilter = filter
                    )
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
        currentPage.value = index
        requestedPage.value = index
        return true
    }

    override fun applySettings(settings: MuPdfSettings) {
        requestedPage.value = currentPage.value
        this.settings.value = settings
    }

    override fun onDestroyView() {
        document = null
        super.onDestroyView()
    }
}
