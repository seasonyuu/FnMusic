package com.seasonyuu.fnmusic.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicBottomBarScrollBehaviorTest {
    @Test
    fun upwardContentScrollCollapsesAndReverseScrollExpands() {
        val distance = 100f
        val collapsedHalfway = DynamicBottomBarScrollBehavior.updateProgress(1f, -50f, distance)
        val expandedAgain = DynamicBottomBarScrollBehavior.updateProgress(collapsedHalfway, 25f, distance)

        assertEquals(0.5f, collapsedHalfway, 0.001f)
        assertEquals(0.75f, expandedAgain, 0.001f)
    }

    @Test
    fun progressNeverLeavesUnitRange() {
        assertEquals(0f, DynamicBottomBarScrollBehavior.updateProgress(0.2f, -1_000f, 100f), 0.001f)
        assertEquals(1f, DynamicBottomBarScrollBehavior.updateProgress(0.8f, 1_000f, 100f), 0.001f)
    }

    @Test
    fun lowVelocitySettlesToNearestEndpoint() {
        assertEquals(0f, DynamicBottomBarScrollBehavior.settleTarget(0.49f, 0f), 0.001f)
        assertEquals(1f, DynamicBottomBarScrollBehavior.settleTarget(0.5f, 0f), 0.001f)
    }

    @Test
    fun flingDirectionOverridesProgress() {
        assertEquals(0f, DynamicBottomBarScrollBehavior.settleTarget(0.9f, -1_500f), 0.001f)
        assertEquals(1f, DynamicBottomBarScrollBehavior.settleTarget(0.1f, 1_500f), 0.001f)
    }
}
