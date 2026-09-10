package com.seasonyuu.fnmusic.feature.music

import org.junit.Assert.*
import org.junit.Test

class PlayerSliderMotionTest {
    @Test fun rubberPullHasResistanceAndStaysBoundedInBothDirections() {
        val motion = PlayerSliderMotion().apply { widthPx = 300f }
        motion.dragTo(310f, 1f, 14, false, 12f)
        val near = motion.targetPullPx
        motion.dragTo(400f, 1f, 14, false, 12f)
        val far = motion.targetPullPx
        assertTrue(near > 0f && near < 10f)
        assertTrue(far > near && far < 12f)
        motion.dragTo(-100f, 1f, 14, false, 12f)
        assertEquals(-far, motion.targetPullPx, 0.001f)
        motion.release()
        assertEquals(0f, motion.targetPullPx, 0f)
    }

    @Test fun edgePulseRearmsOnlyAfterLeavingTheEndpointOrReleasing() {
        val motion = PlayerSliderMotion().apply { widthPx = 300f }
        motion.dragTo(298f, 1f, 14, false, 12f)
        motion.dragTo(330f, 1f, 14, false, 12f)
        motion.dragTo(360f, 1f, 14, false, 12f)
        assertEquals(1, motion.maximumHits)
        motion.dragTo(150f, 1f, 14, false, 12f)
        motion.dragTo(310f, 1f, 14, false, 12f)
        assertEquals(2, motion.maximumHits)
        motion.release()
        motion.dragTo(310f, 1f, 14, false, 12f)
        assertEquals(3, motion.maximumHits)
        motion.dragTo(-10f, 1f, 14, false, 12f)
        assertEquals(1, motion.minimumHits)
    }

    @Test fun directionAndUnknownWidthAreHandled() {
        val motion = PlayerSliderMotion()
        motion.dragTo(100f, 1f, 14, false, 12f)
        assertEquals(0, motion.maximumHits)
        motion.widthPx = 300f
        motion.dragTo(-10f, 1f, 14, true, 12f)
        assertEquals(1, motion.maximumHits)
        motion.dragTo(310f, 1f, 14, true, 12f)
        assertEquals(1, motion.minimumHits)
    }
}
