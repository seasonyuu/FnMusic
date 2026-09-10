package com.seasonyuu.fnmusic.feature.music

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

internal val QueueHistoryTitleHeight = 52.dp
internal val QueueHistoryRowHeight = 72.dp
internal val QueueHistoryGap = 20.dp

/** Coordinates are in the list's unpinned content space, never sticky-header coordinates. */
internal data class QueueSnapGeometry(
    val position: Float,
    val historyEnd: Float,
    val currentHeight: Float,
    val viewport: Float,
    val hasHistory: Boolean,
) {
    val anchors: List<Float>
        get() = buildList {
            if (hasHistory) add(0f)
            add(historyEnd)
            if (currentHeight > 0f) add(historyEnd + currentHeight)
        }
}

/** Only capture a natural landing near a section boundary; songs never snap individually. */
internal fun queueSnapTarget(
    geometry: QueueSnapGeometry,
    projectedDistance: Float,
    dragStart: Float?,
    density: Float,
): Float? {
    val projected = (geometry.position + projectedDistance).coerceAtLeast(0f)
    val baseRadius = minOf(144f * density, geometry.viewport * 0.22f)
    if (baseRadius <= 0f) return null
    // A history section that fits in the viewport has two useful ends, not a dead zone
    // between them. Do not extend this capture range into upcoming songs.
    val radius = if (projected <= geometry.historyEnd && geometry.historyEnd <= geometry.viewport)
        maxOf(baseRadius, geometry.historyEnd * 0.55f) else baseRadius
    val anchors = geometry.anchors
    // A small hysteresis around the midpoint makes exploratory drags return predictably.
    val origin = dragStart?.let { start -> anchors.firstOrNull { abs(it - start) <= 2f * density } }
    if (origin != null) {
        val neighbor = if (projected > origin) anchors.firstOrNull { it > origin }
            else anchors.lastOrNull { it < origin }
        if (neighbor != null && abs(projected - origin) < abs(neighbor - origin) * 0.55f &&
            abs(projected - origin) <= radius) return origin
    }
    return anchors.minByOrNull { abs(it - projected) }
        ?.takeIf { abs(it - projected) <= radius }
}

@Composable
internal fun rememberQueueSnapBehavior(
    listState: LazyListState,
    historyCount: Int,
    currentHeightPx: Int,
    contentKey: Any,
    enabled: Boolean,
): FlingBehavior {
    val density = LocalDensity.current
    val titleHeight = with(density) { QueueHistoryTitleHeight.roundToPx().toFloat() }
    val rowHeight = with(density) { QueueHistoryRowHeight.roundToPx().toFloat() }
    val gap = with(density) { QueueHistoryGap.roundToPx().toFloat() }
    val currentIndex = if (historyCount == 0) 0 else historyCount + 2
    val historyEnd = if (historyCount == 0) 0f else titleHeight + rowHeight * historyCount + gap
    val geometry = rememberUpdatedState<() -> QueueSnapGeometry?> {
        val index = listState.firstVisibleItemIndex
        // Beyond the mode header we are browsing songs freely, not settling a section.
        val before = when {
            index == currentIndex -> historyEnd
            index == currentIndex + 1 -> historyEnd + currentHeightPx
            historyCount > 0 && index == 0 -> 0f
            historyCount > 0 && index <= historyCount -> titleHeight + (index - 1) * rowHeight
            historyCount > 0 && index == historyCount + 1 -> titleHeight + historyCount * rowHeight
            else -> null
        }
        before?.let {
            QueueSnapGeometry(
                it + listState.firstVisibleItemScrollOffset,
                historyEnd, currentHeightPx.toFloat(),
                listState.layoutInfo.viewportSize.height.toFloat(), historyCount > 0,
            )
        }
    }
    val latestEnabled = rememberUpdatedState(enabled)
    val latestKey = rememberUpdatedState(listOf(contentKey, historyCount, currentHeightPx, density.density,
        listState.layoutInfo.viewportSize))
    val decay = rememberSplineBasedDecay<Float>()
    val default = ScrollableDefaults.flingBehavior()
    val behavior = remember(listState, decay, default, density.density) {
        QueueSnapFlingBehavior(
            decay, default, density.density,
            geometry = { geometry.value() },
            enabled = { latestEnabled.value },
            contentKey = { latestKey.value },
        )
    }
    LaunchedEffect(listState, behavior) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) behavior.dragStart = geometry.value()?.position
            if (interaction is DragInteraction.Cancel) behavior.dragStart = null
        }
    }
    return behavior
}

private class QueueSnapFlingBehavior(
    private val decay: DecayAnimationSpec<Float>,
    private val default: FlingBehavior,
    private val density: Float,
    private val geometry: () -> QueueSnapGeometry?,
    private val enabled: () -> Boolean,
    private val contentKey: () -> Any,
) : FlingBehavior {
    var dragStart: Float? = null

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val snapshot = geometry()
        val start = dragStart
        dragStart = null
        val target = if (enabled() && snapshot != null) queueSnapTarget(
            snapshot, decay.calculateTargetValue(0f, initialVelocity), start, density,
        ) else null
        if (target == null || snapshot == null) return with(default) { performFling(initialVelocity) }
        val key = contentKey()
        val distance = target - snapshot.position
        if (abs(distance) < 0.5f) return 0f
        var consumed = 0f
        var velocity = initialVelocity
        var blocked = false
        fun advance(value: Float): Boolean {
            if (!enabled() || key != contentKey()) {
                blocked = true
                return false
            }
            val requested = value - consumed
            val actual = scrollBy(requested)
            consumed += actual
            if (abs(requested - actual) > 0.5f) blocked = true
            return !blocked
        }
        // Long approaches retain their native decay, then enter the spring without a pause.
        if (abs(distance) > 144f * density && initialVelocity.sign == distance.sign) {
            AnimationState(0f, initialVelocity).animateDecay(decay) {
                velocity = this.velocity
                val bounded = if (distance > 0f) value.coerceAtMost(distance) else value.coerceAtLeast(distance)
                if (!advance(bounded) || abs(distance - consumed) <= 80f * density) cancelAnimation()
            }
        }
        if (!blocked) {
            // A release away from the chosen anchor must not throw the list farther away first.
            val remaining = distance - consumed
            val toward = if (velocity.sign == remaining.sign)
                velocity.coerceIn(-abs(remaining) * 20f, abs(remaining) * 20f) else 0f
            AnimationState(consumed, toward).animateTo(
                distance, spring(dampingRatio = 1f, stiffness = 400f),
            ) {
                velocity = this.velocity
                if (!advance(value)) cancelAnimation()
            }
        }
        return if (blocked) velocity else 0f
    }
}
