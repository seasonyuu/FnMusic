package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.ConnectionProfile
import com.seasonyuu.fnmusic.core.model.Endpoint
import com.seasonyuu.fnmusic.core.model.LoginForm
import com.seasonyuu.fnmusic.core.model.SessionState
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertNull
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

    @Test
    fun `edited form survives recreation without starting a session`() = runBlocking {
        val vault = FakeSessionVault(null)
        val coordinator = SessionCoordinator(NetworkRuntime(), vault)
        val form = LoginForm(true, "test-fnid", "https://nas.example/music/", "tester", "test-password", true)
        coordinator.updateLoginForm(form)

        val recreated = SessionCoordinator(NetworkRuntime(), vault)
        recreated.reconnect()

        assertEquals(form, recreated.loginForm.value)
        assertTrue(recreated.state.value is SessionState.Unresolved)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `logout clears the active session but preserves all editable fields after restart`() = runBlocking {
        val profile = ConnectionProfile(Endpoint.FnConnect("test-fnid"), "tester")
        val vault = FakeSessionVault(SavedCredentials(profile, "hash", "token"))
        val coordinator = SessionCoordinator(NetworkRuntime(), vault)
        val form = coordinator.loginForm.value.copy(password = "test-password", directUrl = "https://nas.example/")
        coordinator.updateLoginForm(form)

        coordinator.logout(clearCredentials = true)
        val restarted = SessionCoordinator(NetworkRuntime(), vault)
        restarted.reconnect()

        assertNull(vault.saved)
        assertEquals(form, restarted.loginForm.value)
        assertTrue(restarted.state.value is SessionState.Unresolved)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `legacy credentials migrate connection details without exposing a hash as a password`() {
        val profile = ConnectionProfile(Endpoint.Direct("https://nas.example/music/"), "legacy-user", true)
        val vault = FakeSessionVault(SavedCredentials(profile, "irreversible-hash", "old-token"))
        val coordinator = SessionCoordinator(NetworkRuntime(), vault)
        assertEquals(LoginForm(useDirectConnection = true, directUrl = "https://nas.example/music/", username = "legacy-user", allowPrivateLanHttp = true), coordinator.loginForm.value)
        assertEquals(coordinator.loginForm.value, vault.form)
    }

    @Test
    fun `failed login remembers input and wipes the supplied password array`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":401,"msg":"invalid password"}"""))
        val profile = ConnectionProfile(Endpoint.Direct(server.url("/").toString()), "tester", true)
        val vault = FakeSessionVault(null)
        val coordinator = SessionCoordinator(NetworkRuntime(), vault)
        val password = "incorrect-password".toCharArray()

        coordinator.connect(profile, password)

        assertTrue(coordinator.state.value is SessionState.Error)
        assertTrue(password.all { it == '\u0000' })
        assertEquals("incorrect-password", SessionCoordinator(NetworkRuntime(), vault).loginForm.value.password)
        assertNull(vault.saved)
    }

    @Test
    fun `expired session with rejected credentials returns to the saved form`() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":401,"msg":"expired"}""")) }
        val profile = ConnectionProfile(Endpoint.Direct(server.url("/").toString()), "tester", true)
        val vault = FakeSessionVault(SavedCredentials(profile, "hash", "expired-token"))
        val coordinator = SessionCoordinator(NetworkRuntime(), vault)
        val form = coordinator.loginForm.value.copy(password = "test-password")
        coordinator.updateLoginForm(form)

        coordinator.reconnect()

        assertTrue(coordinator.state.value is SessionState.Error)
        assertEquals(form, coordinator.loginForm.value)
    }

    @Test
    fun `request recovery failure leaves loading state and keeps the remembered form`() = runBlocking {
        val profile = ConnectionProfile(Endpoint.Direct(server.url("/").toString()), "tester", true)
        val vault = FakeSessionVault(SavedCredentials(profile, "hash", "expired-token"))
        val network = NetworkRuntime()
        val coordinator = SessionCoordinator(network, vault)
        network.activateBaseUrl(server.url("/"), allowPrivateLanHttp = true)
        val form = coordinator.loginForm.value.copy(password = "test-password")
        coordinator.updateLoginForm(form)
        // The library read, saved-token validation, and password login all reject authentication.
        repeat(3) { server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":401,"msg":"expired"}""")) }

        runCatching { network.api.playlists() }

        assertTrue(coordinator.state.value is SessionState.Error)
        assertEquals(form, coordinator.loginForm.value)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `session recovery can authenticate when catalog dispatcher slots are occupied`() = runBlocking {
        val profile = ConnectionProfile(Endpoint.Direct(server.url("/").toString()), "tester", true)
        val vault = FakeSessionVault(SavedCredentials(profile, "hash", "expired-token"))
        val network = NetworkRuntime()
        network.httpClient.dispatcher.maxRequests = 1
        network.httpClient.dispatcher.maxRequestsPerHost = 1
        val coordinator = SessionCoordinator(network, vault)
        network.activateBaseUrl(server.url("/"), allowPrivateLanHttp = true)
        repeat(2) { server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":401,"msg":"expired"}""")) }
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"userToken":"fresh-token","user":{}}}"""))
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"serverName":"Test NAS","serverVersion":"1"}}"""))
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"list":[],"total":0}}"""))

        kotlinx.coroutines.withTimeout(5_000) { network.api.playlists() }

        assertTrue(coordinator.state.value is SessionState.Ready)
        assertEquals("fresh-token", vault.saved?.token)
        assertEquals(5, server.requestCount)
    }

    @Test
    fun `restored session retains current user identity`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"guid":"test-user","name":"Listener","role":"admin","lastAccessedAt":"2026-09-01"}}"""))
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"serverName":"Test NAS"}}"""))
        val profile = ConnectionProfile(Endpoint.Direct(server.url("/").toString()), "tester", true)
        val coordinator = SessionCoordinator(NetworkRuntime(), FakeSessionVault(SavedCredentials(profile, "hash", "token")))

        coordinator.reconnect()

        val ready = coordinator.state.value as SessionState.Ready
        assertEquals("test-user", ready.user?.id)
        assertEquals("Listener", ready.user?.name)
        assertEquals("admin", ready.user?.role)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `login retains returned user without an additional authentication request`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"userToken":"token","user":{"guid":"test-user","name":"Listener","role":2}}}"""))
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"serverName":"Test NAS"}}"""))
        val profile = ConnectionProfile(Endpoint.Direct(server.url("/").toString()), "tester", true)
        val coordinator = SessionCoordinator(NetworkRuntime(), FakeSessionVault(null))

        coordinator.connect(profile, "password".toCharArray())

        val ready = coordinator.state.value as SessionState.Ready
        assertEquals("Listener", ready.user?.name)
        assertEquals("2", ready.user?.role)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `password change clears every old secret and retains connection fields`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"guid":"test-user","name":"Listener"}}"""))
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"serverName":"Test NAS"}}"""))
        server.enqueue(MockResponse().setBody("""{"code":0,"data":null}"""))
        val profile = ConnectionProfile(Endpoint.Direct(server.url("/").toString()), "tester", true)
        val vault = FakeSessionVault(SavedCredentials(profile, "old-hash", "old-token"))
        val coordinator = SessionCoordinator(NetworkRuntime(), vault)
        coordinator.updateLoginForm(coordinator.loginForm.value.copy(password = "old-password"))
        coordinator.reconnect()
        val password = "new-password".toCharArray()

        coordinator.changePassword(password)

        assertTrue(password.all { it == '\u0000' })
        assertNull(vault.saved)
        assertEquals("", vault.form?.password)
        assertEquals("tester", vault.form?.username)
        assertTrue(coordinator.state.value is SessionState.Unresolved)
        server.takeRequest()
        server.takeRequest()
        val request = server.takeRequest()
        assertEquals("/music/api/v1/user/passwd-change", request.path)
        assertEquals("POST", request.method)
        assertEquals("""{"password":"${"new-password".sha256()}"}""", request.body.readUtf8())
        val restarted = SessionCoordinator(NetworkRuntime(), vault)
        restarted.reconnect()
        assertTrue(restarted.state.value is SessionState.Unresolved)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `rejected password change keeps session and never replays the mutation`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"guid":"test-user"}}"""))
        server.enqueue(MockResponse().setBody("""{"code":0,"data":{"serverName":"Test NAS"}}"""))
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":401,"msg":"rejected"}"""))
        val profile = ConnectionProfile(Endpoint.Direct(server.url("/").toString()), "tester", true)
        val vault = FakeSessionVault(SavedCredentials(profile, "old-hash", "old-token"))
        val coordinator = SessionCoordinator(NetworkRuntime(), vault)
        coordinator.reconnect()
        val password = "new-password".toCharArray()

        assertTrue(runCatching { coordinator.changePassword(password) }.isFailure)

        assertTrue(password.all { it == '\u0000' })
        assertEquals("old-token", vault.saved?.token)
        assertTrue(coordinator.state.value is SessionState.Ready)
        assertEquals(3, server.requestCount)
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
