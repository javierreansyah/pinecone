package com.example.readerapp.ui.features.reader.components.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.composables.icons.materialsymbols.outlined.Bookmark_check
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.List
import com.composables.icons.materialsymbols.outlined.Match_case
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Restart_alt
import com.composables.icons.materialsymbols.outlined.Search
import com.example.readerapp.R
import org.readium.r2.shared.publication.Locator

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ReaderTopBar(
    isBookmarked: Boolean,
    onBack: () -> Unit,
    onSearchClick: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    onInfoClick: () -> Unit,
    readerBgColor: Color,
    readerTextColor: Color,
    jumpOrigin: Locator?,
    onGoBackToOriginClick: () -> Unit,
    onClearJumpOriginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(readerBgColor, Color.Transparent)
                )
            )
    ) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        MaterialSymbols.Outlined.Arrow_back,
                        contentDescription = stringResource(R.string.action_back),
                        tint = readerTextColor
                    )
                }
            },
            actions = {
                if (jumpOrigin != null) {
                    JumpHistoryPill(
                        onGoBack = onGoBackToOriginClick,
                        onClear = onClearJumpOriginClick,
                        textColor = readerTextColor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        imageVector = if (isBookmarked) MaterialSymbols.Outlined.Bookmark_check else MaterialSymbols.Outlined.Bookmark,
                        contentDescription = if (isBookmarked) stringResource(R.string.reader_remove_bookmark) else stringResource(
                            R.string.reader_add_bookmark
                        ),
                        tint = readerTextColor
                    )
                }

                IconButton(onClick = onTocClick) {
                    Icon(
                        MaterialSymbols.Outlined.List,
                        contentDescription = stringResource(R.string.reader_toc_title),
                        modifier = Modifier.size(28.dp),
                        tint = readerTextColor
                    )
                }

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        MaterialSymbols.Outlined.Match_case,
                        contentDescription = stringResource(R.string.reader_settings_typography),
                        tint = readerTextColor
                    )
                }

                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            MaterialSymbols.Outlined.More_vert,
                            contentDescription = stringResource(R.string.action_more),
                            tint = readerTextColor
                        )
                    }
                    DropdownMenuPopup(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        val groupInteractionSource = remember { MutableInteractionSource() }
                        DropdownMenuGroup(
                            shapes = MenuDefaults.groupShape(0, 1),
                            interactionSource = groupInteractionSource
                        ) {
                            DropdownMenuItem(
                                selected = false,
                                text = {
                                    Text(stringResource(R.string.action_search))
                                },
                                onClick = {
                                    onSearchClick()
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        MaterialSymbols.Outlined.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(MenuDefaults.LeadingIconSize)
                                    )
                                },
                                shapes = MenuDefaults.itemShape(0, 2)
                            )
                            DropdownMenuItem(
                                selected = false,
                                text = {
                                    Text(stringResource(R.string.book_info_title))
                                },
                                onClick = {
                                    onInfoClick()
                                    showMoreMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        MaterialSymbols.Outlined.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(MenuDefaults.LeadingIconSize)
                                    )
                                },
                                shapes = MenuDefaults.itemShape(1, 2)
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                navigationIconContentColor = readerTextColor,
                actionIconContentColor = readerTextColor
            )
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JumpHistoryPill(
    onGoBack: () -> Unit,
    onClear: () -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.action_clear)
    val size = SplitButtonDefaults.ExtraSmallContainerHeight
    val transparentOutline = textColor.copy(alpha = 0.3f)

    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.OutlinedLeadingButton(
                onClick = onGoBack,
                modifier = Modifier.heightIn(size),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = textColor,
                    containerColor = Color.Transparent
                ),
                border = BorderStroke(1.dp, transparentOutline)
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Restart_alt,
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(size)),
                    contentDescription = null,
                    tint = textColor
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(size)))
                Text(
                    text = stringResource(R.string.reader_jump_go_back),
                    style = ButtonDefaults.textStyleFor(size),
                    color = textColor
                )
            }
        },
        trailingButton = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    TooltipAnchorPosition.Above
                ),
                tooltip = {
                    PlainTooltip {
                        Text(description)
                    }
                },
                state = rememberTooltipState(),
            ) {
                SplitButtonDefaults.OutlinedTrailingButton(
                    checked = false,
                    onCheckedChange = { _ -> onClear() },
                    modifier = Modifier.heightIn(size),
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(size),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textColor,
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.dp, transparentOutline)
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Close,
                        modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(size)),
                        contentDescription = description,
                        tint = textColor
                    )
                }
            }
        },
        modifier = modifier
    )
}
