package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.ConnectionProfile
import com.seasonyuu.fnmusic.core.model.Endpoint
import com.seasonyuu.fnmusic.core.model.LoginForm
import com.seasonyuu.fnmusic.core.model.SessionState
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SessionCoordinatorTest {
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
    fun `stale saved token is validated with user me before falling back to login`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":401,"msg":"expired","data":null}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"msg":"ok","data":{"userToken":"fresh-token","user":{}}}"""),
        )
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"code":0,"msg":"ok","data":{"serverName":"Test NAS","serverVersion":"1"}}"""),
        )
        val profile = ConnectionProfile(
            endpoint = Endpoint.Direct(server.url("/").toString()),
            username = "tester",
            allowPrivateLanHttp = true,
        )
        val vault = FakeSessionVault(
            SavedCredentials(profile, passwordHash = "saved-password-hash", token = "stale-token"),
        )
        val coordinator = SessionCoordinator(NetworkRuntime(), vault)

        coordinator.reconnect()

        assertTrue(coordinator.state.value is SessionState.Ready)
        assertEquals("fresh-token", vault.saved?.token)
        assertEquals("/music/api/v1/user/me", server.takeRequest().path)
        assertEquals("/music/api/v1/user/password-login", server.takeRequest().path)
        assertEquals("/music/api/v1/sys/config", server.takeRequest().path)
    }

    @Test
    fun `starts in restoring state so the first frame is not the login form`() {
        val coordinator = SessionCoordinator(NetworkRuntime(), FakeSessionVault(null))

        assertTrue(coordinator.state.value is SessionState.Restoring)
    }

    private class FakeSessionVault(initial: SavedCredentials?) : SessionVault {
        var saved: SavedCredentials? = initial
        var form: LoginForm? = null

        override fun saveLoginForm(form: LoginForm) { this.form = form }
        override fun loadLoginForm() = form

        override fun deviceId() = "device-placeholder"
        override fun save(credentials: SavedCredentials) { saved = credentials }
        override fun load() = saved
        override fun clearCredentials() { saved = null }
    }
}
