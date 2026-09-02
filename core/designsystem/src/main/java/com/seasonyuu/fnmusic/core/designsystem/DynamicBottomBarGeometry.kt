package com.seasonyuu.fnmusic.core.designsystem

import kotlin.math.max

data class FloatRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    val centerX: Float get() = left + width / 2f
    val centerY: Float get() = top + height / 2f
}

data class DynamicBottomBarGeometry(
    val contentHeight: Float,
    val player: FloatRect,
    val primaryTabs: FloatRect,
    val search: FloatRect,
    val cover: FloatRect,
    val primaryItemsAlpha: Float,
    val primaryLabelsAlpha: Float,
    val playerSecondaryAlpha: Float,
)

object DynamicBottomBarGeometryCalculator {
    const val ContainerHeightDp = 122

    fun calculate(
        widthPx: Float,
        density: Float,
        expansionProgress: Float,
    ): DynamicBottomBarGeometry {
        require(widthPx > 0f) { "widthPx must be positive" }
        require(density > 0f) { "density must be positive" }

        val progress = expansionProgress.coerceIn(0f, 1f)
        fun dp(value: Float) = value * density

        val horizontalPadding = dp(16f)
        val gap = dp(8f)
        val expandedPlayerHeight = dp(50f)
        val compactHeight = dp(50f)
        val expandedTabsTop = expandedPlayerHeight + gap
        val expandedSideButtonSize = dp(64f)
        val contentHeight = expandedTabsTop + expandedSideButtonSize
        val compactRowTop = contentHeight - compactHeight

        val expandedPlayer = FloatRect(
            left = horizontalPadding,
            top = 0f,
            width = max(0f, widthPx - horizontalPadding * 2f),
            height = expandedPlayerHeight,
        )
        val compactPlayer = FloatRect(
            left = horizontalPadding + compactHeight + gap,
            top = compactRowTop,
            width = max(0f, widthPx - (horizontalPadding + compactHeight + gap) * 2f),
            height = compactHeight,
        )

        val expandedSearch = FloatRect(
            left = widthPx - horizontalPadding - expandedSideButtonSize,
            top = expandedTabsTop,
            width = expandedSideButtonSize,
            height = expandedSideButtonSize,
        )
        val compactSearch = FloatRect(
            left = widthPx - horizontalPadding - compactHeight,
            top = compactRowTop,
            width = compactHeight,
            height = compactHeight,
        )

        val expandedPrimaryTabs = FloatRect(
            left = horizontalPadding,
            top = expandedTabsTop,
            width = max(0f, expandedSearch.left - gap - horizontalPadding),
            height = dp(64f),
        )
        val compactPrimaryTabs = FloatRect(
            left = horizontalPadding,
            top = compactRowTop,
            width = compactHeight,
            height = compactHeight,
        )

        val player = lerp(compactPlayer, expandedPlayer, progress)
        val primaryTabs = lerp(compactPrimaryTabs, expandedPrimaryTabs, progress)
        val search = lerp(compactSearch, expandedSearch, progress)
        val coverSize = dp(32f)
        val cover = FloatRect(
            left = player.left + dp(16f),
            top = player.top + (player.height - coverSize) / 2f,
            width = coverSize,
            height = coverSize,
        )

        return DynamicBottomBarGeometry(
            contentHeight = contentHeight,
            player = player,
            primaryTabs = primaryTabs,
            search = search,
            cover = cover,
            primaryItemsAlpha = interval(progress, 0.18f, 0.62f),
            primaryLabelsAlpha = interval(progress, 0.38f, 0.82f),
            playerSecondaryAlpha = interval(progress, 0.28f, 0.72f),
        )
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float =
        start + (stop - start) * fraction

    private fun lerp(start: FloatRect, stop: FloatRect, fraction: Float): FloatRect = FloatRect(
        left = lerp(start.left, stop.left, fraction),
        top = lerp(start.top, stop.top, fraction),
        width = lerp(start.width, stop.width, fraction),
        height = lerp(start.height, stop.height, fraction),
    )

    private fun interval(value: Float, start: Float, end: Float): Float =
        ((value - start) / (end - start)).coerceIn(0f, 1f)
}
