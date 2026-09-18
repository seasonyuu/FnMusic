package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.LiquidGlassBlur

/** Experimental clear/default/tinted material, independently resolving blur, brightness, and surface opacity. */
@Immutable
data class LiquidGlassMaterial(val blurScale: Float, val surfaceAlpha: Float, val brightness: Float, val enabled: Boolean = true, val baseSurface: Color = Color(0xFF14121B)) {
    val surfaceColor: Color get() = baseSurface.copy(alpha = surfaceAlpha)
}

fun resolveLiquidGlassMaterial(setting: Float, enabled: Boolean = true): LiquidGlassMaterial {
    if (!enabled) return LiquidGlassMaterial(blurScale = 0f, surfaceAlpha = 0.60f, brightness = 0f, enabled = false)
    val position = LiquidGlassBlur.toSlider(setting)
    val alphaSlots = Triple(0.45f, 0.55f, 0.75f)
    val blurSlots = Triple(1f, 3f, 6f)
    val alpha = if (position <= 0.5f) {
        alphaSlots.first + (alphaSlots.second - alphaSlots.first) * (position * 2f)
    } else {
        alphaSlots.second + (alphaSlots.third - alphaSlots.second) * ((position - 0.5f) * 2f)
    }
    val blurScale = if (position <= 0.5f) {
        blurSlots.first + (blurSlots.second - blurSlots.first) * (position * 2f)
    } else {
        blurSlots.second + (blurSlots.third - blurSlots.second) * ((position - 0.5f) * 2f)
    }
    return LiquidGlassMaterial(
        // With a 1dp base, blur ramps from 1dp through 3dp at default to 6dp at tinted.
        blurScale = blurScale,
        surfaceAlpha = alpha,
        brightness = 0f,
    )
}

@Composable
fun currentLiquidGlassMaterial(): LiquidGlassMaterial {
    val material = resolveLiquidGlassMaterial(LocalLiquidGlassBlur.current, LocalLiquidGlassEnabled.current)
    val surface = FnNavigationSurface
    return material.copy(
        baseSurface = surface,
        // Light glass needs a stronger neutral veil so scrolling text cannot compete
        // with the controls. Disabled glass retains a translucent, theme-matched surface.
        surfaceAlpha = when {
            !material.enabled -> 0.75f
            surface.luminance() > .5f -> material.surfaceAlpha + (1f - material.surfaceAlpha) * .4f
            else -> material.surfaceAlpha
        },
    )
}

/** Shared compact-control optics used by buttons and morphing menus. */
internal object LiquidControlOptics {
    const val Saturation = 1.5f
    val RefractionHeight = 4.dp
    val RefractionAmount = 6.dp
    val Highlight = com.kyant.backdrop.highlight.Highlight.Default
}
