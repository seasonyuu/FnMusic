package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.seasonyuu.fnmusic.core.model.LiquidGlassBlur

/** Experimental clear/default/tinted material, independently resolving blur, brightness, and surface opacity. */
@Immutable
data class LiquidGlassMaterial(val blurScale: Float, val surfaceAlpha: Float, val brightness: Float) {
    val surfaceColor: Color get() = Color(0xFF14121B).copy(alpha = surfaceAlpha)
}

fun resolveLiquidGlassMaterial(setting: Float): LiquidGlassMaterial {
    val position = LiquidGlassBlur.toSlider(setting)
    val alpha = if (position <= 0.5f) {
        0.25f + (0.55f - 0.25f) * (position * 2f)
    } else {
        0.55f + (0.60f - 0.55f) * ((position - 0.5f) * 2f)
    }
    return LiquidGlassMaterial(
        // With a 1dp base, blur ramps from 0dp through 1dp at default to 6dp at tinted.
        blurScale = if (position <= 0.5f) position * 2f else 1f + (position - 0.5f) * 10f,
        surfaceAlpha = alpha,
        brightness = 0f,
    )
}

@Composable
fun currentLiquidGlassMaterial(): LiquidGlassMaterial = resolveLiquidGlassMaterial(LocalLiquidGlassBlur.current)
