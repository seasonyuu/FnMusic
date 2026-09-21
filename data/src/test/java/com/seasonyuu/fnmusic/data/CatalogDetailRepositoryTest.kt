package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.AlbumId
import com.seasonyuu.fnmusic.core.model.ArtistId
import com.seasonyuu.fnmusic.core.network.NetworkRuntime
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogDetailRepositoryTest {
    @Test fun playlistReadsEveryPageAndRejectsAnIncompleteResponse() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val runtime = NetworkRuntime()
            runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)
            val repository = MusicCatalogRepository(runtime.api)
            fun page(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            server.enqueue(page("""{"code":0,"data":{"total":3,"list":[{"guid":"a"},{"guid":"b"}]}}"""))
            server.enqueue(page("""{"code":0,"data":{"total":3,"list":[{"guid":"c"}]}}"""))
            val id = com.seasonyuu.fnmusic.core.model.PlaylistId("playlist")
            assertEquals(listOf("a", "b", "c"), repository.playlistTracks(id, size = 2).map { it.id.value })
            assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("page"))
            assertEquals("2", server.takeRequest().requestUrl!!.queryParameter("page"))
            server.enqueue(page("""{"code":0,"data":{"total":3,"list":[{"guid":"a"},{"guid":"b"}]}}"""))
            server.enqueue(page("""{"code":0,"data":{"total":3,"list":[]}}"""))
            org.junit.Assert.assertTrue(runCatching { repository.playlistTracks(id, size = 2) }.isFailure)
        } finally { server.shutdown() }
    }

    @Test fun `detail endpoints supply counts and artists absent from nested song metadata`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"code":0,"data":{"guid":"album","name":"Album","trackCount":6,"artists":[{"guid":"artist","name":"Artist"}]}}
            """))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""
                {"code":0,"data":{"guid":"artist","name":"Artist","trackCount":65,"albumCount":18}}
            """))
            val runtime = NetworkRuntime()
            runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)
            val repository = MusicCatalogRepository(runtime.api)
            val album = repository.albumDetail(AlbumId("album"))
            assertEquals(6, album.trackCount)
            assertEquals("Artist", album.artists.single().name)
            assertEquals("/music/api/v1/album/detail?guid=album", server.takeRequest().path)
            val artist = repository.artistDetail(ArtistId("artist"))
            assertEquals(65, artist.trackCount)
            assertEquals(18, artist.albumCount)
            assertEquals("/music/api/v1/artist/detail?guid=artist", server.takeRequest().path)
        } finally {
            server.shutdown()
        }
    }
}
