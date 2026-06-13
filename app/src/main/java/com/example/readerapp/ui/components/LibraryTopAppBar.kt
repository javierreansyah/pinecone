package com.example.readerapp.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.example.readerapp.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
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
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
    ),
    expandedHeight: Dp = 180.dp
) {
    val finalNavigationIcon = navigationIcon ?: {
        FilledTonalIconButton(
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            onClick = onBack
        ) {
            Icon(
                MaterialSymbols.Outlined.Arrow_back,
                contentDescription = stringResource(R.string.action_back)
            )
        }
    }

    if (isEmpty) {
        TopAppBar(
            title = title,
            navigationIcon = finalNavigationIcon,
            actions = actions,
            colors = colors,
            modifier = modifier
        )
    } else {
        LargeFlexibleTopAppBar(
            expandedHeight = expandedHeight,
            title = title,
            subtitle = subtitle,
            navigationIcon = finalNavigationIcon,
            actions = actions,
            colors = colors,
            scrollBehavior = scrollBehavior,
            modifier = modifier
        )
    }
}
