@file:OptIn(
    org.readium.r2.shared.ExperimentalReadiumApi::class,
    org.readium.r2.shared.InternalReadiumApi::class
)

package com.javierreansyah.pinecone.reader.pdf

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.readium.r2.navigator.OverflowableNavigator
import org.readium.r2.navigator.SimpleOverflow
import org.readium.r2.navigator.pdf.PdfDocumentFragmentInput
import org.readium.r2.navigator.pdf.PdfEngineProvider
import org.readium.r2.navigator.preferences.Axis
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.util.SingleFragmentFactory
import org.readium.r2.navigator.util.createFragmentFactory
import org.readium.r2.shared.publication.Metadata
import org.readium.r2.shared.publication.Publication

class MuPdfEngineProvider(
    private val searchHighlights: StateFlow<PdfSearchHighlights> = MutableStateFlow(PdfSearchHighlights()),
) :
    PdfEngineProvider<MuPdfSettings, MuPdfPreferences, MuPdfPreferencesEditor> {
    override fun createDocumentFragmentFactory(input: PdfDocumentFragmentInput<MuPdfSettings>): SingleFragmentFactory<MuPdfDocumentFragment> =
        createFragmentFactory { MuPdfDocumentFragment(input, searchHighlights) }

    override fun computeSettings(metadata: Metadata, preferences: MuPdfPreferences): MuPdfSettings =
        MuPdfSettings(preferences)

    override fun computeOverflow(settings: MuPdfSettings): OverflowableNavigator.Overflow =
        SimpleOverflow(
            readingProgression = if (settings.preferences.rightToLeft) ReadingProgression.RTL else ReadingProgression.LTR,
            scroll = settings.preferences.scroll,
            axis = if (settings.preferences.horizontal) Axis.HORIZONTAL else Axis.VERTICAL,
        )

    override fun createPreferenceEditor(
        publication: Publication,
        initialPreferences: MuPdfPreferences
    ): MuPdfPreferencesEditor =
        MuPdfPreferencesEditor(initialPreferences)

    override fun createEmptyPreferences(): MuPdfPreferences = MuPdfPreferences()
}
