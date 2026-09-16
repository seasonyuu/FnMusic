package com.seasonyuu.fnmusic.feature.music

import androidx.paging.PagingData
import com.seasonyuu.fnmusic.core.model.SearchSuggestions
import com.seasonyuu.fnmusic.core.model.SearchType
import com.seasonyuu.fnmusic.data.SearchItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

enum class SearchFilter(val label: String, val type: SearchType?) {
    Best("最佳结果", null),
    Artist("歌手", SearchType.Artist),
    Album("专辑", SearchType.Album),
    Track("歌曲", SearchType.Track),
    Playlist("歌单", SearchType.Playlist),
}

enum class SearchStatus { Idle, Loading, Ready, Error }

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter.Best,
    val generation: Long = 0,
    val status: SearchStatus = SearchStatus.Idle,
    val bestResults: List<SearchItem> = emptyList(),
    val categoryCounts: Map<SearchFilter, Int> = emptyMap(),
    val error: String? = null,
    val pages: Flow<PagingData<SearchItem>> = EmptySearchPages,
)

fun SearchUiState.orderedFilters(): List<SearchFilter> = buildList {
    add(SearchFilter.Best)
    addAll(
        SearchFilter.entries
            .filterNot { it == SearchFilter.Best }
            .filter { categoryCounts[it] != 0 }
            .sortedWith(compareByDescending<SearchFilter> { categoryCounts[it] ?: 0 }.thenBy { it.ordinal }),
    )
}

private val EmptySearchPages = flowOf(PagingData.empty<SearchItem>())

fun SearchSuggestions.bestResults(): List<SearchItem> = buildList {
    artists.firstOrNull()?.let { add(SearchItem.ArtistItem(it)) }
    albums.firstOrNull()?.let { add(SearchItem.AlbumItem(it)) }
    playlists.firstOrNull()?.let { add(SearchItem.PlaylistItem(it)) }
    tracks.forEach { add(SearchItem.TrackItem(it)) }
}.distinctBy { it.searchKey() }

internal fun SearchItem.searchKey(): String = when (this) {
    is SearchItem.TrackItem -> "track:${value.id.value}"
    is SearchItem.AlbumItem -> "album:${value.id.value}"
    is SearchItem.ArtistItem -> "artist:${value.id.value}"
    is SearchItem.PlaylistItem -> "playlist:${value.id.value}"
}
