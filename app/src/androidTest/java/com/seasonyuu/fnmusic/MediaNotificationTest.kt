package com.seasonyuu.fnmusic

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasonyuu.fnmusic.core.model.Album
import com.seasonyuu.fnmusic.core.model.AlbumId
import com.seasonyuu.fnmusic.core.model.Artist
import com.seasonyuu.fnmusic.core.model.ArtistId
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.core.player.Media3PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class MediaNotificationTest {
    @Test fun authenticatedArtworkAndSystemControlsFollowTheCurrentTrack() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<FnMusicApplication>()
        val server = MockWebServer()
        val coverRequests = CopyOnWriteArrayList<RecordedRequest>()
        val red = png(Color.RED)
        val blue = png(Color.BLUE)
        val samples = ByteArray(8000 * 2 * 120)
        val wav = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size).array() + samples
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path?.endsWith(".png") == true) {
                    coverRequests += request
                    if (request.getHeader("Cookie")?.contains("music-token=notification-test") != true ||
                        request.getHeader("authx").isNullOrBlank()) return MockResponse().setResponseCode(403)
                    val bytes = when (request.path) {
                        "/red.png" -> red
                        "/blue.png" -> blue
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
                }
                val offset = request.getHeader("Range")?.substringAfter("bytes=")
                    ?.substringBefore('-')?.toIntOrNull() ?: 0
                return MockResponse().setHeader("Content-Type", "audio/wav")
                    .setBody(Buffer().write(wav, offset, wav.size - offset)).apply {
                        if (offset > 0) {
                            setResponseCode(206)
                            setHeader("Content-Range", "bytes $offset-${wav.lastIndex}/${wav.size}")
                        }
                    }
            }
        }
        server.start()
        context.graph.network.activateBaseUrl(server.url("/"), allowPrivateLanHttp = true)
        context.graph.network.cookieJar.setMusicToken(server.url("/"), "notification-test")
        val controller = withContext(Dispatchers.Main) { Media3PlayerController(context) }
        val notifications = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        fun track(id: String, cover: String?) = PlayableTrack(
            Track(TrackId(id), "Title $id", listOf(Artist(ArtistId("artist"), "Test artist")),
                Album(AlbumId("album"), "Test album"), durationSeconds = 120.0),
            server.url("/$id.wav").toString(),
            cover?.let { server.url(it).toString() },
        )
        val tracks = listOf(track("red", "/red.png"), track("blue", "/blue.png"), track("missing", "/missing.png"))
        try {
            withContext(Dispatchers.Main) { controller.play(tracks, 0, false) }
            await { notifications.activeNotifications.any { it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION) } }
            val notification = notifications.activeNotifications.first {
                it.notification.extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
            }.notification
            @Suppress("DEPRECATION")
            val token = notification.extras.getParcelable<MediaSession.Token>(Notification.EXTRA_MEDIA_SESSION)!!
            val system = MediaController(context, token)
            fun artwork() = system.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: system.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            await { artwork()?.getPixel(0, 0) == Color.RED }
            assertEquals("Title red", system.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE))
            assertEquals("Test artist", system.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST))
            assertEquals("Test album", system.metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM))
            assertNotNull("通知点击必须能返回应用", system.sessionActivity)
            assertEquals(context.packageName, system.sessionActivity!!.creatorPackage)
            assertTrue(artwork()!!.width <= 512 && artwork()!!.height <= 512)
            assertTrue(coverRequests.all { it.getHeader("Cookie")?.contains("music-token=notification-test") == true })

            system.transportControls.pause()
            await { system.playbackState?.state == PlaybackState.STATE_PAUSED }
            system.transportControls.seekTo(15_000)
            await { controller.state.value.positionMs == 15_000L }
            system.transportControls.play()
            await { controller.state.value.isPlaying }
            system.transportControls.skipToNext()
            await { system.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == "Title blue" && artwork()?.getPixel(0, 0) == Color.BLUE }
            system.transportControls.skipToPrevious()
            await { system.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == "Title red" && artwork()?.getPixel(0, 0) == Color.RED }

            system.transportControls.skipToNext()
            system.transportControls.skipToNext()
            await { system.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) == "Title missing" && artwork() == null }
            await { coverRequests.any { it.path == "/missing.png" } }
            await { controller.state.value.isPlaying }
            assertNull("封面失败不能中断音频", controller.state.value.error)
        } finally {
            withContext(Dispatchers.Main) { controller.clear() }
            server.shutdown()
        }
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(10_000) {
        while (!condition()) delay(50)
    }

    private fun png(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }
}
