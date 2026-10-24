package com.javierreansyah.pinecone.ui.features.reader.components.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SelectableDropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_back
import com.composables.icons.materialsymbols.outlined.Bookmark
import com.composables.icons.materialsymbols.outlined.Close
import com.composables.icons.materialsymbols.outlined.Description
import com.composables.icons.materialsymbols.outlined.Info
import com.composables.icons.materialsymbols.outlined.Keyboard_arrow_down
import com.composables.icons.materialsymbols.outlined.List
import com.composables.icons.materialsymbols.outlined.Match_case
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Restart_alt
import com.composables.icons.materialsymbols.outlined.Search
import com.composables.icons.materialsymbols.outlinedfilled.Bookmark_check
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.AnimatedSelectionIcon
import org.readium.r2.shared.publication.Locator

enum class TopBarState { NORMAL, JUMP, SEARCH }

@Composable
fun ReaderTopBar(
    isBookmarked: Boolean,
    isInSearchMode: Boolean,
    searchQuery: String,
    onSearchTextClick: () -> Unit,
    onCloseSearch: () -> Unit,
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
    modifier: Modifier = Modifier,
    canSearch: Boolean = true,
    typographySettings: Boolean = true,
    audioPlayer: Boolean = false,
    showReadMode: Boolean = false,
    readMode: Boolean = false,
    onToggleReadMode: () -> Unit = {},
    converting: Boolean = false,
    showConversionSpinner: Boolean = false,
    onShowConversionProgress: () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(readerBgColor, Color.Transparent),
                ),
            ),
    ) {
        TopAppBar(
            title = {
                val actionsEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                AnimatedVisibility(
                    visible = isInSearchMode,
                    enter = fadeIn(animationSpec = actionsEffectsSpec),
                    exit = fadeOut(animationSpec = actionsEffectsSpec),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                onClick = onSearchTextClick,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            )
                            .padding(vertical = 8.dp),
                    ) {
                        Text(
                            text = searchQuery,
                            color = readerTextColor,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            navigationIcon = {
                if (audioPlayer) {
                    FilledTonalIconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    ) {
                        Icon(
                            MaterialSymbols.Outlined.Keyboard_arrow_down,
                            contentDescription = stringResource(R.string.reader_collapse_audio_player),
                        )
                    }
                } else {
                    IconButton(onClick = onBack) {
                        Icon(
                            MaterialSymbols.Outlined.Arrow_back,
                            contentDescription = stringResource(R.string.action_back),
                            tint = readerTextColor,
                        )
                    }
                }
            },
            actions = {
                if (converting && showConversionSpinner) {
                    val progressDescription = stringResource(R.string.reader_preparing_read_mode)
                    IconButton(
                        onClick = onShowConversionProgress,
                        modifier = Modifier.semantics { contentDescription = progressDescription },
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = readerTextColor,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                val topBarState = when {
                    isInSearchMode -> TopBarState.SEARCH
                    jumpOrigin != null -> TopBarState.JUMP
                    else -> TopBarState.NORMAL
                }

                val density = LocalDensity.current
                val slideOffsetPx = remember(density) { with(density) { 30.dp.roundToPx() } }
                val actionsSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
                val actionsEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                AnimatedContent(
                    targetState = topBarState,
                    transitionSpec = {
                        if (targetState != TopBarState.NORMAL && initialState == TopBarState.NORMAL) {

                            fadeIn(animationSpec = actionsEffectsSpec) togetherWith fadeOut(
                                animationSpec = actionsEffectsSpec
                            ) + slideOutHorizontally(
                                targetOffsetX = { slideOffsetPx },
                                animationSpec = actionsSpatialSpec,
                            )
                        } else if (targetState == TopBarState.NORMAL && initialState != TopBarState.NORMAL) {

                            fadeIn(animationSpec = actionsEffectsSpec) + slideInHorizontally(
                                initialOffsetX = { slideOffsetPx },
                                animationSpec = actionsSpatialSpec,
                            ) togetherWith
                                    fadeOut(animationSpec = actionsEffectsSpec)
                        } else {

                            fadeIn(animationSpec = actionsEffectsSpec) togetherWith fadeOut(
                                animationSpec = actionsEffectsSpec,
                            )
                        }
                    },
                    label = "readerTopBarActionsTransition",
                ) { state ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state == TopBarState.SEARCH) {
                            IconButton(onClick = onCloseSearch) {
                                Icon(
                                    MaterialSymbols.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = readerTextColor,
                                )
                            }
                        } else {
                            if (state == TopBarState.JUMP) {
                                JumpHistoryPill(
                                    onGoBack = onGoBackToOriginClick,
                                    onClear = onClearJumpOriginClick,
                                    textColor = readerTextColor,
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            if (showReadMode && !converting) {
                                IconButton(onClick = onToggleReadMode) {
                                    Icon(
                                        imageVector = if (readMode) MaterialSymbols.Outlined.Description else Icons.AutoMirrored.Rounded.MenuBook,
                                        contentDescription = stringResource(if (readMode) R.string.reader_original_pdf else R.string.reader_read_mode),
                                        tint = readerTextColor,
                                    )
                                }
                            }

                            if (!audioPlayer) {
                                IconButton(onClick = onToggleBookmark) {
                                    AnimatedSelectionIcon(
                                        selected = isBookmarked,
                                        outlinedIcon = MaterialSymbols.Outlined.Bookmark,
                                        filledIcon = MaterialSymbols.OutlinedFilled.Bookmark_check,
                                        contentDescription = if (isBookmarked) stringResource(R.string.reader_remove_bookmark) else stringResource(
                                            R.string.reader_add_bookmark,
                                        ),
                                        tint = readerTextColor,
                                    )
                                }
                            } else {
                                IconButton(onClick = onInfoClick) {
                                    Icon(
                                        MaterialSymbols.Outlined.Info,
                                        contentDescription = stringResource(R.string.reader_menu_info),
                                        tint = readerTextColor,
                                    )
                                }
                            }

                            if (state == TopBarState.NORMAL && !audioPlayer) {
                                if (!showReadMode) IconButton(onClick = onTocClick) {
                                    Icon(
                                        MaterialSymbols.Outlined.List,
                                        contentDescription = stringResource(R.string.reader_toc_title),
                                        modifier = Modifier.size(28.dp),
                                        tint = readerTextColor,
                                    )
                                }

                                IconButton(onClick = onSettingsClick) {
                                    Icon(
                                        if (typographySettings) MaterialSymbols.Outlined.Match_case else Icons.Rounded.Tune,
                                        contentDescription = stringResource(if (typographySettings) R.string.reader_settings_typography else R.string.reader_format_settings),
                                        modifier = Modifier.size(28.dp),
                                        tint = readerTextColor,
                                    )
                                }
                            }

                            val items = remember(state, canSearch, showReadMode, audioPlayer) {
                                buildList {
                                    if ((state == TopBarState.JUMP || showReadMode) && !audioPlayer) add(
                                        "content"
                                    )
                                    if (state == TopBarState.JUMP && !audioPlayer) add("settings")
                                    if (canSearch) add("search")
                                    if (!audioPlayer) add("info")
                                }
                            }

                            if (items.isNotEmpty()) {
                                var showMoreMenu by remember { mutableStateOf(value = false) }
                                Box {
                                    IconButton(onClick = { showMoreMenu = true }) {
                                        Icon(
                                            MaterialSymbols.Outlined.More_vert,
                                            contentDescription = stringResource(R.string.action_more),
                                            tint = readerTextColor,
                                        )
                                    }
                                    DropdownMenuPopup(
                                        expanded = showMoreMenu,
                                        onDismissRequest = { showMoreMenu = false },
                                    ) {
                                        val groupInteractionSource =
                                            remember { MutableInteractionSource() }
                                        DropdownMenuGroup(
                                            shapes = MenuDefaults.groupShape(0, 1),
                                            interactionSource = groupInteractionSource,
                                        ) {
                                            items.forEachIndexed { index, item ->
                                                when (item) {
                                                    "content" -> {
                                                        SelectableDropdownMenuItem(
                                                            selected = false,
                                                            text = {
                                                                Text(stringResource(R.string.reader_menu_content))
                                                            },
                                                            onClick = {
                                                                onTocClick()
                                                                showMoreMenu = false
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                    MaterialSymbols.Outlined.List,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(
                                                                        MenuDefaults.LeadingIconSize,
                                                                    ),
                                                                )
                                                            },
                                                            shapes = MenuDefaults.itemShape(
                                                                index,
                                                                items.size,
                                                            ),
                                                        )
                                                    }

                                                    "settings" -> {
                                                        SelectableDropdownMenuItem(
                                                            selected = false,
                                                            text = {
                                                                Text(stringResource(R.string.reader_menu_settings))
                                                            },
                                                            onClick = {
                                                                onSettingsClick()
                                                                showMoreMenu = false
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                    MaterialSymbols.Outlined.Match_case,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(
                                                                        MenuDefaults.LeadingIconSize,
                                                                    ),
                                                                )
                                                            },
                                                            shapes = MenuDefaults.itemShape(
                                                                index,
                                                                items.size,
                                                            ),
                                                        )
                                                    }

                                                    "search" -> {
                                                        SelectableDropdownMenuItem(
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
                                                                    modifier = Modifier.size(
                                                                        MenuDefaults.LeadingIconSize,
                                                                    ),
                                                                )
                                                            },
                                                            shapes = MenuDefaults.itemShape(
                                                                index,
                                                                items.size,
                                                            ),
                                                        )
                                                    }

                                                    "info" -> {
                                                        SelectableDropdownMenuItem(
                                                            selected = false,
                                                            text = {
                                                                Text(stringResource(R.string.reader_menu_info))
                                                            },
                                                            onClick = {
                                                                onInfoClick()
                                                                showMoreMenu = false
                                                            },
                                                            leadingIcon = {
                                                                Icon(
                                                                    MaterialSymbols.Outlined.Info,
                                                                    contentDescription = null,
                                                                    modifier = Modifier.size(
                                                                        MenuDefaults.LeadingIconSize,
                                                                    ),
                                                                )
                                                            },
                                                            shapes = MenuDefaults.itemShape(
                                                                index,
                                                                items.size,
                                                            ),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                navigationIconContentColor = readerTextColor,
                actionIconContentColor = readerTextColor,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpHistoryPill(
    onGoBack: () -> Unit,
    onClear: () -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier,
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
                    containerColor = Color.Transparent,
                ),
                border = BorderStroke(1.dp, transparentOutline),
            ) {
                Icon(
                    imageVector = MaterialSymbols.Outlined.Restart_alt,
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(size)),
                    contentDescription = null,
                    tint = textColor,
                )
                Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(size)))
                Text(
                    text = stringResource(R.string.reader_jump_go_back),
                    style = ButtonDefaults.textStyleFor(size),
                    color = textColor,
                )
            }
        },
        trailingButton = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(positioning = TooltipAnchorPosition.Above),
                tooltip = {
                    PlainTooltip {
                        Text(description)
                    }
                },
                state = rememberTooltipState(),
                modifier = Modifier.fillMaxHeight(),
            ) {
                SplitButtonDefaults.OutlinedTrailingButton(
                    checked = false,
                    onCheckedChange = { _ -> onClear() },
                    modifier = Modifier
                        .fillMaxHeight()
                        .heightIn(size),
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(size),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = textColor,
                        containerColor = Color.Transparent,
                    ),
                    border = BorderStroke(1.dp, transparentOutline),
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.Close,
                        modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(size)),
                        contentDescription = description,
                        tint = textColor,
                    )
                }
            }
        },
        modifier = modifier,
    )
}
