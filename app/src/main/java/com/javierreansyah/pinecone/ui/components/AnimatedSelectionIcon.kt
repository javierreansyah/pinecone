package com.javierreansyah.pinecone.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Material Symbols vectors have static paths; animate selection by fading between their styles. */
@Composable
fun AnimatedSelectionIcon(
    selected: Boolean,
    outlinedIcon: ImageVector,
    filledIcon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val description = contentDescription
    Crossfade(
        targetState = selected,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        modifier = if (description == null) modifier else modifier.semantics {
            // Expose one label throughout the transition, even while both icons are drawn.
            this.contentDescription = description
        },
        label = "Icon selection fill",
    ) { filled ->
        Icon(
            imageVector = if (filled) filledIcon else outlinedIcon,
            contentDescription = null,
            tint = tint,
        )
    }
}
