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

    @Test fun directoryReadsEncodeParentAndKeepUnknownMetadata() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            val repo = MusicAdministrationRepository(network)
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"list":[{"path":"/vol1/1000/Music","storageType":3,"permission":"rw"}]}}"""))
            assertEquals(3, repo.authorizedDirectories().single().storageType)
            assertEquals("/music/api/v1/app-center/authed-dir/list", server.takeRequest().path)
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"list":[{"path":"/vol1/1000/Music/Album","name":"Album"}]}}"""))
            val parent = "/vol1/1000/音乐 & Jazz+#?"
            assertEquals("Album", repo.childDirectories(parent).single().name)
            assertEquals(parent, server.takeRequest().requestUrl!!.queryParameter("parent"))
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"guid":"folder","path":"/vol1/1000/Music","metadataPreference":"future_mode","accessStatus":7}}"""))
            val folder = repo.folderDetail("folder")
            assertEquals("future_mode", folder.metadataPreference)
            assertEquals(7, folder.accessStatus)
            assertEquals("folder", server.takeRequest().requestUrl!!.queryParameter("guid"))
        }
    }
    @Test fun maintenanceWritesMatchWebAndLocalOnlyDisablesLyrics() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            val repo = MusicAdministrationRepository(network)
            suspend fun check(path: String, body: String, operation: suspend () -> Unit) {
                server.enqueue(MockResponse().setBody("""{"code":0,"data":null}"""))
                operation()
                val request = server.takeRequest()
                assertEquals("POST", request.method)
                assertEquals("/music/api/v1/$path", request.path)
                assertEquals(body, request.body.readUtf8())
            }
            check("shared-library/scan-all", "") { repo.scanAllFolders() }
            check("task/cancel", """{"taskId":"task-1"}""") { repo.cancelTask("task-1") }
            check("task/retry", """{"taskId":"task-1"}""") { repo.retryTask("task-1") }
            check("shared-library/create", """{"path":"/vol1/1000/Music","metadataPreference":"local_only","autoDownloadLyric":false}""") {
                repo.saveFolder(null, "/vol1/1000/Music", "local_only", true)
            }
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"trackCount":5,"albumCount":2,"artistCount":1}}"""))
            assertEquals(SearchIndexResult(5, 2, 1), repo.rebuildSearchIndex())
            val request = server.takeRequest()
            assertEquals("/music/api/v1/search/index/rebuild", request.path)
            assertEquals(0L, request.bodySize)
        }
    }
    @Test fun taskParserPreservesLifecycleAndLyricTasks() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            val repo = MusicAdministrationRepository(network)
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"list":[{"id":"task-1","type":"lyricDownload","name":"lyrics","total":10,"successCount":4,"failCount":2,"canceledCount":1,"done":false,"cancelling":true,"canceled":false,"retryable":true,"createdAt":123,"doneAt":0,"ext":{"libraryGUID":"folder"}}]}}"""))
            val task = repo.scanTasks().single()
            assertEquals("task-1", task.id); assertEquals("lyricDownload", task.type)
            assertEquals("folder", task.libraryGuid); assertTrue(task.cancelling); assertTrue(task.retryable)
            assertEquals(1, task.canceledCount); assertEquals(123L, task.createdAt)
        }
    }
    @Test fun mutationBusinessErrorsAndSessionExpiryNeverReplay() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            var recoveries = 0
            network.installSessionRecovery { recoveries++; true }
            val repo = MusicAdministrationRepository(network)
            server.enqueue(MockResponse().setResponseCode(401).setBody("""{"code":401,"msg":"expired"}"""))
            assertTrue(runCatching { repo.scanAllFolders() }.isFailure)
            server.enqueue(MockResponse().setBody("""{"code":403,"msg":"denied"}"""))
            assertTrue(runCatching { repo.cancelTask("task") }.exceptionOrNull()!!.isMusicPermissionDenied())
            server.enqueue(MockResponse().setBody("""{"code":123,"msg":"failed"}"""))
            assertTrue(runCatching { repo.retryTask("task") }.isFailure)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            assertTrue(runCatching { repo.scanAllFolders() }.isFailure)
            assertEquals(4, server.requestCount); assertEquals(0, recoveries)
        }
    }
}
