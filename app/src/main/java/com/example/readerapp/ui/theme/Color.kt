package com.example.readerapp.ui.theme

import androidx.compose.ui.graphics.Color

//{
//    "neutral": {
//    "50": "#FAFAFA",
//    "100": "#F5F5F5",
//    "200": "#EFEFEF",
//    "300": "#E5E5E5",
//    "400": "#D4D4D4",
//    "500": "#A3A3A3",
//    "600": "#737373",
//    "700": "#525252",
//    "800": "#404040",
//    "900": "#262626",
//    "950": "#1A1A1A",
//    "975": "#0F0F0F",
//    "1000": "#0A0A0A"
//},
//    "red": {
//    "400": "#E8112D",
//    "600": "#B50D23",
//    "800": "#7A0918"
//}
//}

// Swiss Editorial — Light Theme
val LightPrimary = Color(0xFF0A0A0A) // Neutral 950
val LightOnPrimary = Color(0xFFFAFAFA) // Neutral 50
val LightPrimaryContainer = Color(0xFFEFEFEF) // Neutral 100
val LightOnPrimaryContainer = Color(0xFF0A0A0A) // Neutral 950
val LightSecondary = Color(0xFF404040) // Neutral 700 — muted secondary
val LightOnSecondary = Color(0xFFFAFAFA)
val LightSecondaryContainer = Color(0xFFF5F5F5)
val LightOnSecondaryContainer = Color(0xFF262626)
val LightBackground = Color(0xFFFAFAFA)
val LightOnBackground = Color(0xFF0A0A0A)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF0A0A0A)
val LightSurfaceVariant = Color(0xFFEFEFEF)
val LightOnSurfaceVariant = Color(0xFF737373)
val LightOutline = Color(0xFFE5E5E5)

// Swiss Editorial — Dark Theme
val DarkPrimary = Color(0xFFF5F5F5) // Neutral 100
val DarkOnPrimary = Color(0xFF0A0A0A)
val DarkPrimaryContainer = Color(0xFF242424)
val DarkOnPrimaryContainer = Color(0xFFF5F5F5)
val DarkSecondary = Color(0xFFA3A3A3) // Neutral 400 — muted secondary
val DarkOnSecondary = Color(0xFF0A0A0A)
val DarkSecondaryContainer = Color(0xFF2A2A2A)
val DarkOnSecondaryContainer = Color(0xFFD4D4D4)
val DarkBackground = Color(0xFF0F0F0F)
val DarkOnBackground = Color(0xFFF5F5F5)
val DarkSurface = Color(0xFF1A1A1A)
val DarkOnSurface = Color(0xFFF5F5F5)
val DarkSurfaceVariant = Color(0xFF242424)
val DarkOnSurfaceVariant = Color(0xFFA3A3A3)
val DarkOutline = Color(0xFF2A2A2A)

// Accent — Swiss red. Use for icons, tags, rules, CTAs only.
val AccentRed = Color(0xFFE8112D)
val AccentRedPressed = Color(0xFFB50D23)
val AccentRedContainer = Color(0xFF7A0918) // dark bg use
val AccentRedOnContainer = Color(0xFFFAFAFA)

// Semantic — neutral-adjacent, no chroma
val ErrorRed = Color(0xFFE8112D) // reuse accent
val ErrorRedDark = Color(0xFF7A0918)
val SuccessGreen = Color(0xFF404040) // neutral dark in light mode
val WarningYellow = Color(0xFF737373) // neutral mid — no yellow chroma
