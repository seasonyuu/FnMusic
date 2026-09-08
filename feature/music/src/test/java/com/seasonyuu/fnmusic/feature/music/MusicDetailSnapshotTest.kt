package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import org.junit.Assert.*
import org.junit.Test

class MusicDetailSnapshotTest {
    @Test fun switchingResourcesRestoresDataWithoutTakingAnotherPagesLoadingOrError() {
        val album = DetailRequestKey("album", "a")
        val playlist = DetailRequestKey("playlist", "b")
        val tracks = listOf(Track(TrackId("one"), "Album song"))
        val cached = MusicUiState(detailKey = album, detailTracks = tracks).cacheCurrentDetail()
        val other = cached.forDetail(playlist).copy(detailError = "Playlist request failed")
        val restored = other.forDetail(album)
        assertEquals(tracks, restored.detailTracks)
        assertFalse(restored.detailLoading)
        assertNull(restored.detailError)
        assertEquals(album, restored.detailKey)
    }

    @Test fun loadingAndFailedRequestsDoNotReplaceTheLastSuccessfulSnapshot() {
        val key = DetailRequestKey("album", "a")
        val tracks = listOf(Track(TrackId("one"), "Retained song"))
        val cached = MusicUiState(detailKey = key, detailTracks = tracks).cacheCurrentDetail()
        val loading = cached.copy(detailLoading = true, detailTracks = emptyList()).cacheCurrentDetail()
        val failed = loading.copy(detailLoading = false, detailError = "Offline").cacheCurrentDetail()
        assertEquals(tracks, failed.detailCache.getValue(key).tracks)
    }
}
