package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.Album
import com.seasonyuu.fnmusic.core.model.Artist
import com.seasonyuu.fnmusic.core.model.Playlist
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackMetadata

/** Last successful data for a resource; UI and playback state remain owned by MusicUiState. */
data class MusicDetailSnapshot(
    val tracks: List<Track> = emptyList(),
    val album: Album? = null,
    val artist: Artist? = null,
    val playlist: Playlist? = null,
    val metadata: TrackMetadata? = null,
)

fun MusicUiState.cacheCurrentDetail(): MusicUiState {
    val key = detailKey ?: return this
    if (detailLoading || detailError != null) return this
    return copy(detailCache = detailCache + (key to MusicDetailSnapshot(
        tracks = detailTracks, album = detailAlbum, artist = detailArtist,
        playlist = detailPlaylist, metadata = detailMetadata,
    )))
}

fun MusicUiState.forDetail(key: DetailRequestKey?): MusicUiState {
    if (detailKey == key) return this
    val cached = detailCache[key]
    return copy(
        detailKey = key,
        detailTracks = cached?.tracks.orEmpty(),
        detailAlbum = cached?.album,
        detailArtist = cached?.artist,
        detailPlaylist = cached?.playlist,
        detailMetadata = cached?.metadata,
        detailLoading = key != null && cached == null,
        detailError = null,
    )
}
