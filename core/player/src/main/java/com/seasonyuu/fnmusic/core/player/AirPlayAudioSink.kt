package com.seasonyuu.fnmusic.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.AudioOffloadSupport
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import com.seasonyuu.fnmusic.core.airplay.AirPlayAudioOutput
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decoder-thread adapter. All networking is delegated to the connection worker. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class AirPlayAudioSink(local: AudioSink, private val output: () -> AirPlayAudioOutput?) : ForwardingAudioSink(local) {
    private var format: Format? = null
    private var converter: StereoPcmConverter? = null
    private var pending: ShortArray? = null
    private var pendingBytes = 0
    private var startUs = C.TIME_UNSET
    private var ended = false
    private var playing = false
    private var remote: AirPlayAudioOutput? = null
    private fun target() = remote
    override fun supportsFormat(format: Format) = if (output() == null) super.supportsFormat(format)
        else format.sampleMimeType == MimeTypes.AUDIO_RAW && format.pcmEncoding == C.ENCODING_PCM_16BIT && format.channelCount in 1..2
    override fun getFormatSupport(format: Format) = if (output() == null) super.getFormatSupport(format)
        else if (supportsFormat(format)) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else AudioSink.SINK_FORMAT_UNSUPPORTED
    override fun getFormatOffloadSupport(format: Format): AudioOffloadSupport = if (output() == null) super.getFormatOffloadSupport(format) else AudioOffloadSupport.DEFAULT_UNSUPPORTED
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        remote = output()
        format = inputFormat
        if (remote == null) { super.configure(inputFormat, specifiedBufferSize, outputChannels); return }
        if (!supportsFormat(inputFormat) || inputFormat.sampleRate <= 0) throw AudioSink.ConfigurationException("AirPlay requires decoded 16-bit mono/stereo PCM", inputFormat)
        converter = StereoPcmConverter(inputFormat.sampleRate, inputFormat.channelCount)
        ended = false
        remote?.play(playing)
    }
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        val connection = target() ?: return super.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        if (!connection.ready) return false
        if (startUs == C.TIME_UNSET) startUs = presentationTimeUs
        if (!buffer.hasRemaining()) return true
        if (pending == null) {
            val bytesPerFrame = requireNotNull(format).channelCount * 2
            val frames = minOf(buffer.remaining() / bytesPerFrame, 2048)
            pendingBytes = frames * bytesPerFrame
            pending = requireNotNull(converter).convert(buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN), frames)
        }
        if (!connection.offer(requireNotNull(pending))) return false
        buffer.position(buffer.position() + pendingBytes)
        pending = null
        return !buffer.hasRemaining()
    }
    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = target()?.let {
        if (startUs == C.TIME_UNSET) AudioSink.CURRENT_POSITION_NOT_SET else startUs + it.positionFrames() * 1_000_000L / 44100
    } ?: super.getCurrentPositionUs(sourceEnded)
    override fun play() { playing = true; target()?.play(true) ?: super.play() }
    override fun pause() { playing = false; target()?.play(false) ?: super.pause() }
    override fun flush() {
        super.flush()
        target()?.flush()
        remote = output()
        converter = format?.let { StereoPcmConverter(it.sampleRate, it.channelCount) }
        pending = null; startUs = C.TIME_UNSET; ended = false
    }
    override fun handleDiscontinuity() { if (target() == null) super.handleDiscontinuity() else flush() }
    override fun playToEndOfStream() { if (target() == null) super.playToEndOfStream() else ended = true }
    override fun hasPendingData() = target()?.pending() ?: super.hasPendingData()
    override fun isEnded() = if (target() == null) super.isEnded() else ended && !hasPendingData()
    override fun setPlaybackParameters(parameters: PlaybackParameters) { if (target() == null) super.setPlaybackParameters(parameters) }
    override fun getPlaybackParameters() = if (target() == null) super.getPlaybackParameters() else PlaybackParameters.DEFAULT
    override fun setSkipSilenceEnabled(enabled: Boolean) { if (target() == null) super.setSkipSilenceEnabled(enabled) }
    override fun getSkipSilenceEnabled() = target() == null && super.getSkipSilenceEnabled()
    override fun setVolume(volume: Float) { if (target() == null) super.setVolume(volume) }
    override fun reset() { flush(); super.reset(); format = null; converter = null }
    override fun release() { super.release() }
}

/** Streaming linear resampling with a retained boundary frame; no per-buffer timeline reset. */
internal class StereoPcmConverter(private val rate: Int, private val channels: Int) {
    private var inputFrame = 0L
    private var outputFrame = 0L
    private var previousLeft = 0
    private var previousRight = 0
    init { require(rate > 0 && channels in 1..2) }
    fun convert(input: ByteBuffer, frames: Int): ShortArray {
        val result = ShortArray(((frames.toLong() * 44100 / rate + 3) * 2).toInt())
        var used = 0
        repeat(frames) {
            val left = input.short.toInt()
            val right = if (channels == 2) input.short.toInt() else left
            while (outputFrame.toDouble() * rate / 44100 <= inputFrame) {
                val sampleAt = outputFrame.toDouble() * rate / 44100
                val fraction = if (inputFrame == 0L) 1.0 else (sampleAt - (inputFrame - 1)).coerceIn(0.0, 1.0)
                result[used++] = (previousLeft + (left - previousLeft) * fraction).toInt().toShort()
                result[used++] = (previousRight + (right - previousRight) * fraction).toInt().toShort()
                outputFrame++
            }
            previousLeft = left; previousRight = right; inputFrame++
        }
        return result.copyOf(used)
    }
}
