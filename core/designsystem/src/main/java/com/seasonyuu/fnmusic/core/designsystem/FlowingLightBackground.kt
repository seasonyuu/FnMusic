package com.seasonyuu.fnmusic.core.designsystem

import android.graphics.RuntimeShader
import android.graphics.Paint as FrameworkPaint
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.cos
import kotlin.math.sin

enum class FlowingLightStyle(
    val colors: List<Color>,
    val cycleMillis: Int,
    val scale: Float,
    val webSpeed: Float,
    val distortion: Float,
    val swirl: Float,
    val grainMixer: Float,
    val grainOverlay: Float,
) {
    Login(
        colors = listOf(Color(0xFF080B12), Color(0xFF14274B), Color(0xFF71829E), Color(0xFF10141E)),
        cycleMillis = 120_000,
        scale = 1.2f,
        webSpeed = .22f,
        distortion = .65f,
        swirl = .18f,
        grainMixer = .08f,
        grainOverlay = .08f,
    ),
    Favorites(
        colors = listOf(Color(0xFFFF6B35), Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFD740)),
        cycleMillis = 12_000,
        scale = .9f,
        webSpeed = 1.8f,
        distortion = .6f,
        swirl = .2f,
        grainMixer = 0f,
        grainOverlay = 0f,
    ),
    Recent(
        colors = listOf(Color(0xFF0A2A1A), Color(0xFF1B5E3B), Color(0xFF77B5A0), Color(0xFF2ECC71)),
        cycleMillis = 16_000,
        scale = 1.5f,
        webSpeed = 1.3f,
        distortion = .5f,
        swirl = .08f,
        grainMixer = 0f,
        grainOverlay = 0f,
    ),
    RecentlyAdded(
        colors = listOf(Color(0xFF3C3C3C), Color(0xFF777777), Color(0xFFB7B7B7), Color(0xFF545454)),
        cycleMillis = 19_000,
        scale = 1.4f,
        webSpeed = 1.1f,
        distortion = .5f,
        swirl = .06f,
        grainMixer = 0f,
        grainOverlay = 0f,
    ),
}

/**
 * A direct Android RuntimeShader port of the WebGL shader used by fnOS Music's
 * homepage cards. The web version mixes moving color blobs with value noise,
 * center-weighted distortion and a subtle swirl. Keeping the shader here (as
 * opposed to approximating it with a sweep gradient) makes the motion and
 * color blending match the source much more closely on API 33+ devices.
 */
private const val FLOW_FRAGMENT_SHADER = """
uniform float2 u_resolution;
uniform float u_time;
uniform float4 u_color0;
uniform float4 u_color1;
uniform float4 u_color2;
uniform float4 u_color3;
uniform float u_distortion;
uniform float u_swirl;
uniform float u_scale;
uniform float u_grainMixer;
uniform float u_grainOverlay;

float hash21(float2 p) {
    p = fract(p * float2(0.3183099, 0.3678794)) + 0.1;
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

float valueNoise(float2 st) {
    float2 i = floor(st);
    float2 f = fract(st);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float noise(float2 n, float2 seedOffset) {
    return valueNoise(n + seedOffset);
}

float2 rotate(float2 uv, float th) {
    return float2(cos(th) * uv.x + sin(th) * uv.y,
                  -sin(th) * uv.x + cos(th) * uv.y);
}

float2 getPosition(int i, float t) {
    float a = float(i) * 0.37;
    float b = 0.6 + fract(float(i) / 3.0) * 0.9;
    float c = 0.8 + fract(float(i + 1) / 4.0);
    float x = sin(t * b + a);
    float y = cos(t * c + a * 1.5);
    return 0.5 + 0.5 * float2(x, y);
}

half4 main(float2 fragCoord) {
    float minDim = min(u_resolution.x, u_resolution.y);
    float2 uv = (fragCoord - 0.5 * u_resolution) / minDim / u_scale + 0.5;
    float2 grainUV = uv * 1000.0;
    float grain = noise(grainUV, float2(0.0));
    float mixerGrain = 0.4 * u_grainMixer * (grain - 0.5);
    float t = 0.5 * (u_time + 41.5);
    float radius = smoothstep(0.0, 1.0, length(uv - 0.5));
    float center = 1.0 - radius;

    for (float i = 1.0; i <= 2.0; i += 1.0) {
        uv.x += u_distortion * center / i * sin(t + i * 0.4 * smoothstep(0.0, 1.0, uv.y)) * cos(0.2 * t + i * 2.4 * smoothstep(0.0, 1.0, uv.y));
        uv.y += u_distortion * center / i * cos(t + i * 2.0 * smoothstep(0.0, 1.0, uv.x));
    }

    float2 uvRotated = uv - 0.5;
    float angle = 3.0 * u_swirl * radius;
    uvRotated = rotate(uvRotated, -angle) + 0.5;

    float3 color = float3(0.0);
    float opacity = 0.0;
    float totalWeight = 0.0;

    float2 p0 = getPosition(0, t) + float2(mixerGrain);
    float d0 = pow(length(uvRotated - p0), 3.5);
    float w0 = 1.0 / (d0 + 0.001);
    color += u_color0.rgb * u_color0.a * w0;
    opacity += u_color0.a * w0;
    totalWeight += w0;

    float2 p1 = getPosition(1, t) + float2(mixerGrain);
    float d1 = pow(length(uvRotated - p1), 3.5);
    float w1 = 1.0 / (d1 + 0.001);
    color += u_color1.rgb * u_color1.a * w1;
    opacity += u_color1.a * w1;
    totalWeight += w1;

    float2 p2 = getPosition(2, t) + float2(mixerGrain);
    float d2 = pow(length(uvRotated - p2), 3.5);
    float w2 = 1.0 / (d2 + 0.001);
    color += u_color2.rgb * u_color2.a * w2;
    opacity += u_color2.a * w2;
    totalWeight += w2;

    float2 p3 = getPosition(3, t) + float2(mixerGrain);
    float d3 = pow(length(uvRotated - p3), 3.5);
    float w3 = 1.0 / (d3 + 0.001);
    color += u_color3.rgb * u_color3.a * w3;
    opacity += u_color3.a * w3;
    totalWeight += w3;

    color /= max(0.0001, totalWeight);
    opacity /= max(0.0001, totalWeight);
    float grainOverlay = valueNoise(rotate(grainUV, 1.0) + float2(3.0));
    grainOverlay = mix(grainOverlay, valueNoise(rotate(grainUV, 2.0) + float2(-1.0)), 0.5);
    grainOverlay = pow(grainOverlay, 1.3);
    float grainOverlayV = grainOverlay * 2.0 - 1.0;
    float3 grainOverlayColor = float3(step(0.0, grainOverlayV));
    float grainOverlayStrength = u_grainOverlay * abs(grainOverlayV);
    grainOverlayStrength = pow(grainOverlayStrength, 0.8);
    color = mix(color, grainOverlayColor, 0.35 * grainOverlayStrength);
    opacity += 0.5 * grainOverlayStrength;
    opacity = clamp(opacity, 0.0, 1.0);
    return half4(clamp(color, 0.0, 1.0), opacity);
}
"""

@Composable
fun FlowingLightBackground(variant: FlowingLightStyle) {
    val transition = rememberInfiniteTransition(label = "${variant.name}-flow")
    val elapsedSeconds by transition.animateFloat(
        initialValue = 0f,
        targetValue = variant.cycleMillis / 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(variant.cycleMillis, easing = LinearEasing),
            repeatMode = AnimationRepeatMode.Restart,
        ),
        label = "${variant.name}-phase",
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        FlowingLightShaderBackground(variant, elapsedSeconds)
    } else {
        FlowingLightFallback(variant, elapsedSeconds / (variant.cycleMillis / 1000f) * (Math.PI * 2).toFloat())
    }
}

@androidx.annotation.RequiresApi(33)
@Composable
private fun FlowingLightShaderBackground(variant: FlowingLightStyle, elapsedSeconds: Float) {
    val shader = remember { RuntimeShader(FLOW_FRAGMENT_SHADER) }
    val paint = remember { FrameworkPaint() }
    Canvas(Modifier.fillMaxSize()) {
        shader.setFloatUniform("u_resolution", size.width, size.height)
        shader.setFloatUniform("u_time", elapsedSeconds * variant.webSpeed)
        shader.setFloatUniform("u_distortion", variant.distortion)
        shader.setFloatUniform("u_swirl", variant.swirl)
        shader.setFloatUniform("u_scale", variant.scale)
        shader.setFloatUniform("u_grainMixer", variant.grainMixer)
        shader.setFloatUniform("u_grainOverlay", variant.grainOverlay)
        variant.colors[0].let { color -> shader.setFloatUniform("u_color0", color.red, color.green, color.blue, color.alpha) }
        variant.colors[1].let { color -> shader.setFloatUniform("u_color1", color.red, color.green, color.blue, color.alpha) }
        variant.colors[2].let { color -> shader.setFloatUniform("u_color2", color.red, color.green, color.blue, color.alpha) }
        variant.colors[3].let { color -> shader.setFloatUniform("u_color3", color.red, color.green, color.blue, color.alpha) }
        drawIntoCanvas { canvas ->
            paint.shader = shader
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

@Composable
private fun FlowingLightFallback(variant: FlowingLightStyle, phase: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(variant.colors.first())
        val radius = size.maxDimension * (.7f + variant.scale * .12f)
        variant.colors.forEachIndexed { index, color ->
            val speed = .72f + index * .17f
            val angle = phase * speed + index * 1.61f
            val center = Offset(
                x = size.width * (.5f + .46f * cos(angle.toDouble()).toFloat()),
                y = size.height * (.5f + .42f * sin((angle * 1.13f).toDouble()).toFloat()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = .82f),
                        color.copy(alpha = .34f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        val progress = phase / (Math.PI * 2).toFloat()
        val sweepX = -size.width + progress * size.width * 3f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = .11f), Color.Transparent),
                start = Offset(sweepX - size.width * .55f, size.height),
                end = Offset(sweepX + size.width * .55f, 0f),
            ),
        )
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .28f))))
    }
}
