package com.seasonyuu.fnmusic.core.player

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.SettableFuture
import com.seasonyuu.fnmusic.core.airplay.AirPlayNowPlaying
import com.seasonyuu.fnmusic.core.airplay.AirPlaySession
import com.seasonyuu.fnmusic.core.model.AirPlayDevice
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AirPlayNowPlayingPublisherTest {
    @Test fun lateOldCoverCannotOverwriteNewTrackAndDisconnectionStopsPublication() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val snapshots = CopyOnWriteArrayList<AirPlayNowPlaying>()
        val first = SettableFuture.create<Bitmap>()
        val second = SettableFuture.create<Bitmap>()
        val loader = object : BitmapLoader {
            override fun supportsMimeType(mimeType: String) = true
            override fun decodeBitmap(data: ByteArray) = first
            override fun loadBitmap(uri: Uri) = if (uri.lastPathSegment == "one") first else second
        }
        var item = MediaItem.Builder().setMediaId("one").setMediaMetadata(MediaMetadata.Builder()
            .setTitle("第一首").setArtworkUri(Uri.parse("https://example.test/one")).build()).build()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getCurrentMediaItem" -> item
                "getMediaMetadata" -> item.mediaMetadata
                "getCurrentPosition" -> 1000L
                "getDuration" -> 90000L
                "isPlaying" -> true
                else -> null
            }
        } as Player
        val output = object : AirPlaySession {
            override val device = AirPlayDevice("test", "test", "127.0.0.1", 7000, "", "", "_airplay._tcp")
            override val ready = true
            override val hasNonzeroAudio = false
            override fun connect() {}
            override fun submitPin(pin: String) {}
            override fun volume(value: Float) {}
            override fun sentFrames() = 0L
            override fun nowPlaying(value: AirPlayNowPlaying) { snapshots.add(value) }
            override fun play(value: Boolean) {}
            override fun offer(samples: ShortArray) = true
            override fun flush() {}
            override fun positionFrames() = 0L
            override fun pending() = false
            override fun close() {}
        }
        var selected: AirPlaySession? = output
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        fun await(check: () -> Boolean) {
            val limit = android.os.SystemClock.elapsedRealtime() + 5000
            while (!check() && android.os.SystemClock.elapsedRealtime() < limit) Thread.sleep(25)
            assertTrue(check())
        }
        try {
            instrumentation.runOnMainSync { scope.publishAirPlayNowPlaying(player, loader) { selected } }
            await { snapshots.any { it.title == "第一首" } }
            instrumentation.runOnMainSync {
                item = MediaItem.Builder().setMediaId("two").setMediaMetadata(MediaMetadata.Builder()
                    .setTitle("第二首").setArtworkUri(Uri.parse("https://example.test/two")).build()).build()
            }
            await { snapshots.any { it.title == "第二首" } }
            second.set(Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) })
            await { snapshots.last().artwork.isNotEmpty() }
            val expected = snapshots.last().artwork
            first.set(Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) })
            Thread.sleep(650)
            assertEquals("第二首", snapshots.last().title)
            assertArrayEquals(expected, snapshots.last().artwork)
            instrumentation.runOnMainSync { selected = null }
            Thread.sleep(350)
            val count = snapshots.size
            Thread.sleep(400)
            assertEquals(count, snapshots.size)
        } finally { instrumentation.runOnMainSync { scope.cancel() } }
    }
}
