package com.seasonyuu.fnmusic

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LyricsIntegrationTest {
    @Test fun androidParserPersistenceAndOfflineCacheWorkTogether() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val folder = File(context.cacheDir, "amll-integration-${System.nanoTime()}").apply { mkdirs() }
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = folder
        }
        val db = Room.inMemoryDatabaseBuilder(context, FnMusicDatabase::class.java).build()
        val server = MockWebServer().apply { start() }
        // Permit only this loopback test endpoint under the app's existing network policy.
        (context as FnMusicApplication).graph.network.activateBaseUrl(server.url("/"), allowPrivateLanHttp = true)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = LyricsRepository(File(folder, "amll"), SettingsStore(isolated), db.lyricsChoices(),
                { listOf(LyricLine(0, "NAS")) }, baseUrl = { server.url("/").toString() })
            val row = """{"rawLyricFile":"100-author-one.ttml","metadata":[["musicName",["Test"]],["artists",["Singer"]],["ncmMusicId",["123"]]]}"""
            val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div><p begin="1.000" end="3.000"><span begin="1.000" end="2.000">Hello</span> <span begin="2.000" end="3.000">你&#x597D;</span><span ttm:role="x-translation">测试翻译</span></p></div></body></tt>"""
            server.enqueue(MockResponse().setBody(row))
            repository.start(scope)
            withTimeout(10_000) { repository.index.first { it.status == LyricsIndexStatus.Updated } }
            val candidate = repository.search("Test").single()
            val track = Track(TrackId("nas-song"), "Test", listOf(Artist(ArtistId("singer"), "Singer")))
            server.enqueue(MockResponse().setBody(xml))
            val document = repository.preview(candidate)
            assertEquals("Hello 你好", document.lines.single().text)
            assertEquals("测试翻译", document.lines.single().translation)
            assertEquals(LyricTimingSource.Accurate, resolveLyricTimeline(document.lines, 0, 4000)!!.source)
            repository.choose("nas|account", track, LyricsChoice(LyricsChoiceMode.Amll, candidate, 100))
            server.shutdown()
            assertEquals(document, repository.preview(candidate))
            val result = withTimeout(10_000) { repository.observe("nas|account", track).first { it.document?.origin == LyricsOrigin.Amll } }
            assertEquals(1100L, result.document!!.lines.single().timeMs)
            repository.clearCache()
            assertEquals(0, repository.cacheUsage.value.count)
            assertEquals(100L, repository.choice("nas|account", track).first().offsetMs)
            assertNotNull(repository.index.value.version)
        } finally {
            scope.cancel(); db.close(); server.shutdown(); folder.deleteRecursively()
        }
    }
}
