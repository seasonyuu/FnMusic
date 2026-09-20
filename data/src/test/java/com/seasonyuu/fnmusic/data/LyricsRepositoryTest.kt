package com.seasonyuu.fnmusic.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class LyricsRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var scope: CoroutineScope
    private lateinit var server: MockWebServer
    private lateinit var repository: LyricsRepository
    private lateinit var store: SettingsStore
    private val dao = MemoryChoices()
    private var nasCalls = 0
    @Before fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        server = MockWebServer(); server.start()
        store = SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { File(temporary.root, "settings.preferences_pb") })
        repository = LyricsRepository(File(temporary.root, "cache"), store, dao, { nasCalls++; listOf(LyricLine(0, "NAS")) },
            baseUrl = { server.url("/${it.name}/").toString() })
    }
    @After fun cleanup() { scope.cancel(); server.shutdown() }
    private suspend fun ready() {
        server.enqueue(MockResponse().setBody(indexRow()).setHeader("ETag", "v1"))
        repository.start(scope)
        withTimeout(5000) { repository.index.first { it.status == LyricsIndexStatus.Updated } }
    }
    @Test fun disabledAmllSkipsStartupAndPlaybackRequestsAndKeepsChoice() = runBlocking {
        store.setAmllEnabled(false)
        val choice = LyricsChoice(LyricsChoiceMode.Amll, AmllIndex.parse(indexRow()).single(), 100)
        repository.choose("account", song(), choice)
        repository.start(scope)
        val result = withTimeout(5000) { repository.observe("account", song()).first { it.document?.origin == LyricsOrigin.FnMusic && !it.loading } }
        assertEquals("NAS", result.document!!.lines.single().text)
        assertEquals(choice, repository.choice("account", song()).first())
        assertFalse(store.amllEnabled.first())
        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
        server.enqueue(MockResponse().setBody(indexRow()))
        server.enqueue(MockResponse().setBody(lyricFixture))
        store.setAmllEnabled(true)
        withTimeout(5000) { repository.index.first { it.status == LyricsIndexStatus.Updated } }
        val restored = withTimeout(5000) { repository.observe("account", song()).first { it.document?.origin == LyricsOrigin.Amll } }
        assertEquals(100L, restored.document!!.offsetMs)
    }

    @Test fun conditionalUpdatesPreserveVersionAndInvalidResponsesPreserveIndex() = runBlocking {
        ready()
        val initial = repository.index.value
        assertEquals(indexRow().toByteArray().size.toLong(), initial.bytes)
        assertEquals(12, initial.version!!.length)
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(304))
        repository.updateIndex()
        val request = server.takeRequest()
        assertEquals("v1", request.getHeader("If-None-Match"))
        assertEquals("no-cache", request.getHeader("Cache-Control"))
        assertEquals(initial.version, repository.index.value.version)
        server.enqueue(MockResponse().setBody("<html>error</html>"))
        repository.updateIndex()
        assertEquals(LyricsIndexStatus.Failed, repository.index.value.status)
        assertEquals(initial.version, repository.index.value.version)
        assertEquals(1, repository.search("Song").size)
    }
    @Test fun switchingSourceAlwaysChecksAndDoesNotReuseValidators() = runBlocking {
        ready(); server.takeRequest()
        server.enqueue(MockResponse().setBody(indexRow("200-author-next.ttml", title = "Next")))
        repository.setSource(LyricsFetchSource.Dimeta)
        withTimeout(5000) { repository.index.first { it.source == LyricsFetchSource.Dimeta && it.status == LyricsIndexStatus.Updated } }
        val request = server.takeRequest()
        assertEquals("/Dimeta/metadata/raw-lyrics-index.jsonl", request.path)
        assertNull(request.getHeader("If-None-Match"))
        assertEquals(0, repository.search("Song").size)
        server.enqueue(MockResponse().setResponseCode(304))
        repository.setSource(LyricsFetchSource.Bikonoo)
        withTimeout(5000) { repository.index.first { it.source == LyricsFetchSource.Bikonoo && it.status == LyricsIndexStatus.Current } }
        val back = server.takeRequest(5, TimeUnit.SECONDS)!!
        assertEquals("/Bikonoo/metadata/raw-lyrics-index.jsonl", back.path)
        assertEquals("v1", back.getHeader("If-None-Match"))
    }
    @Test fun cachesLyricsAndClearPreservesIndexAndChoice() = runBlocking {
        ready(); server.takeRequest()
        val candidate = repository.search("Song").single()
        repository.choose("account", song(), LyricsChoice(LyricsChoiceMode.Amll, candidate, 100))
        server.enqueue(MockResponse().setBody(lyricFixture))
        repository.preview(candidate)
        assertEquals(1, repository.cacheUsage.value.count)
        assertEquals(lyricFixture.toByteArray().size.toLong(), repository.cacheUsage.value.bytes)
        repository.preview(candidate)
        assertEquals(2, server.requestCount)
        val version = repository.index.value.version
        repository.clearCache()
        assertEquals(LyricsCacheUsage(), repository.cacheUsage.value)
        assertEquals(version, repository.index.value.version)
        assertEquals(100L, repository.choice("account", song()).first().offsetMs)
        assertEquals(1, repository.search("Song").size)
        assertEquals(LyricsChoice(), repository.choice("another-account", song()).first())
    }
    @Test fun automaticMatchingUsesTtmlAndFixedNasMakesNoLyricRequest() = runBlocking {
        ready(); server.takeRequest()
        server.enqueue(MockResponse().setBody(lyricFixture))
        repository.choose("account", song(), LyricsChoice(offsetMs = 100))
        val result = withTimeout(5000) { repository.observe("account", song()).first { it.document?.origin == LyricsOrigin.Amll } }
        assertEquals(1100L, result.document!!.lines.single().timeMs)
        assertEquals(2100L, result.document!!.lines.single().segments.first().endMs)
        repository.choose("account", song(), LyricsChoice(LyricsChoiceMode.FnMusic))
        val before = server.requestCount
        val nas = withTimeout(5000) { repository.observe("account", song()).first { it.document != null && !it.loading } }
        assertEquals(LyricsOrigin.FnMusic, nas.document!!.origin)
        assertEquals(before, server.requestCount)
    }
    @Test fun missingCandidateFallsBackWithoutDownloadingLyrics() = runBlocking {
        ready()
        val result = withTimeout(5000) { repository.observe("account", song("Other")).first { it.document != null && !it.loading } }
        assertEquals("NAS", result.document!!.lines.single().text)
        assertEquals(1, server.requestCount)
        assertEquals(1, nasCalls)
    }
    @Test fun clearDuringDownloadCannotRepopulateCache() = runBlocking {
        ready(); server.takeRequest()
        val candidate = repository.search("Song").single()
        server.enqueue(MockResponse().setBody(lyricFixture).setBodyDelay(2, TimeUnit.SECONDS))
        val download = async { runCatching { repository.preview(candidate) } }
        withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS)!! }
        repository.clearCache()
        download.await()
        assertEquals(LyricsCacheUsage(), repository.cacheUsage.value)
    }
    @Test fun restartRestoresSelectedSourceAndIndexWithoutUnneededDownload() = runBlocking {
        ready(); server.takeRequest()
        server.enqueue(MockResponse().setBody(indexRow("200-remote.ttml")))
        repository.setSource(LyricsFetchSource.Gdba)
        withTimeout(5000) { repository.index.first { it.source == LyricsFetchSource.Gdba && it.status == LyricsIndexStatus.Updated } }
        server.takeRequest()
        val before = server.requestCount
        val restored = LyricsRepository(File(temporary.root, "cache"), store, dao, { emptyList() },
            baseUrl = { server.url("/${it.name}/").toString() })
        restored.start(scope)
        withTimeout(5000) { restored.index.first { it.source == LyricsFetchSource.Gdba && it.version != null } }
        assertEquals(repository.index.value.version, restored.index.value.version)
        assertEquals(before, server.requestCount)
        assertEquals(1, restored.search("Song").size)
    }
    @Test fun changedAndUnchangedBodiesHaveContentVersions() = runBlocking {
        ready(); server.takeRequest()
        val initial = repository.index.value.version
        server.enqueue(MockResponse().setBody(indexRow()))
        repository.updateIndex()
        assertEquals(initial, repository.index.value.version)
        assertEquals(LyricsIndexStatus.Current, repository.index.value.status)
        server.enqueue(MockResponse().setBody(indexRow("200-new.ttml", title = "New")))
        repository.updateIndex()
        assertNotEquals(initial, repository.index.value.version)
        assertEquals(1, repository.search("New").size)
        assertEquals(0, repository.search("Song").size)
    }
    @Test fun lateResponseFromOldSourceCannotReplaceNewIndex() = runBlocking {
        ready(); server.takeRequest()
        server.enqueue(MockResponse().setBody(indexRow("200-stale.ttml", title = "Stale")).setBodyDelay(2, TimeUnit.SECONDS))
        val stale = launch { repository.updateIndex() }
        withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS)!! }
        server.enqueue(MockResponse().setBody(indexRow("300-fresh.ttml", title = "Fresh")))
        repository.setSource(LyricsFetchSource.Gdba)
        withTimeout(5000) { repository.index.first { it.source == LyricsFetchSource.Gdba && it.status == LyricsIndexStatus.Updated } }
        stale.join()
        assertEquals(1, repository.search("Fresh").size)
        assertEquals(0, repository.search("Stale").size)
    }
    @Test fun failedNewSourceDoesNotBorrowAnotherSourcesIndex() = runBlocking {
        ready(); server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(503))
        repository.setSource(LyricsFetchSource.GitHub)
        withTimeout(5000) { repository.index.first { it.source == LyricsFetchSource.GitHub && it.status == LyricsIndexStatus.Failed } }
        assertNull(repository.index.value.version)
        assertEquals(0L, repository.index.value.bytes)
        assertEquals("/GitHub/metadata/raw-lyrics-index.jsonl", server.takeRequest().path)
        assertEquals(2, server.requestCount)
    }
    @Test fun pinnedChoiceSurvivesDownloadFailureAndDoesNotRematch() = runBlocking {
        ready(); server.takeRequest()
        val candidate = repository.search("Song").single()
        val choice = LyricsChoice(LyricsChoiceMode.Amll, candidate)
        repository.choose("account", song(), choice)
        server.enqueue(MockResponse().setResponseCode(404))
        val result = withTimeout(5000) { repository.observe("account", song()).first { it.document != null && !it.loading } }
        assertEquals(LyricsOrigin.FnMusic, result.document!!.origin)
        assertNotNull(result.error)
        assertEquals(choice, repository.choice("account", song()).first())
    }
    private class MemoryChoices : LyricsChoiceDao {
        private val values = MutableStateFlow<Map<Pair<String, String>, LyricsChoiceEntity>>(emptyMap())
        override fun observe(scope: String, trackGuid: String) = values.map { it[scope to trackGuid] }
        override suspend fun save(value: LyricsChoiceEntity) { values.value = values.value + ((value.scope to value.trackGuid) to value) }
    }
}
