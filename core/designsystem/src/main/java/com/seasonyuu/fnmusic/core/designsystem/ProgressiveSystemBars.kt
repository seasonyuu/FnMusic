package com.seasonyuu.fnmusic.core.designsystem

import android.os.Build
import kotlin.math.pow
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

// Default status-bar curve: (1 - progress)^exponent. App bars can supply y/radius stops.
private const val ProgressiveBlurExponent = 2f

/**
 * Lets the whole scene render edge-to-edge, then samples it into non-interactive
 * progressive blur layers over the system bars. Interactive content must still
 * provide scrollable content padding so its first and last items remain reachable.
 */
@Composable
fun FnProgressiveSystemBars(
    modifier: Modifier = Modifier,
    showTopBlur: Boolean = true,
    topBlur: @Composable (Backdrop) -> Unit = { backdrop ->
        ProgressiveBarBlur(backdrop, top = true, modifier = Modifier)
    },
    content: @Composable () -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    androidx.compose.runtime.CompositionLocalProvider(LocalFnBackdrop provides backdrop) {
        Box(modifier.fillMaxSize()) {
            ProgressiveBarBlur(
                backdrop = backdrop,
                top = false,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            content()
            if (showTopBlur) {
                Box(Modifier.align(Alignment.TopCenter)) { topBlur(backdrop) }
            }
        }
    }
}

/** Mask offsets let adjacent surfaces share one continuous fade without overlapping. */
@Composable
fun ProgressiveBarBlur(
    backdrop: Backdrop,
    top: Boolean,
    modifier: Modifier,
    tint: Color = if (top) FnBackgroundTop else FnBackgroundBottom,
    extraHeight: androidx.compose.ui.unit.Dp = 0.dp,
    transitionHeight: androidx.compose.ui.unit.Dp = if (top) 32.dp else 72.dp,
    fadeStartFraction: Float? = null,
    includeSystemInset: Boolean = true,
    maskTopOffset: androidx.compose.ui.unit.Dp = 0.dp,
    maskBottomExtension: androidx.compose.ui.unit.Dp = 0.dp,
    radiusStops: List<BlurRadiusStop>? = null,
) {
    val radiusCurve = androidx.compose.runtime.remember(radiusStops) { radiusStops?.let(::blurRadiusSegments) }
    val maxRadiusDp = radiusStops?.maxOf { it.radius } ?: 32.dp
    val density = LocalDensity.current
    val inset = if (!includeSystemInset) 0.dp else with(density) {
        if (top) WindowInsets.statusBars.getTop(this).toDp()
        else WindowInsets.navigationBars.getBottom(this).toDp()
    }
    val fallbackGradient = if (top && fadeStartFraction != null) {
        // Match the long, smooth fade on devices without runtime shaders.
        Brush.verticalGradient(*Array(17) { index ->
            val position = index / 16f
            val height = inset + transitionHeight + extraHeight
            val maskHeight = height + maskTopOffset + maskBottomExtension
            val maskPosition = if (maskHeight > 0.dp) (height * position + maskTopOffset) / maskHeight else position
            val progress = ((maskPosition - fadeStartFraction) / (1f - fadeStartFraction)).coerceIn(0f, 1f)
            val strength = radiusCurve?.let {
                if (maxRadiusDp.value > 0f) it.radiusAt((height * position + maskTopOffset).value) / maxRadiusDp.value else 0f
            } ?: (1 - progress).pow(ProgressiveBlurExponent)
            position to tint.copy(alpha = 0.32f * strength)
        })
    } else if (top) {
        Brush.verticalGradient(listOf(tint.copy(alpha = 0.94f), Color.Transparent))
    } else {
        Brush.verticalGradient(listOf(Color.Transparent, tint.copy(alpha = 0.94f)))
    }
    val supportsProgressiveBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    Box(
        modifier
            .fillMaxWidth()
            .height(inset + transitionHeight + extraHeight)
            .clearAndSetSemantics {}
            .testTag(if (top) "top-system-bar-blur" else "bottom-system-bar-blur")
            .then(
                if (supportsProgressiveBlur) {
                    Modifier.drawPlainBackdrop(
                        backdrop = backdrop,
                        shape = { RectangleShape },
                        effects = {
                            if (top) {
                                // Capture beyond the visible strip so samples near its edges
                                // see the actual scene rather than a clamped strip boundary.
                                val radius = maxRadiusDp.toPx()
                                padding = radius
                                val maskHeight = size.height + maskTopOffset.toPx() + maskBottomExtension.toPx()
                                val start = fadeStartFraction?.let { maskHeight * it }
                                    ?: if (extraHeight > 0.dp) (inset + extraHeight).toPx() else size.height * .22f
                                for (horizontal in listOf(true, false)) {
                                    runtimeShaderEffect(
                                        "FnProgressiveBlur-${horizontal}-${radiusCurve?.size ?: 0}",
                                        progressiveRadiusShader(horizontal, radiusCurve?.size ?: 0), "content",
                                    ) {
                                        setFloatUniform("maskHeight", maskHeight)
                                        setFloatUniform("maskTopOffset", maskTopOffset.toPx())
                                        setFloatUniform("samplePadding", radius)
                                        setFloatUniform("maxRadius", radius)
                                        setFloatUniform("fadeStart", start)
                                        radiusCurve?.forEachIndexed { index, segment ->
                                            setFloatUniform("curve$index", segment.startY * density.density, segment.endY * density.density,
                                                segment.startRadius * density.density, segment.endRadius * density.density)
                                            setFloatUniform("slopes$index", segment.startSlope, segment.endSlope)
                                        }
                                        if (!horizontal) setColorUniform("tint", tint)
                                    }
                                }
                            } else {
                                blur(20.dp.toPx())
                                runtimeShaderEffect(
                                    if (top) "FnTopSystemBarMask" else "FnBottomSystemBarMask",
                                    progressiveMaskShader(top),
                                    "content",
                                ) {
                                    setFloatUniform("size", size.width, size.height + maskTopOffset.toPx() + maskBottomExtension.toPx())
                                    setFloatUniform("maskTopOffset", maskTopOffset.toPx())
                                    setColorUniform("tint", tint)
                                    setFloatUniform("tintIntensity", 0.32f)
                                    setFloatUniform("fadeStart", fadeStartFraction?.let { (size.height + maskTopOffset.toPx() + maskBottomExtension.toPx()) * it }
                                        ?: if (extraHeight > 0.dp) (inset + extraHeight).toPx() else size.height * .22f)
                                }
                            }
                        },
                    )
                } else {
                    Modifier.background(fallbackGradient)
                }
            ),
    )
}

/** Two separable Gaussian passes with a radius determined by the output row.
 * The scene stays opaque: decreasing the radius, not revealing a sharp copy,
 * restores detail. Only the optional tint fades with the radius.
 */
private fun progressiveRadiusShader(horizontal: Boolean, segmentCount: Int): String = """
    uniform shader content;
    uniform float maskHeight;
    uniform float maskTopOffset;
    uniform float samplePadding;
    uniform float maxRadius;
    uniform float fadeStart;
    ${if (horizontal) "" else "layout(color) uniform half4 tint;"}

    ${(0 until segmentCount).joinToString("\n") { "uniform float4 curve$it; uniform float2 slopes$it;" }}
    float interpolateRadius(float y, float4 point, float2 slope) {
        float distance = point.y - point.x;
        float t = clamp((y - point.x) / distance, 0.0, 1.0);
        float t2 = t * t;
        float t3 = t2 * t;
        return (2.0*t3 - 3.0*t2 + 1.0)*point.z + (t3 - 2.0*t2 + t)*distance*slope.x
            + (-2.0*t3 + 3.0*t2)*point.w + (t3 - t2)*distance*slope.y;
    }
    ${if (segmentCount > 0) """
    float radiusAt(float y) {
        ${(0 until segmentCount).joinToString("\n") { "if (y <= curve$it.y) return interpolateRadius(y, curve$it, slopes$it);" }}
        return curve${segmentCount - 1}.w;
    }
    """ else ""}

    half4 main(float2 coord) {
        float y = coord.y - samplePadding + maskTopOffset;
        float progress = clamp((y - fadeStart) / max(maskHeight - fadeStart, 1.0), 0.0, 1.0);
        float radius = ${if (segmentCount > 0) "max(radiusAt(y), 0.0)" else "maxRadius * pow(1.0 - progress, $ProgressiveBlurExponent)"};
        float strength = maxRadius > 0.0 ? radius / maxRadius : 0.0;
        half4 color = half4(0.0);
        float weightSum = 0.0;
        for (int i = -12; i <= 12; ++i) {
            float fraction = float(i) / 12.0;
            float weight = exp(-4.5 * fraction * fraction);
            float2 offset = ${if (horizontal) "float2(fraction * radius, 0.0)" else "float2(0.0, fraction * radius)"};
            float2 sampleCoord = coord + offset;
            // Extend the screen's top row into the padding. Sampling transparent
            // off-screen pixels would cause both leakage and discrete edge bands.
            sampleCoord.y = max(sampleCoord.y, max(0.0, samplePadding - maskTopOffset));
            color += content.eval(sampleCoord) * half(weight);
            weightSum += weight;
        }
        color /= half(weightSum);
        ${if (horizontal) "return color;" else """
        // Off-screen samples are transparent. Normalize their coverage rather
        // than compositing the partially transparent result over a sharp scene.
        half3 opaqueColor = color.a > half(0.0001) ? color.rgb / color.a : tint.rgb;
        return half4(mix(opaqueColor, tint.rgb, half(0.32 * strength)), 1.0);
        """}
    }
""".trimIndent()

private fun progressiveMaskShader(top: Boolean): String =
    """
        uniform shader content;
        uniform float2 size;
        layout(color) uniform half4 tint;
        uniform float tintIntensity;
        uniform float fadeStart;
        uniform float maskTopOffset;

        half4 main(float2 coord) {
            half strength = ${if (top) "half(1.0 - smoothstep(fadeStart, size.y, coord.y + maskTopOffset))" else "half(smoothstep(0.0, size.y * 0.78, coord.y))"};
            half4 glass = mix(content.eval(coord), tint, half(tintIntensity));
            return mix(half4(0.0), glass, strength);
        }
    """.trimIndent()
val LocalFnBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }
