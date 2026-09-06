package com.seasonyuu.fnmusic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.RepeatMode
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.core.player.Media3PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class PlaybackMetadataRefreshTest {
    @Test fun cachedRestoreIsImmediateAndMetadataRefreshPreservesUserSeek() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Serve ten seconds of silence over loopback, matching the HTTP-only data source.
        val samples = ByteArray(8000 * 2 * 10)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size).array()
        val server = MockWebServer()
        val wav = header + samples
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val offset = request.getHeader("Range")?.substringAfter("bytes=")
                    ?.substringBefore('-')?.toIntOrNull() ?: 0
                return MockResponse().setHeader("Content-Type", "audio/wav")
                    .setBody(Buffer().write(wav, offset, wav.size - offset))
                    .apply {
                        if (offset > 0) {
                            setResponseCode(206)
                            setHeader("Content-Range", "bytes $offset-${wav.lastIndex}/${wav.size}")
                        }
                    }
            }
        }
        server.start()
        (context as FnMusicApplication).graph.network.activateBaseUrl(server.url("/"), allowPrivateLanHttp = true)
        val controller = withContext(Dispatchers.Main) { Media3PlayerController(context) }
        try {
            val original = PlayableTrack(Track(TrackId("cached"), "Cached title"), server.url("/silence.wav").toString())
            withContext(Dispatchers.Main) {
                controller.restore(listOf(original), 0, 1234, false, RepeatMode.All)
                assertEquals("Cached title", controller.state.value.current?.track?.title)
                assertEquals(1234L, controller.state.value.positionMs)
            }
            withTimeout(10_000) { controller.state.first { it.durationMs > 0 } }
            withContext(Dispatchers.Main) { controller.seekTo(3456) }
            withTimeout(10_000) { controller.state.first { it.positionMs == 3456L } }
            val sessionId = controller.state.value.playbackSessionId
            withContext(Dispatchers.Main) {
                controller.updateTracks(listOf(original.copy(track = original.track.copy(title = "Cloud title"))))
            }
            // Wait for another controller poll after the Media3 metadata replacement.
            kotlinx.coroutines.delay(600)
            val updated = controller.state.value
            assertEquals("Cloud title", updated.current?.track?.title)
            assertEquals(3456L, updated.positionMs)
            assertEquals(0, updated.currentIndex)
            assertEquals(RepeatMode.All, updated.repeatMode)
            assertEquals(sessionId, updated.playbackSessionId)
            assertFalse(updated.isPlaying)
            assertNull(updated.error)
            withContext(Dispatchers.Main) { controller.resume() }
            withTimeout(10_000) { controller.state.first { it.isPlaying } }
            withContext(Dispatchers.Main) {
                controller.updateTracks(listOf(original.copy(track = original.track.copy(title = "Live update"))))
            }
            kotlinx.coroutines.delay(600)
            val playing = controller.state.value
            assertTrue(playing.isPlaying)
            assertTrue(playing.positionMs >= 3456L)
            assertEquals("Live update", playing.current?.track?.title)
            assertEquals(sessionId, playing.playbackSessionId)
            assertNull(playing.error)
        } finally {
            withContext(Dispatchers.Main) { controller.clear() }
            server.shutdown()
        }
    }
}
