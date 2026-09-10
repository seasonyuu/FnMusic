package com.seasonyuu.fnmusic.core.model

/** Persisted together so switching glass off never discards its intensity. */
data class LiquidGlassPreference(
    val multiplier: Float = LiquidGlassBlur.Default,
    val enabled: Boolean = true,
) {
    fun normalized() = copy(multiplier = LiquidGlassBlur.normalize(multiplier))
}
