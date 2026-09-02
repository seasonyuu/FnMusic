package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.core.network.NetworkRuntime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class OptimisticFavoriteRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: OptimisticFavoriteRepository
    private val id = TrackId("track-placeholder")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)
        repository = OptimisticFavoriteRepository(runtime.api)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `rapid opposite targets are serialized and final state wins`() = runBlocking {
        repository.seed(listOf(Track(id, "placeholder", isFavorite = false)))
        server.enqueue(success().setBodyDelay(250, TimeUnit.MILLISECONDS))
        server.enqueue(success())

        val create = async(start = CoroutineStart.UNDISPATCHED) { repository.setFavorite(id, true) }
        val first = server.takeRequest(2, TimeUnit.SECONDS)
        val delete = async(start = CoroutineStart.UNDISPATCHED) { repository.setFavorite(id, false) }

        assertNotNull(first)
        assertFalse(repository.overrides.value.getValue(id))
        assertFalse(repository.isFavorite(id).value)
        create.await().getOrThrow()
        delete.await().getOrThrow()
        val second = server.takeRequest(2, TimeUnit.SECONDS)

        assertEquals("/music/api/v1/favorite-track/create", first?.path)
        assertEquals("/music/api/v1/favorite-track/delete", second?.path)
        assertFalse(repository.overrides.value.getValue(id))
    }

    @Test
    fun `failed latest request rolls both observable states back`() = runBlocking {
        repository.seed(listOf(Track(id, "placeholder", isFavorite = false)))
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":9001,"msg":"rejected","data":null}"""),
        )

        val result = repository.setFavorite(id, true)

        assertEquals(true, result.isFailure)
        assertFalse(repository.overrides.value.getValue(id))
        assertFalse(repository.isFavorite(id).value)
    }

    private fun success() = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"code":0,"msg":"ok","data":{}}""")
}
