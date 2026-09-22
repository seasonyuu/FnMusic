package com.seasonyuu.fnmusic.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import com.seasonyuu.fnmusic.core.airplay.AirPlayAudioOutput
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(UnstableApi::class)
class AirPlayAudioSinkTest {
    private class Receiver : AirPlayAudioOutput {
        override var ready = true
        var accepting = true
        var playing = false
        var position = 0L
        val samples = mutableListOf<Short>()
        override fun play(value: Boolean) { playing = value }
        override fun offer(samples: ShortArray): Boolean {
            if (!accepting) return false
            this.samples.addAll(samples.toList()); return true
        }
        override fun flush() { samples.clear(); position = 0 }
        override fun positionFrames() = position
        override fun pending() = samples.isNotEmpty()
    }
    private fun local(onBuffer: () -> Unit = {}): AudioSink = Proxy.newProxyInstance(
        AudioSink::class.java.classLoader, arrayOf(AudioSink::class.java)) { _, method, args ->
        when (method.name) {
            "handleBuffer" -> { onBuffer(); (args[0] as ByteBuffer).position((args[0] as ByteBuffer).limit()); true }
            else -> when (method.returnType) {
                java.lang.Boolean.TYPE -> false
                java.lang.Integer.TYPE -> 0
                java.lang.Long.TYPE -> 0L
                else -> null
            }
        }
    } as AudioSink
    private fun format() = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_RAW)
        .setPcmEncoding(C.ENCODING_PCM_16BIT).setSampleRate(44100).setChannelCount(2).build()
    private fun buffer(vararg samples: Short) = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
        samples.forEach { putShort(it) }; flip()
    }
    @Test fun playBeforeConfigureAndBackpressureDoNotLosePcm() {
        val receiver = Receiver()
        val sink = AirPlayAudioSink(local()) { receiver }
        sink.play(); sink.configure(format(), 0, null)
        assertTrue(receiver.playing)
        val input = buffer(1, 2, 3, 4)
        receiver.accepting = false
        assertFalse(sink.handleBuffer(input, 1_000_000, 1))
        assertEquals(0, input.position())
        receiver.accepting = true
        assertTrue(sink.handleBuffer(input, 1_000_000, 1))
        assertEquals(listOf<Short>(1, 2, 3, 4), receiver.samples)
        receiver.position = 44100
        assertEquals(2_000_000, sink.getCurrentPositionUs(false))
    }
    @Test fun seekDropsPendingOldPcmAndEndWaitsForDrain() {
        val receiver = Receiver()
        val sink = AirPlayAudioSink(local()) { receiver }
        sink.configure(format(), 0, null)
        receiver.accepting = false
        sink.handleBuffer(buffer(100, 200), 0, 1)
        sink.flush(); receiver.accepting = true
        sink.handleBuffer(buffer(7, 8), 60_000_000, 1)
        assertEquals(listOf<Short>(7, 8), receiver.samples)
        assertEquals(60_000_000, sink.getCurrentPositionUs(false))
        sink.playToEndOfStream(); assertFalse(sink.isEnded)
        receiver.samples.clear(); assertTrue(sink.isEnded)
    }
    @Test fun explicitLocalSwitchReturnsPcmToLocalSink() {
        val receiver = Receiver()
        var selected: AirPlayAudioOutput? = receiver
        var localBuffers = 0
        val sink = AirPlayAudioSink(local { localBuffers++ }) { selected }
        sink.configure(format(), 0, null)
        sink.handleBuffer(buffer(1, 2), 0, 1)
        selected = null; sink.flush(); sink.configure(format(), 0, null)
        sink.handleBuffer(buffer(3, 4), 0, 1)
        assertEquals(1, localBuffers)
        assertTrue(receiver.samples.isEmpty())
    }
}
