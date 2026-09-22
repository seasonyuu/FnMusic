package com.seasonyuu.fnmusic.core.airplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.seasonyuu.fnmusic.core.model.AirPlayDevice
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** The service owns this connection. JNI and sockets live exclusively on its worker. */
interface AirPlayAudioOutput {
    val ready: Boolean
    fun play(value: Boolean)
    fun offer(samples: ShortArray): Boolean
    fun flush()
    fun positionFrames(): Long
    fun pending(): Boolean
}

interface AirPlaySession : AirPlayAudioOutput, AutoCloseable {
    val device: AirPlayDevice
    val hasNonzeroAudio: Boolean
    fun connect()
    fun submitPin(pin: String)
    fun volume(value: Float)
    fun nowPlaying(value: AirPlayNowPlaying) {}
    fun sentFrames(): Long
}

class AirPlayConnection(
    context: Context,
    override val device: AirPlayDevice,
    private val onEvent: (String) -> Unit,
) : AirPlaySession {
    private val store = AirPlayCredentialStore(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val nowPlaying = java.util.concurrent.atomic.AtomicReference<ByteArray?>()
    override fun nowPlaying(value: AirPlayNowPlaying) { if (!closed.get()) nowPlaying.set(value.encode()) }
    private val commands = ConcurrentLinkedQueue<String>()
    private val pcm = PcmQueue(44100) // 0.5 s of stereo PCM; offer never blocks the decoder.
    private val epoch = AtomicLong(0)
    @Volatile private var acknowledgedEpoch = 0L
    @Volatile override var ready = false
        private set
    @Volatile override var hasNonzeroAudio = false
        private set
    private var framesSent = 0L
    private var acceptedFrames = 0L
    private var estimatedFrames = 0L
    private val pendingPlayback = ArrayDeque<Pair<Long, Long>>()

    override fun connect() = worker.execute {
        try {
            NativeAirPlaySession().run(device.host, device.port, device.id, store.read(device.id),
                object : NativeAirPlaySession.Callbacks {
                    override fun onEvent(event: String) {
                        // Native events are fixed categories (never receiver text or PINs).
                        android.util.Log.i("FnMusicAirPlay", event)
                        if (event.startsWith("diagnostic:")) return
                        if (event == "audio_nonzero") { hasNonzeroAudio = true; return }
                        if (event.startsWith("flushed:")) {
                            acknowledgedEpoch = event.substringAfter(':').toLong()
                            return
                        }
                        if (event == "connected") ready = true
                        if (event in listOf("connection_failed", "disconnected", "flush_failed", "connection_timeout", "receiver_response_timeout", "receiver_closed_before_ready", "receiver_request_rejected")) ready = false
                        handler.post { if (!closed.get()) this@AirPlayConnection.onEvent(event) }
                    }
                    override fun pollCommand() = commands.poll().orEmpty()
                    override fun pollNowPlaying() = nowPlaying.getAndSet(null)
                    override fun pollPcm(maxSamples: Int): ShortArray =
                        if (acknowledgedEpoch == epoch.get()) pcm.poll(maxSamples) else shortArrayOf()
                    override fun onFrames(frames: Int) {
                        synchronized(this@AirPlayConnection) {
                            if (acknowledgedEpoch != epoch.get()) return
                            framesSent += frames
                            // Protocol latency is fixed by the sender (66150 frames / 44100).
                            pendingPlayback.addLast(SystemClock.elapsedRealtime() + 1500L to framesSent)
                        }
                    }
                    override fun saveCredentials(credentials: String) = store.write(device.id, credentials)
                    override fun isCancelled() = closed.get()
                })
        } catch (error: Exception) {
            android.util.Log.w("FnMusicAirPlay", "connection_exception:" + error.javaClass.simpleName)
            ready = false
            handler.post { if (!closed.get()) onEvent("connection_failed") }
        } catch (error: LinkageError) {
            android.util.Log.w("FnMusicAirPlay", "native_link_failure:" + error.javaClass.simpleName)
            ready = false
            handler.post { if (!closed.get()) onEvent("unsupported_architecture") }
        }
    }
    override fun submitPin(pin: String) {
        require(pin.length == 4 && pin.all { it in '0'..'9' })
        commands.add("pin:$pin")
    }
    override fun volume(value: Float) { commands.add("volume:${value.coerceIn(0f, 100f)}") }
    override fun play(value: Boolean) { commands.add(if (value) "play" else "pause") }
    @Synchronized override fun offer(samples: ShortArray): Boolean {
        if (closed.get() || !pcm.offer(samples)) return false
        acceptedFrames += samples.size / 2
        return true
    }
    @Synchronized override fun flush() {
        val next = epoch.incrementAndGet()
        pcm.clear(); pendingPlayback.clear(); framesSent = 0; acceptedFrames = 0; estimatedFrames = 0
        commands.add("flush:$next")
    }
    @Synchronized override fun positionFrames(): Long {
        val now = SystemClock.elapsedRealtime()
        while (pendingPlayback.isNotEmpty() && pendingPlayback.first().first <= now) {
            estimatedFrames = pendingPlayback.removeFirst().second
        }
        return estimatedFrames
    }
    @Synchronized override fun sentFrames(): Long = framesSent
    @Synchronized override fun pending(): Boolean = acceptedFrames > positionFrames() || acknowledgedEpoch != epoch.get()
    override fun close() {
        closed.set(true); nowPlaying.set(null); ready = false; pcm.clear(); commands.clear(); worker.shutdown()
    }
}

internal class PcmQueue(private val capacity: Int) {
    private val data = ShortArray(capacity)
    private var read = 0
    private var write = 0
    var size = 0
        @Synchronized get
        private set
    @Synchronized fun offer(samples: ShortArray): Boolean {
        if (samples.size > capacity - size) return false
        for (value in samples) { data[write] = value; write = (write + 1) % capacity }
        size += samples.size
        return true
    }
    @Synchronized fun poll(maxSamples: Int): ShortArray {
        val count = minOf(size, maxSamples) / 2 * 2
        val result = ShortArray(count)
        for (i in result.indices) { result[i] = data[read]; read = (read + 1) % capacity }
        size -= count
        return result
    }
    @Synchronized fun clear() { read = 0; write = 0; size = 0 }
}
