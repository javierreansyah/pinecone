package com.javierreansyah.pinecone.ui.features.reader.components.settings

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Restart_alt
import com.javierreansyah.pinecone.R
import com.javierreansyah.pinecone.data.local.preferences.ReaderSettings
import com.javierreansyah.pinecone.data.publication.ReaderProfile
import com.javierreansyah.pinecone.ui.components.SegmentedButtonGroup
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Image/comic settings share one scrolling panel; other formats retain their settings pages. */
@Composable
fun FormatReaderSettings(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    profile: ReaderProfile,
    fixedEpub: Boolean,
    useBookSettings: Boolean,
    onUseBookSettings: (Boolean) -> Unit,
    onReparsePdf: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val sheetHeight = with(density) { (windowInfo.containerSize.height * 0.6f).toDp() }
    if (profile == ReaderProfile.IMAGE) {
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(sheetHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                FormatLighting(settings, onChange, showChromeHint = true)
                FormatAdvancedSettings(
                    settings, onChange, profile, fixedEpub,
                    useBookSettings, onUseBookSettings, onReparsePdf,
                )
            }
        }
        return
    }

    val isPdf = profile == ReaderProfile.PDF
    val isAudio = profile == ReaderProfile.AUDIO
    val titles = if (isAudio) {
        listOf(
            stringResource(R.string.reader_playback),
        )
    } else if (isPdf) {
        listOf(
            stringResource(R.string.reader_pages),
            stringResource(R.string.reader_settings_tab_advanced),
        )
    } else {
        listOf(
            stringResource(R.string.reader_pages),
            stringResource(R.string.reader_settings_tab_lighting),
            stringResource(R.string.reader_settings_tab_advanced),
        )
    }
    val pagerState = rememberPagerState { titles.size }
    val scope = rememberCoroutineScope()
    Column(
        Modifier
            .fillMaxWidth()
            .height(sheetHeight)
            .padding(bottom = 4.dp)
    ) {
        if (titles.size > 1) {
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent
            ) {
                titles.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(title, style = MaterialTheme.typography.titleMedium) })
                }
            }
        }
        CompositionLocalProvider(LocalOverscrollFactory provides null) {
            HorizontalPager(
                state = pagerState, modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                beyondViewportPageCount = titles.lastIndex, verticalAlignment = Alignment.Top
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = if (isAudio) 0.dp else 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val advancedPage = if (isAudio) 0 else if (isPdf) 1 else 2
                    when {
                        page == 0 -> when {
                            profile == ReaderProfile.PDF -> {
                                val pdf = settings.formatPreferences.pdf
                                fun update(transform: (com.javierreansyah.pinecone.data.local.preferences.PdfPreferences) -> com.javierreansyah.pinecone.data.local.preferences.PdfPreferences) =
                                    onChange { current ->
                                        current.copy(
                                            formatPreferences = current.formatPreferences.copy(
                                                pdf = transform(
                                                    current.formatPreferences.pdf
                                                )
                                            )
                                        )
                                    }
                                SettingsGroup(verticalArrangement = Arrangement.Top) {
                                    PreferenceSwitch(
                                        stringResource(R.string.reader_continuous_scroll),
                                        pdf.scroll
                                    ) { update { latest -> latest.copy(scroll = it) } }
                                    PreferenceSwitch(
                                        stringResource(R.string.reader_horizontal),
                                        pdf.horizontal
                                    ) { update { latest -> latest.copy(horizontal = it) } }
                                    PreferenceSwitch(
                                        stringResource(R.string.reader_right_to_left),
                                        pdf.rightToLeft
                                    ) { update { latest -> latest.copy(rightToLeft = it) } }
                                    if (!pdf.scroll || pdf.horizontal) PreferenceSwitch(
                                        stringResource(R.string.reader_fit_width),
                                        pdf.fitWidth
                                    ) { update { latest -> latest.copy(fitWidth = it) } }
                                    IncrementDecrementControl(
                                        label = stringResource(R.string.reader_page_spacing),
                                        inline = true,
                                        value = pdf.pageSpacing.roundToInt().toString(),
                                        onIncrement = {
                                            update { latest ->
                                                latest.copy(
                                                    pageSpacing = (latest.pageSpacing + 1).coerceAtMost(
                                                        64.0
                                                    )
                                                )
                                            }
                                        },
                                        onDecrement = {
                                            update { latest ->
                                                latest.copy(
                                                    pageSpacing = (latest.pageSpacing - 1).coerceAtLeast(
                                                        0.0
                                                    )
                                                )
                                            }
                                        },
                                        modifier = Modifier.padding(
                                            horizontal = 16.dp,
                                            vertical = 4.dp
                                        ),
                                    )
                                }

                                FormatLighting(settings, onChange, showChromeHint = false)
                                PdfInversionControl(settings, onChange)
                            }

                            profile == ReaderProfile.AUDIO -> SettingsGroup {
                                PreferenceSlider(
                                    stringResource(R.string.reader_playback_speed),
                                    settings.formatPreferences.audioSpeed.toFloat(),
                                    .3f..3f,
                                    presets = listOf(.5f, 1f, 1.5f, 2f),
                                    topPadding = 0.dp,
                                ) {
                                    onChange { current ->
                                        current.copy(
                                            formatPreferences = current.formatPreferences.copy(
                                                audioSpeed = it.toDouble()
                                            )
                                        )
                                    }
                                }
                                PreferenceSlider(
                                    stringResource(R.string.reader_playback_pitch),
                                    settings.formatPreferences.audioPitch.toFloat(),
                                    .5f..2f
                                ) {
                                    onChange { current ->
                                        current.copy(
                                            formatPreferences = current.formatPreferences.copy(
                                                audioPitch = it.toDouble()
                                            )
                                        )
                                    }
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
                                        onChange { current ->
                                            current.copy(
                                                formatPreferences = current.formatPreferences.copy(
                                                    epubSpread = values.getValue(it)
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                PreferenceSwitch(
                                    stringResource(R.string.reader_right_to_left),
                                    settings.readingProgression == "RTL"
                                ) {
                                    onChange { current -> current.copy(readingProgression = if (it) "RTL" else "LTR") }
                                }
                            }

                            else -> Text(
                                stringResource(R.string.reader_image_settings_hint),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }

                        !isAudio && !isPdf && page == 1 -> {
                            FormatLighting(settings, onChange, showChromeHint = true)
                        }
                    }

                    if (page == advancedPage) {
                        FormatAdvancedSettings(
                            settings, onChange, profile, fixedEpub,
                            useBookSettings, onUseBookSettings, onReparsePdf,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FormatAdvancedSettings(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    profile: ReaderProfile,
    fixedEpub: Boolean,
    useBookSettings: Boolean,
    onUseBookSettings: (Boolean) -> Unit,
    onReparsePdf: (() -> Unit)?,
) {
    val isPdf = profile == ReaderProfile.PDF
    AdvancedTabContent(
        settings, onChange, includeTextPreferences = false,
        onRestoreDefaultsClick = {
            onChange { current ->
                val format = when {
                    profile == ReaderProfile.PDF -> current.formatPreferences.copy(
                        pdf = com.javierreansyah.pinecone.data.local.preferences.PdfPreferences()
                    )

                    profile == ReaderProfile.AUDIO -> current.formatPreferences.copy(
                        audioSpeed = 1.0,
                        audioPitch = 1.0
                    )

                    fixedEpub -> current.formatPreferences.copy(epubSpread = "auto")
                    else -> current.formatPreferences
                }
                current.copy(
                    formatPreferences = format,
                    brightness = .5f,
                    autoBrightness = true,
                    forceOrientation = "Auto",
                    preventScreenTimeout = false,
                    alwaysShowStatusBar = false,
                    pineconeThemePreset = "Auto",
                )
            }
        },
        content = {
            PreferenceSwitch(
                stringResource(R.string.reader_settings_this_publication),
                useBookSettings,
                onUseBookSettings
            )
            if (isPdf && onReparsePdf != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onReparsePdf)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            stringResource(R.string.reader_reparse_pdf),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            stringResource(R.string.reader_reparse_pdf_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        MaterialSymbols.Outlined.Restart_alt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
    )
}

@Composable
private fun PdfInversionControl(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.reader_invert_pages),
            style = MaterialTheme.typography.titleMedium
        )
        val options = linkedMapOf(
            null to stringResource(R.string.reader_follow_theme),
            true to stringResource(R.string.reader_always),
            false to stringResource(R.string.reader_never),
        )
        SegmentedButtonGroup(
            options = options.values.toList(),
            icons = emptyList(),
            selected = options.getValue(settings.formatPreferences.pdf.invert),
            onSelected = { label ->
                val invert = options.entries.first { it.value == label }.key
                onChange { current ->
                    current.copy(
                        formatPreferences = current.formatPreferences.copy(
                            pdf = current.formatPreferences.pdf.copy(invert = invert),
                        )
                    )
                }
            },
        )
    }
}

@Composable
private fun FormatLighting(
    settings: ReaderSettings,
    onChange: ((ReaderSettings) -> ReaderSettings) -> Unit,
    showChromeHint: Boolean,
) {
    ReaderBrightnessControl(settings, onChange)
    if (showChromeHint) Text(
        stringResource(R.string.reader_chrome_theme_hint),
        Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall
    )
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.reader_settings_theme),
            style = MaterialTheme.typography.titleMedium
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            val themes = listOf(
                Triple("Auto", R.string.reader_settings_theme_auto, Color.Transparent),
                Triple("Light", R.string.reader_settings_theme_light, Color.White),
                Triple("Dark", R.string.reader_settings_theme_dark, Color.Black),
                Triple("Warm", R.string.reader_settings_theme_sepia, Color(0xFFFAF4E8)),
            )
            themes.forEach { (theme, label, color) ->
                ThemeSwatch(
                    isSelected = settings.pineconeThemePreset == theme,
                    onClick = { onChange { current -> current.copy(pineconeThemePreset = theme) } },
                    isAuto = theme == "Auto", color = color, label = stringResource(label),
                    modifier = if (theme == "Auto") Modifier.padding(
                        start = 0.dp,
                        end = 4.dp
                    ) else Modifier,
                )
            }
        }
    }

}

@Composable
internal fun SettingsGroup(
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(16.dp),
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = verticalArrangement) { content() }
}

@Composable
internal fun PreferenceSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    SettingsSwitchRow(title = label, isChecked = value, onCheckedChange = onChange)
}

@Composable
private fun PreferenceSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    presets: List<Float> = emptyList(),
    topPadding: Dp = 8.dp,
    onChange: (Float) -> Unit
) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = 8.dp)) {
        Text(
            stringResource(
                R.string.reader_setting_value,
                label,
                String.format(LocalLocale.current.platformLocale, "%.1f", value)
            ), style = MaterialTheme.typography.titleSmall
        )
        val slider = rememberSliderState(
            steps = ((range.endInclusive - range.start) * 10).roundToInt() - 1,
            trackRange = range,
        )
        slider.value = value.coerceIn(range)
        Slider(
            state = slider,
            onValueChange = { onChange((it * 10).roundToInt() / 10f) },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    drawTick = { _, _ -> },
                    drawStopIndicator = null,
                )
            },
        )
        if (presets.isNotEmpty()) {
            val locale = LocalLocale.current.platformLocale
            val labels = presets.map { String.format(locale, "%.1f×", it) }
            SegmentedButtonGroup(
                options = labels,
                icons = emptyList(),
                selected = presets.indexOf(value).let { labels.getOrNull(it) }.orEmpty(),
                onSelected = { onChange(presets[labels.indexOf(it)]) },
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            )
        }
    }
}
