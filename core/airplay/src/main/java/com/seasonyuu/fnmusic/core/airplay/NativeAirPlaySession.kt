package com.seasonyuu.fnmusic.core.airplay

/** Runs synchronously on the connection's dedicated worker, never on UI/decoder threads. */
internal class NativeAirPlaySession {
    interface Callbacks {
        fun onEvent(event: String)
        fun pollCommand(): String
        fun pollNowPlaying(): ByteArray?
        fun pollPcm(maxSamples: Int): ShortArray
        fun onFrames(frames: Int)
        fun saveCredentials(credentials: String)
        fun isCancelled(): Boolean
    }
    external fun run(host: String, port: Int, deviceId: String, credentials: String, callbacks: Callbacks)
    companion object { init { System.loadLibrary("fnmusic_airplay") } }
}
