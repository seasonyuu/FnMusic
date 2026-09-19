package com.seasonyuu.fnmusic.core.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import kotlin.math.*

internal const val MenuShader =
    """
uniform shader content;
uniform float4 anchor;
uniform float4 body;
uniform float2 radii;
uniform float blend;
uniform float2 origin;
uniform float padding;
uniform float density;
uniform float4 tint;
uniform float2 touch;
uniform float4 optics;
float rounded(float2 p, float4 r, float radius) {
    float2 q = abs(p-r.xy)-r.zw+radius;
    return length(max(q,0.0))+min(max(q.x,q.y),0.0)-radius;
}
float distanceAt(float2 p) {
    float b = rounded(p,body,radii.y);
    if (anchor.z < 0.5 || anchor.w < 0.5) return b;
    float a = rounded(p,anchor,radii.x);
    if (blend < 0.001) return min(a,b);
    float h = max(blend-abs(a-b),0.0)/blend;
    return min(a,b)-h*h*blend*0.25;
}
half4 main(float2 coord) {
    float2 p = coord-padding+origin;
    float d = distanceAt(p);
    float coverage = 1.0-smoothstep(-0.75,0.75,d);
    if (coverage <= 0.0) return half4(0.0);
    float2 gradient = float2(distanceAt(p+float2(0.5,0))-distanceAt(p-float2(0.5,0)),
                             distanceAt(p+float2(0,0.5))-distanceAt(p-float2(0,0.5)));
    float2 normal = gradient/max(length(gradient),0.001);
    // Match Backdrop lens(): a circular refraction profile, with no interior zoom.
    float depth = max(-d,0.0);
    float t = clamp(1.0-depth/optics.x,0.0,1.0);
    float bend = (1.0-sqrt(max(0.0,1.0-t*t)))*optics.y;
    float2 displacement = normal*bend;
    half4 bg = content.eval(coord-displacement);
    float3 color = mix(float3(bg.rgb),tint.rgb,tint.a);
    // Highlight.Default: 45-degree, two-sided lighting with additive white.
    // A softened SDF band follows the fused contour instead of outlining its bounding box.
    float light = abs(dot(normal,normalize(float2(1.0,1.0))));
    float rim = (1.0-smoothstep(optics.z-optics.w,optics.z+optics.w,depth))*light*0.5;
    float glow = touch.x < 0.0 ? 0.0 : exp(-length(p-touch)/(45.0*density))*0.10;
    return half4(half3(clamp(color+rim+glow,0.0,1.0))*half(coverage),half(coverage));
}
"""

internal enum class MenuRendering { Shader, Blur, Solid }

@Composable
internal fun rememberMenuRendering(shaderSource: String = MenuShader): MenuRendering {
    val enabled = currentLiquidGlassMaterial().enabled
    val failed = remember(shaderSource) {
        Build.VERSION.SDK_INT >= 33 &&
            runCatching { android.graphics.RuntimeShader(shaderSource) }.isFailure
    }
    return when {
        !enabled || failed -> MenuRendering.Solid
        Build.VERSION.SDK_INT >= 33 -> MenuRendering.Shader
        Build.VERSION.SDK_INT >= 31 -> MenuRendering.Blur
        else -> MenuRendering.Solid
    }
}

/** Only the backdrop enters the shader. Labels are drawn separately by the menu host. */
@Composable
internal fun LiquidMenuSurface(
    backdrop: Backdrop,
    blobs: MenuBlobs,
    modifier: Modifier = Modifier,
    touch: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset(-1f, -1f),
    shaderSource: String = MenuShader,
    rendering: MenuRendering = rememberMenuRendering(shaderSource),
    opacity: Float = 1f,
) {
    val density = LocalDensity.current
    val material = currentLiquidGlassMaterial()
    val full = rendering == MenuRendering.Shader
    val anchorBounds = if (blobs.anchor.isEmpty) blobs.body else blobs.anchor
    val bounds =
        if (full)
            Rect(
                floor(min(anchorBounds.left, blobs.body.left) - 32 * density.density),
                floor(min(anchorBounds.top, blobs.body.top) - 32 * density.density),
                ceil(max(anchorBounds.right, blobs.body.right) + 32 * density.density),
                ceil(max(anchorBounds.bottom, blobs.body.bottom) + 32 * density.density),
            )
        else blobs.body
    val base =
        modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
            .graphicsLayer { alpha = opacity }
    val tint = material.baseSurface
    if (full) {
        Box(
            base
                .testTag("liquid-menu-shader")
                .drawPlainBackdrop(
                    backdrop,
                    { RectangleShape },
                    effects = {
                        colorControls(brightness = material.brightness, saturation = LiquidControlOptics.Saturation)
                        blur(4f * density.density * material.blurScale)
                        runtimeShaderEffect("FnLiquidMenu:$shaderSource", shaderSource, "content") {
                            setFloatUniform(
                                "anchor",
                                blobs.anchor.center.x,
                                blobs.anchor.center.y,
                                blobs.anchor.width / 2,
                                blobs.anchor.height / 2,
                            )
                            setFloatUniform(
                                "body",
                                blobs.body.center.x,
                                blobs.body.center.y,
                                blobs.body.width / 2,
                                blobs.body.height / 2,
                            )
                            setFloatUniform("radii", blobs.anchorRadius, blobs.radius)
                            setFloatUniform("blend", blobs.blend)
                            setFloatUniform("origin", bounds.left, bounds.top)
                            setFloatUniform("padding", padding)
                            setFloatUniform("density", density.density)
                            setFloatUniform(
                                "tint",
                                tint.red,
                                tint.green,
                                tint.blue,
                                material.surfaceAlpha,
                            )
                            setFloatUniform("optics",
                                with(density) { LiquidControlOptics.RefractionHeight.toPx() },
                                with(density) { LiquidControlOptics.RefractionAmount.toPx() },
                                with(density) { LiquidControlOptics.Highlight.width.toPx() },
                                with(density) { LiquidControlOptics.Highlight.blurRadius.toPx() })
                            setFloatUniform("touch", touch.x, touch.y)
                        }
                    },
                )
        )
    } else {
        val shape = RoundedCornerShape(32.dp)
        val glass = rendering == MenuRendering.Blur
        Box(
            base
                .testTag(if (glass) "liquid-menu-blur" else "liquid-menu-solid")
                .then(
                    if (glass)
                        Modifier.drawPlainBackdrop(
                            backdrop,
                            { shape },
                            effects = { blur(density.density * material.blurScale) },
                            onDrawSurface = { drawRect(tint.copy(alpha = material.surfaceAlpha)) },
                        )
                    else Modifier.background(tint, shape)
                )
        )
    }
}
