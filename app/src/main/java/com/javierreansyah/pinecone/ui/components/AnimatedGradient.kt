package com.javierreansyah.pinecone.ui.components

import android.graphics.BlendMode
import android.graphics.Paint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.materialkolor.hct.Hct
import kotlin.math.abs

/**
 * Controls whether continuous animated gradient shaders and frame clocks should run.
 * Set to `false` on off-screen, collapsed, or measurement-only subtrees so invisible surfaces
 * allocate no shaders and run no `withFrameNanos` loops.
 */
val LocalAnimatedEffectsVisible: ProvidableCompositionLocal<Boolean> =
    compositionLocalOf { true }

object AnimatedFlowGradientDefaults {
    val HalfCycle: Dp = 180.dp
    const val INTENSITY_EPSILON: Float = 0.001f
}

@Immutable
data class AnimatedGradientPalette(
    val baseColor: Color,
    val color0: Color,
    val color1: Color,
    val color2: Color,
    val alpha: Float,
)

@Stable
class AnimatedGradientMotionState internal constructor(
    val intensity: State<Float>,
    val timeSeconds: State<Float>,
)

/**
 * Derives a cohesive, MaterialKolor HCT 2-hue (+ midpoint bridge) palette anchored on the active
 * theme's [primary] and sweeping analogously toward [tertiary] (capped to ±60°).
 *
 * Stays faithful to the active Material 3 tone and chroma range—lifting vibrancy gently without
 * neon overshoot or jarring jumps when crossfading from the solid resting state, while keeping
 * monochrome palettes (`chroma < 4`) strictly neutral.
 */
internal fun animatedGradientColors(
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
                Hct.from(primaryHct.hue, primaryHct.chroma, deepTone).toInt(),
            ).copy(alpha = primary.alpha),
            primary,
            Color(
                Hct.from(primaryHct.hue, primaryHct.chroma, brightTone).toInt(),
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
fun rememberAnimatedGradientPalette(
    baseColor: Color = MaterialTheme.colorScheme.primary,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
): AnimatedGradientPalette = remember(baseColor, accentColor) {
    val stops = animatedGradientColors(primary = baseColor, tertiary = accentColor)
    AnimatedGradientPalette(
        baseColor = baseColor,
        color0 = stops[0],
        color1 = stops[1],
        color2 = stops[2],
        alpha = baseColor.alpha,
    )
}

/**
 * Coordinates gradient dissipation intensity and a lifecycle-gated frame clock.
 *
 * Frame ticks run only while [visible] is true, the host lifecycle is at least
 * [Lifecycle.State.STARTED], and either [active], [keepTimeRunning], or a dissipating intensity
 * requires motion. Reading [AnimatedGradientMotionState.intensity] and
 * [AnimatedGradientMotionState.timeSeconds] inside draw phases avoids per-frame recompositions.
 */
@Composable
fun rememberAnimatedGradientMotionState(
    active: Boolean,
    label: String,
    keepTimeRunning: Boolean = false,
    visible: Boolean = LocalAnimatedEffectsVisible.current,
): AnimatedGradientMotionState {
    val intensity = animateFloatAsState(
        targetValue = if (active && visible) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        visibilityThreshold = AnimatedFlowGradientDefaults.INTENSITY_EPSILON,
        label = label,
    )
    val motionActive by remember(active, keepTimeRunning, visible, intensity) {
        derivedStateOf {
            visible && (
                    active ||
                            keepTimeRunning ||
                            intensity.value > AnimatedFlowGradientDefaults.INTENSITY_EPSILON
                    )
        }
    }
    val elapsedSeconds = remember { mutableFloatStateOf(0f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(motionActive, lifecycleOwner) {
        if (!motionActive) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var lastFrameNanos = withFrameNanos { it }
            while (true) {
                val frameNanos = withFrameNanos { it }
                val deltaSeconds = ((frameNanos - lastFrameNanos) / 1_000_000_000f)
                    .coerceIn(0f, 0.1f)
                lastFrameNanos = frameNanos
                elapsedSeconds.floatValue += deltaSeconds
            }
        }
    }

    return remember(intensity, elapsedSeconds) {
        AnimatedGradientMotionState(
            intensity = intensity,
            timeSeconds = elapsedSeconds,
        )
    }
}

/**
 * Masks the drawn content (rendered in [AnimatedGradientPalette.baseColor]) with a flowing HCT
 * gradient shader using `SRC_IN` when [motion]'s intensity is above 0, smoothly dissipating back
 * to the solid base color when inactive.
 */
fun Modifier.animatedFlowGradientMask(
    palette: AnimatedGradientPalette,
    motion: AnimatedGradientMotionState,
    rtl: Boolean,
    halfCycle: Dp = AnimatedFlowGradientDefaults.HalfCycle,
): Modifier = graphicsLayer {
    compositingStrategy =
        if (motion.intensity.value > AnimatedFlowGradientDefaults.INTENSITY_EPSILON) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
}.then(
    FlowGradientMaskElement(
        palette = palette,
        motion = motion,
        rtl = rtl,
        halfCycle = halfCycle,
    ),
)

/**
 * Draws an organic, center-anchored 2D Lissajous color mesh behind content.
 * Softens back into the solid container color as [motion]'s intensity dissipates toward 0,
 * stays anchored during press width expansion, and skips drawing once settled at 0.
 */
fun Modifier.animatedMeshGradient(
    palette: AnimatedGradientPalette,
    motion: AnimatedGradientMotionState,
    radius: Dp = 68.dp,
): Modifier = then(
    MorphMeshGradientElement(
        palette = palette,
        motion = motion,
        radius = radius,
    ),
)

@Composable
fun rememberFlowGradientLinePainter(): FlowGradientLinePainter =
    remember { FlowGradientLinePainter() }

@Stable
class FlowGradientLinePainter internal constructor() {
    private val shaderHolder = FlowGradientShaderHolder()
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun drawLine(
        drawScope: DrawScope,
        palette: AnimatedGradientPalette,
        motion: AnimatedGradientMotionState,
        rtl: Boolean,
        x: Float,
        startY: Float,
        endY: Float,
        strokeWidthPx: Float,
        halfCycle: Dp = AnimatedFlowGradientDefaults.HalfCycle,
    ) {
        val clampedIntensity = motion.intensity.value.coerceIn(0f, 1f)
        strokePaint.strokeWidth = strokeWidthPx
        if (clampedIntensity <= AnimatedFlowGradientDefaults.INTENSITY_EPSILON) {
            strokePaint.shader = null
            strokePaint.color = palette.baseColor.toArgb()
        } else {
            strokePaint.shader = shaderHolder.configure(
                palette = palette,
                timeSeconds = motion.timeSeconds.value,
                intensity = clampedIntensity,
                alpha = palette.alpha,
                halfCyclePx = with(drawScope) { halfCycle.toPx() },
                rtl = rtl,
            )
        }
        drawScope.drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawLine(x, startY, x, endY, strokePaint)
        }
    }
}

private class FlowGradientShaderHolder {
    private var shader: RuntimeShader? = null

    fun configure(
        palette: AnimatedGradientPalette,
        timeSeconds: Float,
        intensity: Float,
        alpha: Float,
        halfCyclePx: Float,
        rtl: Boolean,
    ): RuntimeShader {
        val activeShader = shader ?: RuntimeShader(FLOW_BAR_AGSL).also { shader = it }
        activeShader.setFloatUniform("uTime", timeSeconds)
        activeShader.setFloatUniform("uHalfCyclePx", halfCyclePx.coerceAtLeast(1f))
        activeShader.setFloatUniform("uDirection", if (rtl) 1f else -1f)
        activeShader.setFloatUniform(
            "uBaseColor",
            palette.baseColor.red,
            palette.baseColor.green,
            palette.baseColor.blue,
        )
        activeShader.setFloatUniform(
            "uColor0",
            palette.color0.red,
            palette.color0.green,
            palette.color0.blue,
        )
        activeShader.setFloatUniform(
            "uColor1",
            palette.color1.red,
            palette.color1.green,
            palette.color1.blue,
        )
        activeShader.setFloatUniform(
            "uColor2",
            palette.color2.red,
            palette.color2.green,
            palette.color2.blue,
        )
        activeShader.setFloatUniform("uIntensity", intensity)
        activeShader.setFloatUniform("uAlpha", alpha)
        return activeShader
    }
}

private data class FlowGradientMaskElement(
    val palette: AnimatedGradientPalette,
    val motion: AnimatedGradientMotionState,
    val rtl: Boolean,
    val halfCycle: Dp,
) : ModifierNodeElement<FlowGradientMaskNode>() {
    override fun create(): FlowGradientMaskNode =
        FlowGradientMaskNode(palette, motion, rtl, halfCycle)

    override fun update(node: FlowGradientMaskNode) {
        node.palette = palette
        node.motion = motion
        node.rtl = rtl
        node.halfCycle = halfCycle
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "animatedFlowGradientMask"
        properties["rtl"] = rtl
        properties["halfCycle"] = halfCycle
    }
}

private class FlowGradientMaskNode(
    var palette: AnimatedGradientPalette,
    var motion: AnimatedGradientMotionState,
    var rtl: Boolean,
    var halfCycle: Dp,
) : Modifier.Node(), DrawModifierNode {
    private val shaderHolder = FlowGradientShaderHolder()
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        blendMode = BlendMode.SRC_IN
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val clampedIntensity = motion.intensity.value.coerceIn(0f, 1f)
        if (clampedIntensity <= AnimatedFlowGradientDefaults.INTENSITY_EPSILON) return
        maskPaint.shader = shaderHolder.configure(
            palette = palette,
            timeSeconds = motion.timeSeconds.value,
            intensity = clampedIntensity,
            alpha = 1f,
            halfCyclePx = halfCycle.toPx(),
            rtl = rtl,
        )
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, maskPaint)
        }
    }
}

private data class MorphMeshGradientElement(
    val palette: AnimatedGradientPalette,
    val motion: AnimatedGradientMotionState,
    val radius: Dp,
) : ModifierNodeElement<MorphMeshGradientNode>() {
    override fun create(): MorphMeshGradientNode =
        MorphMeshGradientNode(palette, motion, radius)

    override fun update(node: MorphMeshGradientNode) {
        node.palette = palette
        node.motion = motion
        node.radius = radius
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "animatedMeshGradient"
        properties["radius"] = radius
    }
}

private class MorphMeshGradientNode(
    var palette: AnimatedGradientPalette,
    var motion: AnimatedGradientMotionState,
    var radius: Dp,
) : Modifier.Node(), DrawModifierNode {
    private var shader: RuntimeShader? = null
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun ContentDrawScope.draw() {
        val clampedIntensity = motion.intensity.value.coerceIn(0f, 1f)
        if (clampedIntensity > AnimatedFlowGradientDefaults.INTENSITY_EPSILON) {
            val activeShader = shader ?: RuntimeShader(MORPH_MESH_AGSL).also { shader = it }
            activeShader.setFloatUniform(
                "uSize",
                size.width.coerceAtLeast(1f),
                size.height.coerceAtLeast(1f),
            )
            activeShader.setFloatUniform("uTime", motion.timeSeconds.value)
            activeShader.setFloatUniform("uRadiusPx", radius.toPx().coerceAtLeast(1f))
            activeShader.setFloatUniform(
                "uColor0",
                palette.color0.red,
                palette.color0.green,
                palette.color0.blue,
            )
            activeShader.setFloatUniform(
                "uColor1",
                palette.color1.red,
                palette.color1.green,
                palette.color1.blue,
            )
            activeShader.setFloatUniform(
                "uColor2",
                palette.color2.red,
                palette.color2.green,
                palette.color2.blue,
            )
            activeShader.setFloatUniform("uIntensity", clampedIntensity)
            activeShader.setFloatUniform("uAlpha", palette.alpha)
            fillPaint.shader = activeShader
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, fillPaint)
            }
        }
        drawContent()
    }
}

private const val FLOW_BAR_AGSL = """
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

private const val MORPH_MESH_AGSL = """
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
