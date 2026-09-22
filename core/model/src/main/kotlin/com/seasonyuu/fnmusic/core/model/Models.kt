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

enum class StreamingQuality { Original, Standard }
data class StreamingQualityPreference(val wifi: StreamingQuality = StreamingQuality.Original, val mobile: StreamingQuality = StreamingQuality.Original)

data class PlaybackCachePreference(
    val enabled: Boolean = true,
    val bytes: Long = 512L * 1024L * 1024L,
    val tracks: Int = 0,
)

enum class AppearancePreference { Dark, Light, System }

data class MusicUser(
    val id: String,
    val name: String,
    val role: String? = null,
    val lastAccessedAt: String? = null,
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
        val user: MusicUser? = null,
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
    val bitrate: Long? = null,
    val path: String? = null,
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
    val discNo: Int? = null,
    val createdAt: Long? = null,
    val genres: List<Genre> = emptyList(),
)

@Serializable
data class Playlist(
    val id: PlaylistId,
    val name: String,
    val coverId: String? = null,
    val trackCount: Int? = null,
)

@Serializable
data class Genre(val guid: String, val name: String)

data class TrackTagOptions(val artists: List<Artist>, val genres: List<Genre>)

data class TrackMetadataEdit(
    val title: String,
    val album: String?,
    val artistGUIDs: List<String>,
    val genreGUIDs: List<String>,
    val year: Int?,
    val trackNo: Int?,
    val discNo: Int?,
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
    val segments: List<LyricSegment> = emptyList(),
    val timingSource: LyricTimingSource? = null,
    val endTimeMs: Long? = null,
)

@Serializable
enum class LyricTimingSource { Accurate, Estimated, Line }

/** UTF-16 text range [startOffset, endOffset), with absolute media times in milliseconds. */
@Serializable
data class LyricSegment(
    val startOffset: Int,
    val endOffset: Int,
    val startMs: Long,
    val endMs: Long,
)

data class SearchSuggestions(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val trackTotal: Int = tracks.size,
    val albumTotal: Int = albums.size,
    val artistTotal: Int = artists.size,
    val playlistTotal: Int = playlists.size,
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

enum class PlaybackStatus {
    Idle,
    Buffering,
    Playing,
    Paused,
    Ended,
}

data class PlayableTrack(
    val track: Track,
    val streamUrl: String,
    val coverUrl: String? = null,
    /** Identifies one queue occurrence, including duplicate songs. Copies keep this identity. */
    val queueEntryId: String = java.util.UUID.randomUUID().toString(),
)

data class PlayerState(
    val outputDeviceName: String? = null,
    val playbackAudioSpec: AudioSpec? = null,
    val queue: List<PlayableTrack> = emptyList(),
    /** Local-only history for the active playback list. It is deliberately not persisted. */
    val playbackHistory: List<PlayableTrack> = emptyList(),
    /** Changes only when a different playback list is started or restored. */
    val playbackSessionId: Long = 0,
    val currentIndex: Int = -1,
    val playbackStatus: PlaybackStatus = PlaybackStatus.Idle,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.Off,
    val isRoaming: Boolean = false,
    val error: String? = null,
    /** Media3 timeline indices in playback order, including its actual shuffle order. */
    val playbackOrder: List<Int> = emptyList(),
) {
    val isPlaying: Boolean
        get() = playbackStatus == PlaybackStatus.Playing

    val isBuffering: Boolean
        get() = playbackStatus == PlaybackStatus.Buffering

    val playbackIntentActive: Boolean
        get() = playbackStatus == PlaybackStatus.Playing || playbackStatus == PlaybackStatus.Buffering

    val current: PlayableTrack?
        get() = queue.getOrNull(currentIndex)

    val orderedQueueIndices: List<Int>
        get() = playbackOrder.ifEmpty { queue.indices.toList() }

    val upcomingQueueIndices: List<Int>
        get() {
            val order = orderedQueueIndices
            val position = order.indexOf(currentIndex)
            if (position < 0) return emptyList()
            return order.drop(position + 1) + if (repeatMode == RepeatMode.All) order.take(position) else emptyList()
        }

    val canSkipNext: Boolean
        get() = isRoaming || upcomingQueueIndices.isNotEmpty() || (current != null && repeatMode != RepeatMode.Off)

    val canSkipPrevious: Boolean
        get() = orderedQueueIndices.indexOf(currentIndex) > 0 || (current != null && repeatMode != RepeatMode.Off)
}

/** Editable login details, stored separately from the active session in the encrypted vault. */
@Serializable
data class LoginForm(
    val useDirectConnection: Boolean = false,
    val fnId: String = "",
    val directUrl: String = "",
    val username: String = "",
    val password: String = "",
    val allowPrivateLanHttp: Boolean = false,
) {
    // Do not expose passwords through incidental logging.
    override fun toString(): String = "LoginForm(redacted)"
}

interface SessionRepository {
    val state: StateFlow<SessionState>
    suspend fun connect(profile: ConnectionProfile, password: CharArray)
    suspend fun reconnect()
    suspend fun logout(clearCredentials: Boolean)
}

interface PlayerController : PlaybackOutputController {
    val state: StateFlow<PlayerState>
    fun play(items: List<PlayableTrack>, startIndex: Int = 0, isRoaming: Boolean = false)
    fun restore(
        items: List<PlayableTrack>,
        startIndex: Int,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
        isRoaming: Boolean = false,
    )
    /** Refreshes matching metadata without changing queue order or playback position. */
    fun updateTracks(tracks: List<PlayableTrack>)
    fun clear()
    fun pause()
    fun resume()
    fun seekTo(positionMs: Long)
    fun skipNext()
    fun skipPrevious()
    fun skipTo(index: Int)
    /** Plays an entry from the active list's local playback history. */
    fun skipToHistoryItem(index: Int)
    fun clearPlaybackHistory()
    fun playNext(item: PlayableTrack)
    fun append(items: List<PlayableTrack>)
    /** Reorders the local playback queue. This never changes a saved playlist. */
    fun moveQueueItem(fromIndex: Int, toIndex: Int)
    fun removeFromQueue(index: Int)
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

/** Ordered accent catalog from the official Music Web design tokens. */
enum class ThemeColorPreference(val id: String, val argb: Long, val label: String) {
    Default("red", 0xFFF62C55, "红色"),
    Pink("pink", 0xFFF05672, "粉色"),
    Purple("purple", 0xFFC934E1, "紫色"),
    Blue("blue", 0xFF1B73FB, "蓝色"),
    Green("green", 0xFF6BAB45, "绿色");
    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Default
    }
}
