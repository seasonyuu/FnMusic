package com.seasonyuu.fnmusic.feature.music

import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Test

class PlayerAdaptiveLayoutTest {
    @Test
    fun breakpointUsesAvailableWidth() {
        assertFalse(playerLayoutGeometry(839.dp, 800.dp).wide)
        assertTrue(playerLayoutGeometry(840.dp, 600.dp).wide)
        assertFalse(playerLayoutGeometry(840.dp - 24.dp, 600.dp).wide)
    }

    @Test
    fun landscapePagesOnlyApplyToShortWideWindows() {
        assertTrue(isShortLandscapePlayer(850.dp, 411.dp))
        assertTrue(isShortLandscapePlayer(600.dp, 499.dp))
        assertFalse(isShortLandscapePlayer(600.dp, 500.dp))
        assertFalse(isShortLandscapePlayer(599.dp, 360.dp))
        assertFalse(isShortLandscapePlayer(390.dp, 844.dp))
        assertFalse(isShortLandscapePlayer(1024.dp, 768.dp))
        val layout = playerLayoutGeometry(905.dp, 360.dp)
        assertEquals(284.dp, layout.playerWidth)
        assertTrue(layout.detailWidth >= 400.dp)
    }

    @Test
    fun immersiveSpaceKeepsTheSelectedLandscapeGeometry() {
        val layout = playerLayoutGeometry(850.dp, 520.dp, shortLandscape = true)
        assertTrue(layout.short)
        assertEquals(layout.contentStart + layout.playerWidth + 32.dp, layout.detailStart)
        assertEquals(layout.contentStart, 850.dp - layout.detailStart - layout.detailWidth)
    }

    @Test
    fun shortLandscapeArtworkUsesHeightInsteadOfColumnWidth() {
        listOf(800.dp, 850.dp, 1024.dp).forEach { width ->
            val layout = playerLayoutGeometry(width, 411.dp)
            assertEquals(335.dp, layout.playerWidth)
            assertEquals(layout.contentStart + 335.dp + 32.dp, layout.detailStart)
        }
    }

    @Test
    fun panesFitReferenceWindows() {
        listOf(390 to 844, 800 to 360, 840 to 600, 1024 to 768, 1280 to 800, 2000 to 1000)
            .forEach { (w, h) ->
                val layout = playerLayoutGeometry(w.dp, h.dp)
                assertTrue(layout.contentStart >= 0.dp)
                assertTrue(layout.playerWidth > 0.dp)
                assertTrue(layout.detailStart + layout.detailWidth <= w.dp)
                if (layout.wide) {
                    assertEquals(
                        layout.contentStart +
                            layout.playerWidth +
                            if (isShortLandscapePlayer(w.dp, h.dp)) 32.dp else 48.dp,
                        layout.detailStart
                    )
                    assertTrue(layout.playerWidth <= 480.dp)
                    assertEquals(
                        layout.contentStart,
                        w.dp - layout.detailStart - layout.detailWidth
                    )
                }
            }
    }

    @Test
    fun shortWindowAndLargeTextReduceArtworkBeforeControls() {
        assertTrue(playerLayoutGeometry(800.dp, 360.dp).short)
        assertFalse(playerLayoutGeometry(840.dp, 600.dp).short)
        val normal = playerCoverSize(440.dp, 700.dp, 74.dp, 244.dp, 24.dp, 440.dp)
        val largeText = playerCoverSize(440.dp, 700.dp, 140.dp, 280.dp, 24.dp, 440.dp)
        assertTrue(largeText < normal)
        assertEquals(48.dp, playerCoverSize(440.dp, 260.dp, 100.dp, 244.dp, 12.dp, 440.dp))
        assertTrue(playerCoverSize(32.dp, 100.dp, 100.dp, 244.dp, 12.dp, 440.dp) <= 32.dp)
    }
}
