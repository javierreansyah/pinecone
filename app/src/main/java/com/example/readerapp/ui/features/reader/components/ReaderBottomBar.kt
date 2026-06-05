package com.example.readerapp.ui.features.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Arrow_forward
import com.composables.icons.materialsymbols.outlined.Close
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReaderBottomBar(
    modifier: Modifier = Modifier,
    progression: Double,
    currentPage: Int?,
    totalPages: Int?,
    readerBgColor: Color,
    readerTextColor: Color,
    onSeekToProgression: (Double) -> Unit,
    // Search navigation
    isInSearchNavigationMode: Boolean = false,
    activeSearchIndex: Int? = null,
    totalSearchResults: Int = 0,
    onExitSearch: () -> Unit = {},
    onPrevSearchResult: () -> Unit = {},
    onNextSearchResult: () -> Unit = {},
) {
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(progression.toFloat()) }
    var pendingSeek by remember { mutableStateOf<Double?>(null) }
    var seekBarWidthPx by remember { mutableFloatStateOf(1f) }


    LaunchedEffect(progression) {
        val target = pendingSeek
        if (!isSeeking) {
            if (target == null || abs(progression - target) < 0.02) {
                pendingSeek = null
                sliderPosition = progression.toFloat().coerceIn(0f, 1f)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, readerBgColor),
                    startY = 0f
                )
            )
            .padding(WindowInsets.navigationBars.asPaddingValues())
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 4.dp)
    ) {
        if (isInSearchNavigationMode) {
            // ── Search navigation helper ───────────────────────────────────
            SearchNavBar(
                activeIndex = activeSearchIndex,
                totalResults = totalSearchResults,
                onExit = onExitSearch,
                onPrev = onPrevSearchResult,
                onNext = onNextSearchResult,
                textColor = readerTextColor
            )
        } else {
            // ── Normal progress bar ────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
            // Progress slider
            val onSurface = readerTextColor
            val density = LocalDensity.current
            val trackStrokeWidthPx = with(density) { 2.dp.toPx() }
            val thumbWidthPx = with(density) { 2.dp.toPx() }
            val thumbHeightPx = with(density) { 14.dp.toPx() }
            val thumbGapPx = with(density) { 4.dp.toPx() } // horizontal padding on each side of thumb

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .onSizeChanged { seekBarWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val p = (offset.x / seekBarWidthPx).coerceIn(0f, 1f)
                            sliderPosition = p
                            pendingSeek = sliderPosition.toDouble()
                            onSeekToProgression(sliderPosition.toDouble())
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                isSeeking = true
                                val p = (offset.x / seekBarWidthPx).coerceIn(0f, 1f)
                                sliderPosition = p
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val p = (change.position.x / seekBarWidthPx).coerceIn(0f, 1f)
                                sliderPosition = p
                            },
                            onDragEnd = {
                                isSeeking = false
                                pendingSeek = sliderPosition.toDouble()
                                onSeekToProgression(sliderPosition.toDouble())
                            },
                            onDragCancel = {
                                isSeeking = false
                            }
                        )
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                ) {
                    val trackY = size.height / 2f
                    val thumbX = size.width * sliderPosition
                    val gapLeft = (thumbX - thumbGapPx).coerceAtLeast(0f)
                    val gapRight = (thumbX + thumbGapPx).coerceAtMost(size.width)

                    if (isSeeking) {
                        // ── Track with gap around thumb ──
                        // Active track (left of thumb), stopping before the gap
                        if (gapLeft > 0f) {
                            drawLine(
                                color = onSurface.copy(alpha = 0.70f),
                                start = Offset(0f, trackY),
                                end = Offset(gapLeft, trackY),
                                strokeWidth = trackStrokeWidthPx,
                                cap = StrokeCap.Round
                            )
                        }
                        // Inactive track (right of thumb), starting after the gap
                        if (gapRight < size.width) {
                            drawLine(
                                color = onSurface.copy(alpha = 0.24f),
                                start = Offset(gapRight, trackY),
                                end = Offset(size.width, trackY),
                                strokeWidth = trackStrokeWidthPx,
                                cap = StrokeCap.Round
                            )
                        }

                        // ── Thumb: simple vertical line ──
                        val halfThumb = thumbHeightPx / 2f
                        drawLine(
                            color = onSurface.copy(alpha = 0.70f),
                            start = Offset(thumbX, trackY - halfThumb),
                            end = Offset(thumbX, trackY + halfThumb),
                            strokeWidth = thumbWidthPx,
                            cap = StrokeCap.Round
                        )
                    } else {
                        // ── Passive continuous track (no thumb) ──
                        drawLine(
                            color = onSurface.copy(alpha = 0.70f),
                            start = Offset(0f, trackY),
                            end = Offset(thumbX, trackY),
                            strokeWidth = trackStrokeWidthPx,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = onSurface.copy(alpha = 0.24f),
                            start = Offset(thumbX, trackY),
                            end = Offset(size.width, trackY),
                            strokeWidth = trackStrokeWidthPx,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

//            Spacer(modifier = Modifier.height(4.dp))

            // Page info text
            val pageText = if (isSeeking || pendingSeek != null) {
                if (totalPages != null) {
                    val page = (sliderPosition * totalPages).roundToInt().coerceIn(1, totalPages)
                    "$page of $totalPages"
                } else {
                    "${(sliderPosition * 100).toInt()}%"
                }
            } else {
                if (currentPage != null && totalPages != null) {
                    "$currentPage of $totalPages"
                } else {
                    "${(progression * 100).toInt()}%"
                }
            }

            Text(
                text = pageText,
                style = MaterialTheme.typography.labelMedium,
                color = readerTextColor
            )
        } // end normal progress Column
        } // end else (normal progress bar)
    } // end outer Box
}

// ── Search Navigation Bar ─────────────────────────────────────────────────────

@Composable
private fun SearchNavBar(
    activeIndex: Int?,
    totalResults: Int,
    onExit: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    textColor: Color
) {
    val currentNum = if (activeIndex != null) activeIndex + 1 else 0
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterStart)
        ) {
            Icon(
                MaterialSymbols.Outlined.Close,
                contentDescription = "Exit search",
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
        }

        // N of M label
        Text(
            text = if (totalResults == 0) "No matches" else "$currentNum of $totalResults",
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )

        // Prev / Next arrows
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            IconButton(
                onClick = onPrev,
                enabled = activeIndex != null && activeIndex > 0,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    MaterialSymbols.Outlined.Arrow_back,
                    contentDescription = "Previous result",
                    modifier = Modifier.size(20.dp),
                    tint = if (activeIndex != null && activeIndex > 0)
                        textColor
                    else
                        textColor.copy(alpha = 0.3f)
                )
            }
            IconButton(
                onClick = onNext,
                enabled = activeIndex != null && activeIndex < totalResults - 1,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    MaterialSymbols.Outlined.Arrow_forward,
                    contentDescription = "Next result",
                    modifier = Modifier.size(20.dp),
                    tint = if (activeIndex != null && activeIndex < totalResults - 1)
                        textColor
                    else
                        textColor.copy(alpha = 0.3f)
                )
            }
        }
    }
}
