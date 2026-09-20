package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsCandidateRankingTest {
    @Test fun ranksRecordingTitleThenArtistThenAlbumWithoutDroppingOtherResults() {
        val track = Track(TrackId("1"), "怪咖", artists = listOf(Artist(ArtistId("a"), "薛之谦")), album = Album(AlbumId("b"), "怪咖"))
        fun row(id: String, title: String = "怪咖", artists: List<String> = listOf("薛之谦"), album: String = "怪咖") =
            LyricsCandidate(id, listOf(title), artists, listOf(album))
        val live = row("live", "怪咖 (Live)")
        val cover = row("cover", artists = listOf("另一歌手"))
        val otherAlbum = row("other", album = "精选")
        val exact = row("exact", artists = listOf("Joker Xue", "薛之谦"))
        val tie = row("tie")
        assertEquals(listOf(exact, tie, otherAlbum, cover, live), rankLyricsCandidates(listOf(live, cover, otherAlbum, exact, tie), track))
    }
    @Test fun normalizesUnicodeCaseAndWhitespaceAndPreservesTies() {
        val track = Track(TrackId("1"), " SONG ")
        val first = LyricsCandidate("1", listOf("ＳＯＮＧ"), emptyList())
        val second = LyricsCandidate("2", listOf("song"), emptyList())
        assertEquals(listOf(first, second), rankLyricsCandidates(listOf(first, second), track))
    }
}
