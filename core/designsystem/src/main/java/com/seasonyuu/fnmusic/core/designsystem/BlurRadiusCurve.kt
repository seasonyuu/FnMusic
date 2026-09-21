package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.ui.unit.Dp

/** Position is measured from the top of the shared blur area, including the status bar. */
data class BlurRadiusStop(val y: Dp, val radius: Dp)

internal data class BlurRadiusSegment(
    val startY: Float,
    val endY: Float,
    val startRadius: Float,
    val endRadius: Float,
    val startSlope: Float,
    val endSlope: Float,
) {
    fun radiusAt(y: Float): Float {
        val distance = endY - startY
        val t = ((y - startY) / distance).coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * startRadius +
            (t3 - 2 * t2 + t) * distance * startSlope +
            (-2 * t3 + 3 * t2) * endRadius +
            (t3 - t2) * distance * endSlope
    }
}

/** Monotone cubic interpolation: continuous slopes, no radius overshoot between stops. */
internal fun blurRadiusSegments(stops: List<BlurRadiusStop>): List<BlurRadiusSegment> {
    require(stops.size >= 2)
    require(stops.all { it.y.value.isFinite() && it.radius.value.isFinite() && it.radius.value >= 0f })
    require(stops.zipWithNext().all { (a, b) -> b.y > a.y && b.radius <= a.radius })
    val widths = stops.zipWithNext { a, b -> (b.y - a.y).value }
    val slopes = stops.zipWithNext { a, b -> (b.radius - a.radius).value / (b.y - a.y).value }
    val tangents = FloatArray(stops.size)
    tangents[0] = slopes.first()
    for (i in 1 until stops.lastIndex) {
        val before = slopes[i - 1]
        val after = slopes[i]
        if (before * after > 0f) {
            val w1 = 2 * widths[i] + widths[i - 1]
            val w2 = widths[i] + 2 * widths[i - 1]
            tangents[i] = (w1 + w2) / (w1 / before + w2 / after)
        }
    }
    // Reach the unblurred scene with zero slope rather than an abrupt cutoff.
    tangents[stops.lastIndex] = 0f
    return stops.zipWithNext { a, b -> a to b }.mapIndexed { i, (a, b) ->
        BlurRadiusSegment(a.y.value, b.y.value, a.radius.value, b.radius.value, tangents[i], tangents[i + 1])
    }
}

internal fun List<BlurRadiusSegment>.radiusAt(y: Float): Float =
    firstOrNull { y <= it.endY }?.radiusAt(y) ?: last().endRadius
