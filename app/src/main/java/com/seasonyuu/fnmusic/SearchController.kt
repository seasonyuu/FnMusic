package com.seasonyuu.fnmusic

import androidx.paging.cachedIn
import com.seasonyuu.fnmusic.data.SearchRepository
import com.seasonyuu.fnmusic.feature.music.MusicUiState
import com.seasonyuu.fnmusic.feature.music.SearchFilter
import com.seasonyuu.fnmusic.feature.music.SearchStatus
import com.seasonyuu.fnmusic.feature.music.SearchUiState
import com.seasonyuu.fnmusic.feature.music.bestResults
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Each request owns its paging cache, so cancelled queries cannot replay stale pages. */
internal class SearchController(
    private val scope: CoroutineScope,
    private val repository: SearchRepository,
    private val music: MutableStateFlow<MusicUiState>,
) {
    private var requestScope: CoroutineScope? = null
    private var generation = 0L

    fun search(query: String) {
        val previous = music.value.search
        if (query.trim() == previous.query.trim()) {
            music.update { it.copy(search = it.search.copy(query = query)) }
            return
        }
        request(query, previous.filter, debounce = true)
    }

    fun select(filter: SearchFilter) {
        if (filter != music.value.search.filter) request(music.value.search.query, filter, debounce = false)
    }

    fun submit() = request(music.value.search.query, music.value.search.filter, debounce = false)

    fun reset() {
        requestScope?.cancel()
        requestScope = null
        generation++
        music.update { it.copy(search = SearchUiState(generation = generation)) }
    }

    private fun request(query: String, filter: SearchFilter, debounce: Boolean) {
        requestScope?.cancel()
        requestScope = null
        val id = ++generation
        val normalized = query.trim()
        music.update {
            val sameQuery = normalized == it.search.query.trim()
            it.copy(search = SearchUiState(
                query = query, filter = filter, generation = id,
                status = if (normalized.isEmpty()) SearchStatus.Idle else SearchStatus.Loading,
                bestResults = if (sameQuery) it.search.bestResults else emptyList(),
                categoryCounts = if (sameQuery) it.search.categoryCounts else emptyMap(),
            ))
        }
        if (normalized.isEmpty()) return
        val currentScope = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))
        requestScope = currentScope
        currentScope.launch {
            try {
                if (debounce) delay(300)
                val type = filter.type
                if (type == null) {
                    val suggestions = repository.suggestions(normalized)
                    val results = suggestions.bestResults()
                    ensureActive()
                    publish(id) {
                        it.copy(
                            status = SearchStatus.Ready,
                            bestResults = results,
                            categoryCounts = mapOf(
                                SearchFilter.Artist to suggestions.artistTotal,
                                SearchFilter.Album to suggestions.albumTotal,
                                SearchFilter.Track to suggestions.trackTotal,
                                SearchFilter.Playlist to suggestions.playlistTotal,
                            ),
                        )
                    }
                } else {
                    val pages = repository.results(normalized, type).cachedIn(currentScope)
                    ensureActive()
                    publish(id) { it.copy(status = SearchStatus.Ready, pages = pages) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                publish(id) { it.copy(status = SearchStatus.Error, error = error.message ?: "请检查网络连接后重试") }
            }
        }
    }

    private fun publish(id: Long, transform: (SearchUiState) -> SearchUiState) {
        music.update { if (it.search.generation == id) it.copy(search = transform(it.search)) else it }
    }
}
