package com.seasonyuu.fnmusic.core.player

import androidx.media3.common.Player
import com.seasonyuu.fnmusic.core.model.PlaybackStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStatusTest {
    @Test
    fun `buffering is distinct from paused when playback is requested`() {
        assertEquals(
            PlaybackStatus.Buffering,
            playbackStatus(Player.STATE_BUFFERING, isPlaying = false, playWhenReady = true),
        )
        assertEquals(
            PlaybackStatus.Paused,
            playbackStatus(Player.STATE_BUFFERING, isPlaying = false, playWhenReady = false),
        )
    }

    @Test
    fun `ready and ended states map to their user visible statuses`() {
        assertEquals(
            PlaybackStatus.Playing,
            playbackStatus(Player.STATE_READY, isPlaying = true, playWhenReady = true),
        )
        assertEquals(
            PlaybackStatus.Paused,
            playbackStatus(Player.STATE_READY, isPlaying = false, playWhenReady = false),
        )
        assertEquals(
            PlaybackStatus.Ended,
            playbackStatus(Player.STATE_ENDED, isPlaying = false, playWhenReady = false),
        )
        assertEquals(
            PlaybackStatus.Idle,
            playbackStatus(Player.STATE_IDLE, isPlaying = false, playWhenReady = false),
        )
    }
}
