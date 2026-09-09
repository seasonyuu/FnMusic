package com.seasonyuu.fnmusic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.seasonyuu.fnmusic.core.model.ConnectionProfile
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.SessionState
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.RepeatMode
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.core.model.TrackSort
import com.seasonyuu.fnmusic.core.model.AlbumSort
import com.seasonyuu.fnmusic.core.model.SearchType
import com.seasonyuu.fnmusic.core.model.AlbumId
import com.seasonyuu.fnmusic.core.model.ArtistId
import com.seasonyuu.fnmusic.core.model.PlaylistId
import com.seasonyuu.fnmusic.data.SearchItem
import com.seasonyuu.fnmusic.feature.music.MusicUiState
import com.seasonyuu.fnmusic.feature.music.DetailRequestKey
import com.seasonyuu.fnmusic.feature.music.cacheCurrentDetail
import com.seasonyuu.fnmusic.feature.music.forDetail
import com.seasonyuu.fnmusic.feature.music.MusicDetailSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val graph: AppGraph) : ViewModel() {
    val session: StateFlow<SessionState> = graph.session.state
    val loginForm = graph.session.loginForm

    fun updateLoginForm(form: com.seasonyuu.fnmusic.core.model.LoginForm) = graph.session.updateLoginForm(form)
    val player: StateFlow<PlayerState> = graph.player.state
    private val mutableMusic = kotlinx.coroutines.flow.MutableStateFlow(MusicUiState())
    val music: StateFlow<MusicUiState> = mutableMusic
    private val trackSort = MutableStateFlow(TrackSort.RecentlyAdded)
    private val albumSort = MutableStateFlow(AlbumSort.RecentlyUpdated)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedTracks = trackSort.flatMapLatest(graph.catalog::tracks).cachedIn(viewModelScope)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedAlbums = albumSort.flatMapLatest(graph.catalog::albums).cachedIn(viewModelScope)
    val pagedArtists = graph.catalog.artists().cachedIn(viewModelScope)
    val pagedFavorites = graph.catalog.favorites().cachedIn(viewModelScope)
    private val pagedSearchRequest = MutableStateFlow("" to SearchType.Track)
    @OptIn(kotlinx.coroutines.FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val pagedSearch = pagedSearchRequest
        .debounce(300)
        .flatMapLatest { (query, type) ->
            if (query.isBlank()) flowOf(PagingData.empty<SearchItem>())
            else graph.search.results(query.trim(), type)
        }
        .cachedIn(viewModelScope)
    private val liquidGlassSettings = com.seasonyuu.fnmusic.data.LiquidGlassSettings(
        scope = viewModelScope,
        read = { graph.settings.liquidGlassBlur.first() },
        write = graph.settings::setLiquidGlassBlur,
    )

    fun previewLiquidGlassBlur(value: Float) = liquidGlassSettings.preview(value)
    fun saveLiquidGlassBlur() = liquidGlassSettings.save()

    private var searchJob: Job? = null
    private var detailJob: Job? = null
    private var detailGeneration = 0L
    private val queueRecovery = PlaybackQueueRecovery(
        json = graph.network.json,
        read = { graph.database.playbackQueue().observe().first() },
        write = { graph.database.playbackQueue().replace(it) },
        state = { player.value },
        playable = graph::playable,
        fetch = { graph.catalog.trackMetadata(it).track },
        restore = { saved ->
            graph.player.restore(saved.queue, saved.currentIndex, saved.positionMs, saved.shuffleEnabled, saved.repeatMode)
        },
        update = { tracks -> graph.player.updateTracks(tracks.map(graph::playable)) },
        isActive = { session.value is SessionState.Ready },
    )
    private var roamQueue: RoamQueue? = null
    private var roamGeneration = 0L
    private val requestedRoamIds = mutableSetOf<String>()
    private val roamPrefetchMutex = Mutex()

    init {
        viewModelScope.launch {
            liquidGlassSettings.state.collect { setting ->
                mutableMusic.value = mutableMusic.value.copy(
                    liquidGlassBlur = setting.multiplier,
                    liquidGlassSaveError = setting.error,
                )
            }
        }
        viewModelScope.launch { graph.session.reconnect() }
        viewModelScope.launch {
            session.collectLatest { value ->
                if (value is SessionState.Ready) {
                    while (!queueRecovery.recover()) delay(30_000)
                }
            }
        }
        viewModelScope.launch {
            session.collectLatest { value ->
                if (value is SessionState.Ready) {
                    graph.catalogCache.load(value.profile)?.let { cached ->
                        mutableMusic.value = cached.toMusicState(mutableMusic.value)
                    }
                    mutableMusic.value = mutableMusic.value.copy(serverName = value.serverName ?: "飞牛音乐")
                    refresh()
                }
            }
        }
        viewModelScope.launch {
            graph.favorites.overrides.collect { overrides ->
                mutableMusic.value = mutableMusic.value.copy(favoriteOverrides = overrides)
            }
        }
        viewModelScope.launch {
            player.map { it.current?.track?.id }.distinctUntilChanged().collectLatest { id ->
                mutableMusic.value = mutableMusic.value.copy(lyrics = emptyList())
                val lines = if (id == null) emptyList() else try {
                    graph.catalog.lyrics(id)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
                if (player.value.current?.track?.id == id) {
                    mutableMusic.value = mutableMusic.value.copy(lyrics = lines)
                }
            }
        }
        viewModelScope.launch {
            player.map { it.isRoaming to it.currentIndex }.distinctUntilChanged().collect { (isRoaming, index) ->
                if (isRoaming && index >= 0) extendRoamFrom(index, roamGeneration)
            }
        }
        viewModelScope.launch {
            graph.settings.cacheBytes.collect { bytes -> mutableMusic.value = mutableMusic.value.copy(cacheBytes = bytes) }
        }
        viewModelScope.launch {
            player.map { Triple(it.queue, it.currentIndex, it.shuffleEnabled to it.repeatMode) }
                .distinctUntilChanged()
                .collect { persistQueueSafely() }
        }
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                persistQueueSafely()
            }
        }
    }

    fun connect(profile: ConnectionProfile, password: CharArray) {
        clearDetailCache()
        viewModelScope.launch { graph.session.connect(profile, password) }
    }

    fun refresh() {
        viewModelScope.launch {
            mutableMusic.value = mutableMusic.value.copy(loading = true, error = null)
            runCatching {
                val tracks = graph.catalog.firstTracks(60, TrackSort.RecentlyAdded)
                val trackTotal = runCatching { graph.catalog.trackCount(TrackSort.RecentlyAdded) }.getOrNull()
                val albums = graph.catalog.firstAlbums(20, AlbumSort.RecentlyUpdated)
                val artists = graph.catalog.firstArtists(30)
                val favorites = graph.catalog.favoritePage(100)
                val recent = graph.catalog.recent(30)
                val playlists = graph.catalog.playlists()
                graph.favorites.seed(tracks + favorites + recent)
                val refreshed = mutableMusic.value.copy(
                    loading = false,
                    tracks = tracks,
                    trackTotal = trackTotal,
                    albums = albums,
                    artists = artists,
                    favorites = favorites,
                    recent = recent,
                    playlists = playlists,
                    favoriteOverrides = graph.favorites.overrides.value,
                    trackSort = trackSort.value,
                    albumSort = albumSort.value,
                )
                (session.value as? SessionState.Ready)?.profile?.let { profile ->
                    graph.catalogCache.save(profile, refreshed)
                }
                refreshed
            }.onSuccess { mutableMusic.value = it }
                .onFailure { mutableMusic.value = mutableMusic.value.copy(loading = false, error = it.message ?: "加载曲库失败") }
        }
    }

    fun search(query: String) {
        mutableMusic.value = mutableMusic.value.copy(searchQuery = query)
        pagedSearchRequest.value = query to mutableMusic.value.searchType
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val suggestions = if (query.isBlank()) {
                com.seasonyuu.fnmusic.core.model.SearchSuggestions()
            } else {
                runCatching { graph.search.suggestions(query) }.getOrDefault(com.seasonyuu.fnmusic.core.model.SearchSuggestions())
            }
            if (mutableMusic.value.searchQuery == query) mutableMusic.value = mutableMusic.value.copy(searchSuggestions = suggestions)
        }
    }

    fun selectSearchType(type: SearchType) {
        mutableMusic.value = mutableMusic.value.copy(searchType = type)
        pagedSearchRequest.value = mutableMusic.value.searchQuery to type
    }

    fun roam() {
        viewModelScope.launch {
            val generation = beginRoamRequest()
            mutableMusic.value = mutableMusic.value.copy(roamLoading = true, error = null)
            runCatching { graph.catalog.startRoam(graph.deviceId()) }
                .onSuccess { window ->
                    if (generation != roamGeneration) return@onSuccess
                    val queue = RoamQueue(window)
                    mutableMusic.value = mutableMusic.value.copy(roamLoading = false)
                    if (queue.items.isNotEmpty()) {
                        roamQueue = queue
                        graph.player.play(queue.items.map { graph.playable(it.track) }, 0, isRoaming = true)
                        extendRoamFrom(0, generation)
                    } else {
                        exitRoamMode()
                        mutableMusic.value = mutableMusic.value.copy(error = "暂时没有可漫游的歌曲")
                    }
                }
                .onFailure { error ->
                    if (generation != roamGeneration) return@onFailure
                    exitRoamMode()
                    mutableMusic.value = mutableMusic.value.copy(
                        roamLoading = false,
                        error = "漫游启动失败：${error.message ?: "未知错误"}",
                    )
                }
        }
    }

    fun selectTrackSort(sort: TrackSort) {
        trackSort.value = sort
        mutableMusic.value = mutableMusic.value.copy(trackSort = sort)
    }

    fun selectAlbumSort(sort: AlbumSort) {
        albumSort.value = sort
        mutableMusic.value = mutableMusic.value.copy(albumSort = sort)
    }

    fun playAllTracks() {
        viewModelScope.launch {
            runCatching { graph.catalog.allTracks(trackSort.value) }
                .onSuccess { if (it.isNotEmpty()) play(it, 0) }
                .onFailure { mutableMusic.value = mutableMusic.value.copy(error = "加载完整曲库失败") }
        }
    }

    fun playAllFavorites() {
        viewModelScope.launch {
            runCatching { graph.catalog.allFavorites() }
                .onSuccess { if (it.isNotEmpty()) play(it, 0) }
                .onFailure { mutableMusic.value = mutableMusic.value.copy(error = "加载全部收藏失败") }
        }
    }

    fun loadAlbum(id: AlbumId) = loadDetail(DetailRequestKey("album", id.value)) {
        val album = graph.catalog.albumDetail(id)
        copy(detailAlbum = album, detailTracks = graph.catalog.albumTracks(id))
    }
    fun loadArtist(id: ArtistId) = loadDetail(DetailRequestKey("artist", id.value)) {
        val artist = graph.catalog.artistDetail(id)
        copy(detailArtist = artist, detailTracks = graph.catalog.artistTracks(id))
    }
    fun loadPlaylist(id: PlaylistId) = loadDetail(DetailRequestKey("playlist", id.value)) {
        val metadata = graph.catalog.playlistDetail(id)
        val tracks = graph.catalog.playlistTracks(id)
        copy(detailTracks = tracks, detailPlaylist = metadata.copy(trackCount = metadata.trackCount ?: tracks.size))
    }

    fun createPlaylist(name: String, coverId: String?, initialTrackId: TrackId? = null) {
        val normalized = name.trim()
        if (normalized.isEmpty() || normalized.length > 32) {
            mutableMusic.value = mutableMusic.value.copy(playlistMessage = "歌单名称需为 1–32 个字符")
            return
        }
        viewModelScope.launch {
            mutableMusic.value = mutableMusic.value.copy(playlistBusy = true, playlistMessage = null)
            runCatching {
                val created = graph.catalog.createPlaylist(normalized, coverId)
                initialTrackId?.let { graph.catalog.addPlaylistTracks(created.id, listOf(it)) }
                created
            }
                .onSuccess {
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlists = graph.catalog.playlists(),
                        playlistMessage = if (initialTrackId == null) "歌单已创建" else "歌单已创建并添加歌曲",
                    )
                }
                .onFailure { error ->
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlistMessage = error.message ?: "创建歌单失败",
                    )
                }
        }
    }

    fun updatePlaylist(id: PlaylistId, name: String, coverId: String?) {
        val normalized = name.trim()
        if (normalized.isEmpty() || normalized.length > 32) {
            mutableMusic.value = mutableMusic.value.copy(playlistMessage = "歌单名称需为 1–32 个字符")
            return
        }
        viewModelScope.launch {
            mutableMusic.value = mutableMusic.value.copy(playlistBusy = true, playlistMessage = null)
            runCatching { graph.catalog.updatePlaylist(id, normalized, coverId) }
                .onSuccess { updated ->
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlists = mutableMusic.value.playlists.map { if (it.id == id) updated else it },
                        detailPlaylist = updated.takeIf { mutableMusic.value.detailKey == DetailRequestKey("playlist", id.value) } ?: mutableMusic.value.detailPlaylist,
                        detailCache = mutableMusic.value.detailCache.mapValues { (key, cached) ->
                            if (key == DetailRequestKey("playlist", id.value)) cached.copy(playlist = updated) else cached
                        },
                        playlistMessage = "歌单已更新",
                    ).cacheCurrentDetail()
                }
                .onFailure { error ->
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlistMessage = error.message ?: "更新歌单失败",
                    )
                }
        }
    }

    fun deletePlaylist(id: PlaylistId) {
        viewModelScope.launch {
            mutableMusic.value = mutableMusic.value.copy(playlistBusy = true, playlistMessage = null)
            runCatching { graph.catalog.deletePlaylist(id) }
                .onSuccess {
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlists = mutableMusic.value.playlists.filterNot { it.id == id },
                        detailPlaylist = mutableMusic.value.detailPlaylist?.takeUnless { it.id == id },
                        detailTracks = if (mutableMusic.value.detailKey == DetailRequestKey("playlist", id.value)) emptyList() else mutableMusic.value.detailTracks,
                        detailCache = mutableMusic.value.detailCache - DetailRequestKey("playlist", id.value),
                        playlistMessage = "歌单已删除",
                    )
                }
                .onFailure { error ->
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlistMessage = error.message ?: "删除歌单失败",
                    )
                }
        }
    }

    fun addTrackToPlaylist(playlistId: PlaylistId, trackId: TrackId) {
        viewModelScope.launch {
            mutableMusic.value = mutableMusic.value.copy(playlistBusy = true, playlistMessage = null)
            runCatching { graph.catalog.addPlaylistTracks(playlistId, listOf(trackId)) }
                .onSuccess {
                    refreshPlaylistAfterMembershipChange(playlistId)
                    mutableMusic.value = mutableMusic.value.copy(playlistBusy = false, playlistMessage = "已添加到歌单")
                }
                .onFailure { error ->
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlistMessage = error.message ?: "添加到歌单失败",
                    )
                }
        }
    }

    fun removeTracksFromPlaylist(playlistId: PlaylistId, trackIds: List<TrackId>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            mutableMusic.value = mutableMusic.value.copy(playlistBusy = true, playlistMessage = null)
            runCatching { graph.catalog.removePlaylistTracks(playlistId, trackIds) }
                .onSuccess {
                    refreshPlaylistAfterMembershipChange(playlistId)
                    mutableMusic.value = mutableMusic.value.copy(playlistBusy = false, playlistMessage = "已从歌单移除")
                }
                .onFailure { error ->
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlistMessage = error.message ?: "移除歌曲失败",
                    )
                }
        }
    }

    fun purgeInvalidPlaylistTracks(id: PlaylistId) {
        viewModelScope.launch {
            mutableMusic.value = mutableMusic.value.copy(playlistBusy = true, playlistMessage = null)
            runCatching { graph.catalog.purgeInvalidPlaylistTracks(id) }
                .onSuccess { count ->
                    if (count > 0) refreshPlaylistAfterMembershipChange(id)
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlistMessage = if (count == 0) "歌单中没有失效歌曲" else "已清理 $count 首失效歌曲",
                    )
                }
                .onFailure { error ->
                    mutableMusic.value = mutableMusic.value.copy(
                        playlistBusy = false,
                        playlistMessage = error.message ?: "清理失效歌曲失败",
                    )
                }
        }
    }

    private suspend fun refreshPlaylistAfterMembershipChange(id: PlaylistId) {
        val metadata = graph.catalog.playlistDetail(id)
        val tracks = graph.catalog.playlistTracks(id)
        graph.favorites.seed(tracks)
        val playlists = graph.catalog.playlists()
        val current = mutableMusic.value
        val active = current.detailKey == DetailRequestKey("playlist", id.value)
        mutableMusic.value = current.copy(
            playlists = playlists,
            detailPlaylist = if (active) metadata.copy(trackCount = metadata.trackCount ?: tracks.size) else current.detailPlaylist,
            detailTracks = if (active) tracks else current.detailTracks,
            detailCache = current.detailCache + (DetailRequestKey("playlist", id.value) to MusicDetailSnapshot(
                tracks = tracks, playlist = metadata.copy(trackCount = metadata.trackCount ?: tracks.size),
            )),
        )
    }

    suspend fun loadTrackTagOptions() = graph.catalog.trackTagOptions()

    suspend fun saveTrackMetadata(track: Track, edit: com.seasonyuu.fnmusic.core.model.TrackMetadataEdit): com.seasonyuu.fnmusic.core.model.TrackMetadata {
        val metadata = graph.catalog.updateTrackMetadata(track, edit)
        val updated = metadata.track.copy(audioSpec = metadata.audioSpec ?: metadata.track.audioSpec)
        graph.player.updateTracks(listOf(graph.playable(updated)))
        val current = mutableMusic.value
        fun List<Track>.updated() = map { if (it.id == updated.id) updated else it }
        mutableMusic.value = current.copy(
            tracks = current.tracks.updated(), favorites = current.favorites.updated(),
            recent = current.recent.updated(), detailTracks = current.detailTracks.updated(),
            detailMetadata = if (current.detailKey == DetailRequestKey("track", track.id.value)) metadata else current.detailMetadata,
            detailCache = current.detailCache.mapValues { (key, cached) ->
                cached.copy(
                    tracks = cached.tracks.updated(),
                    metadata = if (key == DetailRequestKey("track", track.id.value)) metadata else cached.metadata,
                )
            },
        )
        refresh()
        return metadata
    }

    fun loadTrackMetadata(id: TrackId) = loadDetail(DetailRequestKey("track", id.value)) {
        copy(detailMetadata = graph.catalog.trackMetadata(id))
    }

    private fun loadDetail(key: DetailRequestKey, loader: suspend MusicUiState.() -> MusicUiState) {
        val generation = ++detailGeneration
        detailJob?.cancel()
        detailJob = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val retained = mutableMusic.value.cacheCurrentDetail()
            val initial = retained.forDetail(key).copy(
                detailLoading = key !in retained.detailCache,
                detailError = null,
            )
            mutableMusic.value = initial
            try {
                val result = initial.loader()
                if (generation != detailGeneration) return@launch
                graph.favorites.seed(result.detailTracks)
                if (generation != detailGeneration) return@launch
                mutableMusic.value = mutableMusic.value.copy(
                    detailKey = key, detailLoading = false, detailTracks = result.detailTracks,
                    detailPlaylist = result.detailPlaylist, detailAlbum = result.detailAlbum, detailArtist = result.detailArtist,
                    detailMetadata = result.detailMetadata, detailError = null,
                ).cacheCurrentDetail()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                if (generation == detailGeneration) {
                    mutableMusic.value = mutableMusic.value.copy(detailLoading = false, detailError = error.message ?: "加载详情失败")
                }
            }
        }
    }

    fun toggleFavorite(track: Track) {
        // UNDISPATCHED publishes the optimistic value before a second UI tap can
        // calculate its target, while network work still suspends off the frame.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val target = !(graph.favorites.overrides.value[track.id] ?: track.isFavorite)
            graph.favorites.setFavorite(track.id, target).onSuccess {
                val favorites = graph.catalog.favoritePage(100)
                mutableMusic.value = mutableMusic.value.copy(favorites = favorites)
            }.onFailure {
                mutableMusic.value = mutableMusic.value.copy(error = "收藏操作失败，请重试")
            }
        }
    }

    fun play(tracks: List<Track>, index: Int) {
        exitRoamMode()
        graph.player.play(tracks.map(graph::playable), index)
    }

    fun togglePlayback() {
        if (player.value.isPlaying) graph.player.pause() else graph.player.resume()
    }

    fun seekTo(positionMs: Long) = graph.player.seekTo(positionMs)
    fun skipPrevious() = graph.player.skipPrevious()
    fun skipNext() {
        val state = player.value
        if (!state.isRoaming || state.currentIndex < state.queue.lastIndex) {
            graph.player.skipNext()
            return
        }
        val generation = roamGeneration
        viewModelScope.launch {
            if (extendRoamFrom(state.currentIndex, generation)) graph.player.skipNext()
            else mutableMusic.value = mutableMusic.value.copy(error = "暂时没有更多可漫游的歌曲")
        }
    }
    fun skipToQueueItem(index: Int) = graph.player.skipTo(index)
    fun skipToHistoryItem(index: Int) = graph.player.skipToHistoryItem(index)
    fun clearPlaybackHistory() = graph.player.clearPlaybackHistory()
    fun playNext(track: Track) {
        exitRoamMode()
        graph.player.playNext(graph.playable(track))
    }
    fun addToQueue(track: Track) {
        exitRoamMode()
        graph.player.append(listOf(graph.playable(track)))
    }
    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        exitRoamMode()
        graph.player.moveQueueItem(fromIndex, toIndex)
    }
    fun removeFromQueue(index: Int) {
        exitRoamMode()
        graph.player.removeFromQueue(index)
    }
    fun toggleShuffle() {
        if (!player.value.isRoaming) graph.player.setShuffle(!player.value.shuffleEnabled)
    }
    fun cycleRepeatMode() {
        if (player.value.isRoaming) return
        graph.player.setRepeatMode(
            when (player.value.repeatMode) {
                RepeatMode.Off -> RepeatMode.All
                RepeatMode.All -> RepeatMode.One
                RepeatMode.One -> RepeatMode.Off
            },
        )
    }

    fun setCacheSize(bytes: Long) {
        viewModelScope.launch { graph.settings.setCacheBytes(bytes) }
    }

    fun coverUrl(coverId: String?, size: Int): String? = graph.coverUrl(coverId, size)

    fun logout() {
        clearDetailCache()
        viewModelScope.launch {
            exitRoamMode()
            graph.player.clear()
            graph.session.logout(clearCredentials = true)
            graph.catalogCache.clear()
            queueRecovery.clear()
        }
    }

    private fun clearDetailCache() {
        ++detailGeneration
        detailJob?.cancel()
        mutableMusic.value = mutableMusic.value.copy(
            detailKey = null, detailCache = emptyMap(), detailTracks = emptyList(),
            detailPlaylist = null, detailAlbum = null, detailArtist = null,
            detailMetadata = null, detailLoading = false, detailError = null,
        )
    }

    private suspend fun persistQueueSafely() {
        try {
            queueRecovery.persist()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A temporary storage failure must not stop subsequent saves.
        }
    }

    private fun beginRoamRequest(): Long {
        roamGeneration += 1
        roamQueue = null
        requestedRoamIds.clear()
        graph.player.setRoaming(false)
        return roamGeneration
    }

    private fun exitRoamMode() {
        if (roamQueue == null && !player.value.isRoaming) return
        roamGeneration += 1
        roamQueue = null
        requestedRoamIds.clear()
        graph.player.setRoaming(false)
    }

    private suspend fun extendRoamFrom(index: Int, generation: Long): Boolean = roamPrefetchMutex.withLock {
        if (generation != roamGeneration) return@withLock false
        val queue = roamQueue ?: return@withLock false
        val anchor = queue.anchorAt(index) ?: return@withLock false
        if (!requestedRoamIds.add(anchor.roamId)) return@withLock queue.items.size > index + 1

        val window = runCatching { graph.catalog.nextRoam(graph.deviceId(), anchor.roamId) }
            .getOrElse { error ->
                requestedRoamIds.remove(anchor.roamId)
                if (generation == roamGeneration) {
                    mutableMusic.value = mutableMusic.value.copy(error = "漫游续取失败：${error.message ?: "未知错误"}")
                }
                return@withLock queue.items.size > index + 1
            }
        if (generation != roamGeneration || queue !== roamQueue) return@withLock false

        val appended = queue.mergeForward(index, window)
        if (appended.isNotEmpty()) graph.player.append(appended.map { graph.playable(it.track) })
        queue.items.size > index + 1
    }
}
