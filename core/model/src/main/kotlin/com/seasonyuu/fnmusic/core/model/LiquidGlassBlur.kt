package com.seasonyuu.fnmusic.core.model

/** Persisted slider bounds. Renderers resolve this value into separate blur and tint parameters. */
object LiquidGlassBlur {
    const val Minimum = 0.1f
    const val Default = 1f
    const val Maximum = 2f

    fun normalize(value: Float): Float =
        if (value.isFinite()) value.coerceIn(Minimum, Maximum) else Default

    fun fromSlider(position: Float): Float {
        val p = if (position.isFinite()) position.coerceIn(0f, 1f) else 0.5f
        return if (p <= 0.5f) Minimum + (Default - Minimum) * p * 2f
        else Default + (Maximum - Default) * (p - 0.5f) * 2f
    }

    fun toSlider(multiplier: Float): Float {
        val value = normalize(multiplier)
        return if (value <= Default) (value - Minimum) / (Default - Minimum) * 0.5f
        else 0.5f + (value - Default) / (Maximum - Default) * 0.5f
    }
}
