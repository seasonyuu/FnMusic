package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class OnlineLyricsTest {
    @get:Rule val folder = TemporaryFolder()
    private fun row(source: OnlineLyricsSource = OnlineLyricsSource.Netease, album: String = "Album", duration: Long = 200000) =
        LyricsCandidate("", listOf("Song"), listOf("Artist", "Alias"), listOf(album), onlineSource = source, songId = "1", durationMs = duration)
    private val track = song().copy(durationSeconds = 200.0)
    @Test fun matchingUsesAllArtistsDurationAndAlbumDisambiguation() {
        val row = row()
        assertEquals(row, OnlineLyricsMatcher.match(listOf(row), track))
        assertNull(OnlineLyricsMatcher.match(listOf(row.copy(durationMs = 205000)), track))
        assertNull(OnlineLyricsMatcher.match(listOf(row), track.copy(title = "Song (Live)")))
        assertNull(OnlineLyricsMatcher.match(listOf(row), track.copy(artists = emptyList())))
        assertNull(OnlineLyricsMatcher.match(listOf(row), track.copy(artists = track.artists + Artist(ArtistId("guest"), "Guest"))))
        assertEquals(row, OnlineLyricsMatcher.match(listOf(row, row(album = "Other")), track))
        assertNull(OnlineLyricsMatcher.match(listOf(row, row.copy(songId = "2")), track))
    }
    @Test fun parsesThreeWordFormatsAndPreservesText() {
        val expected = "你 A !"
        val inputs = mapOf(
            OnlineLyricsSource.Netease to "[1000,1000](1000,300,0)你 (1300,700,0)A !",
            OnlineLyricsSource.QQ to "[1000,1000]你 (1000,300)A !(1300,700)",
            OnlineLyricsSource.Kugou to "[1000,1000]<0,300,0>你 <300,700,0>A !",
        )
        inputs.forEach { (source, raw) ->
            val line = OnlineLyricsParser.timed(raw, source).single()
            assertEquals(expected, line.text)
            assertEquals(LyricTimingSource.Accurate, line.timingSource)
            assertEquals(1300, line.segments[1].startMs)
            assertEquals("译文", OnlineLyricsParser.translate(listOf(line), "[00:01.000]译文").single().translation)
        }
        val broken = OnlineLyricsParser.timed("[1000,1000](1000,3000,0)字", OnlineLyricsSource.Netease).single()
        assertEquals(LyricTimingSource.Line, broken.timingSource)
        assertTrue(broken.segments.isEmpty())
        assertEquals(" text ", OnlineLyricsParser.lrc("[00:01.00] text ").single().text)
    }
    @Test fun qrcCipherMatchesProtocolVector() {
        assertEquals(listOf(96,190,35,84,205,253,245,242), QrcCipher.decrypt(ByteArray(8) { it.toByte() }).map { it.toInt() and 255 })
        assertEquals(listOf(212,248,10,163,76,160,181,156), QrcCipher.decrypt(ByteArray(8)).map { it.toInt() and 255 })
    }
    @Test fun qrcEntitiesAndKrcCompressionAreDecoded() {
        val xml = "<QrcInfos><Lyric_1 LyricType=\"1\" LyricContent=\"[1000,500]A &amp; B(1000,500)\"/></QrcInfos>"
        assertEquals("A & B", OnlineLyricsParser.timed(xml, OnlineLyricsSource.QQ).single().text)
        val raw = "[1000,500]<0,500,0>字"
        val bytes = java.io.ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(raw.toByteArray()) } }.toByteArray()
        val key = intArrayOf(64,71,97,119,94,50,116,71,81,54,49,45,206,210,110,105)
        val encoded = Base64.getEncoder().encodeToString("krc1".toByteArray() + ByteArray(bytes.size) { (bytes[it].toInt() xor key[it % 16]).toByte() })
        assertEquals(raw, OnlineLyricsParser.krc(encoded))
        assertThrows(Exception::class.java) { OnlineLyricsParser.krc("AAAA") }
    }
    @Test fun parallelCompletionOrderDoesNotChangeProviderPriority() = runBlocking {
        val providers = OnlineLyricsSource.entries.map { source -> object : LyricsProvider {
            override val source = source
            override suspend fun search(query: String): List<LyricsCandidate> { delay((3 - source.ordinal) * 20L); return listOf(row(source)) }
            override suspend fun load(candidate: LyricsCandidate) = OnlineLyricsParser.timed("[1000,500](1000,500,0)字", OnlineLyricsSource.Netease)
        } }
        val repo = OnlineLyricsRepository(folder.root, providers)
        val results = repo.automatic(track, OnlineLyricsPreference())
        assertEquals(listOf(LyricsOrigin.Netease, LyricsOrigin.QQ, LyricsOrigin.Kugou), results.map { it.origin })
        assertEquals(3, repo.cacheUsage.value.count)
        repo.clearCache()
        assertEquals(LyricsCacheUsage(), repo.cacheUsage.value)
    }
    @Test fun timeoutRetainsCompletedSourcesAndDoesNotCacheErrors() = runBlocking {
        var calls = 0
        val provider = object : LyricsProvider {
            override val source = OnlineLyricsSource.Netease
            override suspend fun search(query: String): List<LyricsCandidate> { calls++; error("offline") }
            override suspend fun load(candidate: LyricsCandidate) = emptyList<LyricLine>()
        }
        val repo = OnlineLyricsRepository(folder.root, listOf(provider), stageTimeoutMs = 100)
        repeat(2) { assertTrue(repo.automatic(track, OnlineLyricsPreference(sources = setOf(provider.source))).isEmpty()) }
        assertEquals(2, calls)
    }
}

/** Explicitly opt-in; normal unit tests never contact external services. */
class OnlineLyricsSmokeTest {
    @Test fun searchAndLoadKnownRecording() = runBlocking {
        Assume.assumeTrue(System.getenv("FNMUSIC_LYRICS_SMOKE") == "1")
        defaultLyricsProviders().forEach { provider ->
            val candidates = provider.search("怪咖 薛之谦")
            val selected = candidates.first { it.titles.contains("怪咖") && it.artists.contains("薛之谦") }
            val lines = provider.load(selected)
            println("${provider.source}: id=${selected.songId}, lines=${lines.size}, accurate=${lines.count { it.timingSource == LyricTimingSource.Accurate }}")
            assertTrue("${provider.source} lyrics missing", lines.isNotEmpty())
            assertTrue("${provider.source} word timing missing", lines.any { it.timingSource == LyricTimingSource.Accurate })
        }
    }
}
