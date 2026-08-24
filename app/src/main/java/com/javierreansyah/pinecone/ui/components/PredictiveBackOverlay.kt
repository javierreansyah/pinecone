@file:Suppress("RedundantSuppression", "unused")

package com.javierreansyah.pinecone.ui.components

import androidx.activity.BackEventCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun PredictiveBackOverlay(
    backProgress: Float,
    swipeEdge: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {

    val customShape = remember(backProgress) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density
            ): Outline {
                val w = size.width
                val h = size.height
                val bottomClipPx = backProgress * 0.08f * h
                val cornerRadiusPx = with(density) { (backProgress * 28f).dp.toPx() }

                val rect = Rect(
                    left = 0f,
                    top = 0f,
                    right = w,
                    bottom = h - bottomClipPx
                )
                val roundRect = RoundRect(
                    rect = rect,
                    cornerRadius = CornerRadius(cornerRadiusPx)
                )
                return Outline.Rounded(roundRect)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val w = constraints.maxWidth
                val h = constraints.maxHeight

                if (backProgress > 0f && w > 0) {
                    val targetWidth = (w * (1f - backProgress * 0.112f)).toInt()
                    val childConstraints = constraints.copy(
                        minWidth = targetWidth.coerceAtMost(w),
                        maxWidth = targetWidth.coerceAtMost(w),
                        minHeight = h,
                        maxHeight = h
                    )
                    val placeable = measurable.measure(childConstraints)

                    layout(w, h) {
                        val leftGap = if (swipeEdge == BackEventCompat.EDGE_LEFT) {
                            (backProgress * 0.08f * w).toInt()
                        } else {
                            (backProgress * 0.032f * w).toInt()
                        }
                        placeable.placeRelative(leftGap, 0)
                    }
                } else {
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height) {
                        placeable.placeRelative(0, 0)
                    }
                }
            }
            .graphicsLayer {
                if (backProgress > 0f) {
                    scaleX = 1f
                    scaleY = 1f
                    clip = true
                    shape = customShape
                } else {
                    scaleX = 1f
                    scaleY = 1f
                    clip = false
                }
            }
            .background(backgroundColor)
    ) {
        content()
    }
}
