package com.seasonyuu.fnmusic.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicBottomBarScrollBehaviorTest {
    @Test
    fun upwardScrollTargetsFullyCollapsedRegardlessOfDistance() {
        listOf(-0.1f, -50f, -1_000f).forEach { delta ->
            assertEquals(0f, DynamicBottomBarScrollBehavior.targetForScroll(1f, delta), 0f)
        }
    }

    @Test
    fun downwardScrollTargetsFullyExpandedRegardlessOfDistance() {
        listOf(0.1f, 50f, 1_000f).forEach { delta ->
            assertEquals(1f, DynamicBottomBarScrollBehavior.targetForScroll(0f, delta), 0f)
        }
    }

    @Test
    fun repeatedDirectionPreservesTargetAndReversalChangesIt() {
        val collapsed = DynamicBottomBarScrollBehavior.targetForScroll(1f, -10f)
        assertEquals(collapsed, DynamicBottomBarScrollBehavior.targetForScroll(collapsed, -5f), 0f)
        val expanded = DynamicBottomBarScrollBehavior.targetForScroll(collapsed, 5f)
        assertEquals(1f, expanded, 0f)
        assertEquals(expanded, DynamicBottomBarScrollBehavior.targetForScroll(expanded, 10f), 0f)
    }

    @Test
    fun zeroScrollKeepsCurrentTarget() {
        assertEquals(0f, DynamicBottomBarScrollBehavior.targetForScroll(0f, 0f), 0f)
        assertEquals(1f, DynamicBottomBarScrollBehavior.targetForScroll(1f, 0f), 0f)
    }
}
