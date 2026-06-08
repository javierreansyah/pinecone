package com.example.readerapp.ui.features.reader.components.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.core.graphics.toColorInt
import androidx.compose.ui.res.stringResource
import com.example.readerapp.R
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Arrow_forward
import com.composables.icons.materialsymbols.outlined.Book_3
import com.composables.icons.materialsymbols.outlined.Book_5
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Content_copy
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Dictionary
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Search
import kotlin.math.abs
import kotlin.math.roundToInt

enum class BottomBarMode {
    PROGRESS,
    SEARCH_NAV,
    TEXT_SELECTION
}

@Composable
fun ReaderBottomBarContainer(
    modifier: Modifier = Modifier,
    readerBgColor: Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, readerBgColor),
                    startY = 0f
                )
            )
            .padding(horizontal = 8.dp)
            .padding(
                top = 8.dp,
                bottom = if (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() > 0.dp) 2.dp else 12.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun ReaderProgressTracker(
    modifier: Modifier = Modifier,
    progression: Double,
    currentPage: Int?,
    totalPages: Int?,
    readerTextColor: Color,
    onSeekToProgression: (Double) -> Unit
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        // Progress slider
        val density = LocalDensity.current
        val trackStrokeWidthPx = with(density) { 2.dp.toPx() }
        val thumbWidthPx = with(density) { 2.dp.toPx() }
        val thumbHeightPx = with(density) { 14.dp.toPx() }
        val thumbGapPx = with(density) { 4.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp) // extra padding for thumb
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
                    if (gapLeft > 0f) {
                        drawLine(
                            color = readerTextColor.copy(alpha = 0.70f),
                            start = Offset(0f, trackY),
                            end = Offset(gapLeft, trackY),
                            strokeWidth = trackStrokeWidthPx,
                            cap = StrokeCap.Round
                        )
                    }
                    if (gapRight < size.width) {
                        drawLine(
                            color = readerTextColor.copy(alpha = 0.24f),
                            start = Offset(gapRight, trackY),
                            end = Offset(size.width, trackY),
                            strokeWidth = trackStrokeWidthPx,
                            cap = StrokeCap.Round
                        )
                    }

                    val halfThumb = thumbHeightPx / 2f
                    drawLine(
                        color = readerTextColor.copy(alpha = 0.70f),
                        start = Offset(thumbX, trackY - halfThumb),
                        end = Offset(thumbX, trackY + halfThumb),
                        strokeWidth = thumbWidthPx,
                        cap = StrokeCap.Round
                    )
                } else {
                    drawLine(
                        color = readerTextColor.copy(alpha = 0.70f),
                        start = Offset(0f, trackY),
                        end = Offset(thumbX, trackY),
                        strokeWidth = trackStrokeWidthPx,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = readerTextColor.copy(alpha = 0.24f),
                        start = Offset(thumbX, trackY),
                        end = Offset(size.width, trackY),
                        strokeWidth = trackStrokeWidthPx,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Page info text
        val pageText = if (isSeeking || pendingSeek != null) {
            if (totalPages != null) {
                val page = (sliderPosition * totalPages).roundToInt().coerceIn(1, totalPages)
                androidx.compose.ui.res.pluralStringResource(R.plurals.reader_page_of, totalPages, page, totalPages)
            } else {
                "${(sliderPosition * 100).toInt()}%"
            }
        } else {
            if (currentPage != null && totalPages != null) {
                androidx.compose.ui.res.pluralStringResource(R.plurals.reader_page_of, totalPages, currentPage, totalPages)
            } else {
                "${(progression * 100).toInt()}%"
            }
        }

        Text(
            text = pageText,
            style = MaterialTheme.typography.labelMedium,
            color = readerTextColor
        )
    }
}

@Composable
fun ReaderSearchNavigator(
    modifier: Modifier = Modifier,
    activeIndex: Int?,
    totalResults: Int,
    textColor: Color,
    onExit: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    val currentNum = if (activeIndex != null) activeIndex + 1 else 0
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
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
                contentDescription = stringResource(R.string.action_close),
                tint = textColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = if (totalResults == 0) stringResource(R.string.reader_no_matches) else stringResource(R.string.reader_result_num_of, currentNum, totalResults),
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )

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
                    contentDescription = stringResource(R.string.action_back),
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
                    contentDescription = stringResource(R.string.action_more),
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

@Composable
fun ReaderTextSelectionControl(
    modifier: Modifier = Modifier,
    selectedColorInt: Int?,
    readerTextColor: Color,
    showDeleteOption: Boolean,
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onMakeNote: () -> Unit,
    onDefine: () -> Unit,
    onDelete: () -> Unit,
    onColorSelected: (Int) -> Unit
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left side: Action Icons
        Row(
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
                Icon(MaterialSymbols.Outlined.Content_copy, contentDescription = stringResource(R.string.action_copy), tint = readerTextColor, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(40.dp)) {
                Icon(MaterialSymbols.Outlined.Search, contentDescription = stringResource(R.string.action_search), tint = readerTextColor, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onMakeNote, modifier = Modifier.size(40.dp)) {
                Icon(MaterialSymbols.Outlined.Edit, contentDescription = stringResource(R.string.reader_make_note), tint = readerTextColor, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onDefine, modifier = Modifier.size(40.dp)) {
                Icon(MaterialSymbols.Outlined.Book_3, contentDescription = stringResource(R.string.action_define), tint = readerTextColor, modifier = Modifier.size(20.dp))
            }
            if (showDeleteOption) {
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(MaterialSymbols.Outlined.Delete, contentDescription = stringResource(R.string.action_delete), tint = readerTextColor, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Right side: Color Swatches
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val swatches = listOf(
                    "#40fac02e".toColorInt(), // Yellow
                    "#40fd7142".toColorInt(), // Orange
                    "#408bc24a".toColorInt(), // Green
                    "#4025c6da".toColorInt()  // Blue
                )
                swatches.forEach { colorInt ->
                    val isSelected = selectedColorInt == colorInt
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                color = Color(colorInt).copy(alpha = 1f),
                                shape = CircleShape
                            )
                            .border(
                                width = if (isSelected) 2.dp else 0.dp,
                                color = if (isSelected) readerTextColor else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onColorSelected(colorInt) }
                    )
                }
            }
        }
    }
}
