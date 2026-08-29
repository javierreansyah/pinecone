@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.javierreansyah.pinecone.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

class SegmentedListItemData(
    val key: Any?,
    val selected: Boolean,
    val enabled: Boolean,
    val onClick: (() -> Unit)?,
    val onLongClick: (() -> Unit)?,
    val leadingContent: @Composable (() -> Unit)?,
    val trailingContent: @Composable (() -> Unit)?,
    val supportingContent: @Composable (() -> Unit)?,
    val wrapper: @Composable (itemContent: @Composable () -> Unit) -> Unit,
    val content: @Composable () -> Unit
)

interface SegmentedListScope {
    fun item(
        key: Any? = null,
        selected: Boolean = false,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        wrapper: @Composable (itemContent: @Composable () -> Unit) -> Unit = { it() },
        content: @Composable () -> Unit
    )

    fun <T> items(
        items: List<T>,
        key: ((item: T) -> Any)? = null,
        selected: (item: T) -> Boolean = { false },
        enabled: (item: T) -> Boolean = { true },
        onClick: ((item: T) -> Unit)? = null,
        onLongClick: ((item: T) -> Unit)? = null,
        leadingContent: (@Composable (item: T) -> Unit)? = null,
        trailingContent: (@Composable (item: T) -> Unit)? = null,
        supportingContent: (@Composable (item: T) -> Unit)? = null,
        wrapper: @Composable (item: T, itemContent: @Composable () -> Unit) -> Unit = { _, it -> it() },
        content: @Composable (item: T) -> Unit
    )
}

class SegmentedListBuilder : SegmentedListScope {
    val items = mutableListOf<SegmentedListItemData>()

    override fun item(
        key: Any?,
        selected: Boolean,
        enabled: Boolean,
        onClick: (() -> Unit)?,
        onLongClick: (() -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        wrapper: @Composable (itemContent: @Composable () -> Unit) -> Unit,
        content: @Composable () -> Unit
    ) {
        items.add(
            SegmentedListItemData(
                key = key,
                selected = selected,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                supportingContent = supportingContent,
                wrapper = wrapper,
                content = content
            )
        )
    }

    override fun <T> items(
        items: List<T>,
        key: ((item: T) -> Any)?,
        selected: (item: T) -> Boolean,
        enabled: (item: T) -> Boolean,
        onClick: ((item: T) -> Unit)?,
        onLongClick: ((item: T) -> Unit)?,
        leadingContent: (@Composable (item: T) -> Unit)?,
        trailingContent: (@Composable (item: T) -> Unit)?,
        supportingContent: (@Composable (item: T) -> Unit)?,
        wrapper: @Composable (item: T, itemContent: @Composable () -> Unit) -> Unit,
        content: @Composable (item: T) -> Unit
    ) {
        items.forEach { item ->
            item(
                key = key?.invoke(item),
                selected = selected(item),
                enabled = enabled(item),
                onClick = onClick?.let { { it(item) } },
                onLongClick = onLongClick?.let { { it(item) } },
                leadingContent = leadingContent?.let { { it(item) } },
                trailingContent = trailingContent?.let { { it(item) } },
                supportingContent = supportingContent?.let { { it(item) } },
                wrapper = { wrapper(item, it) },
                content = { content(item) })
        }
    }
}

@Composable
inline fun SegmentedColumn(
    modifier: Modifier = Modifier,
    animated: Boolean = true,
    content: @Composable SegmentedListScope.() -> Unit
) {
    val builder = SegmentedListBuilder().apply { content() }
    val items = builder.items
    val count = items.size

    Column(
        modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isTopDetached = item.selected || index == 0
            val isBottomDetached = item.selected || index == count - 1

            key(item.key ?: index) {
                item.wrapper {
                    SegmentedListItem(
                        selected = item.selected,
                        onClick = item.onClick,
                        onLongClick = item.onLongClick,
                        index = index,
                        count = count,
                        isTopDetached = isTopDetached,
                        isBottomDetached = isBottomDetached,
                        enabled = item.enabled,
                        animated = animated,
                        leadingContent = item.leadingContent,
                        trailingContent = item.trailingContent,
                        supportingContent = item.supportingContent,
                        content = item.content
                    )
                }
            }
        }
    }
}

@Composable
inline fun SegmentedLazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    animated: Boolean = true,
    content: @Composable SegmentedListScope.() -> Unit
) {
    val builder = SegmentedListBuilder().apply { content() }
    val items = builder.items
    val count = items.size

    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(
            count = count, key = { index -> items[index].key ?: index }) { index ->
            val item = items[index]
            val isTopDetached = item.selected || index == 0
            val isBottomDetached = item.selected || index == count - 1

            key(item.key ?: index) {
                item.wrapper {
                    SegmentedListItem(
                        modifier = Modifier.animateItem(),
                        selected = item.selected,
                        onClick = item.onClick,
                        onLongClick = item.onLongClick,
                        index = index,
                        count = count,
                        isTopDetached = isTopDetached,
                        isBottomDetached = isBottomDetached,
                        enabled = item.enabled,
                        animated = animated,
                        leadingContent = item.leadingContent,
                        trailingContent = item.trailingContent,
                        supportingContent = item.supportingContent,
                        content = item.content
                    )
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SegmentedListItem(
    modifier: Modifier = Modifier,
    selected: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    index: Int = 0,
    count: Int = 1,
    isTopDetached: Boolean = (selected || index == 0),
    isBottomDetached: Boolean = (selected || index == count - 1),
    enabled: Boolean = true,
    animated: Boolean = true,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val defaultRadius = 16.dp
    val innerRadius = 4.dp

    val springSpec = MaterialTheme.motionScheme.fastSpatialSpec<Dp>()

    val targetTop = if (isTopDetached) defaultRadius else innerRadius
    val targetBottom = if (isBottomDetached) defaultRadius else innerRadius

    val topRadius = if (animated) {
        val animatedTop by animateDpAsState(
            targetValue = targetTop, animationSpec = springSpec, label = "topRadius"
        )
        animatedTop
    } else {
        targetTop
    }

    val bottomRadius = if (animated) {
        val animatedBottom by animateDpAsState(
            targetValue = targetBottom, animationSpec = springSpec, label = "bottomRadius"
        )
        animatedBottom
    } else {
        targetBottom
    }

    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius
    )

    val targetContainerColor =
        if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
    val containerColor = if (animated) {
        val animatedContainer by animateColorAsState(
            targetValue = targetContainerColor,
            label = "color"
        )
        animatedContainer
    } else {
        targetContainerColor
    }

    val targetBaseContentColor =
        if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val baseContentColor = if (animated) {
        val animatedBaseContent by animateColorAsState(
            targetValue = targetBaseContentColor,
            label = "contentColor"
        )
        animatedBaseContent
    } else {
        targetBaseContentColor
    }

    val contentColor = if (enabled) baseContentColor else baseContentColor.copy(alpha = 0.38f)
    val supportingContentColor =
        if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = 0.38f
        )
    val leadingIconColor =
        if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = 0.38f
        )
    val trailingIconColor =
        if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(
            alpha = 0.38f
        )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (enabled) containerColor else containerColor.copy(alpha = 0.6f))
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick?.let { { it() } },
                        enabled = enabled
                    )
                } else Modifier
            )
            .padding(
                horizontal = 16.dp,
                vertical = if (supportingContent != null) 12.dp else 16.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (leadingContent != null) {
            CompositionLocalProvider(LocalContentColor provides leadingIconColor) {
                Box(contentAlignment = Alignment.Center) {
                    leadingContent()
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
            if (supportingContent != null) {
                CompositionLocalProvider(LocalContentColor provides supportingContentColor) {
                    supportingContent()
                }
            }
        }

        if (trailingContent != null) {
            CompositionLocalProvider(LocalContentColor provides trailingIconColor) {
                Box(contentAlignment = Alignment.Center) {
                    trailingContent()
                }
            }
        }
    }
}
