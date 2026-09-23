package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.data.CatalogRepository
import com.seasonyuu.fnmusic.data.CatalogPage
import com.seasonyuu.fnmusic.feature.music.CatalogSection
import com.seasonyuu.fnmusic.feature.music.MusicUiState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class CatalogRefreshTest {
    private val unused = Proxy.newProxyInstance(CatalogRepository::class.java.classLoader, arrayOf(CatalogRepository::class.java)) { _, method, _ ->
        error("Unexpected call: ${method.name}")
    } as CatalogRepository

    private fun catalog(albums: suspend () -> List<Album>) = object : CatalogRepository by unused {
        override suspend fun firstTracks(size: Int, sort: TrackSort) = listOf(Track(TrackId("fresh"), "Fresh track"))
        override suspend fun firstAlbumPage(size: Int, sort: AlbumSort) = CatalogPage(albums(), 128)
        override suspend fun firstArtistPage(size: Int) = CatalogPage(emptyList<Artist>(), 42)
        override suspend fun favoritePage(size: Int) = CatalogPage(emptyList<Track>(), 17)
        override suspend fun recentPage(size: Int) = CatalogPage(emptyList<Track>(), 81)
        override suspend fun playlistPage() = CatalogPage(emptyList<Playlist>(), 6)
        override suspend fun trackCount(sort: TrackSort) = 1
    }

    @Test fun `fast sections become visible before slow sections finish`() = runBlocking {
        withTimeout(5_000) {
            val gate = CompletableDeferred<List<Album>>()
            val state = MutableStateFlow(MusicUiState())
            val refresh = launch { CatalogRefresh(catalog { gate.await() }, state, {}).refresh() }
            state.first { CatalogSection.Tracks in it.loadedSections }
            assertEquals("Fresh track", state.value.tracks.single().title)
            assertTrue(state.value.loading)
            assertTrue(CatalogSection.Albums in state.value.pendingSections)
            gate.complete(emptyList())
            refresh.join()
            assertFalse(state.value.loading)
            assertTrue(state.value.pendingSections.isEmpty())
            assertTrue(CatalogSection.Albums in state.value.loadedSections)
            assertTrue(state.value.albums.isEmpty())
            assertEquals(128, state.value.albumTotal)
            assertEquals(42, state.value.artistTotal)
            assertEquals(17, state.value.favoriteTotal)
            assertEquals(81, state.value.recentTotal)
            assertEquals(6, state.value.playlistTotal)
        }
    }

    @Test fun `one failure preserves cached content while other sections refresh`() = runBlocking {
        val cached = Album(AlbumId("cached"), "Cached album")
        val state = MutableStateFlow(MusicUiState(albums = listOf(cached), albumTotal = 12))
        CatalogRefresh(catalog { throw IllegalStateException("offline") }, state, {}).refresh()
        assertEquals(listOf(cached), state.value.albums)
        assertEquals(12, state.value.albumTotal)
        assertEquals("Fresh track", state.value.tracks.single().title)
        assertTrue(CatalogSection.Albums in state.value.sectionErrors)
        assertFalse(state.value.loading)
    }

    @Test fun `cancelled session cannot publish delayed results into another session`() = runBlocking {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val gate = CompletableDeferred<List<Album>>()
            val state = MutableStateFlow(MusicUiState())
            val refresh = launch { CatalogRefresh(catalog {
                entered.complete(Unit)
                withContext(NonCancellable) { gate.await() }
            }, state, {}).refresh() }
            entered.await()
            refresh.cancel()
            val next = MusicUiState(serverName = "Another NAS")
            state.value = next
            gate.complete(listOf(Album(AlbumId("old"), "Old account")))
            refresh.join()
            assertEquals(next, state.value)
        }
    }
}
