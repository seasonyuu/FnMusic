package com.seasonyuu.fnmusic.core.player

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StereoPcmConverterTest {
    private fun pcm(vararg samples: Short): ByteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        samples.forEach { putShort(it) }; flip()
    }
    @Test fun stereo44100IsBitExact() {
        val samples = shortArrayOf(Short.MIN_VALUE, Short.MAX_VALUE, 0, -1, 123, -456)
        assertArrayEquals(samples, StereoPcmConverter(44100, 2).convert(pcm(*samples), 3))
    }
    @Test fun monoIsDuplicatedAndLeadingFrameIsNotSilenced() {
        assertArrayEquals(shortArrayOf(100, 100, -200, -200), StereoPcmConverter(44100, 1).convert(pcm(100, -200), 2))
    }
    @Test fun resamplingHasIdenticalOutputAcrossBufferBoundaries() {
        val source = ShortArray(4800) { (it % 1000 - 500).toShort() }
        val whole = StereoPcmConverter(48000, 1).convert(pcm(*source), source.size)
        val split = StereoPcmConverter(48000, 1)
        val pieces = source.toList().chunked(113).flatMap { split.convert(pcm(*it.toShortArray()), it.size).toList() }.toShortArray()
        assertArrayEquals(whole, pieces)
        assertEquals(4410 * 2, whole.size)
    }
    @Test fun newTimelineDoesNotReusePreviousFrame() {
        val first = StereoPcmConverter(48000, 1)
        first.convert(pcm(20000, 20000), 2)
        val newTimeline = StereoPcmConverter(48000, 1)
        assertArrayEquals(shortArrayOf(-100, -100), newTimeline.convert(pcm(-100), 1))
    }
}
