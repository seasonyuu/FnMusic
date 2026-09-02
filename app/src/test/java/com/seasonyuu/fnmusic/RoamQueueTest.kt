package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.RoamItem
import com.seasonyuu.fnmusic.core.model.RoamWindow
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoamQueueTest {
    @Test
    fun `forward window consumes the cached current and appends only the new lookahead`() {
        val queue = RoamQueue(RoamWindow(current = item(0), next = item(1)))

        val appended = queue.mergeForward(
            anchorIndex = 0,
            window = RoamWindow(previous = item(0), current = item(1), next = item(2)),
        )

        assertEquals(listOf(item(2)), appended)
        assertEquals(listOf(item(0), item(1), item(2)), queue.items)
        assertEquals("roam-1", queue.anchorAt(1)?.roamId)
    }

    @Test
    fun `repeated response does not duplicate media items`() {
        val queue = RoamQueue(RoamWindow(current = item(0), next = item(1)))
        val response = RoamWindow(previous = item(0), current = item(1), next = item(2))

        queue.mergeForward(0, response)
        val repeated = queue.mergeForward(0, response)

        assertEquals(emptyList<RoamItem>(), repeated)
        assertEquals(listOf(item(0), item(1), item(2)), queue.items)
    }

    @Test
    fun `invalid index has no anchor`() {
        val queue = RoamQueue(RoamWindow(current = item(0), next = item(1)))

        assertNull(queue.anchorAt(-1))
        assertNull(queue.anchorAt(2))
    }

    private fun item(index: Int) = RoamItem(
        roamId = "roam-$index",
        track = Track(TrackId("track-$index"), "Track $index"),
    )
}
