package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.ui.geometry.*
import org.junit.Assert.*
import org.junit.Test

class LiquidMenuGeometryTest {
    @Test
    fun matchesPinnedUpstreamSamples() {
        val lines =
            javaClass
                .getResourceAsStream("/liquid-menu-upstream.csv")!!
                .bufferedReader()
                .readLines()
                .drop(1)
        lines.forEach { line ->
            val v = line.split(',').map { it.toFloat() }
            val m = menuMorph(v[0])
            listOf(m.path, m.size, m.anchor, m.blend, m.scale).forEachIndexed { i, value ->
                assertEquals("raw=${v[0]}, field=$i", v[i + 1], value, .0001f)
            }
        }
    }

    @Test
    fun targetStaysInsideSafeAreaInAllCornersAndSmallWindows() {
        for (size in listOf(Size(320f, 640f), Size(100f, 120f), Size(800f, 320f))) {
            val safe = Rect(Offset(12f, 24f), Size(size.width - 24f, size.height - 36f))
            for (x in listOf(0f, size.width - 48)) for (y in listOf(0f, size.height - 48)) {
                val target =
                    menuDestination(Rect(Offset(x, y), Size(48f, 48f)), Size(200f, 240f), safe)
                assertTrue(target.left >= safe.left && target.top >= safe.top)
                assertTrue(target.right <= safe.right && target.bottom <= safe.bottom)
            }
        }
    }

    @Test
    fun allAnimationFramesHaveFinitePositiveGeometry() {
        val anchor = Rect(260f, 32f, 308f, 80f)
        val target = Rect(108f, 32f, 308f, 272f)
        for (i in -100..1100) {
            val b = menuBlobs(anchor, target, i / 1000f, 1f)
            assertTrue(b.body.width > 0 && b.body.height > 0)
            assertTrue(b.body.left.isFinite() && b.radius.isFinite())
            assertTrue(b.radius >= 0 && b.radius <= minOf(b.body.width, b.body.height) / 2 + .001f)
        }
    }

    @Test
    fun startsAtTriggerAndSettlesAtMenu() {
        val a = Rect(260f, 32f, 308f, 80f)
        val t = Rect(108f, 32f, 308f, 272f)
        assertEquals(a, menuBlobs(a, t, 0f, 1f).body)
        assertEquals(t, menuBlobs(a, t, 1f, 1f).body)
        assertEquals(0f, menuBlobs(a, t, 1f, 1f).anchor.width, 0f)
    }
    @Test
    fun explicitRoundedRectAndLongCapsuleRespectTheirStartingContours() {
        for (a in listOf(Rect(20f, 20f, 180f, 68f), Rect(20f, 20f, 100f, 100f))) {
            val target = Rect(20f, 20f, 220f, 260f)
            for (radius in listOf(0f, 8f, minOf(a.width, a.height) / 2)) {
                assertEquals(radius, menuBlobs(a, target, 0f, 1f, radius).radius, .0001f)
                assertEquals(32f, menuBlobs(a, target, 1f, 1f, radius).radius, .0001f)
                for (i in -100..1100) {
                    val b = menuBlobs(a, target, i / 1000f, 1f, radius)
                    assertTrue(b.radius.isFinite() && b.radius >= 0f)
                    assertTrue(b.radius <= minOf(b.body.width, b.body.height) / 2 + .001f)
                }
            }
        }
    }

    @Test
    fun foregroundFollowsGeometricErrorInBothDirectionsIncludingUndershoot() {
        val a = Rect(0f, 0f, 100f, 50f)
        assertEquals(1f, menuForegroundAlpha(a, a), 0f)
        assertEquals(1f, menuForegroundAlpha(a, a.translate(Offset(5f, 0f))), .0001f)
        assertEquals(.5f, menuForegroundAlpha(a, a.translate(Offset(20f, 0f))), .0001f)
        assertEquals(0f, menuForegroundAlpha(a, a.translate(Offset(35f, 0f))), .0001f)
        assertEquals(0f, menuForegroundAlpha(Rect.Zero, a), 0f)
        assertEquals(0f, menuForegroundAlpha(a, Rect.Zero), 0f)
        val target = Rect(0f, 0f, 200f, 240f)
        for (raw in listOf(-.02f, 0f, .01f, .1f, .5f, 1f, 1.05f)) {
            val alpha = menuForegroundAlpha(a, menuBlobs(a, target, raw, 1f).body)
            assertTrue(alpha.isFinite() && alpha in 0f..1f)
        }
    }
    @Test
    fun detachedMenusChooseAvailableSpaceWithoutCoveringTheAnchor() {
        val safe = Rect(12f, 24f, 308f, 616f)
        for (y in listOf(-40f, 24f, 300f, 568f, 650f)) {
            val a = Rect(140f, y, 188f, y + 48f)
            val target = menuDetachedDestination(a, Size(200f, 400f), safe, 8f)
            assertTrue(target.top >= safe.top && target.bottom <= safe.bottom)
            assertTrue(target.left >= safe.left && target.right <= safe.right)
            assertTrue(target.bottom <= a.top - 8f || target.top >= a.bottom + 8f)
        }
    }
}
