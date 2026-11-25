package com.javierreansyah.pinecone.ui.features.reader.components

import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialkolor.hct.Hct
import kotlin.math.abs

internal val AudioProgressFlowHalfCycle: Dp = 180.dp

internal data class AudioGradientPalette(
    val baseColor: Color,
    val color0: Color,
    val color1: Color,
    val color2: Color,
    val alpha: Float,
)

/**
 * Derives a cohesive, MaterialKolor HCT 2-hue (+ midpoint bridge) palette anchored on the active
 * theme's [primary] and sweeping analogously toward [tertiary] (capped to ±60°).
 *
 * Stays faithful to the active Material 3 tone and chroma range—lifting vibrancy gently without
 * neon overshoot or jarring jumps when crossfading from the solid Play state, while keeping
 * monochrome palettes (`chroma < 4`) strictly neutral.
 */
internal fun audioProgressGradientColors(
    primary: Color,
    tertiary: Color,
): List<Color> {
    val primaryHct = Hct.fromInt(primary.toArgb())
    val tertiaryHct = Hct.fromInt(tertiary.toArgb())
    val isLightTone = primaryHct.tone >= 50.0

    if (primaryHct.chroma < 4.0) {
        val deepTone = (primaryHct.tone + if (isLightTone) -10.0 else 10.0).coerceIn(10.0, 96.0)
        val brightTone = (primaryHct.tone + if (isLightTone) 8.0 else -6.0).coerceIn(10.0, 96.0)
        return listOf(
            Color(
                Hct.from(primaryHct.hue, primaryHct.chroma, deepTone).toInt()
            ).copy(alpha = primary.alpha),
            primary,
            Color(
                Hct.from(primaryHct.hue, primaryHct.chroma, brightTone).toInt()
            ).copy(alpha = primary.alpha),
        )
    }

    val rawDiff = ((tertiaryHct.hue - primaryHct.hue + 540.0) % 360.0) - 180.0
    val hueSweep = if (tertiaryHct.chroma >= 4.0 && abs(rawDiff) >= 12.0) {
        rawDiff.coerceIn(-60.0, 60.0)
    } else {
        42.0
    }

    fun shiftedHue(fraction: Double): Double =
        ((primaryHct.hue + hueSweep * fraction) % 360.0 + 360.0) % 360.0

    val richTone = when {
        primaryHct.tone >= 85.0 -> (primaryHct.tone - 7.0).coerceIn(18.0, 94.0)
        isLightTone -> (primaryHct.tone - 5.0).coerceIn(18.0, 94.0)
        else -> (primaryHct.tone - 3.0).coerceIn(18.0, 94.0)
    }
    val midTone = when {
        primaryHct.tone >= 85.0 -> (primaryHct.tone - 2.0).coerceIn(18.0, 94.0)
        else -> primaryHct.tone.coerceIn(18.0, 94.0)
    }
    val glowTone = when {
        primaryHct.tone >= 85.0 -> (primaryHct.tone + 3.0).coerceIn(18.0, 96.0)
        isLightTone -> (primaryHct.tone + 4.0).coerceIn(18.0, 96.0)
        else -> (primaryHct.tone + 8.0).coerceIn(18.0, 96.0)
    }

    val richChroma = (primaryHct.chroma + 20.0).coerceIn(primaryHct.chroma, 58.0)
    val midChroma = (primaryHct.chroma + 10.0).coerceIn(primaryHct.chroma, 50.0)
    val glowChroma = (primaryHct.chroma - 2.0).coerceIn(12.0, 42.0)

    return listOf(
        Color(Hct.from(shiftedHue(0.0), richChroma, richTone).toInt()).copy(alpha = primary.alpha),
        Color(Hct.from(shiftedHue(0.45), midChroma, midTone).toInt()).copy(alpha = primary.alpha),
        Color(Hct.from(shiftedHue(0.85), glowChroma, glowTone).toInt()).copy(alpha = primary.alpha),
    )
}

@Composable
internal fun rememberAudioGradientPalette(
    baseColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
): AudioGradientPalette = remember(baseColor, accentColor) {
    val stops = audioProgressGradientColors(primary = baseColor, tertiary = accentColor)
    AudioGradientPalette(
        baseColor = baseColor,
        color0 = stops[0],
        color1 = stops[1],
        color2 = stops[2],
        alpha = baseColor.alpha,
    )
}

/**
 * Animates gradient intensity between `0f` (dissipated / solid base color) and `1f` (active
 * gradient) using the theme's slow effects motion spec.
 */
@Composable
internal fun rememberAudioGradientIntensity(
    active: Boolean,
    label: String,
): State<Float> = animateFloatAsState(
    targetValue = if (active) 1f else 0f,
    animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
    visibilityThreshold = 0.001f,
    label = label,
)

/**
 * Advances continuous elapsed seconds while [active] is true and pauses in place otherwise.
 * Read [State.value] inside draw lambdas only to avoid per-frame recompositions.
 */
@Composable
internal fun rememberAudioMotionTime(active: Boolean): State<Float> {
    val elapsedSeconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        while (true) {
            val frameNanos = withFrameNanos { it }
            val deltaSeconds = ((frameNanos - lastFrameNanos) / 1_000_000_000f)
                .coerceIn(0f, 0.1f)
            lastFrameNanos = frameNanos
            elapsedSeconds.floatValue += deltaSeconds
        }
    }
    return elapsedSeconds
}

/**
 * Keeps motion advancing smoothly while [active] is true or while [intensity] is still dissipating
 * back to `0f`, without triggering per-frame recompositions.
 */
@Composable
internal fun rememberAudioMotionTime(
    active: Boolean,
    intensity: State<Float>,
): State<Float> {
    val motionActive by remember(active, intensity) {
        derivedStateOf { active || (intensity.value > 0.001f) }
    }
    return rememberAudioMotionTime(active = motionActive)
}

@Composable
internal fun rememberAudioFlowBarRenderer(): AudioFlowBarRenderer =
    remember { AudioFlowBarRenderer() }

internal class AudioFlowBarRenderer {
    private val shader = RuntimeShader(AUDIO_FLOW_BAR_AGSL)
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = android.graphics.BlendMode.SRC_IN
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private fun updateUniforms(
        palette: AudioGradientPalette,
        timeSeconds: Float,
        intensity: Float,
        alpha: Float,
        halfCyclePx: Float,
        rtl: Boolean,
    ) {
        shader.setFloatUniform("uTime", timeSeconds)
        shader.setFloatUniform("uHalfCyclePx", halfCyclePx.coerceAtLeast(1f))
        shader.setFloatUniform("uDirection", if (rtl) 1f else -1f)
        shader.setFloatUniform(
            "uBaseColor",
            palette.baseColor.red,
            palette.baseColor.green,
            palette.baseColor.blue,
        )
        shader.setFloatUniform(
            "uColor0",
            palette.color0.red,
            palette.color0.green,
            palette.color0.blue
        )
        shader.setFloatUniform(
            "uColor1",
            palette.color1.red,
            palette.color1.green,
            palette.color1.blue
        )
        shader.setFloatUniform(
            "uColor2",
            palette.color2.red,
            palette.color2.green,
            palette.color2.blue
        )
        shader.setFloatUniform("uIntensity", intensity)
        shader.setFloatUniform("uAlpha", alpha)
    }

    fun maskContent(
        drawScope: DrawScope,
        palette: AudioGradientPalette,
        timeSeconds: Float,
        intensity: Float,
        halfCyclePx: Float,
        rtl: Boolean,
    ) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        if (clampedIntensity <= 0.001f) return
        updateUniforms(
            palette = palette,
            timeSeconds = timeSeconds,
            intensity = clampedIntensity,
            alpha = 1f,
            halfCyclePx = halfCyclePx,
            rtl = rtl,
        )
        maskPaint.shader = shader
        drawScope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                0f,
                0f,
                drawScope.size.width,
                drawScope.size.height,
                maskPaint
            )
        }
    }

    fun drawHandleLine(
        drawScope: DrawScope,
        palette: AudioGradientPalette,
        timeSeconds: Float,
        intensity: Float,
        halfCyclePx: Float,
        rtl: Boolean,
        x: Float,
        startY: Float,
        endY: Float,
        strokeWidthPx: Float,
    ) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        strokePaint.strokeWidth = strokeWidthPx
        if (clampedIntensity <= 0.001f) {
            strokePaint.shader = null
            strokePaint.color = palette.baseColor.toArgb()
        } else {
            updateUniforms(
                palette = palette,
                timeSeconds = timeSeconds,
                intensity = clampedIntensity,
                alpha = palette.alpha,
                halfCyclePx = halfCyclePx,
                rtl = rtl,
            )
            strokePaint.shader = shader
        }
        drawScope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawLine(x, startY, x, endY, strokePaint)
        }
    }
}

@Composable
internal fun rememberAudioMorphMeshRenderer(): AudioMorphMeshRenderer =
    remember { AudioMorphMeshRenderer() }

internal class AudioMorphMeshRenderer {
    private val shader = RuntimeShader(AUDIO_MORPH_MESH_AGSL)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawMesh(
        drawScope: DrawScope,
        palette: AudioGradientPalette,
        timeSeconds: Float,
        intensity: Float,
        radiusPx: Float,
    ) {
        val clampedIntensity = intensity.coerceIn(0f, 1f)
        if (clampedIntensity <= 0.001f) return
        shader.setFloatUniform(
            "uSize",
            drawScope.size.width.coerceAtLeast(1f),
            drawScope.size.height.coerceAtLeast(1f)
        )
        shader.setFloatUniform("uTime", timeSeconds)
        shader.setFloatUniform("uRadiusPx", radiusPx.coerceAtLeast(1f))
        shader.setFloatUniform(
            "uColor0",
            palette.color0.red,
            palette.color0.green,
            palette.color0.blue
        )
        shader.setFloatUniform(
            "uColor1",
            palette.color1.red,
            palette.color1.green,
            palette.color1.blue
        )
        shader.setFloatUniform(
            "uColor2",
            palette.color2.red,
            palette.color2.green,
            palette.color2.blue
        )
        shader.setFloatUniform("uIntensity", clampedIntensity)
        shader.setFloatUniform("uAlpha", palette.alpha)
        fillPaint.shader = shader
        drawScope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                0f,
                0f,
                drawScope.size.width,
                drawScope.size.height,
                fillPaint
            )
        }
    }
}

/**
 * Masks the drawn progress stroke (rendered in [AudioGradientPalette.baseColor]) with the
 * backward-flowing HCT gradient shader using `SRC_IN` when [intensity] is above 0, smoothly
 * dissipating back to the solid base color when paused.
 */
internal fun Modifier.maskWithAudioProgressGradient(
    renderer: AudioFlowBarRenderer,
    palette: AudioGradientPalette,
    timeSeconds: State<Float>,
    intensity: State<Float>,
    rtl: Boolean,
    halfCycle: Dp = AudioProgressFlowHalfCycle,
): Modifier = graphicsLayer {
    compositingStrategy = if (intensity.value > 0.001f) {
        CompositingStrategy.Offscreen
    } else {
        CompositingStrategy.Auto
    }
}
    .drawWithContent {
        drawContent()
        renderer.maskContent(
            drawScope = this,
            palette = palette,
            timeSeconds = timeSeconds.value,
            intensity = intensity.value,
            halfCyclePx = halfCycle.toPx(),
            rtl = rtl,
        )
    }

/**
 * Draws an organic, center-anchored 2D Lissajous color mesh at [intensity] behind button content.
 * Softens and disperses back into the solid container color as [intensity] dissipates toward 0,
 * stays anchored during press width expansion, and skips drawing once settled at 0 (Play state).
 */
internal fun Modifier.drawAudioMorphGradient(
    renderer: AudioMorphMeshRenderer,
    palette: AudioGradientPalette,
    timeSeconds: State<Float>,
    intensity: State<Float>,
    radius: Dp = 68.dp,
): Modifier = drawBehind {
    renderer.drawMesh(
        drawScope = this,
        palette = palette,
        timeSeconds = timeSeconds.value,
        intensity = intensity.value,
        radiusPx = radius.toPx(),
    )
}

private const val AUDIO_FLOW_BAR_AGSL = """
    uniform float uTime;
    uniform float uHalfCyclePx;
    uniform float uDirection;
    uniform float3 uBaseColor;
    uniform float3 uColor0;
    uniform float3 uColor1;
    uniform float3 uColor2;
    uniform float uIntensity;
    uniform float uAlpha;

    half4 main(float2 fragCoord) {
        float pos = (fragCoord.x / max(uHalfCyclePx, 1.0)) - uDirection * uTime * (2.0 / 3.0);
        float organicPos = pos + 0.10 * sin(pos * 1.618 + uTime * 0.73);
        float u = 0.5 - 0.5 * cos(organicPos * 3.14159265);

        float3 gradRgb = u < 0.5
            ? mix(uColor0, uColor1, u * 2.0)
            : mix(uColor1, uColor2, (u - 0.5) * 2.0);
        float3 rgb = mix(uBaseColor, gradRgb, uIntensity);
        return half4(half3(rgb * uAlpha), half(uAlpha));
    }
"""

private const val AUDIO_MORPH_MESH_AGSL = """
    uniform float2 uSize;
    uniform float uTime;
    uniform float uRadiusPx;
    uniform float3 uColor0;
    uniform float3 uColor1;
    uniform float3 uColor2;
    uniform float uIntensity;
    uniform float uAlpha;

    half4 main(float2 fragCoord) {
        float2 centered = fragCoord - 0.5 * uSize;
        float2 p = centered / max(uRadiusPx, 1.0);
        float t = uTime;

        float2 warp = float2(
            sin(p.y * 2.2 + t * 0.67) + 0.5 * cos(p.x * 1.7 - t * 0.43),
            cos(p.x * 2.1 - t * 0.59) + 0.5 * sin(p.y * 1.9 + t * 0.51)
        );
        float2 q = p + (0.11 * uIntensity) * warp;

        float axisAngle = t * 0.34 + 0.75 * sin(t * 0.27 + 0.6) + 0.35 * cos(t * 0.43);
        float spread = 0.62 + 0.16 * sin(t * 0.61 + 1.1);
        float2 axis = float2(cos(axisAngle), sin(axisAngle));

        float2 c0 = axis * spread + float2(0.20 * sin(t * 1.13 + 0.4), 0.16 * cos(t * 0.89 + 1.7));
        float2 c1 = -axis * spread + float2(0.20 * cos(t * 0.97 + 2.3), 0.16 * sin(t * 1.27 + 0.5));
        float2 c2 = float2(0.45 * sin(t * 0.79 + 3.1), 0.32 * cos(t * 1.07 + 2.0));

        float relax = 1.0 + 0.65 * (1.0 - uIntensity);
        float r0 = (0.84 + 0.16 * sin(t * 0.53 + 0.8)) * relax;
        float r1 = (0.84 + 0.16 * cos(t * 0.47 + 2.1)) * relax;
        float r2 = (0.78 + 0.14 * sin(t * 0.71 + 3.4)) * relax;

        float2 d0 = q - c0;
        float2 d1 = q - c1;
        float2 d2 = q - c2;

        float w0 = exp(-dot(d0, d0) / (r0 * r0));
        float w1 = exp(-dot(d1, d1) / (r1 * r1));
        float w2 = 0.65 * exp(-dot(d2, d2) / (r2 * r2));
        float wSum = max(w0 + w1 + w2, 0.0001);

        float3 rgb = (w0 * uColor0 + w1 * uColor2 + w2 * uColor1) / wSum;
        float outAlpha = uAlpha * uIntensity;
        return half4(half3(rgb * outAlpha), half(outAlpha));
    }
"""
