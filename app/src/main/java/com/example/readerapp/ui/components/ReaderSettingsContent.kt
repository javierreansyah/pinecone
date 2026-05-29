package com.example.readerapp.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Brightness_auto
import com.composables.icons.materialsymbols.outlined.Brightness_medium
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
    var showColorPicker by remember { mutableStateOf(false) }
    var themeToDelete by remember { mutableStateOf<CustomReaderTheme?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Brightness Section
        Column {
            Text("Brightness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Spacer(modifier = Modifier.width(16.dp))
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

        // Theme Selection Section
        Column {
            Text("Theme Selection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
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
                    label = "Auto"
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

        // Text Size and Margin
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

        // Font Selection
        Column {
            Text("Font Selection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            SingleSelectDropdown(
                label = "Font Family",
                options = listOf("Original", "Serif", "Sans-Serif", "OpenDyslexic"),
                selected = settings.fontFamily,
                onSelected = { onSettingsChange(settings.copy(fontFamily = it)) }
            )
        }

        // Vertical Scroll Switch
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Vertical Scroll", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = settings.scroll,
                onCheckedChange = { onSettingsChange(settings.copy(scroll = it)) }
            )
        }

        // Typography Section
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Typography", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("Publisher Style", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = settings.publisherStyles,
                    onCheckedChange = { onSettingsChange(settings.copy(publisherStyles = it)) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.alpha(if (settings.publisherStyles) 0.5f else 1.0f)) {
                Text("Alignment", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SegmentedButtonGroup(
                    options = listOf("Start", "Left", "Right", "Justify"),
                    selected = settings.textAlign,
                    onSelected = { onSettingsChange(settings.copy(textAlign = it)) },
                    enabled = !settings.publisherStyles,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
        }

        // Formatting switches (Not disabled by publisher style)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Hyphens", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.hyphens,
                    onCheckedChange = { onSettingsChange(settings.copy(hyphens = it)) }
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Text Normalization", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = settings.textNormalization,
                    onCheckedChange = { onSettingsChange(settings.copy(textNormalization = it)) }
                )
            }
        }

        // Accessibility Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Accessibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            Column {
                Text("Reading Progression", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SegmentedButtonGroup(
                    options = listOf("LTR", "RTL"),
                    selected = settings.readingProgression,
                    onSelected = { onSettingsChange(settings.copy(readingProgression = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column {
                Text("Image Filter", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                SegmentedButtonGroup(
                    options = listOf("None", "Darken", "Invert"),
                    selected = settings.imageFilter,
                    onSelected = { onSettingsChange(settings.copy(imageFilter = it)) },
                    modifier = Modifier.fillMaxWidth()
                )
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
