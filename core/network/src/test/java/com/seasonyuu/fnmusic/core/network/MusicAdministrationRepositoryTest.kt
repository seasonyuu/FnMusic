package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class MusicAdministrationRepositoryTest {
    @Test fun userPermissionsAndPasswordUseOfficialWireContract() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            val repo = MusicAdministrationRepository(network)
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"list":[{"guid":"test-user","name":"Listener","role":"member","sharedLibraryAccess":{"mode":"partial","sharedLibraries":[{"guid":"test-folder"}]}}]}}"""))
            val user = repo.users().single()
            assertEquals(FolderAccess("partial", listOf("test-folder")), user.access)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"code":0,"data":null}"""))
            repo.saveUser(user, "Listener", "new-password", FolderAccess("all", listOf("test-folder")))
            val request = server.takeRequest()
            assertEquals("/music/api/v1/user/edit", request.path)
            val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals("new-password".sha256(), body["password"]?.jsonPrimitive?.content)
            assertEquals(JsonArray(emptyList()), body["sharedLibraryAccess"]?.jsonObject?.get("guids"))
        }
    }
    @Test fun removingLibraryOnlyCallsConfigurationEndpointAndPropagatesFailure() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"code":403,"msg":"denied"}"""))
            val failure = runCatching { MusicAdministrationRepository(network).removeFolder("temporary-library") }.exceptionOrNull()
            assertTrue(failure!!.isMusicPermissionDenied())
            assertEquals("/music/api/v1/shared-library/delete", server.takeRequest().path)
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun serverRenamePreservesLanguage() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            val repo = MusicAdministrationRepository(network)
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"name":"Old NAS","lang":"zh-CN"}}"""))
            val original = repo.serverSettings()
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"code":0,"data":null}"""))
            repo.saveServerSettings(original.copy(name = "New NAS"))
            val request = server.takeRequest()
            assertEquals("/music/api/v1/settings/server", request.path)
            assertEquals("""{"name":"New NAS","lang":"zh-CN"}""", request.body.readUtf8())
        }
    }
}
