package com.javierreansyah.pinecone.reader.pdf

import kotlinx.serialization.Serializable
import org.readium.r2.navigator.preferences.Configurable
import org.readium.r2.navigator.preferences.PreferencesEditor

@Serializable
data class MuPdfPreferences(
    val scroll: Boolean = true,
    val horizontal: Boolean = false,
    val rightToLeft: Boolean = false,
    val fitWidth: Boolean = false,
    val pageSpacing: Double = 12.0,
    val invert: Boolean? = null,
) : Configurable.Preferences<MuPdfPreferences> {
    init {
        require(pageSpacing.isFinite() && pageSpacing in 0.0..64.0)
    }

    override fun plus(other: MuPdfPreferences): MuPdfPreferences = other

    fun resolveTheme(isDark: Boolean): MuPdfPreferences = copy(invert = invert ?: isDark)
}

data class MuPdfSettings(val preferences: MuPdfPreferences) : Configurable.Settings

class MuPdfPreferencesEditor(initial: MuPdfPreferences) : PreferencesEditor<MuPdfPreferences> {
    override var preferences: MuPdfPreferences = initial
        private set

    fun update(value: MuPdfPreferences) {
        preferences = value
    }

    override fun clear() {
        preferences = MuPdfPreferences()
    }
}
