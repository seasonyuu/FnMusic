package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.MusicError
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class FnConnectResolverTest {
    private fun musicPage() = MockResponse()
        .setHeader("Content-Type", "text/html; charset=utf-8")
        .setBody("<!doctype html><title>飞牛音乐</title>")

    @Test
    fun acceptsMusicPageWithoutRelayCookie() {
        MockWebServer().use { server ->
            server.enqueue(musicPage())
            val cookies = MemoryCookieJar()
            val resolver = FnConnectResolver(OkHttpClient.Builder().cookieJar(cookies).build(), Json)
            val url = server.url("/music/")
            assertEquals(url, resolver.activate(url, "abcdef"))
            assertTrue(cookies.loadForRequest(url).isEmpty())
        }
    }

    @Test
    fun retainsLegacyCookieAcrossActivationRedirect() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302)
                .setHeader("Location", "/music/")
                .setHeader("Set-Cookie", "mode=relay; Path=/; HttpOnly"))
            server.enqueue(musicPage())
            val cookies = MemoryCookieJar()
            val resolver = FnConnectResolver(OkHttpClient.Builder().cookieJar(cookies).build(), Json)
            val url = server.url("/music/")
            assertEquals(url, resolver.activate(url, "abcdef"))
            server.takeRequest()
            assertEquals("mode=relay", server.takeRequest().getHeader("Cookie"))
            assertTrue(cookies.loadForRequest(url).any { it.name == "mode" && it.value == "relay" })
        }
    }

    @Test
    fun rejectsBootstrapAndErrorsEvenWithRelayCookie() {
        for (response in listOf(
            MockResponse().setHeader("Content-Type", "text/html")
                .setHeader("Set-Cookie", "mode=relay; Path=/")
                .setBody("<title>FN Connect</title>"),
            musicPage().setResponseCode(503),
            musicPage().setHeader("Content-Type", "application/json"),
        )) {
            MockWebServer().use { server ->
                server.enqueue(response)
                val resolver = FnConnectResolver(OkHttpClient.Builder().cookieJar(MemoryCookieJar()).build(), Json)
                assertThrows(MusicError.Protocol::class.java) {
                    resolver.activate(server.url("/music/"), "abcdef")
                }
            }
        }
    }

    @Test
    fun rejectsRedirectAwayFromMusicRoute() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "/abcdef/music/"))
            server.enqueue(musicPage())
            val resolver = FnConnectResolver(OkHttpClient(), Json)
            assertThrows(MusicError.Protocol::class.java) {
                resolver.activate(server.url("/music/"), "abcdef")
            }
        }
    }
}
