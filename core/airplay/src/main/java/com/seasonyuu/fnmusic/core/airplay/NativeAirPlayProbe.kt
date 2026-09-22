package com.seasonyuu.fnmusic.core.airplay

/** Bounded verification harness, not a production playback API. Run off the main thread. */
internal class NativeAirPlayProbe {
    interface Callbacks {
        fun onEvent(event: String)
        fun pollPin(): String
        fun isCancelled(): Boolean
        fun saveCredentials(credentials: String)
    }

    external fun run(
        host: String,
        port: Int,
        seconds: Int,
        deviceId: String,
        credentials: String,
        callbacks: Callbacks,
    ): String

    companion object {
        init { System.loadLibrary("fnmusic_airplay") }
    }
}
