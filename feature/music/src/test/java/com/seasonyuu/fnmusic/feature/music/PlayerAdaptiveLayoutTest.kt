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
    fun panesFitReferenceWindows() {
        listOf(390 to 844, 800 to 360, 840 to 600, 1024 to 768, 1280 to 800, 2000 to 1000)
            .forEach { (w, h) ->
                val layout = playerLayoutGeometry(w.dp, h.dp)
                assertTrue(layout.contentStart >= 0.dp)
                assertTrue(layout.playerWidth > 0.dp)
                assertTrue(layout.detailStart + layout.detailWidth <= w.dp)
                if (layout.wide) {
                    assertEquals(
                        layout.contentStart + layout.playerWidth + 48.dp,
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
