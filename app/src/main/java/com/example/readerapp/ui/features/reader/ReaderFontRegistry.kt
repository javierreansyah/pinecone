package com.example.readerapp.ui.features.reader

import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.epub.css.FontStyle
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.shared.ExperimentalReadiumApi

@OptIn(ExperimentalReadiumApi::class)
fun EpubNavigatorFragment.Configuration.configureFonts() {
    // Serve the fonts directory so the navigator can load custom font files.
    servedAssets += "fonts/.*"

    // Source Serif 4 — variable font, used as an explicit serif option.
    addFontFamilyDeclaration(FontFamily("Source Serif 4")) {
        addFontFace {
            addSource("fonts/source_serif_4.ttf", preload = true)
            setFontStyle(FontStyle.NORMAL)
            setFontWeight(200..900)
        }
        addFontFace {
            addSource("fonts/source_serif_4_italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(200..900)
        }
    }

    // Source Sans 3 — variable font, used as the default sans-serif option.
    addFontFamilyDeclaration(FontFamily("Source Sans 3")) {
        addFontFace {
            addSource("fonts/source_sans_3.ttf", preload = true)
            setFontStyle(FontStyle.NORMAL)
            setFontWeight(200..900)
        }
        addFontFace {
            addSource("fonts/source_sans_3_italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(200..900)
        }
    }

    // Literata — variable font.
    addFontFamilyDeclaration(FontFamily("Literata")) {
        addFontFace {
            addSource("fonts/literata.ttf", preload = true)
            setFontStyle(FontStyle.NORMAL)
            setFontWeight(200..900)
        }
        addFontFace {
            addSource("fonts/literata_italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(200..900)
        }
    }

    // Atkinson Hyperlegible — static font family.
    addFontFamilyDeclaration(FontFamily("Atkinson Hyperlegible")) {
        addFontFace {
            addSource("fonts/atkinson_hyperlegible.ttf", preload = true)
            setFontStyle(FontStyle.NORMAL)
            setFontWeight(400..500)
        }
        addFontFace {
            addSource("fonts/atkinson_hyperlegible_italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(400..500)
        }
        addFontFace {
            addSource("fonts/atkinson_hyperlegible_bold.ttf")
            setFontStyle(FontStyle.NORMAL)
            setFontWeight(600..700)
        }
        addFontFace {
            addSource("fonts/atkinson_hyperlegible_bold_italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(600..700)
        }
    }

    // Source Code — variable font.
    addFontFamilyDeclaration(FontFamily("Source Code")) {
        addFontFace {
            addSource("fonts/source_code.ttf", preload = true)
            setFontStyle(FontStyle.NORMAL)
            setFontWeight(200..900)
        }
        addFontFace {
            addSource("fonts/source_code_italic.ttf")
            setFontStyle(FontStyle.ITALIC)
            setFontWeight(200..900)
        }
    }
}
