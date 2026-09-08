package com.seasonyuu.fnmusic.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackDtoTest {
    @Test
    fun `metadata preserves file details and editable tags`() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val dto = json.decodeFromString<TrackMetadataDto>("""{
          "track":{"guid":"track","title":"Song","createdAt":1788520908,"discNo":2,
            "genres":[{"guid":"genre","name":"Pop"}],"year":2024,"trackNo":8},
          "audioSpec":{"bitrate":847806,"path":"/music/album/song.flac","format":"flac","duration":266230}
        }""")
        val track = dto.track.toDomain()
        assertEquals(2, track.discNo)
        assertEquals(1788520908L, track.createdAt)
        assertEquals("Pop", track.genres.single().name)
        assertEquals(847806L, dto.audioSpec?.toDomain()?.bitrate)
        assertEquals("/music/album/song.flac", dto.audioSpec?.toDomain()?.path)
    }

    @Test
    fun `service duration milliseconds are normalized to domain seconds`() {
        val track = TrackDto(
            guid = "track-placeholder",
            duration = 158_828.0,
            audioSpec = AudioSpecDto(duration = 158_828.0),
        ).toDomain()

        assertEquals(158.828, track.durationSeconds, 0.001)
        assertEquals(158.828, track.audioSpec?.duration ?: 0.0, 0.001)
    }
}
