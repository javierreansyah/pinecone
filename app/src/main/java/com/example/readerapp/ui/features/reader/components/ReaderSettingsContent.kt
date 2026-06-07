package com.example.readerapp.ui.features.reader.components

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.*
import com.example.readerapp.R
import com.example.readerapp.data.local.CustomReaderTheme
import com.example.readerapp.data.local.ReaderSettings
import java.util.Locale
import kotlin.math.roundToInt
import androidx.core.graphics.toColorInt
import com.example.readerapp.ui.components.CustomColorPickerDialog
import com.example.readerapp.ui.components.IncrementDecrementControl
import com.example.readerapp.ui.components.SegmentedButtonGroup
import com.example.readerapp.ui.components.ThemeSwatch
import kotlinx.coroutines.launch

@SuppressLint("DefaultLocale", "ConfigurationScreenWidthHeight")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderSettingsContent(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val screenHeight = configuration.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.6f

    val tabs = listOf("Text", "Lighting", "Advanced")
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    var showColorPicker by remember { mutableStateOf(false) }
    var themeToEdit by remember { mutableStateOf<CustomReaderTheme?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxSheetHeight)
            .padding(bottom = 4.dp)
    ) {
        PrimaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(index) }
                    },
                    text = { Text(title, style = MaterialTheme.typography.titleMedium) }
                )
            }
        }

        // Overriding the local overscroll configuration to null completely eliminates
        // the platform's elastic snap-back/bounce physics when you finish a swipe.
        CompositionLocalProvider(
            LocalOverscrollFactory provides null
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = 2,
                verticalAlignment = Alignment.Top
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (page) {
                        0 -> TextTabContent(settings, onSettingsChange, locale)
                        1 -> LightingTabContent(
                            settings = settings,
                            onSettingsChange = onSettingsChange,
                            onAddThemeClick = { 
                                themeToEdit = null
                                showColorPicker = true 
                            },
                            onThemeLongClick = { 
                                themeToEdit = it
                                showColorPicker = true
                            }
                        )
                        2 -> AdvancedTabContent(settings, onSettingsChange)
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
            onDismiss = { 
                showColorPicker = false 
                themeToEdit = null
            },
            onDelete = if (themeToEdit != null) {
                {
                    val newThemes = settings.customThemes.filter { it.name != themeToEdit?.name }
                    val newPreset = if (settings.readerThemePreset == themeToEdit?.name) "Auto" else settings.readerThemePreset
                    onSettingsChange(settings.copy(customThemes = newThemes, readerThemePreset = newPreset))
                    showColorPicker = false
                    themeToEdit = null
                }
            } else null,
            onConfirm = { name, bgColor, textColor ->
                val newTheme = CustomReaderTheme(name, bgColor, textColor)
                
                val updatedThemes = if (themeToEdit != null) {
                    // Update existing theme, keeping its position
                    settings.customThemes.map { if (it.name == themeToEdit?.name) newTheme else it }
                } else {
                    // Add new theme
                    settings.customThemes + newTheme
                }

                val newPreset = if (settings.readerThemePreset == themeToEdit?.name || themeToEdit == null) name else settings.readerThemePreset

                onSettingsChange(
                    settings.copy(
                        customThemes = updatedThemes,
                        readerThemePreset = newPreset,
                        customBackgroundColor = if (newPreset == name) bgColor else settings.customBackgroundColor,
                        customTextColor = if (newPreset == name) textColor else settings.customTextColor
                    )
                )
                showColorPicker = false
                themeToEdit = null
            }
        )
    }
}

@Composable
private fun TextTabContent(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    locale: Locale
) {
    // Font Selection
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Font", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val fonts = listOf(
                "Source Serif 4" to "Serif",
                "Source Sans 3" to "Sans",
                "Literata" to "Literata",
                "Atkinson Hyperlegible" to "Atkinson",
                "Source Code" to "Monospace"
            )
            fonts.forEach { (font, label) ->
                FontSwatch(
                    isSelected = settings.fontFamily == font,
                    onClick = { onSettingsChange(settings.copy(fontFamily = font)) },
                    fontName = font,
                    label = label,
                )
            }
        }
    }

    // Typography
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Typography", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
        
        // Text Size, Font Weight
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            IncrementDecrementControl(
                label = "Text Size",
                value = "${(settings.fontSize * 100).roundToInt()}%",
                onIncrement = {
                    onSettingsChange(
                        settings.copy(
                            fontSize = (((settings.fontSize + 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.5,
                                3.0
                            )
                        )
                    )
                },
                onDecrement = {
                    onSettingsChange(
                        settings.copy(
                            fontSize = (((settings.fontSize - 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.5,
                                3.0
                            )
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
            val currentFontWeight = settings.fontWeights[settings.fontFamily] ?: 1.0
            IncrementDecrementControl(
                label = "Font Weight",
                value = String.format(locale, "%.2f", currentFontWeight),
                onIncrement = {
                    val newWeight =
                        (((currentFontWeight + 0.25) * 100.0).roundToInt() / 100.0).coerceIn(
                            0.5,
                            2.25
                        )
                    onSettingsChange(settings.copy(fontWeights = settings.fontWeights + (settings.fontFamily to newWeight)))
                },
                onDecrement = {
                    val newWeight =
                        (((currentFontWeight - 0.25) * 100.0).roundToInt() / 100.0).coerceIn(
                            0.5,
                            2.25
                        )
                    onSettingsChange(settings.copy(fontWeights = settings.fontWeights + (settings.fontFamily to newWeight)))
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Horizontal Margin, Vertical Margin
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            IncrementDecrementControl(
                label = "Horizontal Margin",
                value = String.format(locale, "%.1f", settings.pageMargins),
                onIncrement = {
                    onSettingsChange(
                        settings.copy(
                            pageMargins = (((settings.pageMargins + 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                3.0
                            )
                        )
                    )
                },
                onDecrement = {
                    onSettingsChange(
                        settings.copy(
                            pageMargins = (((settings.pageMargins - 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                3.0
                            )
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
            IncrementDecrementControl(
                label = "Vertical Margin",
                value = String.format(locale, "%.1f", settings.verticalMargin / 32.0),
                onIncrement = {
                    onSettingsChange(
                        settings.copy(
                            verticalMargin = (((settings.verticalMargin + 3.2) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                128.0
                            )
                        )
                    )
                },
                onDecrement = {
                    onSettingsChange(
                        settings.copy(
                            verticalMargin = (((settings.verticalMargin - 3.2) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                128.0
                            )
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Publisher Style
        SettingsSwitchRow(
            title = "Publisher Style",
            isChecked = settings.publisherStyles,
            onCheckedChange = { onSettingsChange(settings.copy(publisherStyles = it)) }
        )

        // Alignment
        SegmentedButtonGroup(
            title = "Alignment",
            options = listOf("Left", "Right", "Justify"),
            icons = listOf(
                MaterialSymbols.Outlined.Format_align_left,
                MaterialSymbols.Outlined.Format_align_right,
                MaterialSymbols.Outlined.Format_align_justify
            ),
            selected = settings.textAlign,
            onSelected = { onSettingsChange(settings.copy(textAlign = it)) },
            enabled = !settings.publisherStyles,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Line Spacing, Letter Spacing
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            IncrementDecrementControl(
                label = "Line Spacing",
                value = String.format(locale, "%.1f", settings.lineHeight),
                onIncrement = {
                    onSettingsChange(
                        settings.copy(
                            lineHeight = (((settings.lineHeight + 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                1.0,
                                2.5
                            )
                        )
                    )
                },
                onDecrement = {
                    onSettingsChange(
                        settings.copy(
                            lineHeight = (((settings.lineHeight - 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                1.0,
                                2.5
                            )
                        )
                    )
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(1f)
            )
            IncrementDecrementControl(
                label = "Letter Spacing",
                value = String.format(locale, "%.1f", settings.letterSpacing * 4.0),
                onIncrement = {
                    onSettingsChange(
                        settings.copy(
                            letterSpacing = (((settings.letterSpacing + 0.025) * 1000.0).roundToInt() / 1000.0).coerceIn(
                                0.0,
                                0.25
                            )
                        )
                    )
                },
                onDecrement = {
                    onSettingsChange(
                        settings.copy(
                            letterSpacing = (((settings.letterSpacing - 0.025) * 1000.0).roundToInt() / 1000.0).coerceIn(
                                0.0,
                                0.25
                            )
                        )
                    )
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(1f)
            )
        }

        // Word Spacing, Paragraph Spacing
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            IncrementDecrementControl(
                label = "Word Spacing",
                value = String.format(locale, "%.1f", settings.wordSpacing),
                onIncrement = {
                    onSettingsChange(
                        settings.copy(
                            wordSpacing = (((settings.wordSpacing + 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                1.0
                            )
                        )
                    )
                },
                onDecrement = {
                    onSettingsChange(
                        settings.copy(
                            wordSpacing = (((settings.wordSpacing - 0.1) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                1.0
                            )
                        )
                    )
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(1f)
            )
            IncrementDecrementControl(
                label = "Paragraph Spacing",
                value = String.format(locale, "%.1f", settings.paragraphSpacing / 2.0),
                onIncrement = {
                    onSettingsChange(
                        settings.copy(
                            paragraphSpacing = (((settings.paragraphSpacing + 0.2) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                2.0
                            )
                        )
                    )
                },
                onDecrement = {
                    onSettingsChange(
                        settings.copy(
                            paragraphSpacing = (((settings.paragraphSpacing - 0.2) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                2.0
                            )
                        )
                    )
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(1f)
            )
        }

        // Paragraph Indent
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
            IncrementDecrementControl(
                label = "Paragraph Indent",
                value = String.format(locale, "%.1f", settings.paragraphIndent / 2.0),
                onIncrement = {
                    onSettingsChange(
                        settings.copy(
                            paragraphIndent = (((settings.paragraphIndent + 0.2) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                4.0
                            )
                        )
                    )
                },
                onDecrement = {
                    onSettingsChange(
                        settings.copy(
                            paragraphIndent = (((settings.paragraphIndent - 0.2) * 10.0).roundToInt() / 10.0).coerceIn(
                                0.0,
                                4.0
                            )
                        )
                    )
                },
                enabled = !settings.publisherStyles,
                modifier = Modifier.weight(0.5f)
            )
            Spacer(modifier = Modifier.weight(0.5f))
        }
    }
}

@Composable
private fun LightingTabContent(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit,
    onAddThemeClick: () -> Unit,
    onThemeLongClick: (CustomReaderTheme) -> Unit
) {
    // Brightness
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Brightness", style = MaterialTheme.typography.titleMedium)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilledIconToggleButton(
                checked = settings.autoBrightness,
                onCheckedChange = { onSettingsChange(settings.copy(autoBrightness = it)) },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = if (settings.autoBrightness) MaterialSymbols.Outlined.Brightness_auto else MaterialSymbols.Outlined.Brightness_medium,
                    contentDescription = "Auto Brightness"
                )
            }
            Slider(
                value = settings.brightness,
                onValueChange = { onSettingsChange(settings.copy(brightness = it)) },
                enabled = !settings.autoBrightness,
                modifier = Modifier.weight(1f),
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        enabled = !settings.autoBrightness,
                    )
                }
            )
        }
    }

    // Theme Selection
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ThemeSwatch(
                isSelected = settings.readerThemePreset == "Auto",
                onClick = { onSettingsChange(settings.copy(readerThemePreset = "Auto")) },
                isAuto = true,
                label = "Auto",
                modifier = Modifier.padding(start = 0.dp, end = 4.dp)
            )
            ThemeSwatch(
                isSelected = settings.readerThemePreset == "Light",
                onClick = { onSettingsChange(settings.copy(readerThemePreset = "Light")) },
                color = Color.White,
                label = "Light"
            )
            ThemeSwatch(
                isSelected = settings.readerThemePreset == "Dark",
                onClick = { onSettingsChange(settings.copy(readerThemePreset = "Dark")) },
                color = Color.Black,
                label = "Dark"
            )
            ThemeSwatch(
                isSelected = settings.readerThemePreset == "Warm",
                onClick = { onSettingsChange(settings.copy(readerThemePreset = "Warm")) },
                color = Color(0xFFFAF4E8),
                label = "Sepia"
            )

            // Custom Saved Themes
            settings.customThemes.forEach { theme ->
                ThemeSwatch(
                    isSelected = settings.readerThemePreset == theme.name,
                    onClick = {
                        onSettingsChange(
                            settings.copy(
                                readerThemePreset = theme.name,
                                customBackgroundColor = theme.backgroundColor,
                                customTextColor = theme.textColor
                            )
                        )
                    },
                    onLongClick = { onThemeLongClick(theme) },
                    color = try {
                        Color(theme.backgroundColor.toColorInt())
                    } catch (_: Exception) {
                        Color.White
                    },
                    label = theme.name
                )
            }

            // Add New Theme Swatch
            ThemeSwatch(
                isSelected = false,
                onClick = onAddThemeClick,
                isCustom = true,
                label = "Add"
            )
        }
    }

    // Image Filter
    SegmentedButtonGroup(
        title = "Image Filter",
        options = listOf("None", "Darken", "Invert"),
        icons = listOf(
            MaterialSymbols.Outlined.Image,
            MaterialSymbols.Outlined.Contrast,
            MaterialSymbols.Outlined.Invert_colors
        ),
        selected = settings.imageFilter,
        onSelected = { onSettingsChange(settings.copy(imageFilter = it)) },
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun AdvancedTabContent(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    var showDictionaryDialog by remember { mutableStateOf(false) }

    Column {
        // Active Dictionary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDictionaryDialog = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dictionary", style = MaterialTheme.typography.titleMedium)
            val activeName = settings.installedDictionaries.find { it.id == settings.activeDictionaryId }?.name ?: "None"
            Text(activeName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (showDictionaryDialog) {
            AlertDialog(
                onDismissRequest = { showDictionaryDialog = false },
                title = { Text("Select Dictionary") },
                text = {
                    Column {
                        if (settings.installedDictionaries.isEmpty()) {
                            Text("No dictionaries installed. Install one from the navigation drawer.")
                        } else {
                            settings.installedDictionaries.forEach { dict ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSettingsChange(settings.copy(activeDictionaryId = dict.id))
                                            showDictionaryDialog = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = settings.activeDictionaryId == dict.id,
                                        onClick = {
                                            onSettingsChange(settings.copy(activeDictionaryId = dict.id))
                                            showDictionaryDialog = false
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(dict.name)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDictionaryDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Vertical Scroll
        SettingsSwitchRow(
            title = "Vertical Scroll",
            isChecked = settings.scroll,
            onCheckedChange = { onSettingsChange(settings.copy(scroll = it)) }
        )

        // Hyphens
        SettingsSwitchRow(
            title = "Hyphens",
            isChecked = settings.hyphens,
            onCheckedChange = { onSettingsChange(settings.copy(hyphens = it)) },
            enabled = !settings.publisherStyles
        )

        // Text Normalization
        SettingsSwitchRow(
            title = "Text Normalization",
            isChecked = settings.textNormalization,
            onCheckedChange = { onSettingsChange(settings.copy(textNormalization = it)) }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Force Orientation
        SegmentedButtonGroup(
            title = "Force Orientation",
            options = listOf("Auto", "Portrait", "Landscape"),
            icons = emptyList(),
            selected = settings.forceOrientation,
            onSelected = { onSettingsChange(settings.copy(forceOrientation = it)) },
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Prevent Screen Timeout
        SettingsSwitchRow(
            title = "Prevent Screen Timeout",
            isChecked = settings.preventScreenTimeout,
            onCheckedChange = { onSettingsChange(settings.copy(preventScreenTimeout = it)) }
        )

        // Always Shows Status Bar
        SettingsSwitchRow(
            title = "Always Show Status Bar",
            isChecked = settings.alwaysShowStatusBar,
            onCheckedChange = { onSettingsChange(settings.copy(alwaysShowStatusBar = it)) }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!isChecked) }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}


@Composable
private fun FontSwatch(
    isSelected: Boolean,
    onClick: () -> Unit,
    fontName: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val provider = remember {
        GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = R.array.com_google_android_gms_fonts_certs
        )
    }

    val fontFamily = remember(fontName) {
        try {
            val googleFontName = if (fontName == "Source Code") "Source Code Pro" else fontName
            
            FontFamily(
                Font(
                    googleFont = GoogleFont(googleFontName),
                    fontProvider = provider
                )
            )
        } catch (e: Exception) {
            Log.e("FontSwatch", "Error loading font $fontName", e)
            FontFamily.Default
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .padding(2.dp)
                .let { m ->
                    if (isSelected) {
                        m.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape
                        )
                    } else m
                }
                .padding(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = CircleShape
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // Literata's internal glyph metrics have extra padding at the top, pushing it down visually.
            // We apply a tiny negative offset just to visually center it in the swatch box.
            val yOffset = if (fontName == "Literata") (-2).dp else 0.dp
            
            Text(
                text = "Aa",
                fontFamily = fontFamily,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.offset(y = yOffset)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(68.dp),
            textAlign = TextAlign.Center
        )
    }
}
