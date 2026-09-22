package com.seasonyuu.fnmusic.core.model

/** IDs are Android audio-port IDs valid only for the current device connection. */
data class LocalAudioDevice(val id: Int, val name: String, val kind: LocalAudioDeviceKind)
enum class LocalAudioDeviceKind { Speaker, Bluetooth, Wired, Usb, Hdmi }

data class LocalAudioState(
    val devices: List<LocalAudioDevice> = emptyList(),
    val currentId: Int? = null,
    val requestedId: Int? = null,
    val pending: Boolean = false,
    val awaitingPlayback: Boolean = false,
    val followsSystem: Boolean = true,
    val error: String? = null,
)
