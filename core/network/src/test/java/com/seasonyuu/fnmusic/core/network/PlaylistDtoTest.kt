package com.seasonyuu.fnmusic.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistDtoTest {
    @Test
    fun `missing track count remains unknown instead of becoming zero`() {
        val playlist = PlaylistDto(
            guid = "playlist-placeholder",
            name = "测试歌单",
        ).toDomain()

        assertNull(playlist.trackCount)
    }

    @Test
    fun `confirmed zero track count remains zero`() {
        val playlist = PlaylistDto(
            guid = "playlist-placeholder",
            name = "空歌单",
            trackCount = 0,
        ).toDomain()

        assertEquals(0, playlist.trackCount)
    }
}
