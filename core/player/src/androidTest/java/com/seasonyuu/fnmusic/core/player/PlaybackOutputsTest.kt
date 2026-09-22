package com.seasonyuu.fnmusic.core.player

import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.airplay.AirPlaySession
import com.seasonyuu.fnmusic.core.model.AirPlayDevice
import com.seasonyuu.fnmusic.core.model.PlaybackOutput
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackOutputsTest {
    @Test fun connectingTargetSurvivesDisappearanceFromDiscovery() {
        val target = AirPlayDevice("001122334455", "Receiver", "192.168.1.2", 7000, "Mac", "", "_airplay._tcp")
        val state = com.seasonyuu.fnmusic.core.model.OutputState(connecting = target, pairing = true)
        assertEquals(state, OutputCommands.decode(OutputCommands.encode(state)))
    }
    private class Session(override val device: AirPlayDevice, val event: (String) -> Unit) : AirPlaySession {
        override var ready = true
        override val hasNonzeroAudio = true
        var closed = false
        var volume = 20f
        override fun connect() {}
        override fun submitPin(pin: String) {}
        override fun volume(value: Float) { volume = value }
        override fun sentFrames() = 44100L
        override fun play(value: Boolean) {}
        override fun offer(samples: ShortArray) = true
        override fun flush() {}
        override fun positionFrames() = 44100L
        override fun pending() = false
        override fun close() { closed = true; ready = false }
    }
    @Test fun failedSwitchKeepsOldOutputAndDisconnectNeverResumesLocally() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            var playing = true
            var position = 12345L
            var prepared = 0
            var nextCount = 0
            var previousCount = 0
            val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { _, method, args ->
                when (method.name) {
                    "getPlayWhenReady" -> playing
                    "getCurrentPosition" -> position
                    "setPlayWhenReady" -> { playing = args[0] as Boolean; null }
                    "play" -> { playing = true; null }
                    "seekToNextMediaItem" -> { nextCount++; null }
                    "seekToPreviousMediaItem" -> { previousCount++; null }
                    "pause" -> { playing = false; null }
                    "seekTo" -> { position = args[0] as Long; null }
                    "prepare" -> { prepared++; null }
                    "stop" -> null
                    "setMediaItems", "clearMediaItems", "removeMediaItems" -> error("Output switching must not modify the queue")
                    else -> null
                }
            } as ExoPlayer
            var permitted = false
            val sessions = mutableListOf<Session>()
            val outputs = PlaybackOutputs(instrumentation.targetContext, { player }, hasNetworkPermission = { permitted }) { device, event -> Session(device, event).also(sessions::add) }
            val a = AirPlayDevice("001122334455", "A", "192.168.1.2", 7000, "Mac", "", "_airplay._tcp")
            val b = a.copy(id = "001122334466", name = "B")
            outputs.discovered(listOf(a, b))
            fun command(operation: String, id: String? = null) = outputs.command(Bundle().apply {
                putString("operation", operation); putString("id", id)
            })
            assertEquals(androidx.media3.session.SessionError.ERROR_PERMISSION_DENIED, command("select", a.id).resultCode)
            assertTrue(sessions.isEmpty())
            permitted = true
            command("select", a.id)
            assertEquals(PlaybackOutput.Local, outputs.state.output)
            sessions[0].event("connected")
            assertEquals(PlaybackOutput.AirPlay(a.id, a.name), outputs.state.output)
            assertEquals(12345L, position); assertTrue(playing)
            sessions[0].event("remote:paus"); assertFalse(playing)
            sessions[0].event("remote:play"); assertTrue(playing)
            sessions[0].event("remote:plps"); assertFalse(playing)
            sessions[0].event("remote:plps"); assertTrue(playing)
            sessions[0].event("remote:nitm"); sessions[0].event("remote:pitm")
            assertEquals(1, nextCount); assertEquals(1, previousCount)
            command("select", b.id)
            sessions[1].event("remote:paus"); assertTrue(playing)
            sessions[1].event("remote:nitm"); assertEquals(1, nextCount)
            sessions[1].event("connection_failed")
            assertSame(sessions[0], outputs.connection)
            assertFalse(sessions[0].closed); assertTrue(playing)
            sessions[1].event("remote:paus"); assertTrue(playing)
            sessions[0].event("disconnected")
            assertFalse(playing)
            assertEquals(PlaybackOutput.AirPlay(a.id, a.name), outputs.state.output)
            assertEquals(12345L, position)
            val prior = prepared
            command("local")
            assertEquals(PlaybackOutput.Local, outputs.state.output)
            assertFalse(playing); assertEquals(prior + 1, prepared)
            playing = true
            command("select", a.id)
            sessions.last().event("connected")
            val active = sessions.last()
            val sessionCount = sessions.size
            command("select", a.id)
            assertEquals("Selecting the connected target must not create another session", sessionCount, sessions.size)
            command("select", b.id)
            val pending = sessions.last()
            pending.event("pairing")
            active.event("disconnected")
            assertEquals(b, outputs.state.connecting)
            assertTrue(outputs.state.pairing)
            assertFalse(playing)
            active.event("connected")
            assertSame("A stale old callback cannot promote the new candidate", active, outputs.connection)
            pending.event("connected")
            assertSame(pending, outputs.connection)
            assertEquals(PlaybackOutput.AirPlay(b.id, b.name), outputs.state.output)
            command("select", a.id)
            val cancelled = sessions.last()
            cancelled.event("pairing")
            val beforeCancel = prepared
            command("cancel")
            assertTrue(cancelled.closed)
            assertFalse(pending.closed)
            assertNull(outputs.state.connecting)
            assertFalse(outputs.state.pairing)
            assertEquals(beforeCancel, prepared)
            cancelled.event("connected")
            assertSame(pending, outputs.connection)
            command("select", a.id)
            sessions.last().event("receiver_response_timeout")
            assertSame(pending, outputs.connection)
            assertFalse(pending.closed)
            assertTrue(outputs.state.error.orEmpty().contains("弹窗"))
            command("select", a.id)
            sessions.last().event("receiver_request_rejected")
            assertSame(pending, outputs.connection)
            assertFalse(pending.closed)
            assertTrue(outputs.state.error.orEmpty().contains("拒绝"))
            assertFalse(outputs.state.error.orEmpty().contains("超时"))
            permitted = false
            command("get")
            assertFalse(playing)
            assertTrue(pending.closed)
            assertEquals(PlaybackOutput.AirPlay(b.id, b.name), outputs.state.output)
            assertNotNull(outputs.state.error)
            outputs.close()
        }
    }
}
