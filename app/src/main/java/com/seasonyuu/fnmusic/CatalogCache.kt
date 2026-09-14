package com.seasonyuu.fnmusic

import android.content.Context
import com.seasonyuu.fnmusic.core.model.Album
import com.seasonyuu.fnmusic.core.model.ConnectionProfile
import com.seasonyuu.fnmusic.core.model.Endpoint
import com.seasonyuu.fnmusic.core.model.Playlist
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.feature.music.MusicUiState
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A small stale-while-revalidate snapshot for the home screen. */
@Serializable
data class CatalogCacheSnapshot(
    val savedAt: Long,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val trackTotal: Int? = null,
    val serverName: String = "飞牛音乐",
)

class CatalogCache(context: Context, private val json: Json) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun load(profile: ConnectionProfile, now: Long = System.currentTimeMillis()): CatalogCacheSnapshot? {
        if (preferences.getString(KEY_PROFILE, null) != profile.cacheKey()) return null
        val payload = preferences.getString(KEY_PAYLOAD, null) ?: return null
        return runCatching { json.decodeFromString<CatalogCacheSnapshot>(payload) }
            .getOrNull()
            ?.takeIf { now - it.savedAt in 0..MAX_AGE_MS }
    }

    fun save(profile: ConnectionProfile, state: MusicUiState, now: Long = System.currentTimeMillis()) {
        val snapshot = CatalogCacheSnapshot(
            savedAt = now,
            tracks = state.tracks.take(MAX_TRACKS),
            albums = state.albums.take(MAX_ALBUMS),
            favorites = state.favorites.take(MAX_FAVORITES),
            recent = state.recent.take(MAX_RECENT),
            playlists = state.playlists.take(MAX_PLAYLISTS),
            trackTotal = state.trackTotal,
            serverName = state.serverName,
        )
        runCatching { json.encodeToString(snapshot) }.onSuccess { payload ->
            preferences.edit()
                .putString(KEY_PROFILE, profile.cacheKey())
                .putString(KEY_PAYLOAD, payload)
                .apply()
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun ConnectionProfile.cacheKey(): String = buildString {
        append(
            when (val endpoint = endpoint) {
            is Endpoint.FnConnect -> "fn:${endpoint.fnId}"
            is Endpoint.Direct -> "direct:${endpoint.baseUrl}"
            },
        )
        append('|')
        append(username)
    }

    private companion object {
        const val PREFERENCES = "catalog_cache"
        const val KEY_PROFILE = "profile"
        const val KEY_PAYLOAD = "payload"
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1_000
        const val MAX_TRACKS = 60
        const val MAX_ALBUMS = 20
        const val MAX_FAVORITES = 100
        const val MAX_RECENT = 30
        const val MAX_PLAYLISTS = 50
    }
}

fun CatalogCacheSnapshot.toMusicState(previous: MusicUiState = MusicUiState()): MusicUiState = previous.copy(
    loading = false,
    pendingSections = emptySet(),
    loadedSections = setOf(
        com.seasonyuu.fnmusic.feature.music.CatalogSection.Tracks,
        com.seasonyuu.fnmusic.feature.music.CatalogSection.Albums,
        com.seasonyuu.fnmusic.feature.music.CatalogSection.Favorites,
        com.seasonyuu.fnmusic.feature.music.CatalogSection.Recent,
        com.seasonyuu.fnmusic.feature.music.CatalogSection.Playlists,
        com.seasonyuu.fnmusic.feature.music.CatalogSection.TrackTotal,
    ),
    sectionErrors = emptyMap(),
    tracks = tracks,
    albums = albums,
    favorites = favorites,
    recent = recent,
    playlists = playlists,
    trackTotal = trackTotal,
    serverName = serverName,
    error = null,
)
