package com.javierreansyah.pinecone.ui.features.reader.components.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FlexibleBottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Arrow_forward
import com.composables.icons.materialsymbols.outlined.Book_3
import com.composables.icons.materialsymbols.outlined.Content_copy
import com.composables.icons.materialsymbols.outlined.Delete
import com.composables.icons.materialsymbols.outlined.Edit
import com.composables.icons.materialsymbols.outlined.Ink_highlighter
import com.composables.icons.materialsymbols.outlined.Search
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.AppDropdownMenuDefaults
import com.javierreansyah.pinecone.ui.components.AppDropdownMenuGroup
import com.javierreansyah.pinecone.ui.components.AppSelectableDropdownMenuItem
import com.javierreansyah.pinecone.ui.components.ConnectedButtonGroup
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconButton
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

enum class BottomBarMode {
    PROGRESS, SEARCH_NAV, TEXT_SELECTION
}

private val HighlightSwatches = listOf(
    0x40FAC02E.toInt(),
    0x40FD7142.toInt(),
    0x408BC24A.toInt(),
    0x4025C6DA.toInt(),
)

@Composable
fun ReaderDockedToolbar(
    modifier: Modifier = Modifier,
    readerBgColor: Color,
    readerTextColor: Color,
    showGradient: Boolean = true,
    content: @Composable () -> Unit,
) {
    val backgroundModifier = if (showGradient) {
        Modifier.background(
            Brush.verticalGradient(
                colors = listOf(readerBgColor.copy(alpha = 0f), readerBgColor),
            ),
        )
    } else {
        Modifier
    }
    // Compose's expressive bottom app bar uses the Material docked-toolbar tokens:
    // 64dp content height, 16dp side padding, and system-bar insets handled once here.
    FlexibleBottomAppBar(
        modifier = modifier
            .fillMaxWidth()
            .then(backgroundModifier),
        containerColor = Color.Transparent,
        contentColor = readerTextColor,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.BottomCenter) {
            content()
        }
    }
}

@Composable
fun ReaderProgressTracker(
    modifier: Modifier = Modifier,
    progression: Double,
    currentPage: Int?,
    totalPages: Int?,
    readerTextColor: Color,
    onSeekToProgression: (Double) -> Unit,
    showProgressBar: Boolean = true,
) {
    if (!showProgressBar) {
        val pageText = if (totalPages != null && totalPages > 0) {
            androidx.compose.ui.res.pluralStringResource(
                R.plurals.reader_page_of,
                totalPages,
                (currentPage ?: 1).coerceIn(1, totalPages),
                totalPages,
            )
        } else {
            stringResource(R.string.reader_page_label, currentPage ?: 1)
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = pageText,
                style = MaterialTheme.typography.labelMedium,
                color = readerTextColor,
            )
        }
        return
    }
    var isSeeking by remember { mutableStateOf(value = false) }
    var isPressed by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(progression.toFloat().coerceIn(0f, 1f)) }
    var pendingSeek by remember { mutableStateOf<Double?>(null) }
    val onSeek by rememberUpdatedState(onSeekToProgression)
    val commitSeek by rememberUpdatedState {
        pendingSeek = sliderPosition.toDouble()
        onSeek(sliderPosition.toDouble())
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val handleActive = isPressed || isSeeking
    val handleScale by animateFloatAsState(
        targetValue = if (handleActive) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "readerHandleVerticalScale",
    )
    val handleOpacity by animateFloatAsState(
        targetValue = if (handleActive) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "readerHandleOpacity",
    )
    // Soften against the reading background in both light and dark themes.
    val progressColor = readerTextColor.copy(alpha = readerTextColor.alpha * 0.65f)
    val seekLabel = stringResource(R.string.publication_reading_progress)
    val progressAnimSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val animatedSliderPosition = remember {
        Animatable(progression.toFloat().coerceIn(0f, 1f))
    }

    LaunchedEffect(progression, isSeeking, pendingSeek) {
        val target = pendingSeek
        if (!isSeeking) {
            if (target != null && abs(progression - target) >= 0.02) {
                // A seek can settle away from its requested estimate. Never leave
                // the thumb permanently detached from subsequent page turns.
                delay(1500.milliseconds)
            }
            pendingSeek = null
            sliderPosition = progression.toFloat().coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(sliderPosition, isSeeking) {
        if (isSeeking) {
            animatedSliderPosition.snapTo(sliderPosition)
        } else {
            animatedSliderPosition.animateTo(
                targetValue = sliderPosition,
                animationSpec = progressAnimSpec,
            )
        }
    }

    val displayPosition = if (isSeeking) sliderPosition else animatedSliderPosition.value

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .height(32.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = seekLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(sliderPosition, 0f..1f)
                    setProgress { fraction ->
                        sliderPosition = fraction.coerceIn(0f, 1f)
                        commitSeek()
                        true
                    }
                }
                .pointerInput(rtl) {
                    detectTapGestures(
                        onPress = {
                            isPressed = true
                            try {
                                tryAwaitRelease()
                            } finally {
                                isPressed = false
                            }
                        },
                        onTap = { offset ->
                            sliderPosition = readerSeekFraction(offset.x, size.width.toFloat(), rtl)
                            commitSeek()
                        },
                    )
                }
                .pointerInput(rtl) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isSeeking = true
                            sliderPosition = readerSeekFraction(offset.x, size.width.toFloat(), rtl)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            sliderPosition =
                                readerSeekFraction(change.position.x, size.width.toFloat(), rtl)
                        },
                        onDragEnd = {
                            isSeeking = false
                            commitSeek()
                        },
                        onDragCancel = {
                            isSeeking = false
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            LinearProgressIndicator(
                progress = { displayPosition },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clearAndSetSemantics {},
                color = progressColor,
                trackColor = readerTextColor.copy(alpha = 0.18f),
                strokeCap = StrokeCap.Round,
                gapSize = 6.dp,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .graphicsLayer {
                        scaleY = handleScale.coerceAtLeast(0f)
                        alpha = handleOpacity.coerceIn(0f, 1f)
                    },
            ) {
                // Match the audiobook's rounded handle and vertical-only spring animation,
                // with a slimmer profile for the compact reading toolbar.
                val handleWidth = 3.dp.toPx()
                val halfWidth = handleWidth / 2f
                val rawX = size.width * if (rtl) 1f - displayPosition else displayPosition
                val shiftedX = rawX + 2.dp.toPx()
                val handleX =
                    shiftedX.coerceIn(halfWidth, (size.width - halfWidth).coerceAtLeast(halfWidth))
                val centerY = size.height / 2f
                val halfHeight = 18.dp.toPx() / 2f
                drawLine(
                    color = progressColor,
                    start = Offset(handleX, centerY - halfHeight),
                    end = Offset(handleX, centerY + halfHeight),
                    strokeWidth = handleWidth,
                    cap = StrokeCap.Round,
                )
            }
        }

        val pageText = if (isSeeking || pendingSeek != null) {
            if (totalPages != null) {
                val page = (sliderPosition * totalPages).roundToInt().coerceIn(1, totalPages)
                androidx.compose.ui.res.pluralStringResource(
                    R.plurals.reader_page_of, totalPages, page, totalPages,
                )
            } else {
                "${(sliderPosition * 100).toInt()}%"
            }
        } else {
            if (totalPages != null) {
                androidx.compose.ui.res.pluralStringResource(
                    R.plurals.reader_page_of,
                    totalPages,
                    (currentPage ?: 1).coerceIn(1, totalPages),
                    totalPages,
                )
            } else {
                "${(progression * 100).toInt()}%"
            }
        }

        Text(
            text = pageText, style = MaterialTheme.typography.labelMedium, color = readerTextColor,
        )
    }
}

internal fun readerSeekFraction(x: Float, width: Float, rtl: Boolean): Float {
    val fraction = (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
    return if (rtl) 1f - fraction else fraction
}

@Composable
fun ReaderSearchNavigator(
    modifier: Modifier = Modifier,
    activeIndex: Int?,
    totalResults: Int,
    containerColor: Color,
    textColor: Color,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    val currentNum = if (activeIndex != null) activeIndex + 1 else 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (totalResults == 0) stringResource(R.string.reader_no_matches) else stringResource(
                R.string.reader_result_num_of, currentNum, totalResults,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            modifier = Modifier.padding(start = 8.dp),
        )

        Spacer(modifier = Modifier.weight(1f))

        ConnectedButtonGroup(
            containerColor = containerColor,
            contentColor = textColor,
        ) {
            ConnectedGroupIconButton(
                onClick = onPrev,
                enabled = activeIndex != null && activeIndex > 0,
                contentColor = textColor,
            ) {
                Icon(
                    MaterialSymbols.Outlined.Arrow_back,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            ConnectedGroupIconButton(
                onClick = onNext,
                enabled = activeIndex != null && activeIndex < totalResults - 1,
                contentColor = textColor,
            ) {
                Icon(
                    MaterialSymbols.Outlined.Arrow_forward,
                    contentDescription = stringResource(R.string.action_more),
                )
            }
        }
    }
}

@Composable
fun ReaderTextSelectionControl(
    modifier: Modifier = Modifier,
    selectedColorInt: Int?,
    containerColor: Color,
    readerTextColor: Color,
    showDeleteOption: Boolean,
    onCopy: () -> Unit,
    onSearch: () -> Unit,
    onMakeNote: () -> Unit,
    onDefine: () -> Unit,
    onDelete: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(value = false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConnectedButtonGroup(
            containerColor = containerColor,
            contentColor = readerTextColor,
        ) {
            ConnectedGroupIconButton(
                onClick = onCopy,
                contentColor = readerTextColor,
            ) {
                Icon(
                    MaterialSymbols.Outlined.Content_copy,
                    contentDescription = stringResource(R.string.action_copy),
                )
            }
            ConnectedGroupIconButton(
                onClick = onSearch,
                contentColor = readerTextColor,
            ) {
                Icon(
                    MaterialSymbols.Outlined.Search,
                    contentDescription = stringResource(R.string.action_search),
                )
            }
            ConnectedGroupIconButton(
                onClick = onDefine,
                contentColor = readerTextColor,
            ) {
                Icon(
                    MaterialSymbols.Outlined.Book_3,
                    contentDescription = stringResource(R.string.action_define),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ConnectedButtonGroup(
            containerColor = containerColor,
            contentColor = readerTextColor,
        ) {
            if (showDeleteOption) {
                ConnectedGroupIconButton(
                    onClick = onDelete,
                    contentColor = readerTextColor,
                ) {
                    Icon(
                        MaterialSymbols.Outlined.Delete,
                        contentDescription = stringResource(R.string.action_delete),
                    )
                }
            }
            ConnectedGroupIconButton(
                onClick = onMakeNote,
                contentColor = readerTextColor,
            ) {
                Icon(
                    MaterialSymbols.Outlined.Edit,
                    contentDescription = stringResource(R.string.reader_make_note),
                )
            }
            Box {
                ConnectedGroupIconButton(
                    onClick = { expanded = true },
                    contentColor = selectedColorInt?.let { Color(it).copy(alpha = 1f) }
                        ?: readerTextColor,
                ) {
                    Icon(
                        MaterialSymbols.Outlined.Ink_highlighter,
                        contentDescription = stringResource(R.string.reader_highlight_color),
                    )
                }
                DropdownMenuPopup(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    val totalItems = HighlightSwatches.size
                    AppDropdownMenuGroup {
                        HighlightSwatches.forEachIndexed { index, colorInt ->
                            val isSelected = selectedColorInt == colorInt
                            AppSelectableDropdownMenuItem(
                                selected = isSelected,
                                modifier = Modifier
                                    .width(64.dp),
                                text = {
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(
                                                    color = Color(colorInt).copy(alpha = 1f),
                                                    shape = CircleShape,
                                                )
                                                .border(
                                                    width = 2.dp,
                                                    color = MaterialTheme.colorScheme.surfaceContainer.copy(
                                                        alpha = 0.2f,
                                                    ),
                                                    shape = CircleShape,
                                                ),
                                        )
                                    }
                                },
                                contentPadding = PaddingValues(0.dp),
                                shapes = AppDropdownMenuDefaults.itemShape(index, totalItems),
                                onClick = {
                                    onColorSelected(colorInt)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
