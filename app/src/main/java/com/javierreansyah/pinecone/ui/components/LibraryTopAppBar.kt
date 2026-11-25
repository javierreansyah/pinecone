package com.javierreansyah.pinecone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.javierreansyah.pinecone.R

val LocalTopAppBarCollapsedRow = compositionLocalOf { false }

@Composable
fun LibraryTopAppBarTitle(
    text: String,
    modifier: Modifier = Modifier,
    expandedMaxLines: Int = 2,
) {
    Text(
        text = text,
        maxLines = if (LocalTopAppBarCollapsedRow.current) 1 else expandedMaxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun topAppBarNavigationButtonColors(): IconButtonColors {
    val containerColor = MaterialTheme.colorScheme.background
    return IconButtonDefaults.filledTonalIconButtonColors(
        containerColor = containerColor,
        contentColor = contentColorFor(containerColor),
    )
}

@Composable
fun Modifier.topBarFadeBackground(
    color: Color = MaterialTheme.colorScheme.background,
): Modifier = background(
    Brush.verticalGradient(
        0f to color,
        0.25f to color.copy(alpha = 0.95f),
        0.75f to color.copy(alpha = 0.75f),
        1f to color.copy(alpha = 0f),
    ),
)

@Composable
fun TopAppBarActionGroup(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.background,
    contentColor: Color = contentColorFor(containerColor),
    content: @Composable RowScope.() -> Unit,
) {
    ConnectedButtonGroup(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
        content = content,
    )
}

@Composable
fun LibraryTopAppBar(
    title: @Composable () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isEmpty: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    subtitle: (@Composable () -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
    ),
    expandedHeight: Dp = 180.dp,
) {
    val finalNavigationIcon: @Composable () -> Unit = {
        if (navigationIcon != null) {
            navigationIcon()
        } else {
            FilledTonalIconButton(
                modifier = Modifier.size(AppBarButtonSize),
                shape = CircleShape,
                colors = topAppBarNavigationButtonColors(),
                onClick = onBack,
            ) {
                Icon(
                    MaterialSymbols.Outlined.Arrow_back,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
        }
    }

    val barModifier = modifier.topBarFadeBackground()

    if (isEmpty) {
        TopAppBar(
            title = {
                CompositionLocalProvider(LocalTopAppBarCollapsedRow provides true, content = title)
            },
            navigationIcon = finalNavigationIcon,
            actions = actions,
            colors = colors,
            modifier = barModifier,
        )
    } else {
        val collapsedTitleFontSize = MaterialTheme.typography.titleLarge.fontSize
        val collapsedSubtitleFontSize = MaterialTheme.typography.labelMedium.fontSize
        val expandedOnlySubtitle: (@Composable () -> Unit)? = subtitle?.let { content ->
            {
                if (LocalTextStyle.current.fontSize > collapsedSubtitleFontSize) {
                    content()
                }
            }
        }
        LargeFlexibleTopAppBar(
            expandedHeight = expandedHeight,
            title = {
                val isCollapsedRow = LocalTextStyle.current.fontSize <= collapsedTitleFontSize
                CompositionLocalProvider(
                    LocalTopAppBarCollapsedRow provides isCollapsedRow,
                    content = title,
                )
            },
            subtitle = expandedOnlySubtitle,
            navigationIcon = finalNavigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
            modifier = barModifier,
        )
    }
}
