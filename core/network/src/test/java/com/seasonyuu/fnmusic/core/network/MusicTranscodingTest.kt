package com.seasonyuu.fnmusic.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class MusicTranscodingTest {
    @Test fun standardQualityStartsOnceHeartbeatsAndDisposes() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            val transcoding = MusicTranscoding(network)
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"status":"ready"}}"""))
            transcoding.prepare("test-track")
            transcoding.prepare("test-track")
            val start = server.takeRequest()
            assertEquals("/music/api/v1/track/transcode", start.path)
            assertEquals("""{"guid":"test-track","output":{"codec":"opus","bitrate":128,"channel":2}}""", start.body.readUtf8())
            server.enqueue(MockResponse().setBody("""{"code":0}"""))
            transcoding.maintain("test-track", 12)
            assertEquals("""{"guid":"test-track","timestamp":12}""", server.takeRequest().body.readUtf8())
            server.enqueue(MockResponse().setBody("""{"code":0}"""))
            transcoding.maintain(null, 0)
            assertEquals("/music/api/v1/track/transcode/quit", server.takeRequest().path)
            assertEquals(3, server.requestCount)
        }
    }
    @Test fun unsupportedTranscodingDoesNotPretendToStartOrRetry() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            val transcoding = MusicTranscoding(network)
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"status":"failed","errno":8192}}"""))
            val failure = runCatching { transcoding.prepare("test-track") }.exceptionOrNull()
            assertTrue(failure?.message?.contains("8192") == true)
            transcoding.maintain(null, 0)
            assertEquals(1, server.requestCount)
        }
    }
}
