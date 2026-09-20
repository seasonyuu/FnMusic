package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class OnlineLyricsProvidersTest {
    private lateinit var server: MockWebServer
    private lateinit var http: LyricsHttp
    @Before fun setup() { server = MockWebServer(); server.start(); http = LyricsHttp(rewriteUrl = { url -> server.url("/" + url.substringAfter("://").substringAfter('/')).toString() }) }
    @After fun cleanup() { server.shutdown() }
    @Test fun neteaseSearchAndYrcUseIsolatedEapiRequests() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":200,"data":{"resources":[{"baseInfo":{"simpleSongData":{"id":574921549,"name":"怪咖","ar":[{"name":"薛之谦"}],"al":{"name":"怪咖"},"dt":250491}}}]}}"""))
        val provider = NeteaseLyricsProvider(http)
        val candidate = provider.search("怪咖 薛之谦").single()
        assertEquals("574921549", candidate.songId)
        val request = server.takeRequest()
        assertEquals("/eapi/search/song/list/page", request.path)
        assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
        val wire = request.body.readUtf8().removePrefix("params=").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertTrue(String(cipher.doFinal(wire)).contains("怪咖 薛之谦"))
        server.enqueue(MockResponse().setBody("""{"code":200,"yrc":{"lyric":"[1000,1000](1000,1000,0)Test"},"tlyric":{"lyric":"[00:01]译文"}}"""))
        assertEquals("译文", provider.load(candidate).single().translation)
    }
    @Test fun qqSearchAndPlainQrcFixturePreserveTiming() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"code":0,"req_0":{"code":0,"data":{"body":{"item_song":[{"id":214182478,"title":"怪咖","singer":[{"name":"薛之谦"}],"album":{"name":"怪咖"},"interval":250}]}}}}"""))
        val provider = QQLyricsProvider(http)
        val candidate = provider.search("怪咖").single()
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("DoSearchForQQMusicLite", body["req_0"]!!.jsonObject["method"]!!.jsonPrimitive.content)
        assertEquals(250000L, candidate.durationMs)
        server.enqueue(MockResponse().setBody("""{"code":0,"req_0":{"code":0,"data":{"lyric":"[1000,1000]Test(1000,1000)"}}}"""))
        assertEquals(LyricTimingSource.Accurate, provider.load(candidate).single().timingSource)
    }
    @Test fun kugouSearchRetainsHashAndUsesSignedQuery() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"error_code":0,"data":{"lists":[{"ID":127102960,"SongName":"怪咖","Singers":[{"name":"薛之谦"}],"AlbumName":"怪咖","Duration":250,"FileHash":"abc123"}]}}"""))
        val candidate = KugouLyricsProvider(http).search("怪咖").single()
        assertEquals("abc123", candidate.downloadMetadata["hash"])
        val url = server.takeRequest().requestUrl!!
        assertEquals("怪咖", url.queryParameter("keyword"))
        assertEquals(32, url.queryParameter("signature")!!.length)
    }
    @Test fun errorsAreNotReportedAsSuccessfulEmptySearches() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        assertTrue(runCatching { QQLyricsProvider(http).search("Song") }.isFailure)
        server.enqueue(MockResponse().setBody("<html>unavailable</html>"))
        assertTrue(runCatching { NeteaseLyricsProvider(http).search("Song") }.isFailure)
        server.enqueue(MockResponse().setBody("""{"error_code":1}"""))
        assertTrue(runCatching { KugouLyricsProvider(http).search("Song") }.isFailure)
    }
}
