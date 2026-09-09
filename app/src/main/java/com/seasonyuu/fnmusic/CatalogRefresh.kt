package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.AlbumSort
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackSort
import com.seasonyuu.fnmusic.data.CatalogRepository
import com.seasonyuu.fnmusic.feature.music.CatalogSection
import com.seasonyuu.fnmusic.feature.music.MusicUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Publish each section as it arrives; a failed request retains that section's cached content. */
internal class CatalogRefresh(
    private val catalog: CatalogRepository,
    private val state: MutableStateFlow<MusicUiState>,
    private val seedFavorites: suspend (List<Track>) -> Unit,
) {
    suspend fun refresh() = coroutineScope {
        state.update { it.copy(loading = true, pendingSections = CatalogSection.entries.toSet(), sectionErrors = emptyMap(), error = null) }
        suspend fun load(section: CatalogSection, fetch: suspend () -> (MusicUiState) -> MusicUiState) {
            try {
                val apply = fetch()
                currentCoroutineContext().ensureActive()
                state.update { previous ->
                    val pending = previous.pendingSections - section
                    apply(previous).copy(pendingSections = pending, loading = pending.isNotEmpty(), loadedSections = previous.loadedSections + section)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                currentCoroutineContext().ensureActive()
                state.update {
                    val pending = it.pendingSections - section
                    it.copy(pendingSections = pending, loading = pending.isNotEmpty(), sectionErrors = it.sectionErrors + (section to "${section.title}加载失败，请重试"))
                }
            }
        }
        launch { load(CatalogSection.Tracks) {
            val tracks = catalog.firstTracks(60, TrackSort.RecentlyAdded)
            currentCoroutineContext().ensureActive()
            seedFavorites(tracks)
            return@load { it: MusicUiState -> it.copy(tracks = tracks) }
        } }
        launch { load(CatalogSection.Albums) {
            val albums = catalog.firstAlbums(20, AlbumSort.RecentlyUpdated)
            return@load { it: MusicUiState -> it.copy(albums = albums) }
        } }
        launch { load(CatalogSection.Artists) {
            val artists = catalog.firstArtists(30)
            return@load { it: MusicUiState -> it.copy(artists = artists) }
        } }
        launch { load(CatalogSection.Favorites) {
            val favorites = catalog.favoritePage(100)
            currentCoroutineContext().ensureActive()
            seedFavorites(favorites)
            return@load { it: MusicUiState -> it.copy(favorites = favorites) }
        } }
        launch { load(CatalogSection.Recent) {
            val recent = catalog.recent(30)
            currentCoroutineContext().ensureActive()
            seedFavorites(recent)
            return@load { it: MusicUiState -> it.copy(recent = recent) }
        } }
        launch { load(CatalogSection.Playlists) {
            val playlists = catalog.playlists()
            return@load { it: MusicUiState -> it.copy(playlists = playlists) }
        } }
        launch { load(CatalogSection.TrackTotal) {
            val total = catalog.trackCount(TrackSort.RecentlyAdded)
            return@load { it: MusicUiState -> it.copy(trackTotal = total) }
        } }
    }
}
