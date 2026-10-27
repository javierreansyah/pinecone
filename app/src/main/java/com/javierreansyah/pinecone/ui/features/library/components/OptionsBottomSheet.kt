package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Arrow_drop_down
import com.composables.icons.materialsymbols.outlined.Arrow_drop_up
import com.composables.icons.materialsymbols.outlined.Calendar_view_month
import com.composables.icons.materialsymbols.outlined.View_carousel
import com.composables.icons.materialsymbols.outlined.View_list
import com.composables.icons.materialsymbols.outlined.Window
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.ui.components.AnimatedSelectionIcon
import com.javierreansyah.pinecone.ui.features.library.FileFilter
import com.javierreansyah.pinecone.ui.features.library.FilterSortPreferences
import com.javierreansyah.pinecone.ui.features.library.LayoutMode
import com.javierreansyah.pinecone.ui.features.library.LibraryView
import com.javierreansyah.pinecone.ui.features.library.PublicationWording
import com.javierreansyah.pinecone.ui.features.library.ShelfFilter
import com.javierreansyah.pinecone.ui.features.library.SortType
import com.javierreansyah.pinecone.ui.features.library.StatusFilter
import com.javierreansyah.pinecone.ui.features.library.publicationWording
import com.composables.icons.materialsymbols.outlinedfilled.Calendar_view_month as FilledCalendarViewMonth
import com.composables.icons.materialsymbols.outlinedfilled.View_carousel as FilledViewCarousel
import com.composables.icons.materialsymbols.outlinedfilled.View_list as FilledViewList
import com.composables.icons.materialsymbols.outlinedfilled.Window as FilledWindow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
fun OptionsBottomSheetSection(
    title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
fun <T> SortRadioGroup(
    options: List<T>,
    selectedOption: T,
    isAscending: Boolean,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionLabel: @Composable (T) -> String,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, type ->
            val isSelected = selectedOption == type
            ToggleButton(
                checked = isSelected,
                onCheckedChange = { onOptionSelected(type) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.semantics { role = Role.RadioButton },
            ) {
                ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(optionLabel(type))
                        if (isSelected) {
                            Icon(
                                imageVector = if (isAscending) MaterialSymbols.Outlined.Arrow_drop_up else MaterialSymbols.Outlined.Arrow_drop_down,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun <T> SortSection(
    options: List<T>,
    selectedOption: T,
    isAscending: Boolean,
    onOptionSelected: (T) -> Unit,
    optionLabel: @Composable (T) -> String,
) {
    OptionsBottomSheetSection(title = stringResource(R.string.action_sort)) {
        SortRadioGroup(
            options = options,
            selectedOption = selectedOption,
            isAscending = isAscending,
            onOptionSelected = onOptionSelected,
            optionLabel = optionLabel,
        )
    }
}

@Composable
fun <T> SingleToggleGroupSection(
    title: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    optionContent: @Composable (T) -> Unit,
) {
    OptionsBottomSheetSection(title = title) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = selectedOption == option
                ToggleButton(
                    checked = isSelected,
                    onCheckedChange = { onOptionSelected(option) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                ) {
                    optionContent(option)
                }
            }
        }
    }
}

@Composable
fun <T> MultiToggleGroupSection(
    title: String,
    options: List<T>,
    selectedOptions: Set<T>,
    onOptionToggled: (T) -> Unit,
    singleSelection: Boolean = false,
    optionContent: @Composable (T) -> Unit,
) {
    OptionsBottomSheetSection(title = title) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option in selectedOptions
                ToggleButton(
                    modifier = if (singleSelection) Modifier.semantics {
                        role = Role.RadioButton
                    } else Modifier,
                    checked = isSelected,
                    onCheckedChange = { onOptionToggled(option) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                ) {
                    ProvideTextStyle(value = MaterialTheme.typography.labelLarge) {
                        optionContent(option)
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryFilterBottomSheet(
    isShelvesTab: Boolean,
    preferences: FilterSortPreferences,
    availableFormats: Set<FileFilter>,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onShelfFilterToggle: (ShelfFilter) -> Unit = {},
    onFileFilterToggle: (FileFilter) -> Unit = {},
    libraryView: LibraryView = LibraryView.Both,
    onLibraryViewChange: (LibraryView) -> Unit = {},
    onDismiss: () -> Unit,
) {
    OptionsBottomSheet(onDismissRequest = onDismiss) {
        val layoutModes = if (isShelvesTab) listOf(LayoutMode.List, LayoutMode.BigList)
        else listOf(LayoutMode.Grid, LayoutMode.BigGrid, LayoutMode.List)

        val sortTypes = if (isShelvesTab) listOf(
            SortType.Title, SortType.LastRead, SortType.Added, SortType.Progress,
        )
        else listOf(
            SortType.Title, SortType.Author, SortType.LastRead, SortType.Added, SortType.Progress,
        )

        FilterLayoutAndSort(
            preferences,
            layoutModes,
            sortTypes,
            onLayoutModeChange,
            onSortTypeChange
        )

        if (!isShelvesTab) {
            MultiToggleGroupSection(
                title = stringResource(R.string.library_label_status),
                options = StatusFilter.entries,
                selectedOptions = preferences.selectedStatus,
                onOptionToggled = onStatusToggle,
            ) { status ->
                Text(
                    stringResource(statusLabelRes(status)),
                )
            }
        } else {
            MultiToggleGroupSection(
                title = stringResource(R.string.action_filter),
                options = ShelfFilter.entries,
                selectedOptions = preferences.selectedShelfFilter,
                onOptionToggled = onShelfFilterToggle,
            ) { filter ->
                Text(
                    if (filter == ShelfFilter.Shelves) stringResource(R.string.library_tab_shelves) else stringResource(
                        R.string.library_label_unshelved,
                    ),
                )
            }
        }

        MultiToggleGroupSection(
            title = stringResource(R.string.library_label_show),
            options = LibraryView.entries,
            selectedOptions = setOf(libraryView),
            onOptionToggled = onLibraryViewChange,
            singleSelection = true,
        ) { view ->
            Text(
                stringResource(
                    when (view) {
                        LibraryView.Both -> R.string.library_show_both
                        LibraryView.Books -> if (publicationWording(availableFormats) == PublicationWording.Books) {
                            R.string.content_books_only
                        } else R.string.library_show_items

                        LibraryView.Shelves -> R.string.library_show_shelves
                    }
                )
            )
        }

        FileFilterSection(preferences, availableFormats, onFileFilterToggle)
    }
}

@Composable
private fun FilterLayoutAndSort(
    preferences: FilterSortPreferences,
    layoutModes: List<LayoutMode>,
    sortTypes: List<SortType>,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
) {
    SingleToggleGroupSection(
        title = stringResource(R.string.library_label_layout),
        options = layoutModes,
        selectedOption = preferences.layoutMode,
        onOptionSelected = onLayoutModeChange,
    ) { mode ->
        LayoutModeIcon(mode, selected = mode == preferences.layoutMode)
    }

    SortSection(
        options = sortTypes,
        selectedOption = preferences.sortType,
        isAscending = preferences.isAscending,
        onOptionSelected = onSortTypeChange,
        optionLabel = { stringResource(sortTypeLabelRes(it)) },
    )
}

@Composable
fun ShelfDetailFilterBottomSheet(
    shelfId: String,
    preferences: FilterSortPreferences,
    availableFormats: Set<FileFilter>,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onFileFilterToggle: (FileFilter) -> Unit = {},
    onDismiss: () -> Unit,
) {
    FilterResultBottomSheet(
        preferences, availableFormats, onLayoutModeChange, onSortTypeChange, onStatusToggle,
        onFileFilterToggle, onDismiss = onDismiss,
        sortTypes = if (shelfId == "unshelved") SortType.entries.filter { it != SortType.Custom }
        else SortType.entries,
    )
}

@Composable
fun FilterResultBottomSheet(
    preferences: FilterSortPreferences,
    availableFormats: Set<FileFilter>,
    onLayoutModeChange: (LayoutMode) -> Unit,
    onSortTypeChange: (SortType) -> Unit,
    onStatusToggle: (StatusFilter) -> Unit,
    onFileFilterToggle: (FileFilter) -> Unit = {},
    sortTypes: List<SortType> = listOf(
        SortType.Title, SortType.Author, SortType.LastRead, SortType.Added, SortType.Progress,
    ),
    onDismiss: () -> Unit,
) {
    OptionsBottomSheet(onDismissRequest = onDismiss) {
        FilterLayoutAndSort(
            preferences, listOf(LayoutMode.Grid, LayoutMode.BigGrid, LayoutMode.List), sortTypes,
            onLayoutModeChange, onSortTypeChange,
        )

        MultiToggleGroupSection(
            title = stringResource(R.string.library_label_status),
            options = StatusFilter.entries,
            selectedOptions = preferences.selectedStatus,
            onOptionToggled = onStatusToggle,
        ) { status ->
            Text(
                stringResource(statusLabelRes(status)),
            )
        }

        FileFilterSection(preferences, availableFormats, onFileFilterToggle)
    }
}

enum class FilterItemSortType { Label, Size }

@Composable
fun FilterItemSortBottomSheet(
    currentSortType: FilterItemSortType,
    isAscending: Boolean,
    onSortTypeChange: (FilterItemSortType) -> Unit,
    onDismiss: () -> Unit,
) {
    OptionsBottomSheet(onDismissRequest = onDismiss) {
        SortSection(
            options = FilterItemSortType.entries,
            selectedOption = currentSortType,
            isAscending = isAscending,
            onOptionSelected = onSortTypeChange,
        ) {
            if (it == FilterItemSortType.Label) stringResource(R.string.library_sort_label) else stringResource(
                R.string.library_sort_size,
            )
        }
    }
}

@Composable
private fun LayoutModeIcon(mode: LayoutMode, selected: Boolean) {
    val outlinedIcon = when (mode) {
        LayoutMode.Grid -> MaterialSymbols.Outlined.Calendar_view_month
        LayoutMode.BigGrid -> MaterialSymbols.Outlined.Window
        LayoutMode.List -> MaterialSymbols.Outlined.View_list
        LayoutMode.BigList -> MaterialSymbols.Outlined.View_carousel
    }
    val filledIcon = when (mode) {
        LayoutMode.Grid -> MaterialSymbols.OutlinedFilled.FilledCalendarViewMonth
        LayoutMode.BigGrid -> MaterialSymbols.OutlinedFilled.FilledWindow
        LayoutMode.List -> MaterialSymbols.OutlinedFilled.FilledViewList
        LayoutMode.BigList -> MaterialSymbols.OutlinedFilled.FilledViewCarousel
    }
    AnimatedSelectionIcon(selected, outlinedIcon, filledIcon, contentDescription = null)
}

private fun sortTypeLabelRes(type: SortType): Int = when (type) {
    SortType.Title -> R.string.library_sort_title
    SortType.Author -> R.string.library_sort_author
    SortType.LastRead -> R.string.library_sort_last_read
    SortType.Added -> R.string.library_sort_added
    SortType.Progress -> R.string.library_sort_progress
    SortType.Custom -> R.string.library_sort_custom
}

private fun statusLabelRes(status: StatusFilter): Int = when (status) {
    StatusFilter.NotStarted -> R.string.library_status_not_started
    StatusFilter.Reading -> R.string.library_status_reading
    StatusFilter.Finished -> R.string.library_status_finished
}

@Composable
private fun FileFilterSection(
    preferences: FilterSortPreferences,
    availableFormats: Set<FileFilter>,
    onToggle: (FileFilter) -> Unit,
) {
    val formats = FileFilter.entries.filter { it in availableFormats }
    if (formats.isEmpty()) return
    MultiToggleGroupSection(
        title = stringResource(R.string.library_label_formats),
        options = formats,
        selectedOptions = preferences.selectedFormats,
        onOptionToggled = onToggle,
    ) { format ->
        Text(
            when (format) {
                FileFilter.EPUB -> "EPUB"
                FileFilter.PDF -> "PDF"
                FileFilter.Comics -> stringResource(R.string.library_format_comics)
                FileFilter.Audio -> stringResource(R.string.library_format_audio)
                FileFilter.Images -> stringResource(R.string.library_format_images)
                FileFilter.WebPublication -> "WebPub"
                FileFilter.Other -> stringResource(R.string.library_format_other)
            }
        )
    }
}
