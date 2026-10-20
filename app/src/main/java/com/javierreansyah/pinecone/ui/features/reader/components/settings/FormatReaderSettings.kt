package com.javierreansyah.pinecone.ui.features.reader.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.ui.components.SegmentedButtonGroup

/** Same tabbed sheet and expressive grouped surfaces as the reflowable EPUB reader. */
@Composable
fun FormatReaderSettings(
    settings: ReaderSettings,
    onChange: (ReaderSettings) -> Unit,
    profile: ReaderProfile,
    fixedEpub: Boolean,
) {
    var selected by remember(profile, fixedEpub) { mutableIntStateOf(0) }
    val titles = listOf(
        if (profile == ReaderProfile.AUDIO) stringResource(R.string.reader_playback) else stringResource(
            R.string.reader_pages
        ),
        stringResource(R.string.reader_settings_tab_lighting),
        stringResource(R.string.reader_settings_tab_advanced)
    )
    Column(Modifier
        .fillMaxWidth()
        .heightIn(max = 560.dp)) {
        PrimaryTabRow(selectedTabIndex = selected) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected == index,
                    onClick = { selected = index },
                    text = { Text(title) })
            }
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (selected) {
                0 -> when {
                    profile == ReaderProfile.PDF -> {
                        val pdf = settings.formatPreferences.pdf
                        fun update(value: com.javierreansyah.pinecone.reader.pdf.MuPdfPreferences) =
                            onChange(
                                settings.copy(
                                    formatPreferences = settings.formatPreferences.copy(pdf = value)
                                )
                            )
                        SettingsGroup {
                            PreferenceSwitch(
                                stringResource(R.string.reader_continuous_scroll),
                                pdf.scroll
                            ) { update(pdf.copy(scroll = it)) }
                            PreferenceSwitch(
                                stringResource(R.string.reader_horizontal),
                                pdf.horizontal
                            ) { update(pdf.copy(horizontal = it)) }
                            PreferenceSwitch(
                                stringResource(R.string.reader_right_to_left),
                                pdf.rightToLeft
                            ) { update(pdf.copy(rightToLeft = it)) }
                            if (!pdf.scroll || pdf.horizontal) PreferenceSwitch(
                                stringResource(R.string.reader_fit_width),
                                pdf.fitWidth
                            ) { update(pdf.copy(fitWidth = it)) }
                            PreferenceSwitch(
                                stringResource(R.string.reader_invert_pages),
                                pdf.invert
                            ) { update(pdf.copy(invert = it)) }
                            PreferenceSlider(
                                stringResource(R.string.reader_page_spacing),
                                pdf.pageSpacing.toFloat(),
                                0f..64f
                            ) { update(pdf.copy(pageSpacing = it.toDouble())) }
                        }
                        Text(
                            stringResource(R.string.reader_pdf_zoom_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    profile == ReaderProfile.AUDIO -> SettingsGroup {
                        PreferenceSlider(
                            stringResource(R.string.reader_playback_speed),
                            settings.formatPreferences.audioSpeed.toFloat(),
                            .25f..3f
                        ) {
                            onChange(
                                settings.copy(
                                    formatPreferences = settings.formatPreferences.copy(
                                        audioSpeed = it.toDouble()
                                    )
                                )
                            )
                        }
                        PreferenceSlider(
                            stringResource(R.string.reader_playback_pitch),
                            settings.formatPreferences.audioPitch.toFloat(),
                            .5f..2f
                        ) {
                            onChange(
                                settings.copy(
                                    formatPreferences = settings.formatPreferences.copy(
                                        audioPitch = it.toDouble()
                                    )
                                )
                            )
                        }
                    }

                    fixedEpub -> SettingsGroup {
                        Text(
                            stringResource(R.string.reader_page_spread),
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                        val values = linkedMapOf(
                            stringResource(R.string.reader_settings_theme_auto) to "auto",
                            stringResource(R.string.reader_single_page) to "never",
                            stringResource(R.string.reader_two_pages) to "always"
                        )
                        SegmentedButtonGroup(
                            options = values.keys.toList(),
                            icons = emptyList(),
                            selected = values.entries.firstOrNull { it.value == settings.formatPreferences.epubSpread }?.key
                                ?: values.keys.first(),
                            onSelected = {
                                onChange(
                                    settings.copy(
                                        formatPreferences = settings.formatPreferences.copy(
                                            epubSpread = values.getValue(it)
                                        )
                                    )
                                )
                            },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        PreferenceSwitch(
                            stringResource(R.string.reader_right_to_left),
                            settings.readingProgression == "RTL"
                        ) {
                            onChange(settings.copy(readingProgression = if (it) "RTL" else "LTR"))
                        }
                    }

                    else -> Text(
                        stringResource(R.string.reader_image_settings_hint),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                1 -> {
                    SettingsGroup {
                        PreferenceSwitch(
                            stringResource(R.string.reader_auto_brightness),
                            settings.autoBrightness
                        ) { onChange(settings.copy(autoBrightness = it)) }
                        if (!settings.autoBrightness) PreferenceSlider(
                            stringResource(R.string.reader_brightness),
                            settings.brightness,
                            .01f..1f
                        ) { onChange(settings.copy(brightness = it)) }
                        Text(
                            stringResource(R.string.reader_chrome_theme_hint),
                            Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                        val themes = listOf(
                            "Auto" to R.string.reader_settings_theme_auto,
                            "Light" to R.string.reader_settings_theme_light,
                            "Warm" to R.string.reader_theme_warm,
                            "Dark" to R.string.reader_settings_theme_dark
                        )
                        themes.forEach { (theme, label) ->
                            TextButton(
                                onClick = { onChange(settings.copy(pineconeThemePreset = theme)) },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(stringResource(label)) }
                        }
                    }
                }

                2 -> SettingsGroup {
                    PreferenceSwitch(
                        stringResource(R.string.reader_keep_screen_on),
                        settings.preventScreenTimeout
                    ) { onChange(settings.copy(preventScreenTimeout = it)) }
                    PreferenceSwitch(
                        stringResource(R.string.reader_show_system_bars),
                        settings.alwaysShowStatusBar
                    ) { onChange(settings.copy(alwaysShowStatusBar = it)) }
                    Text(
                        stringResource(R.string.reader_orientation),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val orientations = linkedMapOf(
                        stringResource(R.string.reader_settings_orientation_auto) to "Auto",
                        stringResource(R.string.reader_settings_orientation_portrait) to "Portrait",
                        stringResource(R.string.reader_settings_orientation_landscape) to "Landscape"
                    )
                    SegmentedButtonGroup(
                        options = orientations.keys.toList(),
                        icons = emptyList(),
                        selected = orientations.entries.firstOrNull { it.value == settings.forceOrientation }?.key
                            ?: orientations.keys.first(),
                        onSelected = {
                            onChange(
                                settings.copy(
                                    forceOrientation = orientations.getValue(
                                        it
                                    )
                                )
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    TextButton(onClick = {
                        val format = when {
                            profile == ReaderProfile.PDF -> settings.formatPreferences.copy(pdf = com.javierreansyah.pinecone.reader.pdf.MuPdfPreferences())
                            profile == ReaderProfile.AUDIO -> settings.formatPreferences.copy(
                                audioSpeed = 1.0,
                                audioPitch = 1.0
                            )

                            fixedEpub -> settings.formatPreferences.copy(epubSpread = "auto")
                            else -> settings.formatPreferences
                        }
                        onChange(
                            settings.copy(
                                formatPreferences = format,
                                brightness = .5f,
                                autoBrightness = true,
                                forceOrientation = "Auto",
                                preventScreenTimeout = false,
                                alwaysShowStatusBar = false,
                                pineconeThemePreset = "Auto"
                            )
                        )
                    }) {
                        Text(stringResource(R.string.reader_settings_restore_defaults))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsGroup(content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) { content() }
    }
}

@Composable
internal fun PreferenceSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(trailingContent = {
        Switch(
            checked = value,
            onCheckedChange = onChange
        )
    }) { Text(label) }
}

@Composable
private fun PreferenceSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            stringResource(
                R.string.reader_setting_value,
                label,
                String.format(LocalLocale.current.platformLocale, "%.2f", value)
            ), style = MaterialTheme.typography.titleSmall
        )
        val slider = rememberSliderState(trackRange = range)
        slider.value = value.coerceIn(range)
        Slider(state = slider, onValueChange = onChange)
    }
}
