package com.javierreansyah.pinecone.ui.features.library.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
internal fun AnimatedSelectionCheckbox(
    visible: Boolean,
    checked: Boolean,
    modifier: Modifier = Modifier,
) {
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = effectsSpec) +
                scaleIn(initialScale = 0.8f, animationSpec = spatialSpec),
        exit = fadeOut(animationSpec = effectsSpec) +
                scaleOut(targetScale = 0.8f, animationSpec = spatialSpec),
    ) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center,
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
            )
        }
    }
}
