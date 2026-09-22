package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.Album
import com.seasonyuu.fnmusic.core.model.Genre
import com.seasonyuu.fnmusic.core.model.AlbumId
import com.seasonyuu.fnmusic.core.model.Artist
import com.seasonyuu.fnmusic.core.model.ArtistId
import com.seasonyuu.fnmusic.core.model.AudioSpec
import com.seasonyuu.fnmusic.core.model.Playlist
import com.seasonyuu.fnmusic.core.model.PlaylistId
import com.seasonyuu.fnmusic.core.model.RoamItem
import com.seasonyuu.fnmusic.core.model.RoamWindow
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

@Serializable
data class PlaylistCoverUploadDto(val coverId: String)

@Serializable
data class ApiEnvelope<T>(
    val code: Int = -1,
    val msg: String = "",
    val data: T? = null,
)

@Serializable
data class PageDto<T>(
    val list: List<T> = emptyList(),
    val total: Int = 0,
    val sort: String? = null,
)

@Serializable
data class ArtistDto(
    val guid: String,
    val name: String = "",
    val coverId: String? = null,
    val trackCount: Int = 0,
    val albumCount: Int = 0,
) {
    fun toDomain() = Artist(ArtistId(guid), name, coverId, trackCount, albumCount)
}

@Serializable
data class AlbumDto(
    val guid: String,
    val name: String = "",
    val artists: List<ArtistDto> = emptyList(),
    val coverId: String? = null,
    val releaseDate: String? = null,
    val trackCount: Int = 0,
) {
    fun toDomain() = Album(
        id = AlbumId(guid),
        name = name,
        artists = artists.map(ArtistDto::toDomain),
        coverId = coverId,
        releaseDate = releaseDate,
        trackCount = trackCount,
    )
}

@Serializable
data class AudioSpecDto(
    val format: String? = null,
    val codec: String? = null,
    val size: Long? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channel: Int? = null,
    val duration: Double? = null,
    val bitrate: Long? = null,
    val path: String? = null,
) {
    fun toDomain() = AudioSpec(format, codec, size, sampleRate, bitDepth, channel, duration?.div(1_000.0), bitrate, path)
}

@Serializable
data class TrackDto(
    val guid: String,
    val title: String = "",
    val artists: List<ArtistDto> = emptyList(),
    val album: AlbumDto? = null,
    val audioSpec: AudioSpecDto? = null,
    val coverId: String? = null,
    val duration: Double = 0.0,
    val isFavorite: Boolean = false,
    val year: Int? = null,
    val trackNo: Int? = null,
    val discNo: Int? = null,
    val createdAt: Long? = null,
    val genres: List<Genre> = emptyList(),
    val accessStatus: Int? = null,
) {
    fun toDomain(favoriteOverride: Boolean? = null) = Track(
        id = TrackId(guid),
        title = title,
        artists = artists.map(ArtistDto::toDomain),
        album = album?.toDomain(),
        coverId = coverId,
        durationSeconds = (duration.takeIf { it > 0 } ?: audioSpec?.duration ?: 0.0) / 1_000.0,
        isFavorite = favoriteOverride ?: isFavorite,
        audioSpec = audioSpec?.toDomain(),
        year = year,
        trackNo = trackNo,
        discNo = discNo,
        createdAt = createdAt,
        genres = genres,
        accessStatus = accessStatus,
    )
}

@Serializable
data class PlaylistDto(
    val guid: String,
    val name: String = "",
    val coverId: String? = null,
    val trackCount: Int? = null,
) {
    fun toDomain() = Playlist(PlaylistId(guid), name, coverId, trackCount)
}

@Serializable
data class LoginRequest(val username: String, val password: String, val deviceId: String)

@Serializable
data class ChangePasswordRequest(val password: String) {
    override fun toString() = "ChangePasswordRequest(password=<redacted>)"
}

@Serializable
data class LoginData(val userToken: String, val user: JsonObject? = null)

@Serializable
data class FavoriteRequest(val trackGUID: String)

@Serializable
data class PlaylistCreateRequest(
    val name: String,
    val coverId: String? = null,
)

@Serializable
data class PlaylistEditRequest(
    val guid: String,
    val name: String,
    val coverId: String? = null,
)

@Serializable
data class PlaylistTracksRequest(
    val guid: String,
    val trackGUIDs: List<String>,
)

@Serializable
data class PlaylistGuidRequest(val guid: String)

@Serializable
data class TotalDto(val total: Int = 0)

@Serializable
data class EventReportRequest(val events: List<PlaybackEventDto>)

@Serializable
data class PlaybackEventDto(
    val eventType: String = "track_play",
    val occurredAt: Long,
    val payload: PlaybackPayloadDto,
)

@Serializable
data class PlaybackPayloadDto(val trackGUID: String)

@Serializable
data class SystemConfigDto(
    val mediasrvVersion: String? = null,
    val serverGUID: String? = null,
    val serverName: String? = null,
    val serverVersion: String? = null,
)

@Serializable
data class TrackMetadataUpdateRequest(
    val guid: String,
    val title: String,
    val album: String?,
    val artistGUIDs: List<String>,
    val genreGUIDs: List<String>,
    val year: Int?,
    val trackNo: Int?,
    val discNo: Int?,
    val coverId: String?,
) {
    // Explicit nulls clear optional tags, even though normal API requests omit nulls.
    fun toJson(): JsonObject = kotlinx.serialization.json.Json { encodeDefaults = true }
        .encodeToJsonElement(serializer(), this) as JsonObject
}

@Serializable
data class TrackMetadataDto(val track: TrackDto, val audioSpec: AudioSpecDto? = null)

@Serializable
data class RoamItemDto(val roamId: String? = null, val track: TrackDto) {
    fun toDomain(): RoamItem? = roamId?.takeIf(String::isNotBlank)?.let { RoamItem(it, track.toDomain()) }
}

@Serializable
data class RoamDto(
    val previous: RoamItemDto? = null,
    val current: RoamItemDto? = null,
    val next: RoamItemDto? = null,
) {
    fun toDomain() = RoamWindow(
        previous = previous?.toDomain(),
        current = current?.toDomain(),
        next = next?.toDomain(),
    )
}

@Serializable
data class SuggestionGroupDto<T>(
    val items: List<T> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class SearchSuggestionsDto(
    val track: SuggestionGroupDto<TrackDto> = SuggestionGroupDto(),
    val album: SuggestionGroupDto<AlbumDto> = SuggestionGroupDto(),
    val artist: SuggestionGroupDto<ArtistDto> = SuggestionGroupDto(),
    val playlist: SuggestionGroupDto<PlaylistDto> = SuggestionGroupDto(),
)

@Serializable
data class LyricDto(
    val guid: String,
    val content: String = "",
    val isLRC: Boolean = false,
    val offset: Long = 0,
    val source: Int? = null,
)

@Serializable
data class LyricDataDto(
    val list: List<LyricDto> = emptyList(),
    val preferred: String? = null,
)

interface MusicApi {
    @retrofit2.http.Multipart
    @POST("api/v1/static/cover/playlist")
    suspend fun uploadPlaylistCover(@retrofit2.http.Part file: okhttp3.MultipartBody.Part): ApiEnvelope<PlaylistCoverUploadDto>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/user/password-login")
    suspend fun passwordLogin(@Body body: LoginRequest): ApiEnvelope<LoginData>

    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @GET("api/v1/sys/config")
    suspend fun systemConfig(): ApiEnvelope<SystemConfigDto>

    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @GET("api/v1/user/me")
    suspend fun currentUser(): ApiEnvelope<JsonObject>

    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/user/passwd-change")
    suspend fun changePassword(@Body body: ChangePasswordRequest): ApiEnvelope<JsonObject>

    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/track/transcode")
    suspend fun startTranscode(@Body body: JsonObject): ApiEnvelope<JsonObject>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/track/transcode/heartbeat")
    suspend fun heartbeatTranscode(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/track/transcode/quit")
    suspend fun quitTranscode(@Body body: JsonObject): ApiEnvelope<JsonElement>

    @GET("api/v1/app-center/authed-dir/list")
    suspend fun authorizedDirectories(): ApiEnvelope<JsonElement>
    @GET("api/v1/app-center/authed-dir/sub/list")
    suspend fun childDirectories(@Query("parent") parent: String): ApiEnvelope<JsonElement>
    @GET("api/v1/shared-library/detail")
    suspend fun folderDetail(@Query("guid") guid: String): ApiEnvelope<JsonObject>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/shared-library/scan-all")
    suspend fun scanAllFolders(): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/task/cancel")
    suspend fun cancelTask(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/task/retry")
    suspend fun retryTask(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/search/index/rebuild")
    suspend fun rebuildSearchIndex(): ApiEnvelope<JsonObject>

    @GET("api/v1/task/list")
    suspend fun adminTasks(): ApiEnvelope<JsonElement>
    @GET("api/v1/shared-library/list")
    suspend fun adminFolders(): ApiEnvelope<JsonElement>
    @GET("api/v1/user/list")
    suspend fun adminUsers(): ApiEnvelope<JsonElement>
    @GET("api/v1/settings/user")
    suspend fun defaultUserSettings(): ApiEnvelope<JsonObject>
    @GET("api/v1/settings/server")
    suspend fun adminServerSettings(): ApiEnvelope<JsonObject>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/shared-library/create")
    suspend fun createFolder(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/shared-library/edit")
    suspend fun editFolder(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/shared-library/delete")
    suspend fun deleteFolder(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/shared-library/scan")
    suspend fun scanFolder(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/user/create")
    suspend fun createUser(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/user/edit")
    suspend fun editUser(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/settings/user")
    suspend fun saveDefaultUserSettings(@Body body: JsonObject): ApiEnvelope<JsonElement>
    @Headers("$NO_SESSION_RECOVERY_HEADER: true")
    @POST("api/v1/settings/server")
    suspend fun saveAdminServerSettings(@Body body: JsonObject): ApiEnvelope<JsonElement>

    @GET("api/v1/track/list")
    suspend fun tracks(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String? = null,
    ): ApiEnvelope<PageDto<TrackDto>>

    @GET("api/v1/album/list")
    suspend fun albums(
        @Query("page") page: Int,
        @Query("size") size: Int,
        @Query("sort") sort: String? = null,
    ): ApiEnvelope<PageDto<AlbumDto>>

    @GET("api/v1/album/detail")
    suspend fun albumDetail(@Query("guid") guid: String): ApiEnvelope<AlbumDto>

    @GET("api/v1/artist/detail")
    suspend fun artistDetail(@Query("guid") guid: String): ApiEnvelope<ArtistDto>

    @GET("api/v1/artist/list")
    suspend fun artists(@Query("page") page: Int, @Query("size") size: Int): ApiEnvelope<PageDto<ArtistDto>>

    @GET("api/v1/track/album-detail/list")
    suspend fun albumTracks(
        @Query("albumGUID") albumGuid: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiEnvelope<PageDto<TrackDto>>

    @GET("api/v1/track/artist-detail/list")
    suspend fun artistTracks(
        @Query("artistGUID") artistGuid: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiEnvelope<PageDto<TrackDto>>

    @GET("api/v1/track/playlist-detail/list")
    suspend fun playlistTracks(
        @Query("playlistGUID") playlistGuid: String,
        @Query("page") page: Int,
        @Query("size") size: Int,
    ): ApiEnvelope<PageDto<TrackDto>>

    @GET("api/v1/playlist/list")
    suspend fun playlists(): ApiEnvelope<PageDto<PlaylistDto>>

    @GET("api/v1/playlist/detail")
    suspend fun playlistDetail(@Query("guid") guid: String): ApiEnvelope<PlaylistDto>

    @POST("api/v1/playlist/create")
    suspend fun createPlaylist(@Body body: PlaylistCreateRequest): ApiEnvelope<JsonObject>

    @POST("api/v1/playlist/edit")
    suspend fun editPlaylist(@Body body: PlaylistEditRequest): ApiEnvelope<JsonObject>

    @POST("api/v1/playlist/delete")
    suspend fun deletePlaylist(@Body body: PlaylistGuidRequest): ApiEnvelope<JsonObject>

    @POST("api/v1/playlist/add-track")
    suspend fun addPlaylistTracks(@Body body: PlaylistTracksRequest): ApiEnvelope<JsonObject>

    @POST("api/v1/playlist/remove-track")
    suspend fun removePlaylistTracks(@Body body: PlaylistTracksRequest): ApiEnvelope<JsonObject>

    @GET("api/v1/playlist/purge-track-count")
    suspend fun playlistInvalidTrackCount(@Query("guid") guid: String): ApiEnvelope<TotalDto>

    @POST("api/v1/playlist/purge-track")
    suspend fun purgePlaylistTracks(@Body body: PlaylistGuidRequest): ApiEnvelope<JsonObject>

    @GET("api/v1/favorite-track/list")
    suspend fun favorites(@Query("page") page: Int, @Query("size") size: Int): ApiEnvelope<PageDto<TrackDto>>

    @GET("api/v1/play-history/list")
    suspend fun history(@Query("page") page: Int, @Query("size") size: Int): ApiEnvelope<PageDto<TrackDto>>

    @GET("api/v1/track/metadata")
    suspend fun trackMetadata(@Query("guid") guid: String): ApiEnvelope<TrackMetadataDto>

    @POST("api/v1/track/metadata")
    suspend fun updateTrackMetadata(@Body body: JsonObject): ApiEnvelope<JsonObject>

    @GET("api/v1/genre/list")
    suspend fun genres(@Query("page") page: Int, @Query("size") size: Int): ApiEnvelope<PageDto<Genre>>

    @GET("api/v1/track/roam-start")
    suspend fun roamStart(@Query("deviceId") deviceId: String): ApiEnvelope<RoamDto>

    @GET("api/v1/track/roam-next")
    suspend fun roamNext(
        @Query("deviceId") deviceId: String,
        @Query("relativeRoamId") relativeRoamId: String,
    ): ApiEnvelope<RoamDto>

    @GET("api/v1/track/roam-previous")
    suspend fun roamPrevious(
        @Query("deviceId") deviceId: String,
        @Query("relativeRoamId") relativeRoamId: String,
    ): ApiEnvelope<RoamDto>

    @GET("api/v1/lyric/list")
    suspend fun lyrics(@Query("trackGUID") trackGuid: String): ApiEnvelope<LyricDataDto>

    @GET("api/v1/search/suggest")
    suspend fun suggestions(@Query("q") query: String): ApiEnvelope<SearchSuggestionsDto>

    @GET("api/v1/search/track")
    suspend fun searchTracks(@Query("q") query: String, @Query("page") page: Int, @Query("size") size: Int): ApiEnvelope<PageDto<TrackDto>>

    @GET("api/v1/search/album")
    suspend fun searchAlbums(@Query("q") query: String, @Query("page") page: Int, @Query("size") size: Int): ApiEnvelope<PageDto<AlbumDto>>

    @GET("api/v1/search/artist")
    suspend fun searchArtists(@Query("q") query: String, @Query("page") page: Int, @Query("size") size: Int): ApiEnvelope<PageDto<ArtistDto>>

    @GET("api/v1/search/playlist")
    suspend fun searchPlaylists(@Query("q") query: String, @Query("page") page: Int, @Query("size") size: Int): ApiEnvelope<PageDto<PlaylistDto>>

    @POST("api/v1/favorite-track/create")
    suspend fun createFavorite(@Body body: FavoriteRequest): ApiEnvelope<JsonObject>

    @POST("api/v1/favorite-track/delete")
    suspend fun deleteFavorite(@Body body: FavoriteRequest): ApiEnvelope<JsonObject>

    @POST("api/v1/event/report")
    suspend fun reportEvent(@Body body: EventReportRequest): ApiEnvelope<JsonObject>
}
