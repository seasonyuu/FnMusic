package com.seasonyuu.fnmusic.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackDtoTest {
    @Test
    fun `service duration milliseconds are normalized to domain seconds`() {
        val track = TrackDto(
            guid = "track-placeholder",
            duration = 158_828.0,
            audioSpec = AudioSpecDto(duration = 158_828.0),
        ).toDomain()

        assertEquals(158.828, track.durationSeconds, 0.001)
        assertEquals(158.828, track.audioSpec?.duration ?: 0.0, 0.001)
    }
}
