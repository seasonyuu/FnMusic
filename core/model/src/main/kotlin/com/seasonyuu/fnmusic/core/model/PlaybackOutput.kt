package com.seasonyuu.fnmusic.core.model

import kotlinx.coroutines.flow.StateFlow

sealed interface PlaybackOutput {
    data object Local : PlaybackOutput
    data class AirPlay(val id: String, val name: String) : PlaybackOutput
}

data class AirPlayDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val model: String,
    val features: String,
    val serviceType: String,
)

data class OutputState(
    val output: PlaybackOutput = PlaybackOutput.Local,
    val devices: List<AirPlayDevice> = emptyList(),
    val scanning: Boolean = false,
    val connecting: AirPlayDevice? = null,
    val pairing: Boolean = false,
    val volume: Float = 20f,
    val error: String? = null,
    val sentFrames: Long = 0,
    val hasNonzeroAudio: Boolean = false,
)

interface PlaybackOutputController {
    val outputState: StateFlow<OutputState> get() = LocalOnlyOutput.state
    fun scanOutputs(start: Boolean) {}
    fun selectOutput(deviceId: String) {}
    fun cancelOutputConnection() {}
    fun submitOutputPin(pin: String) {}
    fun setOutputVolume(volume: Float) {}
    fun useLocalOutput() {}
}

private object LocalOnlyOutput { val state: StateFlow<OutputState> = kotlinx.coroutines.flow.MutableStateFlow(OutputState()) }
