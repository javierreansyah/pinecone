package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemShapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object SegmentedListDefaults {
    val Gap: Dp = 2.dp
    val OuterCornerRadius: Dp = 20.dp
    val InnerCornerRadius: Dp = 4.dp

    private val outerShape = RoundedCornerShape(OuterCornerRadius)
    private val innerShape = RoundedCornerShape(InnerCornerRadius)
    private val hoveredShape = RoundedCornerShape(12.dp)

    private val singleShapes = listItemShapes(outerShape)
    private val firstShapes = listItemShapes(
        RoundedCornerShape(
            topStart = OuterCornerRadius,
            topEnd = OuterCornerRadius,
            bottomStart = InnerCornerRadius,
            bottomEnd = InnerCornerRadius,
        ),
    )
    private val middleShapes = listItemShapes(innerShape)
    private val lastShapes = listItemShapes(
        RoundedCornerShape(
            topStart = InnerCornerRadius,
            topEnd = InnerCornerRadius,
            bottomStart = OuterCornerRadius,
            bottomEnd = OuterCornerRadius,
        ),
    )

    fun shapes(index: Int, count: Int): ListItemShapes = when {
        count <= 1 -> singleShapes
        index == 0 -> firstShapes
        index == count - 1 -> lastShapes
        else -> middleShapes
    }

    private fun listItemShapes(shape: RoundedCornerShape): ListItemShapes = ListItemShapes(
        shape = shape,
        selectedShape = outerShape,
        pressedShape = outerShape,
        focusedShape = outerShape,
        hoveredShape = hoveredShape,
        draggedShape = outerShape,
    )
}
