package com.seasonyuu.fnmusic.core.model

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class TrackId(val value: String)

@JvmInline
@Serializable
value class AlbumId(val value: String)

@JvmInline
@Serializable
value class ArtistId(val value: String)

@JvmInline
@Serializable
value class PlaylistId(val value: String)

@Serializable
sealed interface Endpoint {
    @Serializable
    @SerialName("fn_connect")
    data class FnConnect(val fnId: String) : Endpoint

    @Serializable
    @SerialName("direct")
    data class Direct(val baseUrl: String) : Endpoint
}

@Serializable
data class ConnectionProfile(
    val endpoint: Endpoint,
    val username: String,
    val allowPrivateLanHttp: Boolean = false,
)

sealed interface SessionState {
    /** The app is checking encrypted credentials before deciding whether to show login. */
    data object Restoring : SessionState
    data object Unresolved : SessionState
    data object Locating : SessionState
    data object RelaySelected : SessionState
    data object RelayActive : SessionState
    data object MusicAuthenticated : SessionState
    data class Ready(
        val profile: ConnectionProfile,
        val serverName: String? = null,
        val serverVersion: String? = null,
    ) : SessionState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
    ) : SessionState
}

@Serializable
data class Artist(
    val id: ArtistId,
    val name: String,
    val coverId: String? = null,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
)

@Serializable
data class Album(
    val id: AlbumId,
    val name: String,
    val artists: List<Artist> = emptyList(),
    val coverId: String? = null,
    val releaseDate: String? = null,
    val trackCount: Int = 0,
)

@Serializable
data class AudioSpec(
    val format: String? = null,
    val codec: String? = null,
    val size: Long? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channel: Int? = null,
    val duration: Double? = null,
)

@Serializable
data class Track(
    val id: TrackId,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val coverId: String? = null,
    val durationSeconds: Double = 0.0,
    val isFavorite: Boolean = false,
    val audioSpec: AudioSpec? = null,
    val year: Int? = null,
    val trackNo: Int? = null,
)

@Serializable
data class Playlist(
    val id: PlaylistId,
    val name: String,
    val coverId: String? = null,
    val trackCount: Int? = null,
)

@Serializable
data class TrackMetadata(
    val track: Track,
    val audioSpec: AudioSpec? = null,
)

data class RoamItem(
    val roamId: String,
    val track: Track,
)

data class RoamWindow(
    val previous: RoamItem? = null,
    val current: RoamItem? = null,
    val next: RoamItem? = null,
)

@Serializable
data class LyricLine(
    val timeMs: Long? = null,
    val text: String,
    val translation: String? = null,
)

data class SearchSuggestions(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
)

enum class SearchType { Track, Album, Artist, Playlist }

enum class TrackSort(val wireValue: String?) {
    Default(null),
    RecentlyAdded("createdAt,desc"),
    OldestAdded("createdAt,asc"),
    TitleAscending("title,asc"),
    TitleDescending("title,desc"),
}

enum class AlbumSort(val wireValue: String?) {
    Default(null),
    RecentlyUpdated("newTrackAddedAt,desc"),
    OldestUpdated("newTrackAddedAt,asc"),
    NameAscending("name,asc"),
    NameDescending("name,desc"),
}

enum class RepeatMode { Off, All, One }

data class PlayableTrack(
    val track: Track,
    val streamUrl: String,
    val coverUrl: String? = null,
)

data class PlayerState(
    val queue: List<PlayableTrack> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val isRoaming: Boolean = false,
    val error: String? = null,
) {
    val current: PlayableTrack?
        get() = queue.getOrNull(currentIndex)
}

interface SessionRepository {
    val state: StateFlow<SessionState>
    suspend fun connect(profile: ConnectionProfile, password: CharArray)
    suspend fun reconnect()
    suspend fun logout(clearCredentials: Boolean)
}

interface PlayerController {
    val state: StateFlow<PlayerState>
    fun play(items: List<PlayableTrack>, startIndex: Int = 0, isRoaming: Boolean = false)
    fun restore(
        items: List<PlayableTrack>,
        startIndex: Int,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
    )
    fun clear()
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun skipTo(index: Int)
    fun playNext(item: PlayableTrack)
    fun append(items: List<PlayableTrack>)
    fun removeFromQueue(index: Int)
    fun keepCurrentOnly()
    fun setRoaming(enabled: Boolean)
    fun setShuffle(enabled: Boolean)
    fun setRepeatMode(mode: RepeatMode)
}

sealed class MusicError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class Network(cause: Throwable) : MusicError("无法连接到飞牛音乐服务", cause)
    class Authentication(message: String = "登录状态已失效") : MusicError(message)
    class Protocol(message: String) : MusicError(message)
    class Business(val code: Int, message: String) : MusicError(message)
    class UnsafeConnection(message: String) : MusicError(message)
}
