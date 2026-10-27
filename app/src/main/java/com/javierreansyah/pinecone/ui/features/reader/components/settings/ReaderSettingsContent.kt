package com.javierreansyah.pinecone.ui.features.reader.components.settings

import android.annotation.SuppressLint
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Brightness_medium
import com.composables.icons.materialsymbols.outlined.Contrast
import com.composables.icons.materialsymbols.outlined.Format_align_justify
import com.composables.icons.materialsymbols.outlined.Format_align_left
import com.composables.icons.materialsymbols.outlined.Format_align_right
import com.composables.icons.materialsymbols.outlined.Image
import com.composables.icons.materialsymbols.outlined.Invert_colors
import com.composables.icons.materialsymbols.outlined.Restart_alt
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.CustomPineconeTheme
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.reader.epub.toEpubPreferences
import com.javierreansyah.pinecone.ui.components.AnimatedSelectionIcon
import com.javierreansyah.pinecone.ui.components.SegmentedButtonGroup
import com.javierreansyah.pinecone.ui.components.SettingsChoiceDialog
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import com.composables.icons.materialsymbols.outlinedfilled.Brightness_auto as FilledBrightnessAuto

@SuppressLint("DefaultLocale", "ConfigurationScreenWidthHeight", "LogNotTimber")
@Composable
fun ReaderSettingsContent(
    settings: ReaderSettings, onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    profile: com.javierreansyah.pinecone.data.publication.ReaderProfile = com.javierreansyah.pinecone.data.publication.ReaderProfile.EPUB,
    fixedEpub: Boolean = false,
    publication: org.readium.r2.shared.publication.Publication? = null,
    useBookSettings: Boolean = false,
    onUseBookSettings: (Boolean) -> Unit = {},
    onReparsePdf: (() -> Unit)? = null,
) {
    if (profile != com.javierreansyah.pinecone.data.publication.ReaderProfile.EPUB || fixedEpub) {
        FormatReaderSettings(
            settings,
            onSettingsChange,
            profile,
            fixedEpub,
            useBookSettings,
            onUseBookSettings,
            onReparsePdf
        )
        return
    }
    val context = LocalContext.current
    val editor = remember(publication, settings) {
        publication?.let {
            org.readium.r2.navigator.epub.EpubNavigatorFactory(it)
                .createPreferencesEditor(settings.toEpubPreferences(false))
        }
    }
    val restoreDefaultsSuccessMsg =
        stringResource(R.string.reader_settings_restore_defaults_success)
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val maxSheetHeight = remember(windowInfo.containerSize, density) {
        with(density) { (windowInfo.containerSize.height * 0.6f).toDp() }
    }

    val textTabLabel = stringResource(R.string.reader_settings_tab_text)
    val lightingTabLabel = stringResource(R.string.reader_settings_tab_lighting)
    val advancedTabLabel = stringResource(R.string.reader_settings_tab_advanced)
    val tabs = listOf(textTabLabel, lightingTabLabel, advancedTabLabel)
    val pagerState = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()

    var showColorPicker by remember { mutableStateOf(value = false) }
    var themeToEdit by remember { mutableStateOf<CustomPineconeTheme?>(null) }
    var showRestoreDefaultWarning by remember { mutableStateOf(value = false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxSheetHeight)
            .padding(bottom = 4.dp),
    ) {
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage, containerColor = Color.Transparent,
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                    },
                )
            }
        }

        // Overriding the local overscroll configuration to null completely eliminates
        // the platform's elastic snap-back/bounce physics when you finish a swipe.
        CompositionLocalProvider(
            LocalOverscrollFactory provides null,
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 2,
                verticalAlignment = Alignment.Top,
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (page) {
                        0 -> {
                            TextTabContent(settings, onSettingsChange, locale)
                            editor?.let { preferences ->
                                if (preferences.ligatures.isEffective) PreferenceSwitch(
                                    stringResource(R.string.reader_ligatures),
                                    preferences.ligatures.value
                                        ?: preferences.ligatures.effectiveValue,
                                ) {
                                    onSettingsChange { current ->
                                        current.copy(
                                            formatPreferences = current.formatPreferences.copy(
                                                epubLigatures = it
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        1 -> LightingTabContent(
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                            onAddThemeClick = {
                                themeToEdit = null
                                showColorPicker = true
                            },
                        ) {
                            themeToEdit = it
                            showColorPicker = true
                        }

                        2 -> {
                            AdvancedTabContent(
                                settings = settings,
                                onSettingsChange = onSettingsChange,
                                onRestoreDefaultsClick = { showRestoreDefaultWarning = true },
                                content = {
                                    PreferenceSwitch(
                                        stringResource(R.string.reader_settings_this_publication),
                                        useBookSettings,
                                        onUseBookSettings
                                    )
                                },
                                textPreferencesContent = {
                                    editor?.let { preferences ->
                                        if (preferences.verticalText.isEffective) PreferenceSwitch(
                                            stringResource(R.string.reader_vertical_text),
                                            preferences.verticalText.value
                                                ?: preferences.verticalText.effectiveValue,
                                        ) {
                                            onSettingsChange { current ->
                                                current.copy(
                                                    formatPreferences = current.formatPreferences.copy(
                                                        epubVerticalText = it
                                                    )
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showColorPicker) {
        CustomColorPickerDialog(
            initialName = themeToEdit?.name ?: "",
            initialBgColor = themeToEdit?.backgroundColor ?: "#",
            initialTextColor = themeToEdit?.textColor ?: "#",
            initialUiTheme = themeToEdit?.uiTheme ?: "Setting",
            onDismiss = {
                showColorPicker = false
                themeToEdit = null
            },
            onDelete = if (themeToEdit != null) {
                {
                    val editedName = themeToEdit?.name
                    onSettingsChange { current ->
                        current.copy(
                            customThemes = current.customThemes.filter { it.name != editedName },
                            pineconeThemePreset = if (current.pineconeThemePreset == editedName) {
                                "Auto"
                            } else current.pineconeThemePreset,
                        )
                    }
                    showColorPicker = false
                    themeToEdit = null
                }
            } else null,
        ) { name, bgColor, textColor, uiTheme ->
            val newTheme = CustomPineconeTheme(name, bgColor, textColor, uiTheme)

            val editedName = themeToEdit?.name
            onSettingsChange { current ->
                val themes = if (editedName == null) current.customThemes + newTheme else {
                    current.customThemes.map { if (it.name == editedName) newTheme else it }
                }
                val preset = if (editedName == null || current.pineconeThemePreset == editedName) {
                    name
                } else current.pineconeThemePreset
                current.copy(
                    customThemes = themes,
                    pineconeThemePreset = preset,
                    customBackgroundColor = if (preset == name) bgColor else current.customBackgroundColor,
                    customTextColor = if (preset == name) textColor else current.customTextColor,
                )
            }
            showColorPicker = false
            themeToEdit = null
        }
    }

    if (showRestoreDefaultWarning) {
        AlertDialog(
            onDismissRequest = { showRestoreDefaultWarning = false },
            title = { Text(stringResource(R.string.reader_settings_restore_defaults)) },
            text = { Text(stringResource(R.string.reader_settings_restore_defaults_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestoreDefaultWarning = false
                        onSettingsChange { current ->
                            current.copy(
                                brightness = 0.5f,
                                autoBrightness = true,
                                forceOrientation = "Auto",
                                preventScreenTimeout = false,
                                alwaysShowStatusBar = false,
                                publisherStyles = true,
                                fontSize = 1.0,
                                fontFamily = "Source Serif 4",
                                textAlign = "Start",
                                lineHeight = 1.5,
                                paragraphSpacing = 0.0,
                                paragraphIndent = 0.0,
                                wordSpacing = 0.0,
                                letterSpacing = 0.0,
                                fontWeights = emptyMap(),
                                hyphens = false,
                                scroll = false,
                                columnCount = "Auto",
                                pageMargins = 1.0,
                                imageFilter = "None",
                                typeScale = 1.0,
                                verticalMargin = 32.0,
                                readingProgression = "LTR",
                                textNormalization = false,
                                pineconeThemePreset = "Auto",
                                customBackgroundColor = "#FFFFFF",
                                customTextColor = "#000000",
                                customThemes = emptyList(),
                                jumpHistoryMode = "explicit",
                                formatPreferences = current.formatPreferences.copy(
                                    epubSpread = "auto",
                                    epubLigatures = null,
                                    epubVerticalText = null,
                                    epubImageFilterFollowTheme = true
                                ),
                            )
                        }
                        Toast.makeText(
                            context,
                            restoreDefaultsSuccessMsg,
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text(stringResource(R.string.action_proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreDefaultWarning = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun TextTabContent(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    locale: Locale,
) {

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.reader_settings_font),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val fonts = listOf(
                "Source Serif 4" to "Serif",
                "Source Sans 3" to "Sans",
                "Literata" to "Literata",
                "Atkinson Hyperlegible" to "Atkinson",
                "Source Code" to "Mono",
            )
            fonts.forEach { (font, label) ->
                FontSwatch(
                    isSelected = settings.fontFamily == font,
                    onClick = { onSettingsChange { current -> current.copy(fontFamily = font) } },
                    fontName = font,
                    label = label,
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.reader_settings_typography),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            IncrementDecrementControl(
                label = stringResource(R.string.reader_settings_text_size),
                value = "${(settings.fontSize * 100).roundToInt()}%",
                onStep = { direction ->
                    onSettingsChange { current ->
                        current.copy(
                            fontSize = (((current.fontSize + direction * 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.5, 3.0,
                            ),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
            val currentFontWeight = settings.fontWeights[settings.fontFamily] ?: 1.0
            IncrementDecrementControl(
                label = stringResource(R.string.reader_settings_font_weight),
                value = String.format(locale, "%.2f", currentFontWeight),
                onStep = { direction ->
                    val newWeight =
                        (((currentFontWeight + direction * 0.25) * 100.0).roundToInt() / 100.0).coerceIn(
                            0.5, 2.25,
                        )
                    onSettingsChange { current -> current.copy(fontWeights = current.fontWeights + (current.fontFamily to newWeight)) }
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            IncrementDecrementControl(
                label = stringResource(R.string.reader_settings_horizontal_margin),
                value = String.format(locale, "%.1f", settings.pageMargins),
                onStep = { direction ->
                    onSettingsChange { current ->
                        current.copy(
                            pageMargins = (((current.pageMargins + direction * 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0, 3.0,
                            ),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
            IncrementDecrementControl(
                label = stringResource(R.string.reader_settings_vertical_margin),
                value = String.format(locale, "%.1f", settings.verticalMargin / 32.0),
                onStep = { direction ->
                    onSettingsChange { current ->
                        current.copy(
                            verticalMargin = (((current.verticalMargin + direction * 3.2) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0, 128.0,
                            ),
                        )
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        SettingsSwitchRow(
            title = stringResource(R.string.reader_settings_publisher_style),
            isChecked = settings.publisherStyles,
            onCheckedChange = { onSettingsChange { current -> current.copy(publisherStyles = it) } },
        )

        val alignmentOptionsMap = mapOf(
            "Left" to stringResource(R.string.reader_settings_alignment_left),
            "Right" to stringResource(R.string.reader_settings_alignment_right),
            "Justify" to stringResource(R.string.reader_settings_alignment_justify),
        )
        SegmentedButtonGroup(
            title = stringResource(R.string.reader_settings_alignment),
            options = alignmentOptionsMap.keys.toList(),
            optionLabel = { alignmentOptionsMap[it] ?: it },
            icons = listOf(
                MaterialSymbols.Outlined.Format_align_left,
                MaterialSymbols.Outlined.Format_align_right,
                MaterialSymbols.Outlined.Format_align_justify,
            ),
            selected = settings.textAlign,
            onSelected = { value -> onSettingsChange { current -> current.copy(textAlign = value) } },
            enabled = !settings.publisherStyles,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            IncrementDecrementControl(
                label = stringResource(R.string.reader_settings_line_spacing),
                value = String.format(locale, "%.1f", settings.lineHeight),
                onStep = { direction ->
                    onSettingsChange { current ->
                        current.copy(
                            lineHeight = (((current.lineHeight + direction * 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                1.0, 2.5,
                            ),
                        )
                    }
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(1f),
            )
            IncrementDecrementControl(
                label = stringResource(R.string.reader_settings_letter_spacing),
                value = String.format(locale, "%.1f", settings.letterSpacing * 4.0),
                onStep = { direction ->
                    onSettingsChange { current ->
                        current.copy(
                            letterSpacing = (((current.letterSpacing + direction * 0.025) * 1000.0).roundToInt() / 1000.0).coerceIn(
                                0.0, 0.25,
                            ),
                        )
                    }
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            IncrementDecrementControl(
                label = stringResource(R.string.reader_settings_word_spacing),
                value = String.format(locale, "%.1f", settings.wordSpacing),
                onStep = { direction ->
                    onSettingsChange { current ->
                        current.copy(
                            wordSpacing = (((current.wordSpacing + direction * 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0, 1.0,
                            ),
                        )
                    }
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(1f),
            )
            IncrementDecrementControl(
                label = stringResource(R.string.reader_settings_paragraph_spacing),
                value = String.format(locale, "%.1f", settings.paragraphSpacing / 2.0),
                onStep = { direction ->
                    onSettingsChange { current ->
                        current.copy(
                            paragraphSpacing = (((current.paragraphSpacing + direction * 0.2) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0, 2.0,
                            ),
                        )
                    }
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            IncrementDecrementControl(
                label = stringResource(R.string.reader_settings_paragraph_indent),
                value = String.format(locale, "%.1f", settings.paragraphIndent / 2.0),
                onStep = { direction ->
                    onSettingsChange { current ->
                        current.copy(
                            paragraphIndent = (((current.paragraphIndent + direction * 0.2) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0, 4.0,
                            ),
                        )
                    }
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(0.5f),
            )
            Spacer(modifier = Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun LightingTabContent(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    onAddThemeClick: () -> Unit,
    onThemeLongClick: (CustomPineconeTheme) -> Unit,
) {
    ReaderBrightnessControl(settings, onSettingsChange)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.reader_settings_theme),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThemeSwatch(
                isSelected = settings.pineconeThemePreset == "Auto",
                onClick = { onSettingsChange { current -> current.copy(pineconeThemePreset = "Auto") } },
                isAuto = true,
                label = stringResource(R.string.reader_settings_theme_auto),
                modifier = Modifier.padding(start = 0.dp, end = 4.dp),
            )
            ThemeSwatch(
                isSelected = settings.pineconeThemePreset == "Light",
                onClick = { onSettingsChange { current -> current.copy(pineconeThemePreset = "Light") } },
                color = Color.White,
                label = stringResource(R.string.reader_settings_theme_light),
            )
            ThemeSwatch(
                isSelected = settings.pineconeThemePreset == "Dark",
                onClick = { onSettingsChange { current -> current.copy(pineconeThemePreset = "Dark") } },
                color = Color.Black,
                label = stringResource(R.string.reader_settings_theme_dark),
            )
            ThemeSwatch(
                isSelected = settings.pineconeThemePreset == "Warm",
                onClick = { onSettingsChange { current -> current.copy(pineconeThemePreset = "Warm") } },
                color = Color(0xFFFAF4E8),
                label = stringResource(R.string.reader_settings_theme_sepia),
            )

            settings.customThemes.forEach { theme ->
                ThemeSwatch(
                    isSelected = settings.pineconeThemePreset == theme.name,
                    onClick = {
                        onSettingsChange { current ->
                            current.copy(
                                pineconeThemePreset = theme.name,
                                customBackgroundColor = theme.backgroundColor,
                                customTextColor = theme.textColor,
                            )
                        }
                    },
                    onLongClick = { onThemeLongClick(theme) },
                    color = try {
                        Color(theme.backgroundColor.toColorInt())
                    } catch (_: Exception) {
                        Color.White
                    },
                    label = theme.name,
                )
            }

            ThemeSwatch(
                isSelected = false,
                onClick = onAddThemeClick,
                isCustom = true,
                label = stringResource(R.string.action_add),
            )
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.reader_settings_image_filter),
            style = MaterialTheme.typography.titleMedium,
        )
        val imageFilterOptionsMap = mapOf(
            "None" to stringResource(R.string.reader_settings_image_filter_none),
            "Darken" to stringResource(R.string.reader_settings_image_filter_darken),
            "Invert" to stringResource(R.string.reader_settings_image_filter_invert),
        )
        SegmentedButtonGroup(
            options = imageFilterOptionsMap.keys.toList(),
            optionLabel = { imageFilterOptionsMap[it] ?: it },
            icons = listOf(
                MaterialSymbols.Outlined.Image,
                MaterialSymbols.Outlined.Contrast,
                MaterialSymbols.Outlined.Invert_colors,
            ),
            selected = settings.imageFilter,
            onSelected = { value -> onSettingsChange { current -> current.copy(imageFilter = value) } },
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.reader_image_filter_activation),
            style = MaterialTheme.typography.titleMedium
        )
        val modes = linkedMapOf(
            true to stringResource(R.string.reader_follow_theme),
            false to stringResource(R.string.reader_always),
        )
        SegmentedButtonGroup(
            options = modes.keys.toList(),
            optionLabel = modes::getValue,
            icons = emptyList(),
            selected = settings.formatPreferences.epubImageFilterFollowTheme,
            onSelected = { value ->
                onSettingsChange { current ->
                    current.copy(
                        formatPreferences = current.formatPreferences.copy(
                            epubImageFilterFollowTheme = value,
                        )
                    )
                }
            },
        )
    }

}

@Composable
internal fun ReaderBrightnessControl(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        Text(
            stringResource(R.string.reader_settings_brightness),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledIconToggleButton(
                checked = settings.autoBrightness,
                onCheckedChange = { onSettingsChange { current -> current.copy(autoBrightness = it) } },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.size(48.dp),
            ) {
                AnimatedSelectionIcon(
                    selected = settings.autoBrightness,
                    outlinedIcon = MaterialSymbols.Outlined.Brightness_medium,
                    filledIcon = MaterialSymbols.OutlinedFilled.FilledBrightnessAuto,
                    contentDescription = stringResource(R.string.reader_settings_auto_brightness),
                )
            }
            val brightnessSliderState = rememberSliderState(value = settings.brightness)
            // The settings flow can update brightness independently of slider gestures.
            brightnessSliderState.value = settings.brightness
            Slider(
                state = brightnessSliderState,
                onValueChange = { onSettingsChange { current -> current.copy(brightness = it) } },
                enabled = !settings.autoBrightness,
                interactionSource = interactionSource,
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        enabled = !settings.autoBrightness,
                        modifier = Modifier.height(16.dp),
                        trackCornerSize = 8.dp,
                    )
                },
                thumb = { _ ->
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        modifier = Modifier,
                        colors = SliderDefaults.colors(),
                        enabled = !settings.autoBrightness,
                        thumbSize = DpSize(4.dp, 48.dp),
                    )
                },
            )
        }
    }

}

@Composable
internal fun AdvancedTabContent(
    settings: ReaderSettings,
    onSettingsChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    includeTextPreferences: Boolean = true,
    onRestoreDefaultsClick: () -> Unit,
    content: @Composable () -> Unit = {},
    textPreferencesContent: @Composable () -> Unit = {},
) {

    Column {
        content()

        if (includeTextPreferences) {
            ReaderChoiceRow(
                title = stringResource(R.string.reader_settings_dictionary),
                dialogTitle = stringResource(R.string.dictionaries_select),
                valueLabel = settings.installedDictionaries.find { it.id == settings.activeDictionaryId }?.name
                    ?: stringResource(R.string.reader_settings_image_filter_none),
                options = settings.installedDictionaries,
                isSelected = { it.id == settings.activeDictionaryId },
                optionLabel = { it.name },
                emptyText = stringResource(R.string.dictionaries_none_installed),
                onSelected = { onSettingsChange { current -> current.copy(activeDictionaryId = it.id) } },
            )

        }

        val orientationOptionsMap = mapOf(
            "Auto" to stringResource(R.string.reader_settings_orientation_auto),
            "Portrait" to stringResource(R.string.reader_settings_orientation_portrait),
            "Landscape" to stringResource(R.string.reader_settings_orientation_landscape),
        )

        ReaderChoiceRow(
            title = stringResource(R.string.reader_settings_force_orientation),
            valueLabel = orientationOptionsMap[settings.forceOrientation]
                ?: settings.forceOrientation,
            options = orientationOptionsMap.keys.toList(),
            isSelected = { it == settings.forceOrientation },
            optionLabel = orientationOptionsMap::getValue,
            onSelected = { onSettingsChange { current -> current.copy(forceOrientation = it) } },
        )

        val jumpHistoryOptionsMap = mapOf(
            "explicit" to stringResource(R.string.reader_jump_history_mode_explicit),
            "page_turn" to stringResource(R.string.reader_jump_history_mode_page_turn),
            "disabled" to stringResource(R.string.reader_jump_history_mode_disabled),
        )

        ReaderChoiceRow(
            title = stringResource(R.string.reader_jump_history_mode),
            valueLabel = jumpHistoryOptionsMap[settings.jumpHistoryMode]
                ?: settings.jumpHistoryMode,
            options = jumpHistoryOptionsMap.keys.toList(),
            isSelected = { it == settings.jumpHistoryMode },
            optionLabel = jumpHistoryOptionsMap::getValue,
            onSelected = { onSettingsChange { current -> current.copy(jumpHistoryMode = it) } },
        )

        if (includeTextPreferences) {
            SettingsSwitchRow(
                title = stringResource(R.string.reader_settings_vertical_scroll),
                isChecked = settings.scroll,
                onCheckedChange = { onSettingsChange { current -> current.copy(scroll = it) } },
            )

            SettingsSwitchRow(
                title = stringResource(R.string.reader_settings_hyphens),
                isChecked = settings.hyphens,
                onCheckedChange = { onSettingsChange { current -> current.copy(hyphens = it) } },
                enabled = !settings.publisherStyles,
            )

            textPreferencesContent()

            SettingsSwitchRow(
                title = stringResource(R.string.reader_settings_text_normalization),
                isChecked = settings.textNormalization,
                onCheckedChange = { onSettingsChange { current -> current.copy(textNormalization = it) } },
            )

        }

        SettingsSwitchRow(
            title = stringResource(R.string.reader_settings_prevent_timeout),
            isChecked = settings.preventScreenTimeout,
            onCheckedChange = { onSettingsChange { current -> current.copy(preventScreenTimeout = it) } },
        )

        SettingsSwitchRow(
            title = stringResource(R.string.reader_settings_always_status_bar),
            isChecked = settings.alwaysShowStatusBar,
            onCheckedChange = { onSettingsChange { current -> current.copy(alwaysShowStatusBar = it) } },
        )

        ListItem(
            onClick = onRestoreDefaultsClick,
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            trailingContent = {
                Icon(MaterialSymbols.Outlined.Restart_alt, contentDescription = null)
            },
            content = { Text(stringResource(R.string.reader_settings_restore_defaults)) },
        )
    }
}

@Composable
private fun <T : Any> ReaderChoiceRow(
    title: String, valueLabel: String, options: List<T>, isSelected: (T) -> Boolean,
    optionLabel: (T) -> String, onSelected: (T) -> Unit,
    dialogTitle: String = title, emptyText: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    ListItem(
        onClick = { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        trailingContent = { Text(valueLabel, style = MaterialTheme.typography.bodyMedium) },
        content = { Text(title) },
    )
    if (showDialog) SettingsChoiceDialog(
        content = { Text(dialogTitle) }, options = options, isSelected = isSelected,
        optionLabel = optionLabel, onSelected = onSelected, emptyText = emptyText,
        onDismiss = { showDialog = false },
    )
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ListItem(
        modifier = modifier.toggleable(
            value = isChecked, enabled = enabled, role = Role.Switch,
            onValueChange = onCheckedChange,
        ),
        enabled = enabled,
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        trailingContent = {
            Switch(checked = isChecked, onCheckedChange = null, enabled = enabled)
        },
        content = { Text(title) },
    )
}

@SuppressLint("LogNotTimber")
@Composable
private fun FontSwatch(
    isSelected: Boolean,
    onClick: () -> Unit,
    fontName: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    val provider = remember {
        GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs,
        )
    }

    val fontFamily = remember(fontName) {
        try {
            val googleFontName = if (fontName == "Source Code") "Source Code Pro" else fontName

            FontFamily(
                Font(
                    googleFont = GoogleFont(googleFontName), fontProvider = provider,
                ),
            )
        } catch (e: Exception) {
            Log.e("FontSwatch", "Error loading font $fontName", e)
            FontFamily.Default
        }
    }

    SwatchFrame(
        modifier = modifier, isSelected = isSelected, label = label,
        background = MaterialTheme.colorScheme.surfaceVariant,
        interaction = Modifier.clickable(onClick = onClick),
    ) {
        // Literata's internal glyph metrics have extra padding at the top, pushing it down visually.
        // We apply a tiny negative offset just to visually center it in the swatch box.
        val yOffset = if (fontName == "Literata") (-2).dp else 0.dp

        Text(
            text = "Aa",
            fontFamily = fontFamily,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.offset(y = yOffset),
        )
    }
}
