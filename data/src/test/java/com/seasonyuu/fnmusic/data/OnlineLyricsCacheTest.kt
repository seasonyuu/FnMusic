package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File

class OnlineLyricsCacheTest {
    @get:Rule val folder = TemporaryFolder()
    private val candidate = LyricsCandidate("", listOf("Song"), listOf("Artist"), onlineSource = OnlineLyricsSource.Netease, songId = "1")
    private val lines = listOf(LyricLine(0, "line", timingSource = LyricTimingSource.Line))
    @Test fun searchCacheExpiresAndMetadataChangesInvalidateIt() = runBlocking {
        var time = 0L
        var calls = 0
        var empty = false
        val provider = object : LyricsProvider {
            override val source = OnlineLyricsSource.Netease
            override suspend fun search(query: String): List<LyricsCandidate> { calls++; return if (empty) emptyList() else listOf(candidate) }
            override suspend fun load(candidate: LyricsCandidate) = lines
        }
        val repo = OnlineLyricsRepository(folder.root, listOf(provider), now = { time })
        repo.search(provider.source, "Song", "album1"); repo.search(provider.source, "Song", "album1")
        assertEquals(1, calls)
        repo.search(provider.source, "Song", "album2"); assertEquals(2, calls)
        time = 24 * 60 * 60_000L
        repo.search(provider.source, "Song", "album1"); assertEquals(3, calls)
        empty = true
        repo.search(provider.source, "Unknown"); repo.search(provider.source, "Unknown")
        assertEquals(4, calls)
        time += 30 * 60_000L
        repo.search(provider.source, "Unknown"); assertEquals(5, calls)
    }
    @Test fun clearingCancelsDownloadsAndCannotRefillDisk() = runBlocking {
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        val provider = object : LyricsProvider {
            override val source = OnlineLyricsSource.Netease
            override suspend fun search(query: String) = listOf(candidate)
            override suspend fun load(candidate: LyricsCandidate): List<LyricLine> {
                started.complete(Unit)
                try { awaitCancellation() } finally { cancelled = true }
            }
        }
        val repo = OnlineLyricsRepository(folder.root, listOf(provider))
        val fetch = async { runCatching { repo.preview(candidate) } }
        started.await(); repo.clearCache()
        assertTrue(fetch.await().isFailure)
        assertTrue(cancelled)
        assertEquals(LyricsCacheUsage(), repo.cacheUsage.value)
        assertFalse(File(folder.root, "documents").exists())
    }
    @Test fun cachedLyricsSurviveRestartAndCountSourcesSeparately() = runBlocking {
        var fetches = 0
        val providers = OnlineLyricsSource.entries.map { source -> object : LyricsProvider {
            override val source = source
            override suspend fun search(query: String) = emptyList<LyricsCandidate>()
            override suspend fun load(candidate: LyricsCandidate): List<LyricLine> { fetches++; return lines }
        } }
        val repo = OnlineLyricsRepository(folder.root, providers)
        providers.forEach { repo.preview(candidate.copy(onlineSource = it.source)) }
        assertEquals(3, repo.cacheUsage.value.count)
        val restored = OnlineLyricsRepository(folder.root, providers)
        restored.start()
        assertEquals(repo.cacheUsage.value, restored.cacheUsage.value)
        restored.preview(candidate)
        assertEquals(3, fetches)
    }
    @Test fun stageDeadlineKeepsFastSourceWhileCancellingSlowSource() = runBlocking {
        var cancelled = false
        val providers = OnlineLyricsSource.entries.take(2).map { source -> object : LyricsProvider {
            override val source = source
            override suspend fun search(query: String): List<LyricsCandidate> {
                if (source == OnlineLyricsSource.Netease) try { awaitCancellation() } finally { cancelled = true }
                return listOf(candidate.copy(onlineSource = source))
            }
            override suspend fun load(candidate: LyricsCandidate) = lines
        } }
        val repo = OnlineLyricsRepository(folder.root, providers, stageTimeoutMs = 100)
        val results = repo.automatic(song(), OnlineLyricsPreference())
        assertEquals(listOf(LyricsOrigin.QQ), results.map { it.origin })
        assertTrue(cancelled)
    }
}
