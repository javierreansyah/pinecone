package com.example.readerapp.ui.features.reader.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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

    val isInteracting = isSeeking

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
                onNext = onNextSearchResult
            )
        } else {
            // ── Normal progress bar ────────────────────────────────────────
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
            // Progress slider
            val sliderAlpha by animateFloatAsState(
                targetValue = if (isInteracting) 1f else 0f,
                label = "sliderInteractAlpha"
            )
            val onSurface = MaterialTheme.colorScheme.onSurface
            val primary = MaterialTheme.colorScheme.primary
            val density = LocalDensity.current
            val lineWidthPx = with(density) { 2.dp.toPx() }
            val separatorHeightPx = with(density) { 6.dp.toPx() }

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
                // Passive thin track
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                ) {
                    val trackY = size.height / 2
                    drawLine(
                        color = onSurface.copy(alpha = 0.24f),
                        start = Offset(0f, trackY),
                        end = Offset(size.width, trackY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = onSurface.copy(alpha = 0.70f),
                        start = Offset(0f, trackY),
                        end = Offset(size.width * sliderPosition, trackY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    if (isInteracting) {
                        val x = size.width * sliderPosition
                        val halfSep = separatorHeightPx / 2f
                        drawLine(
                            color = readerBgColor,
                            start = Offset(x, trackY - halfSep),
                            end = Offset(x, trackY + halfSep),
                            strokeWidth = lineWidthPx,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Vertical seek line
                if (isInteracting) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .graphicsLayer { alpha = sliderAlpha }
                    ) {
                        val x = size.width * sliderPosition
                        val midY = size.height / 2f
                        val halfGap = lineWidthPx / 2f

                        // Background border for visual separation
                        val borderStrokeWidth = lineWidthPx * 2.5f
                        drawLine(
                            color = readerBgColor,
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = borderStrokeWidth,
                            cap = StrokeCap.Round
                        )

                        // Primary seeker line
                        drawLine(
                            color = primary,
                            start = Offset(x, 0f),
                            end = Offset(x, (midY - halfGap).coerceAtLeast(0f)),
                            strokeWidth = lineWidthPx,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = primary,
                            start = Offset(x, (midY + halfGap).coerceAtMost(size.height)),
                            end = Offset(x, size.height),
                            strokeWidth = lineWidthPx,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

//            Spacer(modifier = Modifier.height(4.dp))

            // Page info text
            val pageText = if (isInteracting || pendingSeek != null) {
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
    onNext: () -> Unit
) {
    val currentNum = if (activeIndex != null) activeIndex + 1 else 0
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // ✕ exit button
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .size(40.dp)
                .align(Alignment.CenterStart)
        ) {
            Icon(
                MaterialSymbols.Outlined.Close,
                contentDescription = "Exit search",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        // N of M label
        Text(
            text = if (totalResults == 0) "No matches" else "$currentNum of $totalResults",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
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
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
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
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}
