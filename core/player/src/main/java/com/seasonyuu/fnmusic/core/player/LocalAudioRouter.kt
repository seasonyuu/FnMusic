package com.seasonyuu.fnmusic.core.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.seasonyuu.fnmusic.core.model.*

internal interface LocalAudioRouter : AutoCloseable {
    val state: LocalAudioState
    fun request(id: Int?): Boolean
    fun activate(active: Boolean)
    fun tick(playing: Boolean)
}

/** Main-thread device inventory and actual AudioTrack routing; no Bluetooth pairing or discovery. */
internal class AndroidLocalAudioRouter(
    context: Context,
    private val setPreferred: (AudioDeviceInfo?) -> Unit,
    private val pause: () -> Unit,
) : LocalAudioRouter {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val tracker = LocalRouteTracker()
    override val state get() = tracker.state
    private var devices = emptyList<AudioDeviceInfo>()
    private var track: AudioTrack? = null
    private var active = true
    private var closed = false
    private val routingListener = AudioRouting.OnRoutingChangedListener { router ->
        if (!closed && active && router === track) tick(track?.playState == AudioTrack.PLAYSTATE_PLAYING)
    }
    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }
    init { refresh(); audio.registerAudioDeviceCallback(deviceCallback, main) }

    private fun refresh() {
        if (closed) return
        devices = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        val lost = tracker.devices(devices.mapNotNull(::localDevice))
        if (lost) {
            setPreferred(null)
            if (active) pause()
        }
    }
    override fun request(id: Int?): Boolean {
        refresh()
        val device = id?.let { wanted -> devices.firstOrNull { it.id == wanted && localDevice(it) != null } }
        if (id != null && device == null) {
            tracker.fail("设备已不可用，请重新选择。")
            return false
        }
        return try {
            setPreferred(device)
            tracker.request(id)
            true
        } catch (_: RuntimeException) {
            tracker.fail("系统拒绝了输出设备切换，请重试。")
            false
        }
    }
    /** Called by the renderer. Registration/removal and callbacks are serialized on main. */
    fun attach(audioTrack: AudioTrack) { main.post {
        if (closed) return@post
        detach()
        track = audioTrack
        audioTrack.addOnRoutingChangedListener(routingListener, main)
    } }
    fun invalidateTrack() { main.post { detach() } }
    private fun detach() {
        track?.let { runCatching { it.removeOnRoutingChangedListener(routingListener) } }
        track = null
    }
    override fun activate(active: Boolean) {
        this.active = active
        if (!active) { tracker.deactivate(); detach() }
    }
    override fun tick(playing: Boolean) {
        if (closed || !active) return
        val actual = runCatching { track?.routedDevice?.id }.getOrNull()
        if (tracker.observe(actual, playing, SystemClock.elapsedRealtime())) pause()
    }
    override fun close() {
        if (closed) return
        closed = true; audio.unregisterAudioDeviceCallback(deviceCallback); detach(); tracker.deactivate()
    }
}

internal fun localDevice(device: AudioDeviceInfo): LocalAudioDevice? {
    if (!device.isSink) return null
    val kind = localDeviceKind(device.type) ?: return null
    val fallback = when (kind) {
        LocalAudioDeviceKind.Speaker -> "手机扬声器"
        LocalAudioDeviceKind.Bluetooth -> "蓝牙音频设备"
        LocalAudioDeviceKind.Wired -> "有线耳机"
        LocalAudioDeviceKind.Usb -> "USB 音频设备"
        LocalAudioDeviceKind.Hdmi -> "HDMI 音频设备"
    }
    return LocalAudioDevice(device.id, if (kind == LocalAudioDeviceKind.Speaker) fallback
        else device.productName?.toString()?.takeIf { it.isNotBlank() } ?: fallback, kind)
}

internal fun localDeviceKind(type: Int): LocalAudioDeviceKind? = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> LocalAudioDeviceKind.Speaker
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_HEARING_AID -> LocalAudioDeviceKind.Bluetooth
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> LocalAudioDeviceKind.Wired
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY, AudioDeviceInfo.TYPE_USB_HEADSET -> LocalAudioDeviceKind.Usb
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> LocalAudioDeviceKind.Hdmi
    else -> null // Exclude earpiece, telephony/SCO and virtual devices from music output choices.
}
