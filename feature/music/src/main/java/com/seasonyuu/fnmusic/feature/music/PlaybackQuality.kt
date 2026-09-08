package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.AudioSpec
import java.util.Locale

/** Describes the source stream; it does not imply a device output sample rate. */
internal fun playbackQualityLabel(audio: AudioSpec?): String {
    if (audio == null) return "音质未知"
    val format = audio.format?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)
    val codec = audio.codec?.lowercase(Locale.ROOT)
    val lossless = codec in setOf("flac", "alac", "ape", "wavpack", "tta") ||
        codec?.startsWith("pcm_") == true ||
        (codec.isNullOrBlank() && format in setOf("FLAC", "ALAC", "APE", "WAV", "AIFF"))
    val quality = when {
        codec?.startsWith("dsd") == true || format in setOf("DSF", "DFF") -> "DSD"
        lossless && (audio.bitDepth ?: 0) > 16 && (audio.sampleRate ?: 0) > 44_100 -> "Hi-Res 无损"
        lossless -> "无损"
        else -> null
    }
    return listOfNotNull(
        quality, format,
        if (!lossless) audio.bitrate?.takeIf { it > 0 }?.let { "${kotlin.math.round(it / 1000.0).toLong()} kbps" } else null,
    ).joinToString(" · ").ifEmpty { "音质未知" }
}
