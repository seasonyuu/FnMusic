package com.seasonyuu.fnmusic.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.map
import com.seasonyuu.fnmusic.core.model.Album
import com.seasonyuu.fnmusic.core.model.AlbumSort
import com.seasonyuu.fnmusic.core.model.AlbumId
import com.seasonyuu.fnmusic.core.model.Artist
import com.seasonyuu.fnmusic.core.model.ArtistId
import com.seasonyuu.fnmusic.core.model.Playlist
import com.seasonyuu.fnmusic.core.model.PlaylistId
import com.seasonyuu.fnmusic.core.model.LyricLine
import com.seasonyuu.fnmusic.core.model.RoamWindow
import com.seasonyuu.fnmusic.core.model.SearchSuggestions
import com.seasonyuu.fnmusic.core.model.SearchType
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.core.model.TrackMetadata
import com.seasonyuu.fnmusic.core.model.TrackMetadataEdit
import com.seasonyuu.fnmusic.core.model.TrackTagOptions
import com.seasonyuu.fnmusic.core.network.TrackMetadataUpdateRequest
import com.seasonyuu.fnmusic.core.model.TrackSort
import com.seasonyuu.fnmusic.core.network.AlbumDto
import com.seasonyuu.fnmusic.core.network.EventReportRequest
import com.seasonyuu.fnmusic.core.network.FavoriteRequest
import com.seasonyuu.fnmusic.core.network.MusicApi
import com.seasonyuu.fnmusic.core.network.PlaybackEventDto
import com.seasonyuu.fnmusic.core.network.PlaybackPayloadDto
import com.seasonyuu.fnmusic.core.network.PlaylistCreateRequest
import com.seasonyuu.fnmusic.core.network.PlaylistEditRequest
import com.seasonyuu.fnmusic.core.network.PlaylistGuidRequest
import com.seasonyuu.fnmusic.core.network.PlaylistTracksRequest
import com.seasonyuu.fnmusic.core.network.TrackDto
import com.seasonyuu.fnmusic.core.network.requireData
import com.seasonyuu.fnmusic.core.network.requireSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

interface CatalogRepository {
    fun tracks(sort: TrackSort = TrackSort.Default): Flow<PagingData<Track>>
    /** Returns the server-reported number of tracks without loading the entire catalog. */
    suspend fun trackCount(sort: TrackSort = TrackSort.Default): Int
    fun albums(sort: AlbumSort = AlbumSort.Default): Flow<PagingData<Album>>
    fun artists(): Flow<PagingData<Artist>>
    fun favorites(): Flow<PagingData<Track>>
    suspend fun firstTracks(size: Int = 24, sort: TrackSort = TrackSort.Default): List<Track>
    suspend fun allTracks(sort: TrackSort = TrackSort.Default): List<Track>
    suspend fun allFavorites(): List<Track>
    suspend fun firstAlbums(size: Int = 12, sort: AlbumSort = AlbumSort.Default): List<Album>
    suspend fun firstArtists(size: Int = 24): List<Artist>
    suspend fun recent(size: Int = 24): List<Track>
    suspend fun favoritePage(size: Int = 100): List<Track>
    suspend fun playlists(): List<Playlist>
    suspend fun playlistDetail(id: PlaylistId): Playlist
    suspend fun createPlaylist(name: String, coverId: String?): Playlist
    suspend fun updatePlaylist(id: PlaylistId, name: String, coverId: String?): Playlist
    suspend fun deletePlaylist(id: PlaylistId)
    suspend fun addPlaylistTracks(id: PlaylistId, tracks: List<TrackId>)
    suspend fun removePlaylistTracks(id: PlaylistId, tracks: List<TrackId>)
    suspend fun purgeInvalidPlaylistTracks(id: PlaylistId): Int
    suspend fun albumDetail(id: AlbumId): Album
    suspend fun artistDetail(id: ArtistId): Artist
    suspend fun albumTracks(id: AlbumId, size: Int = 200): List<Track>
    suspend fun artistTracks(id: ArtistId, size: Int = 200): List<Track>
    suspend fun playlistTracks(id: PlaylistId, size: Int = 200): List<Track>
    suspend fun updateTrackMetadata(track: Track, edit: TrackMetadataEdit): TrackMetadata
    suspend fun trackTagOptions(): TrackTagOptions
    suspend fun trackMetadata(id: TrackId): TrackMetadata
    suspend fun lyrics(id: TrackId): List<LyricLine>
    suspend fun startRoam(deviceId: String): RoamWindow
    suspend fun nextRoam(deviceId: String, relativeRoamId: String): RoamWindow
    suspend fun previousRoam(deviceId: String, relativeRoamId: String): RoamWindow
}

sealed interface SearchItem {
    data class TrackItem(val value: Track) : SearchItem
    data class AlbumItem(val value: Album) : SearchItem
    data class ArtistItem(val value: Artist) : SearchItem
    data class PlaylistItem(val value: Playlist) : SearchItem
}

interface SearchRepository {
    suspend fun suggestions(query: String): SearchSuggestions
    fun results(query: String, type: SearchType): Flow<PagingData<SearchItem>>
}

interface FavoriteRepository {
    val overrides: StateFlow<Map<TrackId, Boolean>>
    fun isFavorite(id: TrackId): StateFlow<Boolean>
    suspend fun seed(tracks: List<Track>)
    suspend fun setFavorite(id: TrackId, favorite: Boolean): Result<Unit>
}

interface PlaybackEventReporter {
    suspend fun reportPlayed(id: TrackId, startedAt: Long = System.currentTimeMillis())
}

class MusicCatalogRepository(private val api: MusicApi) : CatalogRepository {
    override fun tracks(sort: TrackSort): Flow<PagingData<Track>> =
        pager { page, size -> api.tracks(page, size, sort.wireValue).requireData() }
            .map { data -> data.map(TrackDto::toDomain) }

    override suspend fun trackCount(sort: TrackSort): Int =
        api.tracks(page = 1, size = 1, sort = sort.wireValue).requireData().total

    override fun albums(sort: AlbumSort): Flow<PagingData<Album>> =
        pager { page, size -> api.albums(page, size, sort.wireValue).requireData() }
            .map { data -> data.map(AlbumDto::toDomain) }

    override fun artists(): Flow<PagingData<Artist>> =
        pager { page, size -> api.artists(page, size).requireData() }
            .map { data -> data.map { it.toDomain() } }

    override fun favorites(): Flow<PagingData<Track>> =
        pager { page, size -> api.favorites(page, size).requireData() }
            .map { data -> data.map { it.toDomain(favoriteOverride = true) } }

    override suspend fun firstTracks(size: Int, sort: TrackSort) = api.tracks(1, size, sort.wireValue).requireData().list.map(TrackDto::toDomain)
    override suspend fun allTracks(sort: TrackSort) = loadAll { page, size -> api.tracks(page, size, sort.wireValue).requireData() }
        .map(TrackDto::toDomain)
    override suspend fun allFavorites() = loadAll { page, size -> api.favorites(page, size).requireData() }
        .map { it.toDomain(favoriteOverride = true) }
    override suspend fun firstAlbums(size: Int, sort: AlbumSort) = api.albums(1, size, sort.wireValue).requireData().list.map(AlbumDto::toDomain)
    override suspend fun firstArtists(size: Int) = api.artists(1, size).requireData().list.map { it.toDomain() }
    override suspend fun recent(size: Int) = api.history(1, size).requireData().list.map(TrackDto::toDomain)
    override suspend fun favoritePage(size: Int) = api.favorites(1, size).requireData().list.map { it.toDomain(true) }
    override suspend fun playlists(): List<Playlist> = coroutineScope {
        val details = Semaphore(4)
        api.playlists().requireData().list.map { summary ->
            async {
                if (summary.trackCount != null) summary.toDomain()
                else details.withPermit {
                    runCatching { api.playlistDetail(summary.guid).requireData() }
                        .getOrDefault(summary)
                        .toDomain()
                }
            }
        }.awaitAll()
    }
    override suspend fun playlistDetail(id: PlaylistId) = api.playlistDetail(id.value).requireData().toDomain()
    override suspend fun createPlaylist(name: String, coverId: String?): Playlist {
        val normalizedName = name.trim()
        api.createPlaylist(PlaylistCreateRequest(normalizedName, coverId)).requireSuccess()
        return playlists().firstOrNull { it.name == normalizedName }
            ?: throw IllegalStateException("歌单创建成功，但无法读取新歌单")
    }

    override suspend fun updatePlaylist(id: PlaylistId, name: String, coverId: String?): Playlist {
        api.editPlaylist(PlaylistEditRequest(id.value, name.trim(), coverId)).requireSuccess()
        return playlistDetail(id)
    }

    override suspend fun deletePlaylist(id: PlaylistId) {
        api.deletePlaylist(PlaylistGuidRequest(id.value)).requireSuccess()
    }

    override suspend fun addPlaylistTracks(id: PlaylistId, tracks: List<TrackId>) {
        if (tracks.isEmpty()) return
        api.addPlaylistTracks(PlaylistTracksRequest(id.value, tracks.map(TrackId::value))).requireSuccess()
    }

    override suspend fun removePlaylistTracks(id: PlaylistId, tracks: List<TrackId>) {
        if (tracks.isEmpty()) return
        api.removePlaylistTracks(PlaylistTracksRequest(id.value, tracks.map(TrackId::value))).requireSuccess()
    }

    override suspend fun purgeInvalidPlaylistTracks(id: PlaylistId): Int {
        val count = api.playlistInvalidTrackCount(id.value).requireData().total
        if (count > 0) api.purgePlaylistTracks(PlaylistGuidRequest(id.value)).requireSuccess()
        return count
    }
    override suspend fun albumDetail(id: AlbumId) = api.albumDetail(id.value).requireData().toDomain()
    override suspend fun artistDetail(id: ArtistId) = api.artistDetail(id.value).requireData().toDomain()
    override suspend fun albumTracks(id: AlbumId, size: Int) = api.albumTracks(id.value, 1, size).requireData().list.map(TrackDto::toDomain)
    override suspend fun artistTracks(id: ArtistId, size: Int) = api.artistTracks(id.value, 1, size).requireData().list.map(TrackDto::toDomain)
    override suspend fun playlistTracks(id: PlaylistId, size: Int) = api.playlistTracks(id.value, 1, size).requireData().list.map(TrackDto::toDomain)

    override suspend fun trackMetadata(id: TrackId): TrackMetadata {
        val value = api.trackMetadata(id.value).requireData()
        return TrackMetadata(value.track.toDomain(), value.audioSpec?.toDomain())
    }

    override suspend fun updateTrackMetadata(track: Track, edit: TrackMetadataEdit): TrackMetadata {
        api.updateTrackMetadata(TrackMetadataUpdateRequest(
            guid = track.id.value, title = edit.title, album = edit.album,
            artistGUIDs = edit.artistGUIDs, genreGUIDs = edit.genreGUIDs,
            year = edit.year, trackNo = edit.trackNo, discNo = edit.discNo, coverId = track.coverId,
        ).toJson()).requireSuccess()
        return trackMetadata(track.id)
    }

    override suspend fun trackTagOptions() = TrackTagOptions(
        artists = loadAll { page, size -> api.artists(page, size).requireData() }.map { it.toDomain() },
        genres = loadAll { page, size -> api.genres(page, size).requireData() },
    )

    override suspend fun startRoam(deviceId: String) = api.roamStart(deviceId).requireData().toDomain()

    override suspend fun nextRoam(deviceId: String, relativeRoamId: String) =
        api.roamNext(deviceId, relativeRoamId).requireData().toDomain()

    override suspend fun previousRoam(deviceId: String, relativeRoamId: String) =
        api.roamPrevious(deviceId, relativeRoamId).requireData().toDomain()

    override suspend fun lyrics(id: TrackId): List<LyricLine> {
        val data = api.lyrics(id.value).requireData()
        val lyric = data.list.firstOrNull { it.guid == data.preferred } ?: data.list.firstOrNull() ?: return emptyList()
        return if (lyric.isLRC) LrcParser.parse(lyric.content, lyric.offset) else lyric.content.lineSequence()
            .filter(String::isNotBlank)
            .map { LyricLine(text = it.trim()) }
            .toList()
    }

    private fun <T : Any> pager(load: suspend (page: Int, size: Int) -> com.seasonyuu.fnmusic.core.network.PageDto<T>): Flow<PagingData<T>> =
        Pager(PagingConfig(pageSize = 30, initialLoadSize = 30, prefetchDistance = 8)) {
            ApiPagingSource(load)
        }.flow

    private suspend fun <T : Any> loadAll(
        load: suspend (page: Int, size: Int) -> com.seasonyuu.fnmusic.core.network.PageDto<T>,
    ): List<T> {
        val pageSize = 200
        val result = mutableListOf<T>()
        var page = 1
        while (true) {
            val value = load(page, pageSize)
            result += value.list
            if (value.list.isEmpty() || result.size >= value.total) break
            page += 1
        }
        return result
    }
}

private class ApiPagingSource<T : Any>(private val loadPage: suspend (Int, Int) -> com.seasonyuu.fnmusic.core.network.PageDto<T>) : PagingSource<Int, T>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, T> = try {
        val page = params.key ?: 1
        val result = loadPage(page, params.loadSize)
        LoadResult.Page(
            data = result.list,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (page * params.loadSize >= result.total || result.list.isEmpty()) null else page + 1,
        )
    } catch (error: Throwable) {
        LoadResult.Error(error)
    }

    override fun getRefreshKey(state: PagingState<Int, T>): Int? =
        state.anchorPosition?.let(state::closestPageToPosition)?.let { page -> page.prevKey?.plus(1) ?: page.nextKey?.minus(1) }
}

class MusicSearchRepository(private val api: MusicApi) : SearchRepository {
    override suspend fun suggestions(query: String): SearchSuggestions {
        if (query.isBlank()) return SearchSuggestions()
        val value = api.suggestions(query.trim()).requireData()
        return SearchSuggestions(
            tracks = value.track.items.map(TrackDto::toDomain),
            albums = value.album.items.map(AlbumDto::toDomain),
            artists = value.artist.items.map { it.toDomain() },
            playlists = value.playlist.items.map { it.toDomain() },
        )
    }

    override fun results(query: String, type: SearchType): Flow<PagingData<SearchItem>> =
        Pager(PagingConfig(pageSize = 30, initialLoadSize = 30, prefetchDistance = 8)) {
            ApiPagingSource { page, size ->
                when (type) {
                    SearchType.Track -> api.searchTracks(query, page, size).requireData().let { pageData ->
                        com.seasonyuu.fnmusic.core.network.PageDto(pageData.list.map { SearchItem.TrackItem(it.toDomain()) }, pageData.total, pageData.sort)
                    }
                    SearchType.Album -> api.searchAlbums(query, page, size).requireData().let { pageData ->
                        com.seasonyuu.fnmusic.core.network.PageDto(pageData.list.map { SearchItem.AlbumItem(it.toDomain()) }, pageData.total, pageData.sort)
                    }
                    SearchType.Artist -> api.searchArtists(query, page, size).requireData().let { pageData ->
                        com.seasonyuu.fnmusic.core.network.PageDto(pageData.list.map { SearchItem.ArtistItem(it.toDomain()) }, pageData.total, pageData.sort)
                    }
                    SearchType.Playlist -> api.searchPlaylists(query, page, size).requireData().let { pageData ->
                        com.seasonyuu.fnmusic.core.network.PageDto(pageData.list.map { SearchItem.PlaylistItem(it.toDomain()) }, pageData.total, pageData.sort)
                    }
                }
            }
        }.flow
}

class OptimisticFavoriteRepository(private val api: MusicApi) : FavoriteRepository {
    private val mutableOverrides = MutableStateFlow<Map<TrackId, Boolean>>(emptyMap())
    override val overrides: StateFlow<Map<TrackId, Boolean>> = mutableOverrides.asStateFlow()
    private val locks = ConcurrentHashMap<TrackId, Mutex>()
    private val itemStates = ConcurrentHashMap<TrackId, MutableStateFlow<Boolean>>()

    override fun isFavorite(id: TrackId): StateFlow<Boolean> =
        itemStates.getOrPut(id) { MutableStateFlow(mutableOverrides.value[id] ?: false) }.asStateFlow()

    override suspend fun seed(tracks: List<Track>) {
        val seeded = tracks.associate { it.id to it.isFavorite }
        mutableOverrides.value = mutableOverrides.value + seeded
        seeded.forEach { (id, value) -> itemStates[id]?.value = value }
    }

    override suspend fun setFavorite(id: TrackId, favorite: Boolean): Result<Unit> {
        val before = mutableOverrides.value[id]
        publish(id, favorite)
        return locks.getOrPut(id) { Mutex() }.withLock {
            // A newer tap superseded this request while it waited for the same-track lock.
            if (mutableOverrides.value[id] != favorite) return@withLock Result.success(Unit)
            runCatching {
                val response = if (favorite) api.createFavorite(FavoriteRequest(id.value)) else api.deleteFavorite(FavoriteRequest(id.value))
                response.requireSuccess()
                Unit
            }.onFailure {
                // Do not overwrite a newer optimistic target when an older request fails.
                if (mutableOverrides.value[id] == favorite) {
                    if (before == null) {
                        mutableOverrides.value = mutableOverrides.value - id
                        itemStates[id]?.value = false
                    } else publish(id, before)
                }
            }
        }
    }

    private fun publish(id: TrackId, value: Boolean) {
        mutableOverrides.value = mutableOverrides.value + (id to value)
        itemStates.getOrPut(id) { MutableStateFlow(value) }.value = value
    }
}

class MusicPlaybackEventReporter(private val api: MusicApi) : PlaybackEventReporter {
    override suspend fun reportPlayed(id: TrackId, startedAt: Long) {
        api.reportEvent(
            EventReportRequest(
                events = listOf(
                    PlaybackEventDto(
                        occurredAt = startedAt,
                        payload = PlaybackPayloadDto(id.value),
                    ),
                ),
            ),
        ).requireSuccess()
    }
}
