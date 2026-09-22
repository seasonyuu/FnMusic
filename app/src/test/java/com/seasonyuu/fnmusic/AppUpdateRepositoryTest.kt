package com.seasonyuu.fnmusic

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test

class AppUpdateRepositoryTest {
    private fun release(tag: String = "v1.10.0", flags: String = "\"draft\":false,\"prerelease\":false") =
        """{"tag_name":"$tag",$flags,"published_at":"2026-09-20T12:00:00Z","body":"Changes","html_url":"https://unexpected.example/apk"}"""

    @Test fun comparesNumericVersionsAndDevelopmentBase() {
        assertTrue(parseRelease(release(), "1.9.9").newer)
        assertFalse(parseRelease(release(), "1.10.0").newer)
        assertFalse(parseRelease(release(), "2.0.0").newer)
        assertFalse(parseRelease(release(), "1.10.0-dev.20+abcdef0").newer)
        assertTrue(parseRelease(release(), "1.9.9-dev.999+abcdef0").newer)
        assertTrue(parseRelease(release(), "0.0.0-dev.1+abcdef0").newer)
        assertEquals("https://github.com/seasonyuu/FnMusic/releases/tag/v1.10.0", parseRelease(release(), "1.0.0").url)
    }

    @Test fun rejectsMalformedAndNonStableReleases() {
        listOf("v1.2", "v01.2.3", "v1.2.3-beta.1", "v999999999999.2.3", "v1.2.3/path").forEach {
            assertThrows(IllegalArgumentException::class.java) { parseRelease(release(it), "1.0.0") }
        }
        assertThrows(IllegalArgumentException::class.java) { parseRelease(release(flags = "\"draft\":false,\"prerelease\":true"), "1.0.0") }
        assertThrows(IllegalArgumentException::class.java) { parseRelease(release(flags = "\"draft\":true,\"prerelease\":false"), "1.0.0") }
        assertThrows(IllegalArgumentException::class.java) { parseRelease(release(), "unknown") }
        assertThrows(IllegalArgumentException::class.java) { parseRelease("not json", "1.0.0") }
    }

    @Test fun handlesNullReleaseNotes() {
        assertEquals("", parseRelease(release().replace("\"Changes\"", "null"), "1.0.0").notes)
    }

    @Test fun publicRequestDoesNotCarryCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(release()))
            val result = AppUpdateRepository(endpoint = server.url("/latest").toString()).check("1.0.0")
            assertTrue(result.newer)
            val request = server.takeRequest()
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            assertEquals("application/vnd.github+json", request.getHeader("Accept"))
        }
    }

    @Test fun handlesMissingReleaseRateLimitAndServerFailure() {
        MockWebServer().use { server ->
            val repository = AppUpdateRepository(endpoint = server.url("/latest").toString())
            listOf(404 to "暂未", 403 to "限制", 429 to "限制", 500 to "HTTP 500").forEach { (code, message) ->
                server.enqueue(MockResponse().setResponseCode(code))
                val error = assertThrows(IOException::class.java) { runBlocking { repository.check("1.0.0") } }
                assertTrue(error.message.orEmpty().contains(message))
            }
            server.enqueue(MockResponse().setBody("{broken"))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repository.check("1.0.0") } }
        }
    }

    @Test fun boundsAnUnresponsiveRequest() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val client = OkHttpClient.Builder().callTimeout(150, TimeUnit.MILLISECONDS).build()
            val repository = AppUpdateRepository(client, server.url("/latest").toString())
            assertThrows(IOException::class.java) { runBlocking { repository.check("1.0.0") } }
        }
    }
}
