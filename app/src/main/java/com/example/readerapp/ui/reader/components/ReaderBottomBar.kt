package com.example.readerapp.ui.reader.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ReaderBottomBar(
    progression: Double,
    currentPage: Int?,
    totalPages: Int?,
    readerBgColor: Color,
    onSeekToProgression: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(progression.toFloat()) }
    var pendingSeek by remember { mutableStateOf<Double?>(null) }
    var seekBarWidthPx by remember { mutableStateOf(1f) }

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
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
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
                                change.consumePositionChange()
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
                        val gap = lineWidthPx
                        val halfGap = gap / 2f

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
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
