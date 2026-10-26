package com.javierreansyah.pinecone.data.local.preferences

import kotlinx.serialization.json.Json

private val BookSettingsJson = Json { encodeDefaults = true }

/** An enabled default override must stay distinct from the disabled sentinel, `{}`. */
fun ReaderSettings.encodeReadingOverrides(): String = BookSettingsJson.encodeToString(this)

/** Null and the disabled sentinel are distinct from an enabled default override. */
fun decodeReadingOverrides(encoded: String?): ReaderSettings? =
    encoded?.takeUnless { it == "{}" }?.let { BookSettingsJson.decodeFromString<ReaderSettings>(it) }

/** Merge reader controls only. A book override must never replace backup/dictionary/app settings. */
fun ReaderSettings.withReadingPreferencesFrom(value: ReaderSettings): ReaderSettings = copy(
    brightness = value.brightness,
    autoBrightness = value.autoBrightness,
    forceOrientation = value.forceOrientation,
    preventScreenTimeout = value.preventScreenTimeout,
    alwaysShowStatusBar = value.alwaysShowStatusBar,
    publisherStyles = value.publisherStyles,
    fontSize = value.fontSize,
    fontFamily = value.fontFamily,
    textAlign = value.textAlign,
    lineHeight = value.lineHeight,
    paragraphSpacing = value.paragraphSpacing,
    paragraphIndent = value.paragraphIndent,
    wordSpacing = value.wordSpacing,
    letterSpacing = value.letterSpacing,
    fontWeights = value.fontWeights,
    hyphens = value.hyphens,
    scroll = value.scroll,
    columnCount = value.columnCount,
    pageMargins = value.pageMargins,
    imageFilter = value.imageFilter,
    typeScale = value.typeScale,
    verticalMargin = value.verticalMargin,
    readingProgression = value.readingProgression,
    textNormalization = value.textNormalization,
    pineconeThemePreset = value.pineconeThemePreset,
    customBackgroundColor = value.customBackgroundColor,
    customTextColor = value.customTextColor,
    customThemes = value.customThemes,
    jumpHistoryMode = value.jumpHistoryMode,
    formatPreferences = value.formatPreferences,
)
