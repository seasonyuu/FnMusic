package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.data.SearchItem
import org.junit.Assert.*
import org.junit.Test

class SearchStateTest {
    @Test fun `best results preserve type order and deduplicate songs by identity`() {
        val first = Track(TrackId("shared"), "first")
        val second = Track(TrackId("second"), "second")
        val result = SearchSuggestions(
            artists = listOf(Artist(ArtistId("shared"), "artist"), Artist(ArtistId("other"), "other")),
            albums = listOf(Album(AlbumId("shared"), "album"), Album(AlbumId("other"), "other")),
            playlists = listOf(Playlist(PlaylistId("shared"), "playlist")),
            tracks = listOf(first, second, first.copy(title = "duplicate")),
        ).bestResults()
        assertEquals(listOf("artist:shared", "album:shared", "playlist:shared", "track:shared", "track:second"), result.map { it.searchKey() })
        assertEquals(first, (result[3] as SearchItem.TrackItem).value)
    }

    @Test fun `missing categories produce no placeholder rows`() {
        assertTrue(SearchSuggestions().bestResults().isEmpty())
        val track = Track(TrackId("only"), "only")
        assertEquals(listOf(SearchItem.TrackItem(track)), SearchSuggestions(tracks = listOf(track)).bestResults())
    }

    @Test fun `category filters sort by result totals while best stays first`() {
        val state = SearchUiState(categoryCounts = mapOf(
            SearchFilter.Track to 12,
            SearchFilter.Playlist to 8,
            SearchFilter.Album to 3,
            SearchFilter.Artist to 1,
        ))
        assertEquals(
            listOf(SearchFilter.Best, SearchFilter.Track, SearchFilter.Playlist, SearchFilter.Album, SearchFilter.Artist),
            state.orderedFilters(),
        )
    }
}
