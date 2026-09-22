package com.seasonyuu.fnmusic.core.player

import com.seasonyuu.fnmusic.core.model.LocalAudioDevice
import com.seasonyuu.fnmusic.core.model.LocalAudioState

/** Platform-independent confirmation and timeout policy; preference is never proof of routing. */
internal class LocalRouteTracker(private val timeoutMs: Long = 8000) {
    var state = LocalAudioState()
        private set
    private var activeMs = 0L
    private var lastTick: Long? = null

    fun devices(devices: List<LocalAudioDevice>): Boolean {
        val available = devices.distinctBy { it.id }
        val missing = state.requestedId?.let { id -> available.none { it.id == id } } == true
        val currentLost = state.currentId?.let { id -> available.none { it.id == id } } == true
        state = state.copy(devices = available, currentId = state.currentId.takeUnless { currentLost })
        if (missing || currentLost) {
            state = state.copy(requestedId = null, followsSystem = true)
            fail("所选设备已断开，播放已暂停。请选择其他设备。")
        }
        return missing || currentLost
    }

    fun request(id: Int?) {
        require(id == null || state.devices.any { it.id == id }) { "Unavailable audio device" }
        activeMs = 0; lastTick = null
        state = state.copy(requestedId = id, pending = true, awaitingPlayback = true,
            followsSystem = id == null, error = null)
    }

    fun observe(actualId: Int?, playing: Boolean, nowMs: Long): Boolean {
        if (playing && actualId != null && state.devices.any { it.id == actualId }) {
            state = state.copy(currentId = actualId)
            if (state.pending && (state.requestedId == null || actualId == state.requestedId)) {
                state = state.copy(pending = false, awaitingPlayback = false, error = null)
            }
        }
        if (!state.pending) { lastTick = null; return false }
        state = state.copy(awaitingPlayback = !playing)
        if (!playing) { lastTick = null; return false }
        lastTick?.let { activeMs += (nowMs - it).coerceAtLeast(0) }
        lastTick = nowMs
        if (activeMs < timeoutMs) return false
        fail("系统未能切换到所选设备，播放已暂停。可重试或使用系统输出选择器。")
        return true
    }

    fun fail(message: String) {
        state = state.copy(pending = false, awaitingPlayback = false, error = message)
        lastTick = null
    }

    fun deactivate() {
        state = state.copy(pending = false, awaitingPlayback = false, currentId = null, error = null)
        lastTick = null; activeMs = 0
    }
}
