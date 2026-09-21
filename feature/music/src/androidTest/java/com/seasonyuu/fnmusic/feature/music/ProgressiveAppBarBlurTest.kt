package com.seasonyuu.fnmusic.feature.music

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.ProgressiveBarBlur
import com.seasonyuu.fnmusic.core.designsystem.BlurRadiusStop
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ProgressiveAppBarBlurTest {
    @get:Rule val compose = createComposeRule()

    @Test fun edgeSpreadNarrowsInsteadOfOnlyBecomingTransparent() = verifyEdgeSpread(false)

    @Test fun physicalRadiusStopsRenderWithoutTopLeakage() = verifyEdgeSpread(true)

    private fun verifyEdgeSpread(usePhysicalStops: Boolean) {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        var showBlur by mutableStateOf(false)
        compose.setContent {
            val backdrop = rememberLayerBackdrop()
            Box(Modifier.size(200.dp, 120.dp).testTag("blur-fixture")) {
                Canvas(Modifier.size(200.dp, 120.dp).layerBackdrop(backdrop)) {
                    drawRect(Color.Black)
                    drawRect(Color.White, Offset(size.width / 2, 0f), Size(size.width / 2, size.height))
                }
                if (showBlur) ProgressiveBarBlur(
                    backdrop, top = true, modifier = Modifier, tint = Color.Black,
                    includeSystemInset = false, extraHeight = 120.dp,
                    transitionHeight = 0.dp, fadeStartFraction = 0f,
                    radiusStops = if (usePhysicalStops) listOf(
                        BlurRadiusStop(0.dp, 32.dp), BlurRadiusStop(30.dp, 24.dp),
                        BlurRadiusStop(60.dp, 12.dp), BlurRadiusStop(100.dp, 3.dp),
                        BlurRadiusStop(120.dp, 0.dp),
                    ) else null,
                )
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { showBlur = true }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("blur-fixture").captureToImage().toPixelMap()
        val scale = pixels.width / 200f
        fun tailRatio(yDp: Float): Float {
            val y = (yDp * scale).toInt()
            val baseline = pixels[(60 * scale).toInt(), y].red
            val near = pixels[(98 * scale).toInt(), y].red - baseline
            val far = pixels[(94 * scale).toInt(), y].red - baseline
            assertTrue("Expected a blurred edge at y=$yDp, near=$near", near > .015f)
            return far / near
        }
        // A transparent off-screen sample must not expose the original white
        // scene at the top. The opaque, 32%-tinted result is approximately .68.
        val topWhite = pixels[(150 * scale).toInt(), 1].red
        assertTrue("Top edge leaked the sharp scene: $topWhite", topWhite in .65f.. .74f)
        val upper = tailRatio(30f)
        val middle = tailRatio(55f)
        val lower = tailRatio(80f)
        // Fading a fixed-radius overlay scales both samples equally, leaving
        // this ratio unchanged. A smaller radius has a much shorter edge tail.
        assertTrue("Upper edge should have a visible blur tail: $upper", upper > .15f)
        assertTrue("Radius must decrease before the bottom: upper=$upper middle=$middle", middle < upper * .85f)
        assertTrue("Blur radius should shrink: upper=$upper lower=$lower", lower < upper * .65f)
    }
}
