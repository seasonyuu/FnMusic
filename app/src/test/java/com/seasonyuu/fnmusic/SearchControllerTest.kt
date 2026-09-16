package com.seasonyuu.fnmusic

import androidx.paging.PagingData
import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.data.SearchItem
import com.seasonyuu.fnmusic.data.SearchRepository
import com.seasonyuu.fnmusic.feature.music.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test

class SearchControllerTest {
    private class Repository : SearchRepository {
        val suggestions = mutableListOf<String>()
        val pages = mutableListOf<Pair<String, SearchType>>()
        var fetch: suspend (String) -> SearchSuggestions = { SearchSuggestions(tracks = listOf(Track(TrackId(it), it))) }
        override suspend fun suggestions(query: String): SearchSuggestions {
            suggestions += query
            return fetch(query)
        }
        override fun results(query: String, type: SearchType): Flow<PagingData<SearchItem>> {
            pages += query to type
            return flowOf(PagingData.empty())
        }
    }

    private fun check(block: suspend CoroutineScope.(SearchController, Repository, MutableStateFlow<MusicUiState>) -> Unit) = runBlocking {
        withTimeout(5_000) {
            val repository = Repository()
            val state = MutableStateFlow(MusicUiState())
            val scope = CoroutineScope(coroutineContext + SupervisorJob(coroutineContext[Job]))
            try { block(SearchController(scope, repository, state), repository, state) }
            finally { scope.cancel() }
        }
    }

    @Test fun `typing debounces and requests only final normalized best query`() = check { controller, repository, state ->
        controller.search("周")
        controller.search(" 周杰伦 ")
        assertEquals(SearchStatus.Loading, state.value.search.status)
        assertTrue(repository.suggestions.isEmpty())
        state.first { it.search.status == SearchStatus.Ready }
        assertEquals(listOf("周杰伦"), repository.suggestions)
        assertTrue(repository.pages.isEmpty())
        controller.search("周杰伦")
        assertEquals(SearchStatus.Ready, state.value.search.status)
        assertEquals(1, repository.suggestions.size)
    }

    @Test fun `submit bypasses pending debounce without duplicate request`() = check { controller, repository, state ->
        controller.search("立即")
        controller.submit()
        yield()
        assertEquals(SearchStatus.Ready, state.value.search.status)
        delay(350)
        assertEquals(listOf("立即"), repository.suggestions)
    }

    @Test fun `switching category cancels best request and isolates paging cache`() = check { controller, repository, state ->
        controller.search("专辑")
        controller.select(SearchFilter.Album)
        state.first { it.search.status == SearchStatus.Ready }
        val firstPages = state.value.search.pages
        assertEquals(listOf("专辑" to SearchType.Album), repository.pages)
        assertTrue(repository.suggestions.isEmpty())
        controller.select(SearchFilter.Track)
        assertEquals(SearchStatus.Loading, state.value.search.status)
        assertTrue(state.value.search.bestResults.isEmpty())
        assertNotSame(firstPages, state.value.search.pages)
        state.first { it.search.status == SearchStatus.Ready }
        assertEquals("专辑" to SearchType.Track, repository.pages.last())
        controller.select(SearchFilter.Best)
        state.first { it.search.status == SearchStatus.Ready }
        assertEquals(listOf("专辑"), repository.suggestions)
    }

    @Test fun `late non cancellable response cannot overwrite newer results`() = check { controller, repository, state ->
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        repository.fetch = { query ->
            if (query == "old") {
                entered.complete(Unit)
                withContext(NonCancellable) { release.await() }
                returned.complete(Unit)
            }
            SearchSuggestions(tracks = listOf(Track(TrackId(query), query)))
        }
        controller.search("old"); controller.submit()
        entered.await()
        controller.search("new"); controller.submit()
        state.first { it.search.status == SearchStatus.Ready }
        release.complete(Unit); returned.await(); yield()
        assertEquals("new", (state.value.search.bestResults.single() as SearchItem.TrackItem).value.title)
    }

    @Test fun `clear immediately removes results and cancels pending request`() = check { controller, repository, state ->
        controller.search("old"); controller.submit()
        state.first { it.search.status == SearchStatus.Ready }
        controller.search("pending")
        controller.search("")
        assertEquals(SearchStatus.Idle, state.value.search.status)
        assertTrue(state.value.search.bestResults.isEmpty())
        delay(350)
        assertEquals(listOf("old"), repository.suggestions)
    }

    @Test fun `failure is distinct from empty results and submit retries`() = check { controller, repository, state ->
        repository.fetch = { error("offline") }
        controller.search("retry"); controller.submit()
        state.first { it.search.status == SearchStatus.Error }
        assertEquals("offline", state.value.search.error)
        repository.fetch = { SearchSuggestions() }
        controller.submit()
        state.first { it.search.status == SearchStatus.Ready }
        assertNull(state.value.search.error)
        assertTrue(state.value.search.bestResults.isEmpty())
        assertEquals(listOf("retry", "retry"), repository.suggestions)
    }

    @Test fun `session reset prevents old search from publishing`() = check { controller, repository, state ->
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        repository.fetch = {
            entered.complete(Unit)
            withContext(NonCancellable) { release.await() }
            returned.complete(Unit)
            SearchSuggestions(tracks = listOf(Track(TrackId("old"), "old")))
        }
        controller.search("old"); controller.submit(); entered.await()
        controller.reset()
        release.complete(Unit); returned.await(); yield()
        assertEquals(SearchStatus.Idle, state.value.search.status)
        assertEquals("", state.value.search.query)
        assertTrue(state.value.search.bestResults.isEmpty())
    }
}
