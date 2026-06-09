@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.example.readerapp.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
    modifier: Modifier = Modifier, content: @Composable SegmentedListScope.() -> Unit
) {
    val builder = SegmentedListBuilder().apply { content() }
    val items = builder.items
    val count = items.size

    Column(
        modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, item ->
            item.wrapper {
                SegmentedListItem(
                    selected = item.selected,
                    onClick = item.onClick,
                    index = index,
                    count = count,
                    enabled = item.enabled,
                    leadingContent = item.leadingContent,
                    trailingContent = item.trailingContent,
                    supportingContent = item.supportingContent,
                    content = item.content
                )
            }
        }
    }
}

@Composable
inline fun SegmentedLazyColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
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
            item.wrapper {
                SegmentedListItem(
                    selected = item.selected,
                    onClick = item.onClick,
                    index = index,
                    count = count,
                    enabled = item.enabled,
                    leadingContent = item.leadingContent,
                    trailingContent = item.trailingContent,
                    supportingContent = item.supportingContent,
                    content = item.content
                )
            }
        }
    }
}

@Composable
fun SegmentedListItem(
    selected: Boolean,
    onClick: (() -> Unit)?,
    index: Int,
    count: Int,
    enabled: Boolean = true,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val defaultRadius = 16.dp
    val innerRadius = 4.dp
    val springSpec = spring<Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow
    )

    val targetTop = if (selected || index == 0) defaultRadius else innerRadius
    val targetBottom = if (selected || index == count - 1) defaultRadius else innerRadius

    val topRadius by animateDpAsState(
        targetValue = targetTop, animationSpec = springSpec, label = "topRadius"
    )
    val bottomRadius by animateDpAsState(
        targetValue = targetBottom, animationSpec = springSpec, label = "bottomRadius"
    )

    val shape = RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomStart = bottomRadius,
        bottomEnd = bottomRadius
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        label = "color"
    )
    val baseContentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        label = "contentColor"
    )
    val contentColor = if (enabled) baseContentColor else baseContentColor.copy(alpha = 0.38f)

    if (onClick != null) {
        ListItem(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape),
            enabled = enabled,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            verticalAlignment = Alignment.CenterVertically,
            colors = ListItemDefaults.colors(
                containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.6f),
                headlineColor = contentColor,
                supportingColor = contentColor,
                leadingIconColor = contentColor,
                trailingIconColor = contentColor
            ),
            content = content
        )
    } else {
        ListItem(
            headlineContent = content,
            supportingContent = supportingContent,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            colors = ListItemDefaults.colors(
                containerColor = if (enabled) containerColor else containerColor.copy(alpha = 0.6f),
                headlineColor = contentColor,
                supportingColor = contentColor,
                leadingIconColor = contentColor,
                trailingIconColor = contentColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
        )
    }
}
