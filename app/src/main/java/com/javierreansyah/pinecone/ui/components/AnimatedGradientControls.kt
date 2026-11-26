package com.javierreansyah.pinecone.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlinedfilled.Pause
import com.composables.icons.materialsymbols.outlinedfilled.Play_arrow

/**
 * Shared play/pause button interior that layers the organic morphing mesh gradient behind an
 * animated play/pause icon transition.
 */
@Composable
fun MorphingPlayPauseContent(
    playing: Boolean,
    contentDescription: String,
    palette: AnimatedGradientPalette,
    motion: AnimatedGradientMotionState,
    gradientRadius: Dp,
    modifier: Modifier = Modifier,
    iconSize: Dp? = null,
    label: String = "playPauseTransition",
) {
    val effectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    Box(
        modifier = modifier
            .fillMaxSize()
            .animatedMeshGradient(
                palette = palette,
                motion = motion,
                radius = gradientRadius,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = {
                fadeIn(animationSpec = effectsSpec) +
                        scaleIn(
                            initialScale = 0.75f,
                            animationSpec = spatialSpec,
                        ) togetherWith fadeOut(animationSpec = effectsSpec) +
                        scaleOut(
                            targetScale = 0.75f,
                            animationSpec = spatialSpec,
                        )
            },
            label = label,
        ) { isPlaying ->
            Icon(
                imageVector = if (isPlaying) {
                    MaterialSymbols.OutlinedFilled.Pause
                } else {
                    MaterialSymbols.OutlinedFilled.Play_arrow
                },
                contentDescription = contentDescription,
                modifier = if (iconSize != null) Modifier.size(iconSize) else Modifier,
            )
        }
    }
}

/**
 * Renders a two-pass progress indicator whose unmasked [track] stays in its standard surface color
 * while the foreground [indicator] is masked with [Modifier.animatedFlowGradientMask].
 */
@Composable
fun GradientMaskedProgressBar(
    palette: AnimatedGradientPalette,
    motion: AnimatedGradientMotionState,
    modifier: Modifier = Modifier,
    halfCycle: Dp = AnimatedFlowGradientDefaults.HalfCycle,
    track: @Composable (Modifier) -> Unit,
    indicator: @Composable (Modifier, Color) -> Unit,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(modifier = modifier) {
        track(Modifier.fillMaxSize())
        indicator(
            Modifier
                .fillMaxSize()
                .animatedFlowGradientMask(
                    palette = palette,
                    motion = motion,
                    rtl = rtl,
                    halfCycle = halfCycle,
                ),
            palette.baseColor,
        )
    }
}
