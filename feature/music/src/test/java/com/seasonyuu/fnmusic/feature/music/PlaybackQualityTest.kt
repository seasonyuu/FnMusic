package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.AudioSpec
import org.junit.Assert.*
import org.junit.Test

class PlaybackQualityTest {
    @Test fun losslessAndHighResolutionRequireLosslessCodec() {
        assertEquals("无损 · FLAC", playbackQualityLabel(AudioSpec(format = "flac", codec = "flac", sampleRate = 44100, bitDepth = 16)))
        assertEquals("Hi-Res 无损 · FLAC", playbackQualityLabel(AudioSpec(format = "flac", codec = "flac", sampleRate = 96000, bitDepth = 24)))
        assertEquals("MP3 · 320 kbps", playbackQualityLabel(AudioSpec(format = "mp3", codec = "mp3", sampleRate = 48000, bitrate = 320000)))
        assertFalse(playbackQualityLabel(AudioSpec(format = "wav", codec = "mp3", sampleRate = 96000, bitDepth = 24)).contains("无损"))
    }
    @Test fun missingMetadataDoesNotInventQuality() {
        assertEquals("音质未知", playbackQualityLabel(null))
        assertEquals("音质未知", playbackQualityLabel(AudioSpec(sampleRate = 0, bitrate = 0, bitDepth = 0)))
    }
}
