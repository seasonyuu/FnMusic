package com.seasonyuu.fnmusic.feature.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueSnapBehaviorTest {
    private fun shortHistory(position: Float) = QueueSnapGeometry(position, 144f, 78f, 600f, true)

    @Test fun singleHistoryCanStayOpenOrReturnToCurrent() {
        assertEquals(0f, queueSnapTarget(shortHistory(35f), 0f, 144f, 1f))
        assertEquals(144f, queueSnapTarget(shortHistory(110f), 0f, 144f, 1f))
    }

    @Test fun midpointHysteresisPreservesTheStartingSection() {
        assertEquals(144f, queueSnapTarget(shortHistory(70f), 0f, 144f, 1f))
        assertEquals(0f, queueSnapTarget(shortHistory(70f), 0f, 0f, 1f))
    }

    @Test fun shortHistoryDoesNotLeaveAnUnsnappedGapBetweenItsEnds() {
        val threeRows = QueueSnapGeometry(140f, 288f, 78f, 500f, true)
        assertEquals(288f, queueSnapTarget(threeRows, 0f, 288f, 1f))
        assertEquals(0f, queueSnapTarget(threeRows, 0f, 0f, 1f))
        assertNull(queueSnapTarget(threeRows.copy(position = 520f), 0f, null, 1f))
    }

    @Test fun releaseMomentumCanSelectTheNextSection() {
        assertEquals(0f, queueSnapTarget(shortHistory(110f), -90f, 144f, 1f))
        assertEquals(222f, queueSnapTarget(shortHistory(160f), 70f, 144f, 1f))
    }

    @Test fun deepHistoryAndUpcomingSongsRemainFree() {
        val deep = QueueSnapGeometry(700f, 1600f, 78f, 600f, true)
        assertNull(queueSnapTarget(deep, 70f, null, 1f))
        assertNull(queueSnapTarget(shortHistory(200f), 600f, 144f, 1f))
    }

    @Test fun returningFlingCanLandAtTheCurrentBoundary() {
        val deep = QueueSnapGeometry(700f, 1600f, 78f, 600f, true)
        assertEquals(1600f, queueSnapTarget(deep, 880f, null, 1f))
    }

    @Test fun noHistoryHasOnlyCurrentAndQueueBoundaries() {
        val empty = QueueSnapGeometry(55f, 0f, 78f, 600f, false)
        assertEquals(listOf(0f, 78f), empty.anchors)
        assertEquals(78f, queueSnapTarget(empty, 0f, 0f, 1f))
    }

    @Test fun captureRangeScalesWithDensityAndViewport() {
        val scaled = QueueSnapGeometry(220f, 288f, 156f, 1200f, true)
        assertEquals(288f, queueSnapTarget(scaled, 0f, 288f, 2f))
        val short = QueueSnapGeometry(700f, 1600f, 78f, 200f, true)
        assertNull(queueSnapTarget(short, 820f, null, 1f))
    }
}
