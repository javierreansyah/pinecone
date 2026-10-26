package com.javierreansyah.pinecone.data.local.preferences

/** Pure reading-surface decisions shared by storage and navigator adapters. */
fun ReaderSettings.readingThemeIsDark(isSystemDark: Boolean): Boolean {
    val appIsDark = when (themeMode) {
        "Dark" -> true
        "Light" -> false
        else -> isSystemDark
    }
    return when (pineconeThemePreset) {
        "Dark" -> true
        "Light", "Warm" -> false
        "Auto" -> appIsDark
        else -> {
            val hex = customBackgroundColor.removePrefix("#")
            val rgb = when (hex.length) {
                3 -> hex.flatMap { listOf(it, it) }.joinToString("").toLongOrNull(16)
                6, 8 -> hex.takeLast(6).toLongOrNull(16)
                else -> null
            } ?: return appIsDark
            val red = rgb shr 16 and 255
            val green = rgb shr 8 and 255
            val blue = rgb and 255
            red * 0.2126 + green * 0.7152 + blue * 0.0722 < 128
        }
    }
}

/** Applied through one stylesheet so Readium's night-only filter cannot double the effect. */
fun ReaderSettings.epubImageFilterCss(isSystemDark: Boolean): String {
    if (formatPreferences.epubImageFilterFollowTheme && !readingThemeIsDark(isSystemDark)) return ""
    val filter = when (imageFilter) {
        "Darken" -> "brightness(0.7)"
        "Invert" -> "invert(1)"
        else -> return ""
    }
    // Generated formula SVGs already follow the text color through currentColor.
    return "img, svg:not(.math-layout):not(.math-layout *) { filter: $filter !important; }"
}
