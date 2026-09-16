package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.math.*

// Physics adapted from liquid_glass_widgets @ 097dea6993e474d5d04db33a4f6f2ea31dd40ac1.
// Copyright (c) 2024–2026 Sebastian Degenaar. See third_party/licenses/liquid-glass-widgets.txt.
internal data class MenuMorph(
    val path: Float,
    val size: Float,
    val anchor: Float,
    val blend: Float,
    val scale: Float,
)

internal fun menuMorph(raw: Float): MenuMorph {
    val t = raw.coerceIn(0f, 1f)
    val under = min(raw, 0f)
    val v = t - 1f
    val path = if (t == 0f) under else v * v * (3.5f * v + 2.5f) + 1f + under
    val size = menuCubic(t, .35f, .91f, .33f, .97f) + under
    return MenuMorph(
        path,
        size,
        (1f - t / .4f).coerceIn(0f, 1f),
        (abs(path - size) * 150f).coerceIn(0f, 28f),
        if (raw > 1f) 1f + (raw - 1f) * .1f else if (raw < 0f) 1f + raw * .55f else 1f,
    )
}

internal fun menuCubic(t: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (t <= 0f || t >= 1f) return t.coerceIn(0f, 1f)
    fun curve(u: Float, a: Float, b: Float) =
        3f * (1f - u).pow(2) * u * a + 3f * (1f - u) * u * u * b + u * u * u
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val u = (lo + hi) / 2
        if (curve(u, x1, x2) < t) lo = u else hi = u
    }
    return curve((lo + hi) / 2, y1, y2)
}

internal fun menuDestination(anchor: Rect, desired: Size, safe: Rect): Rect {
    val w = desired.width.coerceIn(0f, safe.width.coerceAtLeast(0f))
    val h = desired.height.coerceIn(0f, safe.height.coerceAtLeast(0f))
    val x = if (anchor.center.x > safe.center.x) anchor.right - w else anchor.left
    val y =
        if (safe.bottom - anchor.top < h && anchor.bottom - safe.top >= h) anchor.bottom - h
        else anchor.top
    return Rect(
        Offset(
            x.coerceIn(safe.left, max(safe.left, safe.right - w)),
            y.coerceIn(safe.top, max(safe.top, safe.bottom - h)),
        ),
        Size(w, h),
    )
}

internal data class MenuBlobs(
    val anchor: Rect,
    val body: Rect,
    val anchorRadius: Float,
    val radius: Float,
    val blend: Float,
)

internal fun menuBlobs(anchor: Rect, target: Rect, raw: Float, density: Float, startRadius: Float = min(anchor.width, anchor.height) / 2): MenuBlobs {
    val m = menuMorph(raw)
    val delta = target.center - anchor.center
    val center = anchor.center + delta * m.path
    val w = max(1f, (anchor.width + (target.width - anchor.width) * m.size) * m.scale)
    val h = max(1f, (anchor.height + (target.height - anchor.height) * m.size) * m.scale)
    val half = Offset(w / 2, h / 2)
    val aCenter = anchor.center + delta * min(raw, 0f)
    val aHalf = Offset(anchor.width / 2, anchor.height / 2) * m.anchor
    val rT = if (m.size <= 0f) 0f else 2f.pow(10f * (m.size.coerceIn(0f, 1f) - 1f))
    val maxR = min(w, h) / 2
    val capsule = startRadius >= min(anchor.width, anchor.height) / 2
    val initialR = startRadius.coerceIn(0f, min(anchor.width, anchor.height) / 2)
    return MenuBlobs(
        Rect(aCenter - aHalf, aCenter + aHalf),
        Rect(center - half, center + half),
        min(initialR * m.anchor, min(aHalf.x, aHalf.y)),
        ((if (capsule) maxR else min(initialR * m.scale, maxR)) * (1f - rT) + min(32f * density, maxR) * rT),
        m.blend * density,
    )
}

/** Visibility follows geometry, including the spring's closing undershoot. */
internal fun menuForegroundAlpha(anchor: Rect, body: Rect): Float {
    if (anchor.isEmpty || body.isEmpty) return 0f
    val error = maxOf(
        abs(body.center.x - anchor.center.x) / anchor.width,
        abs(body.center.y - anchor.center.y) / anchor.height,
        abs(body.width - anchor.width) / anchor.width,
        abs(body.height - anchor.height) / anchor.height,
    )
    val t = ((.35f - error) / .30f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Independent menus never cover their trigger; the roomier side becomes scrollable. */
internal fun menuDetachedDestination(anchor: Rect, desired: Size, safe: Rect, gap: Float): Rect {
    val belowStart = (anchor.bottom + gap).coerceIn(safe.top, safe.bottom)
    val aboveEnd = (anchor.top - gap).coerceIn(safe.top, safe.bottom)
    val below = safe.bottom - belowStart
    val above = aboveEnd - safe.top
    val useBelow = below >= desired.height || below >= above
    val available = if (useBelow) below else above
    val height = min(desired.height, available)
    val width = min(desired.width, safe.width).coerceAtLeast(0f)
    val left = (if (anchor.center.x > safe.center.x) anchor.right - width else anchor.left)
        .coerceIn(safe.left, max(safe.left, safe.right - width))
    val top = if (useBelow) belowStart else aboveEnd - height
    return Rect(Offset(left, top.coerceIn(safe.top, max(safe.top, safe.bottom - height))), Size(width, height))
}
