package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurRadiusCurveTest {
    private fun stops(statusHeight: Float) = listOf(
        BlurRadiusStop(0.dp, 32.dp),
        BlurRadiusStop(statusHeight.dp, 24.dp),
        BlurRadiusStop((statusHeight + 22).dp, 12.dp),
        BlurRadiusStop((statusHeight + 44).dp, 3.dp),
        BlurRadiusStop((statusHeight + 54).dp, 0.dp),
    )

    @Test fun toolbarRadiiAreIndependentOfStatusBarHeight() {
        for (statusHeight in listOf(24f, 48f, 64f)) {
            val curve = blurRadiusSegments(stops(statusHeight))
            assertEquals(24f, curve.radiusAt(statusHeight), .0001f)
            assertEquals(12f, curve.radiusAt(statusHeight + 22), .0001f)
            assertEquals(3f, curve.radiusAt(statusHeight + 44), .0001f)
            assertEquals(0f, curve.radiusAt(statusHeight + 54), .0001f)
            assertEquals(0f, curve.radiusAt(statusHeight + 80), .0001f)
        }
    }

    @Test fun curveHasContinuousSlopesAndNeverOvershoots() {
        val curve = blurRadiusSegments(stops(48f))
        curve.zipWithNext { a, b -> assertEquals(a.endSlope, b.startSlope, .0001f) }
        var previous = 32f
        for (step in 0..1020) {
            val radius = curve.radiusAt(step / 10f)
            assertTrue("Radius must fall without overshoot: $radius after $previous", radius >= 0f && radius <= previous + .0001f)
            previous = radius
        }
        assertEquals(0f, curve.last().endSlope, 0f)
    }
}
