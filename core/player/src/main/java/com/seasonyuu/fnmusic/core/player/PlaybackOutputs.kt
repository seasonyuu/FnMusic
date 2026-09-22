package com.seasonyuu.fnmusic.core.player

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.seasonyuu.fnmusic.core.airplay.AirPlayConnection
import com.seasonyuu.fnmusic.core.airplay.AirPlaySession
import com.seasonyuu.fnmusic.core.airplay.AirPlayDiscovery
import com.seasonyuu.fnmusic.core.model.*

internal object OutputCommands {
    val command = SessionCommand("com.seasonyuu.fnmusic.OUTPUT", Bundle.EMPTY)
    fun encode(state: OutputState) = Bundle().apply {
        putString("target", (state.output as? PlaybackOutput.AirPlay)?.id)
        putString("name", (state.output as? PlaybackOutput.AirPlay)?.name)
        putBoolean("scanning", state.scanning); putBoolean("pairing", state.pairing)
        putString("connecting", state.connecting?.id)
        putBundle("connectingDevice", state.connecting?.let { d -> Bundle().apply {
            putString("id", d.id); putString("name", d.name); putString("host", d.host); putInt("port", d.port)
            putString("model", d.model); putString("features", d.features); putString("type", d.serviceType)
        } })
        putFloat("volume", state.volume); putString("error", state.error)
        putLong("sentFrames", state.sentFrames); putBoolean("nonzero", state.hasNonzeroAudio)
        putParcelableArrayList("devices", ArrayList(state.devices.map { d -> Bundle().apply {
            putString("id", d.id); putString("name", d.name); putString("host", d.host); putInt("port", d.port)
            putString("model", d.model); putString("features", d.features); putString("type", d.serviceType)
        } }))
    }
    @Suppress("DEPRECATION") fun decode(bundle: Bundle): OutputState {
        val devices = bundle.getParcelableArrayList<Bundle>("devices").orEmpty().map {
            AirPlayDevice(it.getString("id").orEmpty(), it.getString("name").orEmpty(), it.getString("host").orEmpty(),
                it.getInt("port"), it.getString("model").orEmpty(), it.getString("features").orEmpty(), it.getString("type").orEmpty())
        }
        return OutputState(output = bundle.getString("target")?.let { PlaybackOutput.AirPlay(it, bundle.getString("name").orEmpty()) } ?: PlaybackOutput.Local,
            devices = devices, scanning = bundle.getBoolean("scanning"), connecting = bundle.getBundle("connectingDevice")?.let {
                AirPlayDevice(it.getString("id").orEmpty(), it.getString("name").orEmpty(), it.getString("host").orEmpty(),
                    it.getInt("port"), it.getString("model").orEmpty(), it.getString("features").orEmpty(), it.getString("type").orEmpty())
            } ?: devices.find { it.id == bundle.getString("connecting") },
            pairing = bundle.getBoolean("pairing"), volume = bundle.getFloat("volume", 20f), error = bundle.getString("error"),
            sentFrames = bundle.getLong("sentFrames"), hasNonzeroAudio = bundle.getBoolean("nonzero"))
    }
}

/** Main-thread service state; the renderer sees only the volatile selected connection. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class PlaybackOutputs(private val context: Context, private val player: () -> ExoPlayer,
    private val hasNetworkPermission: () -> Boolean = {
        android.os.Build.VERSION.SDK_INT < 37 ||
            context.checkSelfPermission(android.Manifest.permission.ACCESS_LOCAL_NETWORK) == android.content.pm.PackageManager.PERMISSION_GRANTED
    },
    private val createConnection: (AirPlayDevice, (String) -> Unit) -> AirPlaySession = { device, event -> AirPlayConnection(context, device, event) },
) : AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    @Volatile var connection: AirPlaySession? = null
        private set
    private var candidate: AirPlaySession? = null
    private var discovery: AirPlayDiscovery? = null
    private var generation = 0
    var state = OutputState()
        private set
    fun command(args: Bundle): SessionResult {
        if (!hasNetworkPermission()) {
            if (state.scanning || candidate != null || connection?.ready == true) permissionLost()
            if (args.getString("operation") in listOf("select", "pin") ||
                (args.getString("operation") == "scan" && args.getBoolean("enabled"))) {
                state = state.copy(error = "需要本地网络权限", scanning = false)
                return SessionResult(SessionError.ERROR_PERMISSION_DENIED, OutputCommands.encode(state))
            }
        }
        try {
            when (args.getString("operation")) {
                "get" -> Unit
                "cancel" -> cancelConnection()
                "scan" -> scan(args.getBoolean("enabled"))
                "select" -> select(args.getString("id").orEmpty())
                "pin" -> candidate?.submitPin(args.getString("pin").orEmpty())
                "volume" -> {
                    val value = args.getFloat("volume"); require(value.isFinite())
                    state = state.copy(volume = value.coerceIn(0f, 100f)); connection?.volume(state.volume)
                }
                "local" -> {
                    generation++; candidate?.close(); candidate = null
                    val old = connection; connection = null; handoff(); old?.close()
                    state = state.copy(output = PlaybackOutput.Local, connecting = null, pairing = false, error = null)
                }
                else -> return SessionResult(SessionError.ERROR_BAD_VALUE)
            }
        } catch (_: SecurityException) {
            state = state.copy(error = "需要本地网络权限", scanning = false)
            permissionLost()
        } catch (_: IllegalArgumentException) { return SessionResult(SessionError.ERROR_BAD_VALUE) }
        state = state.copy(sentFrames = connection?.sentFrames() ?: 0, hasNonzeroAudio = connection?.hasNonzeroAudio ?: false)
        return SessionResult(SessionResult.RESULT_SUCCESS, OutputCommands.encode(state))
    }
    private fun scan(enabled: Boolean) {
        discovery?.close(); discovery = null
        state = state.copy(scanning = enabled, error = if (enabled) null else state.error)
        if (!enabled) return
        val scanner = AirPlayDiscovery(context)
        discovery = scanner
        scanner.start({ devices -> main.post { if (discovery === scanner) discovered(devices) } },
            { reason -> main.post {
                if (discovery === scanner) {
                    state = state.copy(error = if (reason == "local_network_permission_required") "需要本地网络权限" else "设备搜索失败，请重试", scanning = false)
                    if (reason == "local_network_permission_required") permissionLost()
                }
            } })
    }
    internal fun discovered(devices: List<AirPlayDevice>) { state = state.copy(devices = devices) }
    private fun select(id: String) {
        val device = state.devices.firstOrNull { it.id == id } ?: throw IllegalArgumentException("Unknown device")
        require(device.serviceType.contains("_airplay")) { "Requires AirPlay 2 endpoint" }
        if (connection?.device?.id == id && connection?.ready == true) {
            cancelConnection()
            return
        }
        generation++; val token = generation
        candidate?.close()
        state = state.copy(connecting = device, pairing = false, error = null)
        lateinit var next: AirPlaySession
        next = createConnection(device, callback@{ event ->
            if (token != generation && connection !== next) return@callback
            if (event.startsWith("remote:")) {
                if (connection !== next || !next.ready) return@callback
                when (event.substringAfter(':')) {
                    "play" -> player().play()
                    "paus" -> player().pause()
                    "plps" -> if (player().playWhenReady) player().pause() else player().play()
                    "nitm" -> player().seekToNextMediaItem()
                    "pitm" -> player().seekToPreviousMediaItem()
                }
                return@callback
            }
            when (event) {
                "pairing" -> if (candidate === next) state = state.copy(pairing = true)
                "connected" -> {
                    if (candidate !== next) return@callback
                    val selected = next
                    val old = connection; connection = selected; candidate = null
                    selected.volume(state.volume)
                    handoff(); old?.close()
                    state = state.copy(output = PlaybackOutput.AirPlay(device.id, device.name), connecting = null, pairing = false, error = null)
                }
                "connection_failed", "disconnected", "flush_failed", "connection_timeout",
                "receiver_response_timeout", "receiver_closed_before_ready", "receiver_request_rejected", "unsupported_architecture" -> {
                    if (candidate === next) { candidate?.close(); candidate = null }
                    else if (connection === next) { next.close(); player().pause() }
                    val message = when (event) {
                        "receiver_response_timeout" -> "等待接收设备响应已超时。请检查接收设备是否有待确认的 AirPlay 弹窗，确认后重试。"
                        "receiver_request_rejected" -> "接收设备拒绝了连接请求。请检查接收端确认弹窗或访问设置，再重试。"
                        "receiver_closed_before_ready" -> "接收设备已结束连接请求。请重新选择设备，并及时确认接收端弹窗。"
                        "unsupported_architecture" -> "当前设备架构不支持 AirPlay 输出。"
                        else -> "AirPlay 连接中断或失败，请重试或切回本机"
                    }
                    state = state.copy(connecting = candidate?.device,
                        pairing = candidate != null && state.pairing, error = message)
                }
                else -> Unit
            }
        })
        candidate = next
        next.connect()
    }
    private fun cancelConnection() {
        generation++; candidate?.close(); candidate = null
        state = state.copy(connecting = null, pairing = false)
    }
    private fun handoff() {
        val p = player(); val intent = p.playWhenReady; val position = p.currentPosition
        p.stop(); p.seekTo(position); p.prepare(); p.playWhenReady = intent
    }
    fun permissionLost() {
        scan(false); generation++; candidate?.close(); candidate = null
        if (connection != null) { player().pause(); connection?.close() }
        state = state.copy(connecting = null, pairing = false, error = "本地网络权限已撤销，请授权后重新选择设备")
    }
    override fun close() {
        generation++; discovery?.close(); candidate?.close(); connection?.close(); connection = null
    }
}
