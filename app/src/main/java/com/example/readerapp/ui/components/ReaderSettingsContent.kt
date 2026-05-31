package com.example.readerapp.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Brightness_auto
import com.composables.icons.materialsymbols.outlined.Brightness_medium
import com.composables.icons.materialsymbols.outlined.Format_align_left
import com.composables.icons.materialsymbols.outlined.Format_align_right
import com.composables.icons.materialsymbols.outlined.Format_align_justify
import com.composables.icons.materialsymbols.outlined.Format_textdirection_l_to_r
import com.composables.icons.materialsymbols.outlined.Format_textdirection_r_to_l
import com.composables.icons.materialsymbols.outlined.Image
import com.composables.icons.materialsymbols.outlined.Contrast
import com.composables.icons.materialsymbols.outlined.Invert_colors
import com.example.readerapp.data.local.CustomReaderTheme
import com.example.readerapp.data.local.ReaderSettings
import kotlin.math.roundToInt
import androidx.core.graphics.toColorInt

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReaderSettingsContent(
    settings: ReaderSettings,
    onSettingsChange: (ReaderSettings) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Text", "Lighting", "Advanced")

    var showColorPicker by remember { mutableStateOf(false) }
    var themeToDelete by remember { mutableStateOf<CustomReaderTheme?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selectedTab) {
                0 -> { // Text Tab
                    // Font Selection
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Font", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val fonts = listOf("Original", "Serif", "Sans-Serif", "OpenDyslexic")
                            fonts.forEach { font ->
                                FontSwatch(
                                    isSelected = settings.fontFamily == font,
                                    onClick = { onSettingsChange(settings.copy(fontFamily = font)) },
                                    fontName = font,
                                    label = font,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    // Typography
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Typography", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IncrementDecrementControl(
                                label = "Text Size",
                                value = "${(settings.fontSize * 100).roundToInt()}%",
                                onIncrement = { onSettingsChange(settings.copy(fontSize = (((settings.fontSize + 0.1) * 10.0).roundToInt() / 10.0).coerceIn(0.5, 3.0))) },
                                onDecrement = { onSettingsChange(settings.copy(fontSize = (((settings.fontSize - 0.1) * 10.0).roundToInt() / 10.0).coerceIn(0.5, 3.0))) },
                                modifier = Modifier.weight(1f)
                            )
                            IncrementDecrementControl(
                                label = "Margin",
                                value = String.format("%.2f", settings.pageMargins),
                                onIncrement = { onSettingsChange(settings.copy(pageMargins = (((settings.pageMargins + 0.25) * 4.0).roundToInt() / 4.0).coerceIn(0.0, 3.0))) },
                                onDecrement = { onSettingsChange(settings.copy(pageMargins = (((settings.pageMargins - 0.25) * 4.0).roundToInt() / 4.0).coerceIn(0.0, 3.0))) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IncrementDecrementControl(
                                label = "Line Spacing",
                                value = String.format("%.1f", settings.lineHeight),
                                onIncrement = { onSettingsChange(settings.copy(lineHeight = (((settings.lineHeight + 0.1) * 10.0).roundToInt() / 10.0).coerceIn(1.0, 2.5))) },
                                onDecrement = { onSettingsChange(settings.copy(lineHeight = (((settings.lineHeight - 0.1) * 10.0).roundToInt() / 10.0).coerceIn(1.0, 2.5))) },
                                enabled = !settings.publisherStyles,
                                modifier = Modifier.weight(1f)
                            )
                            IncrementDecrementControl(
                                label = "Paragraph Spacing",
                                value = "${(settings.paragraphSpacing * 100).roundToInt()}%",
                                onIncrement = { onSettingsChange(settings.copy(paragraphSpacing = (((settings.paragraphSpacing + 0.1) * 10.0).roundToInt() / 10.0).coerceIn(0.0, 2.0))) },
                                onDecrement = { onSettingsChange(settings.copy(paragraphSpacing = (((settings.paragraphSpacing - 0.1) * 10.0).roundToInt() / 10.0).coerceIn(0.0, 2.0))) },
                                enabled = !settings.publisherStyles,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IncrementDecrementControl(
                                label = "Word Spacing",
                                value = "${(settings.wordSpacing * 100).roundToInt()}%",
                                onIncrement = { onSettingsChange(settings.copy(wordSpacing = (((settings.wordSpacing + 0.1) * 10.0).roundToInt() / 10.0).coerceIn(0.0, 1.0))) },
                                onDecrement = { onSettingsChange(settings.copy(wordSpacing = (((settings.wordSpacing - 0.1) * 10.0).roundToInt() / 10.0).coerceIn(0.0, 1.0))) },
                                enabled = !settings.publisherStyles,
                                modifier = Modifier.weight(1f)
                            )
                            IncrementDecrementControl(
                                label = "Letter Spacing",
                                value = "${(settings.letterSpacing * 100).roundToInt()}%",
                                onIncrement = { onSettingsChange(settings.copy(letterSpacing = (((settings.letterSpacing + 0.1) * 10.0).roundToInt() / 10.0).coerceIn(0.0, 0.5))) },
                                onDecrement = { onSettingsChange(settings.copy(letterSpacing = (((settings.letterSpacing - 0.1) * 10.0).roundToInt() / 10.0).coerceIn(0.0, 0.5))) },
                                enabled = !settings.publisherStyles,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

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
                        enabled = !settings.publisherStyles
                    )

                    // Publisher Style
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Publisher Style", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.publisherStyles,
                            onCheckedChange = { onSettingsChange(settings.copy(publisherStyles = it)) }
                        )
                    }
                }

                1 -> { // Lighting Tab
                    // Brightness
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Brightness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
                                        onSettingsChange(settings.copy(
                                            readerThemePreset = theme.name,
                                            customBackgroundColor = theme.backgroundColor,
                                            customTextColor = theme.textColor
                                        ))
                                    },
                                    onLongClick = { themeToDelete = theme },
                                    color = try { Color(theme.backgroundColor.toColorInt()) } catch (_: Exception) { Color.White },
                                    label = theme.name
                                )
                            }

                            // Add New Theme Swatch
                            ThemeSwatch(
                                isSelected = false,
                                onClick = { showColorPicker = true },
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
                        onSelected = { onSettingsChange(settings.copy(imageFilter = it)) }
                    )
                }

                2 -> { // Advanced Tab
                    // Vertical Scroll
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vertical Scroll", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.scroll,
                            onCheckedChange = { onSettingsChange(settings.copy(scroll = it)) }
                        )
                    }

                    // Hyphens
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Hyphens", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.hyphens,
                            onCheckedChange = { onSettingsChange(settings.copy(hyphens = it)) }
                        )
                    }

                    // Text Normalization
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Text Normalization", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = settings.textNormalization,
                            onCheckedChange = { onSettingsChange(settings.copy(textNormalization = it)) }
                        )
                    }

                    // Reading Progression
                    SegmentedButtonGroup(
                        title = "Reading Progression",
                        options = listOf("LTR", "RTL"),
                        icons = listOf(
                            MaterialSymbols.Outlined.Format_textdirection_l_to_r,
                            MaterialSymbols.Outlined.Format_textdirection_r_to_l
                        ),
                        selected = settings.readingProgression,
                        onSelected = { onSettingsChange(settings.copy(readingProgression = it)) }
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        CustomColorPickerDialog(
            onDismiss = { showColorPicker = false },
            onConfirm = { name, bgColor, textColor ->
                val newTheme = CustomReaderTheme(name, bgColor, textColor)
                onSettingsChange(settings.copy(
                    customThemes = settings.customThemes + newTheme,
                    readerThemePreset = name,
                    customBackgroundColor = bgColor,
                    customTextColor = textColor
                ))
                showColorPicker = false
            }
        )
    }

    if (themeToDelete != null) {
        AlertDialog(
            onDismissRequest = { themeToDelete = null },
            title = { Text("Delete Theme") },
            text = { Text("Are you sure you want to delete '${themeToDelete?.name}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newThemes = settings.customThemes.filter { it.name != themeToDelete?.name }
                        val newPreset = if (settings.readerThemePreset == themeToDelete?.name) "Auto" else settings.readerThemePreset
                        onSettingsChange(settings.copy(customThemes = newThemes, readerThemePreset = newPreset))
                        themeToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { themeToDelete = null }) { Text("Cancel") }
            }
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
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
            Text(
                text = "Aa",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = when (fontName) {
                        "Serif" -> FontFamily.Serif
                        "Sans-Serif" -> FontFamily.SansSerif
                        else -> FontFamily.Default
                    }
                )
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.Center
        )
    }
}
