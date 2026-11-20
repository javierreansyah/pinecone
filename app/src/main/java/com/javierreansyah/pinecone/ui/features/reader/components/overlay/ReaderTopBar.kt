package com.javierreansyah.pinecone.ui.features.reader.components.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
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
import com.composables.icons.materialsymbols.outlined.Menu_book
import com.composables.icons.materialsymbols.outlined.More_vert
import com.composables.icons.materialsymbols.outlined.Restart_alt
import com.composables.icons.materialsymbols.outlined.Search
import com.composables.icons.materialsymbols.outlined.Tune
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.AnimatedSelectionIcon
import com.javierreansyah.pinecone.ui.components.AppBarButtonSize
import com.javierreansyah.pinecone.ui.components.ConnectedButtonGroup
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconButton
import com.javierreansyah.pinecone.ui.components.ConnectedGroupIconToggleButton
import com.javierreansyah.pinecone.ui.components.topAppBarNavigationButtonColors
import com.javierreansyah.pinecone.ui.components.topBarFadeBackground
import org.readium.r2.shared.publication.Locator
import com.composables.icons.materialsymbols.outlinedfilled.Bookmark_check as FilledBookmarkCheck

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
    pdfMode: Boolean = false,
    pdfDarkPage: Boolean = false,
) {
    val pdfContainerColor = if (pdfDarkPage) Color.Black else Color.White
    val pdfContentColor = if (pdfDarkPage) Color.White else Color.Black
    val barContainerColor = if (pdfMode) pdfContainerColor else readerBgColor
    val barContentColor = if (pdfMode) pdfContentColor else readerTextColor

    Box(
        modifier = modifier
            .fillMaxWidth()
            .topBarFadeBackground(barContainerColor),
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
                            color = barContentColor,
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
                        modifier = Modifier.size(AppBarButtonSize),
                        onClick = onBack,
                        shape = CircleShape,
                        colors = topAppBarNavigationButtonColors(),
                    ) {
                        Icon(
                            MaterialSymbols.Outlined.Keyboard_arrow_down,
                            contentDescription = stringResource(R.string.reader_collapse_audio_player),
                        )
                    }
                } else {
                    FilledTonalIconButton(
                        modifier = Modifier.size(AppBarButtonSize),
                        onClick = onBack,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = barContainerColor,
                            contentColor = barContentColor,
                        ),
                    ) {
                        Icon(
                            MaterialSymbols.Outlined.Arrow_back,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                }
            },
            actions = {
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
                            FilledTonalIconButton(
                                modifier = Modifier.size(AppBarButtonSize),
                                onClick = onCloseSearch,
                                shape = CircleShape,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = barContainerColor,
                                    contentColor = barContentColor,
                                ),
                            ) {
                                Icon(
                                    MaterialSymbols.Outlined.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                )
                            }
                        } else {
                            if (state == TopBarState.JUMP) {
                                val jumpContainerColor = if (audioPlayer) {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                } else {
                                    barContentColor.copy(alpha = 0.18f)
                                        .compositeOver(barContainerColor)
                                }
                                JumpHistoryPill(
                                    onGoBack = onGoBackToOriginClick,
                                    onClear = onClearJumpOriginClick,
                                    textColor = barContentColor,
                                    containerColor = jumpContainerColor,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
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

                            if (audioPlayer) {
                                FilledTonalIconButton(
                                    modifier = Modifier.size(AppBarButtonSize),
                                    onClick = onInfoClick,
                                    shape = CircleShape,
                                    colors = topAppBarNavigationButtonColors(),
                                ) {
                                    Icon(
                                        MaterialSymbols.Outlined.Info,
                                        contentDescription = stringResource(R.string.reader_menu_info),
                                    )
                                }
                            } else {
                                ReaderTopBarButtonGroup(
                                    state = state,
                                    isBookmarked = isBookmarked,
                                    typographySettings = typographySettings,
                                    showReadMode = showReadMode,
                                    readMode = readMode,
                                    converting = converting,
                                    moreItems = items,
                                    containerColor = barContainerColor,
                                    contentColor = barContentColor,
                                    onToggleReadMode = onToggleReadMode,
                                    onToggleBookmark = onToggleBookmark,
                                    onSettingsClick = onSettingsClick,
                                    onTocClick = onTocClick,
                                    onSearchClick = onSearchClick,
                                    onInfoClick = onInfoClick,
                                )
                            }
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                navigationIconContentColor = barContentColor,
                actionIconContentColor = barContentColor,
            ),
        )
    }
}

@Composable
private fun ReaderTopBarButtonGroup(
    state: TopBarState,
    isBookmarked: Boolean,
    typographySettings: Boolean,
    showReadMode: Boolean,
    readMode: Boolean,
    converting: Boolean,
    moreItems: List<String>,
    containerColor: Color,
    contentColor: Color,
    onToggleReadMode: () -> Unit,
    onToggleBookmark: () -> Unit,
    onSettingsClick: () -> Unit,
    onTocClick: () -> Unit,
    onSearchClick: () -> Unit,
    onInfoClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMoreMenu by remember { mutableStateOf(false) }

    ConnectedButtonGroup(
        modifier = modifier,
        containerColor = containerColor,
        contentColor = contentColor,
    ) {
        if (showReadMode && !converting) {
            ConnectedGroupIconButton(
                onClick = onToggleReadMode,
                contentColor = contentColor,
            ) {
                Icon(
                    imageVector = if (readMode) MaterialSymbols.Outlined.Description else MaterialSymbols.Outlined.Menu_book,
                    contentDescription = stringResource(if (readMode) R.string.reader_original_pdf else R.string.reader_read_mode),
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        ConnectedGroupIconToggleButton(
            checked = isBookmarked,
            onCheckedChange = { onToggleBookmark() },
            contentColor = contentColor,
        ) {
            AnimatedSelectionIcon(
                selected = isBookmarked,
                outlinedIcon = MaterialSymbols.Outlined.Bookmark,
                filledIcon = MaterialSymbols.OutlinedFilled.FilledBookmarkCheck,
                contentDescription = if (isBookmarked) stringResource(R.string.reader_remove_bookmark) else stringResource(
                    R.string.reader_add_bookmark,
                ),
                modifier = Modifier.size(24.dp),
                tint = contentColor,
            )
        }

        if (state == TopBarState.NORMAL) {
            if (!showReadMode) {
                ConnectedGroupIconButton(
                    onClick = onTocClick,
                    contentColor = contentColor,
                ) {
                    Icon(
                        imageVector = MaterialSymbols.Outlined.List,
                        contentDescription = stringResource(R.string.reader_toc_title),
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            ConnectedGroupIconButton(
                onClick = onSettingsClick,
                contentColor = contentColor,
            ) {
                Icon(
                    imageVector = if (typographySettings) MaterialSymbols.Outlined.Match_case else MaterialSymbols.Outlined.Tune,
                    contentDescription = stringResource(if (typographySettings) R.string.reader_settings_typography else R.string.reader_format_settings),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        if (moreItems.isNotEmpty()) {
            Box {
                ConnectedGroupIconButton(
                    onClick = { showMoreMenu = true },
                    contentColor = contentColor,
                ) {
                    Icon(
                        MaterialSymbols.Outlined.More_vert,
                        contentDescription = stringResource(R.string.action_more),
                        modifier = Modifier.size(24.dp),
                    )
                }
                ReaderTopBarMoreMenu(
                    expanded = showMoreMenu,
                    items = moreItems,
                    onDismiss = { showMoreMenu = false },
                    onTocClick = onTocClick,
                    onSettingsClick = onSettingsClick,
                    onSearchClick = onSearchClick,
                    onInfoClick = onInfoClick,
                )
            }
        }
    }
}

@Composable
private fun ReaderTopBarMoreMenu(
    expanded: Boolean,
    items: List<String>,
    onDismiss: () -> Unit,
    onTocClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
    onInfoClick: () -> Unit,
) {
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        val groupInteractionSource = remember { MutableInteractionSource() }
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
                                onDismiss()
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
                                onDismiss()
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
                                onDismiss()
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
                                onDismiss()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JumpHistoryPill(
    onGoBack: () -> Unit,
    onClear: () -> Unit,
    textColor: Color,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Transparent,
) {
    val description = stringResource(R.string.action_clear)
    val size = AppBarButtonSize
    val buttonColors = ButtonDefaults.filledTonalButtonColors(
        containerColor = containerColor,
        contentColor = textColor,
    )

    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = onGoBack,
                modifier = Modifier.heightIn(size),
                shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
                colors = buttonColors,
                elevation = null,
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
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    positioning = TooltipAnchorPosition.Above,
                ),
                tooltip = {
                    PlainTooltip {
                        Text(description)
                    }
                },
                state = rememberTooltipState(),
            ) {
                SplitButtonDefaults.TrailingButton(
                    onClick = onClear,
                    modifier = Modifier.heightIn(size),
                    shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(size),
                    colors = buttonColors,
                    elevation = null,
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
