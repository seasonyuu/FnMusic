package com.seasonyuu.fnmusic.core.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicBottomBarGeometryTest {
    private val width = 1080f
    private val density = 2.75f

    @Test
    fun endpointsMatchExpandedAndCompactLayouts() {
        val compact = DynamicBottomBarGeometryCalculator.calculate(width, density, 0f)
        val expanded = DynamicBottomBarGeometryCalculator.calculate(width, density, 1f)

        assertEquals(50f * density, compact.player.height, 0.001f)
        assertEquals(50f * density, expanded.player.height, 0.001f)
        assertEquals(50f * density, compact.primaryTabs.width, 0.001f)
        assertEquals(64f * density, expanded.primaryTabs.height, 0.001f)
        assertEquals(32f * density, compact.cover.height, 0.001f)
        assertEquals(32f * density, expanded.cover.height, 0.001f)
        assertEquals(
            DynamicBottomBarGeometryCalculator.ContainerHeightDp * density,
            expanded.contentHeight,
            0.001f,
        )
        assertTrue(expanded.primaryTabs.width > compact.primaryTabs.width)
        assertEquals(0f, compact.primaryItemsAlpha, 0.001f)
        assertEquals(1f, expanded.primaryItemsAlpha, 0.001f)
    }

    @Test
    fun geometryStaysInsideContainerAtRepresentativeProgressValues() {
        listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { progress ->
            val geometry = DynamicBottomBarGeometryCalculator.calculate(width, density, progress)
            listOf(geometry.player, geometry.primaryTabs, geometry.search, geometry.cover).forEach { rect ->
                assertTrue("positive width at $progress", rect.width > 0f)
                assertTrue("positive height at $progress", rect.height > 0f)
                assertTrue("left bound at $progress", rect.left >= 0f)
                assertTrue("right bound at $progress", rect.right <= width + 0.001f)
                assertTrue("top bound at $progress", rect.top >= 0f)
                assertTrue("bottom bound at $progress", rect.bottom <= geometry.contentHeight + 0.001f)
            }
        }
    }

    @Test
    fun playerMovesAndGrowsMonotonicallyWhileExpanding() {
        val samples = (0..20).map {
            DynamicBottomBarGeometryCalculator.calculate(width, density, it / 20f)
        }

        samples.zipWithNext().forEach { (before, after) ->
            assertTrue(after.player.top <= before.player.top + 0.001f)
            assertTrue(after.player.width >= before.player.width - 0.001f)
            assertTrue(after.player.height >= before.player.height - 0.001f)
            assertTrue(after.primaryTabs.width >= before.primaryTabs.width - 0.001f)
        }
    }

    @Test
    fun adjacentFramesDoNotJump() {
        val samples = (0..100).map {
            DynamicBottomBarGeometryCalculator.calculate(width, density, it / 100f)
        }
        val maximumExpectedStep = 20f

        samples.zipWithNext().forEach { (before, after) ->
            assertTrue(kotlin.math.abs(after.player.left - before.player.left) < maximumExpectedStep)
            assertTrue(kotlin.math.abs(after.player.top - before.player.top) < maximumExpectedStep)
            assertTrue(kotlin.math.abs(after.primaryTabs.width - before.primaryTabs.width) < maximumExpectedStep)
        }
    }

    @Test
    fun progressIsClamped() {
        val below = DynamicBottomBarGeometryCalculator.calculate(width, density, -1f)
        val compact = DynamicBottomBarGeometryCalculator.calculate(width, density, 0f)
        val above = DynamicBottomBarGeometryCalculator.calculate(width, density, 2f)
        val expanded = DynamicBottomBarGeometryCalculator.calculate(width, density, 1f)

        assertEquals(compact, below)
        assertEquals(expanded, above)
    }
}
