package com.seasonyuu.fnmusic.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class BaseUrlProviderTest {
    private val provider = BaseUrlProvider().apply {
        set("https://nas.example.com/music/".toHttpUrl())
    }

    @Test
    fun `cover urls use the server supported size buckets`() {
        assertEquals(
            "https://nas.example.com/music/api/v1/static/cover?coverId=track_1&size=120",
            provider.coverUrl("track_1", 120),
        )
        assertEquals(
            "https://nas.example.com/music/api/v1/static/cover?coverId=track_1&size=160",
            provider.coverUrl("track_1", 160),
        )
        listOf(240, 320, 360, 640, 800, 1600).forEach { requestedSize ->
            assertEquals(
                "https://nas.example.com/music/api/v1/static/cover?coverId=track_1&size=640",
                provider.coverUrl("track_1", requestedSize),
            )
        }
    }
}
