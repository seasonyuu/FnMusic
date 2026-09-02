package com.seasonyuu.fnmusic.core.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SearchSuggestionsDtoTest {
    @Test
    fun `nested suggestion groups decode from current service shape`() {
        val value = Json { ignoreUnknownKeys = true }.decodeFromString<SearchSuggestionsDto>(
            """
            {
              "track":{"items":[{"guid":"track-placeholder","title":"Track"}],"total":1},
              "album":{"items":[],"total":0},
              "artist":{"items":[],"total":0},
              "playlist":{"items":[],"total":0}
            }
            """.trimIndent(),
        )

        assertEquals(1, value.track.total)
        assertEquals("track-placeholder", value.track.items.single().guid)
        assertNotNull(value.album.items)
    }
}
