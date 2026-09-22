package com.seasonyuu.fnmusic.core.airplay

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Immutable snapshot of the service player's state; no URLs or credentials go on the wire. */
data class AirPlayNowPlaying(
    val title: String, val artist: String, val album: String,
    val positionMs: Long, val durationMs: Long, val playing: Boolean,
    val artwork: ByteArray = byteArrayOf(),
) {
    internal fun encode(): ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            for (text in listOf(title, artist, album)) {
                val utf8 = text.take(4096).toByteArray(Charsets.UTF_8)
                out.writeInt(utf8.size); out.write(utf8)
            }
            out.writeLong(positionMs.coerceAtLeast(0)); out.writeLong(durationMs.coerceAtLeast(0))
            out.writeBoolean(playing)
            require(artwork.size <= 512 * 1024)
            out.writeInt(artwork.size); out.write(artwork)
        }
    }.toByteArray()
}
