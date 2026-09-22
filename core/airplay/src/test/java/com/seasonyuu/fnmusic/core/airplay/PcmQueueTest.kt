package com.seasonyuu.fnmusic.core.airplay

import org.junit.Assert.*
import org.junit.Test

class PcmQueueTest {
    @Test fun fullQueueAppliesBackpressureWithoutDroppingOrDuplicating() {
        val queue = PcmQueue(4)
        assertTrue(queue.offer(shortArrayOf(1,2,3,4)))
        assertFalse(queue.offer(shortArrayOf(5,6)))
        assertArrayEquals(shortArrayOf(1,2), queue.poll(2))
        assertTrue(queue.offer(shortArrayOf(5,6)))
        assertArrayEquals(shortArrayOf(3,4,5,6), queue.poll(6))
        assertEquals(0, queue.size)
    }
    @Test fun flushDiscardsOldEpochIncludingWrappedData() {
        val queue = PcmQueue(4)
        queue.offer(shortArrayOf(1,2,3,4)); queue.poll(2); queue.offer(shortArrayOf(5,6))
        queue.clear(); queue.offer(shortArrayOf(7,8))
        assertArrayEquals(shortArrayOf(7,8), queue.poll(4))
    }
}
