package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max

internal object OnlineLyricsMatcher {
    fun match(rows: List<LyricsCandidate>, track: Track): LyricsCandidate? {
        val duration = (track.durationSeconds * 1000).toLong()
        val compatible = rows.filter { row -> duration <= 0 || row.durationMs <= 0 || abs(row.durationMs - duration) <= max(3000L, duration / 50) }
        val title = AmllIndex.normalize(track.title)
        val artists = track.artists.map { AmllIndex.normalize(it.name) }.filter(String::isNotBlank).toSet()
        if (title.isBlank() || artists.isEmpty()) return null
        val matches = compatible.filter { row -> row.titles.any { AmllIndex.normalize(it) == title } && row.artists.map(AmllIndex::normalize).toSet().containsAll(artists) }
        if (matches.size == 1) return matches.single()
        val album = track.album?.name?.let(AmllIndex::normalize)?.takeIf(String::isNotBlank) ?: return null
        return matches.filter { it.albums.any { name -> AmllIndex.normalize(name) == album } }.singleOrNull()
    }
}

@Serializable
private data class CachedSearch(val savedAt: Long, val candidates: List<LyricsCandidate>)
@Serializable
private data class CachedOnlineLyrics(val candidate: LyricsCandidate, val lines: List<LyricLine>)

class OnlineLyricsRepository(
    private val root: File,
    private val providers: List<LyricsProvider> = defaultLyricsProviders(),
    private val now: () -> Long = System::currentTimeMillis,
    private val stageTimeoutMs: Long = 15_000,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private var generation = 0L
    private val downloads = mutableSetOf<Job>()
    private val usage = MutableStateFlow(LyricsCacheUsage())
    val cacheUsage = usage.asStateFlow()
    private val documents = File(root, "documents")
    private val searches = File(root, "searches")
    suspend fun start() = withContext(Dispatchers.IO) { refreshUsage() }
    private fun hash(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun atomic(file: File, text: String) {
        file.parentFile!!.mkdirs()
        val temp = File.createTempFile("pending-", ".tmp", file.parentFile)
        try { temp.writeText(text); Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        finally { temp.delete() }
    }
    suspend fun search(source: OnlineLyricsSource, query: String, contextKey: String = "manual"): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val epoch = synchronized(lock) { generation }
        val file = File(searches, hash("v1|$source|${AmllIndex.normalize(query)}|$contextKey") + ".json")
        val cached = synchronized(lock) { runCatching { json.decodeFromString<CachedSearch>(file.readText()) }.getOrNull() }
        if (cached != null && now() - cached.savedAt in 0 until (if (cached.candidates.isEmpty()) 30 * 60_000L else 24 * 60 * 60_000L)) return@withContext cached.candidates
        val rows = cancellableDownload { providers.first { it.source == source }.search(query).take(10) }
        ensureActive()
        synchronized(lock) { if (generation == epoch) { atomic(file, json.encodeToString(CachedSearch(now(), rows))); refreshUsage() } }
        rows
    }
    suspend fun preview(candidate: LyricsCandidate): LyricsDocument = withContext(Dispatchers.IO) {
        val source = requireNotNull(candidate.onlineSource)
        val epoch = synchronized(lock) { generation }
        val file = File(documents, hash("$source|${candidate.songId}|${candidate.downloadMetadata}") + ".json")
        val cached = synchronized(lock) { runCatching { json.decodeFromString<CachedOnlineLyrics>(file.readText()).takeIf { valid(it.lines) } }.getOrNull() }
        val lines = cached?.lines ?: cancellableDownload { providers.first { it.source == source }.load(candidate) }.also {
            require(valid(it)) { "该歌曲没有可用的同步歌词" }
            ensureActive()
            synchronized(lock) {
                check(generation == epoch) { "缓存已清除" }
                atomic(file, json.encodeToString(CachedOnlineLyrics(candidate, it)))
                refreshUsage()
            }
        }
        ensureActive()
        LyricsDocument(lines, when (source) { OnlineLyricsSource.Netease -> LyricsOrigin.Netease; OnlineLyricsSource.QQ -> LyricsOrigin.QQ; OnlineLyricsSource.Kugou -> LyricsOrigin.Kugou }, candidate)
    }
    /** Preserve completed providers when another provider reaches the stage deadline. */
    suspend fun automatic(track: Track, preference: OnlineLyricsPreference): List<LyricsDocument> = coroutineScope {
        if (!preference.enabled) return@coroutineScope emptyList()
        val results = java.util.concurrent.ConcurrentHashMap<OnlineLyricsSource, LyricsDocument>()
        val contextKey = json.encodeToString(track)
        withTimeoutOrNull(stageTimeoutMs) {
            preference.sources.map { source -> launch {
                try {
                    val row = OnlineLyricsMatcher.match(search(source, track.title + " " + track.artists.joinToString(" ") { it.name }, contextKey), track)
                    if (row != null) results[source] = preview(row)
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* A failed provider does not block lower-priority sources. */ }
            } }.joinAll()
        }
        OnlineLyricsSource.entries.mapNotNull { results[it] }
    }
    private suspend fun <T> cancellableDownload(block: suspend () -> T): T = coroutineScope {
        val task = async(start = CoroutineStart.LAZY) { block() }
        synchronized(lock) { downloads.add(task) }
        try { task.start(); task.await() }
        catch (e: CancellationException) { currentCoroutineContext().ensureActive(); throw IOException("缓存已清除，下载已取消", e) }
        finally { synchronized(lock) { downloads.remove(task) } }
    }
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            generation++
            downloads.toList().forEach { it.cancel() }
            val deletedDocuments = documents.deleteRecursively()
            val deletedSearches = searches.deleteRecursively()
            refreshUsage()
            check(deletedDocuments && deletedSearches) { "部分缓存未能清除" }
        }
    }
    private fun valid(lines: List<LyricLine>) = lines.isNotEmpty() && lines.any { it.timeMs != null && it.text.isNotBlank() }
    private fun refreshUsage() = synchronized(lock) {
        val files = documents.listFiles().orEmpty().filter { it.extension == "json" && runCatching { valid(json.decodeFromString<CachedOnlineLyrics>(it.readText()).lines) }.getOrDefault(false) }
        val extras = searches.listFiles().orEmpty().filter { it.isFile && it.extension == "json" }.sumOf(File::length)
        usage.value = LyricsCacheUsage(files.size, files.sumOf(File::length) + extras)
    }
}
