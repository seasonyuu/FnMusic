package com.seasonyuu.fnmusic.core.airplay

import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream

class AirPlayNowPlayingTest {
    @Test fun unicodeAndBinaryArtworkSurviveSnapshotEncoding() {
        val cover = byteArrayOf(0, -1, 4, 0)
        val input = DataInputStream(AirPlayNowPlaying("歌曲 🎵", "歌手", "", -1, 90000, true, cover).encode().inputStream())
        fun text() = ByteArray(input.readInt()).also(input::readFully).toString(Charsets.UTF_8)
        assertEquals("歌曲 🎵", text()); assertEquals("歌手", text()); assertEquals("", text())
        assertEquals(0L, input.readLong()); assertEquals(90000L, input.readLong()); assertTrue(input.readBoolean())
        assertArrayEquals(cover, ByteArray(input.readInt()).also(input::readFully)); assertEquals(-1, input.read())
    }
    @Test(expected = IllegalArgumentException::class) fun oversizedCoverRejected() {
        AirPlayNowPlaying("", "", "", 0, 0, false, ByteArray(512 * 1024 + 1)).encode()
    }
}
