package com.seasonyuu.fnmusic.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Request

class NetworkRuntimeIntegrationTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `Retrofit request is moved to active music base and signed`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"msg":"ok","data":{"list":[],"total":0}}"""),
        )
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)

        val result = runtime.api.tracks(page = 1, size = 30).requireData()
        val request = server.takeRequest()

        assertTrue(result.list.isEmpty())
        assertEquals("/music/api/v1/track/list?page=1&size=30", request.path)
        assertTrue(request.getHeader("authx")?.matches(Regex("nonce=\\d{6}&timestamp=\\d{13}&sign=[0-9a-f]{32}")) == true)
    }

    @Test
    fun `track sort is sent unchanged to the service`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"msg":"ok","data":{"list":[],"total":0}}"""),
        )
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)

        runtime.api.tracks(page = 1, size = 30, sort = "title,desc").requireData()

        assertEquals("/music/api/v1/track/list?page=1&size=30&sort=title%2Cdesc", server.takeRequest().path)
    }

    @Test
    fun `roam endpoint decodes current and next tracks`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """{"code":0,"msg":"ok","data":{"current":{"roamId":"roam-one","track":{"guid":"track-one","title":"One"}},"next":{"roamId":"roam-two","track":{"guid":"track-two","title":"Two"}}}}""",
                ),
        )
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)

        val value = runtime.api.roamStart("device-placeholder").requireData()
        val request = server.takeRequest()

        assertEquals("roam-one", value.current?.roamId)
        assertEquals("track-one", value.current?.track?.guid)
        assertEquals("track-two", value.next?.track?.guid)
        assertEquals("/music/api/v1/track/roam-start?deviceId=device-placeholder", request.path)
    }

    @Test
    fun `roam navigation sends the relative roam id`() = runBlocking {
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """{"code":0,"msg":"ok","data":{"previous":{"roamId":"roam-zero","track":{"guid":"track-zero"}},"current":{"roamId":"roam-one","track":{"guid":"track-one"}},"next":{"roamId":"roam-two","track":{"guid":"track-two"}}}}""",
                    ),
            )
        }
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)

        val next = runtime.api.roamNext("device-placeholder", "roam-zero").requireData()
        val previous = runtime.api.roamPrevious("device-placeholder", "roam-two").requireData()

        assertEquals("roam-one", next.current?.roamId)
        assertEquals("track-zero", previous.previous?.track?.guid)
        assertEquals(
            "/music/api/v1/track/roam-next?deviceId=device-placeholder&relativeRoamId=roam-zero",
            server.takeRequest().path,
        )
        assertEquals(
            "/music/api/v1/track/roam-previous?deviceId=device-placeholder&relativeRoamId=roam-two",
            server.takeRequest().path,
        )
    }

    @Test
    fun `playlist writes use guid and track GUID arrays from the current web contract`() = runBlocking {
        repeat(5) {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"code":0,"msg":"ok","data":null}"""),
            )
        }
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"msg":"ok","data":{"total":2}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"msg":"ok","data":null}"""),
        )
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)

        runtime.api.createPlaylist(PlaylistCreateRequest("Road trip", "playlist_default_1")).requireSuccess()
        runtime.api.editPlaylist(PlaylistEditRequest("playlist-placeholder", "Road trip 2", "playlist_default_2")).requireSuccess()
        runtime.api.addPlaylistTracks(PlaylistTracksRequest("playlist-placeholder", listOf("track-one", "track-two"))).requireSuccess()
        runtime.api.removePlaylistTracks(PlaylistTracksRequest("playlist-placeholder", listOf("track-one"))).requireSuccess()
        runtime.api.deletePlaylist(PlaylistGuidRequest("playlist-placeholder")).requireSuccess()
        val invalid = runtime.api.playlistInvalidTrackCount("playlist-placeholder").requireData()
        runtime.api.purgePlaylistTracks(PlaylistGuidRequest("playlist-placeholder")).requireSuccess()

        assertEquals(2, invalid.total)
        val createRequest = server.takeRequest()
        val editRequest = server.takeRequest()
        val addRequest = server.takeRequest()
        val removeRequest = server.takeRequest()
        val deleteRequest = server.takeRequest()
        val countRequest = server.takeRequest()
        val purgeRequest = server.takeRequest()

        assertEquals("POST", createRequest.method)
        assertEquals("/music/api/v1/playlist/create", createRequest.path)
        assertEquals(
            "{\"name\":\"Road trip\",\"coverId\":\"playlist_default_1\"}",
            createRequest.body.readUtf8(),
        )
        assertEquals("/music/api/v1/playlist/edit", editRequest.path)
        assertEquals(
            "{\"guid\":\"playlist-placeholder\",\"trackGUIDs\":[\"track-one\",\"track-two\"]}",
            addRequest.body.readUtf8(),
        )
        assertEquals(
            "{\"guid\":\"playlist-placeholder\",\"trackGUIDs\":[\"track-one\"]}",
            removeRequest.body.readUtf8(),
        )
        assertEquals("/music/api/v1/playlist/delete", deleteRequest.path)
        assertEquals("GET", countRequest.method)
        assertEquals("/music/api/v1/playlist/purge-track-count?guid=playlist-placeholder", countRequest.path)
        assertEquals("/music/api/v1/playlist/purge-track", purgeRequest.path)
    }

    @Test
    fun `non-zero business code is never treated as success`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":9001,"msg":"rejected","data":null}"""),
        )
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)

        val envelope = runtime.api.tracks(page = 1, size = 1)
        val error = runCatching { envelope.requireData() }.exceptionOrNull()

        assertTrue(error is com.seasonyuu.fnmusic.core.model.MusicError.Business)
    }

    @Test
    fun `successful write envelope may contain null data`() {
        val envelope = ApiEnvelope<kotlinx.serialization.json.JsonObject>(code = 0, msg = "ok", data = null)

        envelope.requireSuccess()
        assertThrows(com.seasonyuu.fnmusic.core.model.MusicError.Protocol::class.java) {
            envelope.requireData()
        }
    }

    @Test
    fun `loopback HTTP is blocked without an explicit host authorization`() {
        val runtime = NetworkRuntime()
        runtime.baseUrlProvider.set(server.url("/music/"))

        val error = assertThrows(java.io.IOException::class.java) {
            runBlocking { runtime.api.tracks(page = 1, size = 1) }
        }
        assertTrue(error.message.orEmpty().contains("未经授权"))
    }

    @Test
    fun `authenticated GET is recovered and replayed once after HTTP 401`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":401,"msg":"expired","data":null}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"msg":"ok","data":{"list":[],"total":0}}"""),
        )
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)
        val recoveries = AtomicInteger()
        runtime.installSessionRecovery { recoveries.incrementAndGet(); true }

        val result = runtime.api.tracks(page = 1, size = 30).requireData()

        assertTrue(result.list.isEmpty())
        assertEquals(1, recoveries.get())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `business auth code in HTTP 200 triggers recovery`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":401,"msg":"expired","data":null}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"msg":"ok","data":{"list":[],"total":0}}"""),
        )
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)
        val recoveries = AtomicInteger()
        runtime.installSessionRecovery { recoveries.incrementAndGet(); true }

        runtime.api.tracks(page = 1, size = 1).requireData()

        assertEquals(1, recoveries.get())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `stream retry preserves Range header`() {
        server.enqueue(MockResponse().setResponseCode(401))
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "audio/flac")
                .setHeader("Content-Range", "bytes 1024-1027/2048")
                .setBody("data"),
        )
        val runtime = NetworkRuntime()
        runtime.activateBaseUrl(server.url("/music/"), allowPrivateLanHttp = true)
        runtime.installSessionRecovery { true }
        val request = Request.Builder()
            .url(runtime.baseUrlProvider.streamUrl("track-placeholder"))
            .header("Range", "bytes=1024-")
            .build()

        runtime.httpClient.newCall(request).execute().use { response -> assertEquals(206, response.code) }
        server.takeRequest()
        val replay = server.takeRequest()
        assertEquals("bytes=1024-", replay.getHeader("Range"))
    }
}
