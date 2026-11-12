package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

class FolderBrowserRepositoryTest {
    @Test fun capabilityIsExplicitAndIndependentOfVersion() {
        val json = NetworkRuntime().json
        for (body in listOf("{}", """{"serverVersion":"1.1.19"}""", """{"capabilities":null}""", """{"capabilities":{"folderView":false}}""")) {
            assertFalse(json.decodeFromString<SystemConfigDto>(body).capabilities?.folderView == true)
        }
        assertTrue(json.decodeFromString<SystemConfigDto>("""{"serverVersion":"future","capabilities":{"folderView":true,"newCapability":true}}""").capabilities!!.folderView == true)
    }

    @Test fun rootsAndMixedContentsPreserveOpaquePathsAndSeparateCounts() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            val repo = FolderBrowserRepository(network)
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"list":[{"guid":"root","name":"Music","path":"/vol1/Music","accessStatus":3}]}}"""))
            assertEquals(3, repo.roots().single().accessStatus)
            assertEquals("/music/api/v1/shared-library/list", server.takeRequest().path)
            val path = "/vol1/音乐 & Jazz+#?/ album "
            val response = buildJsonObject {
                put("code", 0)
                putJsonObject("data") {
                    put("sort", "updatedAt,desc")
                    putJsonObject("folder") {
                        put("path", path); put("name", " album "); put("itemCount", 24)
                        putJsonArray("folders") { add(buildJsonObject { put("path", "$path/removed"); put("name", "removed"); put("itemCount", 23); put("isAdminDeleted", true) }) }
                        putJsonArray("tracks") { add(Json.parseToJsonElement("""{"guid":"song","title":"song","duration":158828,"isCue":true,"isAdminDeleted":true,"audioSpec":{"format":"FLAC","size":42,"path":"/vol1/Music/song.flac"}}""")) }
                    }
                }
            }
            server.enqueue(MockResponse().setBody(response.toString()))
            val content = repo.content(path, FolderSort.ModifiedDescending)
            val request = server.takeRequest()
            assertEquals(path, request.requestUrl!!.queryParameter("parentPath"))
            assertEquals("updatedAt,desc", request.requestUrl!!.queryParameter("sort"))
            assertEquals(path, content.folder.path)
            assertEquals(24, content.folder.itemCount)
            assertEquals(2, content.rowCount)
            assertEquals(FolderSort.ModifiedDescending, content.sort)
            assertTrue(content.folders.single().isAdminDeleted)
            assertEquals(158.828, content.tracks.single().durationSeconds, .0001)
            assertTrue(content.tracks.single().isCue)
            assertFalse(content.tracks.single().isAvailable)
        }
    }

    @Test fun emptyDirectoriesAreNotErrorsAndCompactPlayableTracksAreSupported() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            val repo = FolderBrowserRepository(network)
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"folder":{"path":"/music/empty","name":"empty","folders":[],"tracks":[]},"sort":"title,asc"}}"""))
            assertEquals(0, repo.content("/music/empty", FolderSort.TitleAscending).rowCount)
            server.takeRequest()
            server.enqueue(MockResponse().setBody("""{"code":0,"data":{"tracks":[{"guid":"track","title":"title","artists":[],"coverId":"cover"}]}}"""))
            val tracks = repo.playableTracks(FolderSelection(listOf("/music/album ", "/music/album "), listOf(TrackId("track"))))
            assertEquals("title", tracks.single().title)
            assertEquals(0.0, tracks.single().durationSeconds, .0)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/music/api/v1/folder-view/playable-tracks", request.path)
            assertEquals(Json.parseToJsonElement("""{"folderPaths":["/music/album "],"trackGUIDs":["track"]}"""), Json.parseToJsonElement(request.body.readUtf8()))
            assertTrue(repo.playableTracks(FolderSelection()).isEmpty())
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun mutationsUseDistinctWireFieldsAndAreNeverRetriedOnAuthFailure() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val network = NetworkRuntime().apply { activateBaseUrl(server.url("/music/"), true) }
            var recoveries = 0
            network.installSessionRecovery { recoveries++; true }
            val repo = FolderBrowserRepository(network)
            val selection = FolderSelection(listOf("/music/album"), listOf(TrackId("t"), TrackId("t")))
            suspend fun expect(endpoint: String, extra: String, operation: suspend () -> Unit) {
                server.enqueue(MockResponse().setBody("""{"code":0,"data":null}"""))
                operation()
                val request = server.takeRequest()
                assertEquals("/music/api/v1/folder-view/$endpoint", request.path)
                assertEquals(Json.parseToJsonElement("""{"folderPaths":["/music/album"],"trackGUIDs":["t"]$extra}"""), Json.parseToJsonElement(request.body.readUtf8()))
            }
            expect("add-to-playlist", ",\"playlistGUID\":\"playlist\"") { repo.addToPlaylist(selection, PlaylistId("playlist")) }
            expect("delete", ",\"isPermanent\":false") { repo.delete(selection, false) }
            expect("delete", ",\"isPermanent\":true") { repo.delete(selection, true) }
            expect("recover", "") { repo.recover(selection) }
            server.enqueue(MockResponse().setBody("""{"code":401,"msg":"expired"}"""))
            assertTrue(runCatching { repo.delete(selection, true) }.exceptionOrNull() is MusicError.Authentication)
            assertEquals(0, recoveries)
            assertEquals(5, server.requestCount)
        }
    }
}
