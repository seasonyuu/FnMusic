package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable
internal data class IndexManifest(val hash: String, val bytes: Long, val checkedAt: Long, val etag: String? = null, val modified: String? = null)

/** All external traffic uses this dedicated client, with no NAS interceptors, cookies or HTTP cache. */
class LyricsRepository(
    private val root: File,
    private val settings: SettingsStore,
    private val choices: LyricsChoiceDao,
    private val fnLyrics: suspend (TrackId) -> List<LyricLine>,
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
    private val baseUrl: (LyricsFetchSource) -> String = { it.baseUrl },
    private val now: () -> Long = System::currentTimeMillis,
    private val online: OnlineLyricsRepository? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableIndex = MutableStateFlow(LyricsIndexState())
    val index: StateFlow<LyricsIndexState> = mutableIndex.asStateFlow()
    private val mutableUsage = MutableStateFlow(LyricsCacheUsage())
    val cacheUsage: StateFlow<LyricsCacheUsage> = mutableUsage.asStateFlow()
    private val source = MutableStateFlow<LyricsFetchSource?>(null)
    private val revision = MutableStateFlow(0L)
    private val lock = Any()
    private val updateMutex = Mutex()
    private var sourceEpoch = 0L
    private var cacheEpoch = 0L
    private var rows: List<LyricsCandidate> = emptyList()
    private var manifest: IndexManifest? = null
    private val validatedFiles = mutableMapOf<File, Pair<Long, Long>>()
    private val lyricCalls = mutableSetOf<Call>()
    private val indexCalls = mutableSetOf<Call>()
    private val indexes = File(root, "indexes")
    private val lyrics = File(root, "lyrics")

    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            refreshUsage()
            combine(settings.lyricsSource, settings.amllEnabled) { selected, enabled -> selected to enabled }
                .distinctUntilChanged().collectLatest { (selected, enabled) ->
                val switched = source.value != null
                val changedSource = source.value != selected
                if (changedSource) synchronized(lock) {
                    sourceEpoch++
                    indexCalls.forEach(Call::cancel); lyricCalls.forEach(Call::cancel)
                    rows = emptyList(); manifest = null
                    mutableIndex.value = LyricsIndexState(source = selected)
                    source.value = selected
                }
                if (changedSource) loadIndex(selected)
                if (!enabled) synchronized(lock) { indexCalls.forEach(Call::cancel) }
                if (enabled) updateIndex(force = switched)
            }
        }
    }

    suspend fun setSource(value: LyricsFetchSource) = settings.setLyricsSource(value)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun atomic(file: File, bytes: ByteArray) {
        file.parentFile!!.mkdirs()
        val temporary = File.createTempFile("pending-", ".tmp", file.parentFile)
        try {
            temporary.writeBytes(bytes)
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } finally { temporary.delete() }
    }

    private fun loadIndex(selected: LyricsFetchSource) {
        val epoch = synchronized(lock) { sourceEpoch }
        val loaded = runCatching {
            val folder = File(indexes, selected.name)
            val saved = json.decodeFromString<IndexManifest>(File(folder, "manifest.json").readText())
            require(saved.hash.matches(Regex("[a-f0-9]{64}")))
            val bytes = File(folder, "${saved.hash}.jsonl").readBytes()
            require(hash(bytes) == saved.hash && bytes.size.toLong() == saved.bytes)
            saved to AmllIndex.parse(bytes.toString(Charsets.UTF_8))
        }.getOrNull() ?: return
        synchronized(lock) {
            if (sourceEpoch != epoch || source.value != selected) return
            manifest = loaded.first; rows = loaded.second
            mutableIndex.value = loaded.first.state(selected, LyricsIndexStatus.Current)
            revision.value++
        }
    }

    private fun IndexManifest.state(selected: LyricsFetchSource, status: LyricsIndexStatus) =
        LyricsIndexState(selected, hash.take(12), bytes, checkedAt, status)

    suspend fun updateIndex(force: Boolean = true) = withContext(Dispatchers.IO) {
        val selected = source.filterNotNull().first()
        val epoch = synchronized(lock) { sourceEpoch }
        updateMutex.withLock {
            ensureActive()
            if (!isCurrent(selected, epoch)) return@withLock
            val previous = synchronized(lock) { manifest }
            if (!force && previous != null && now() - previous.checkedAt in 0 until INDEX_AGE) return@withLock
            synchronized(lock) {
                if (sourceEpoch != epoch || source.value != selected) return@withLock
                mutableIndex.value = mutableIndex.value.copy(status = LyricsIndexStatus.Checking, error = null)
            }
            try {
                val request = Request.Builder().url(baseUrl(selected) + "metadata/raw-lyrics-index.jsonl")
                    .header("Cache-Control", "no-cache")
                previous?.etag?.let { request.header("If-None-Match", it) }
                previous?.modified?.let { request.header("If-Modified-Since", it) }
                val response = fetch(request.build(), false, selected, epoch)
                ensureActive()
                val saved: IndexManifest
                var parsed: List<LyricsCandidate>? = null
                var content: ByteArray? = null
                if (response.code == 304) {
                    require(previous != null) { "服务返回无效的索引更新响应" }
                    saved = previous.copy(checkedAt = now())
                } else {
                    require(response.code == 200) { "获取源返回 HTTP ${response.code}" }
                    content = response.bytes
                    val digest = hash(content)
                    parsed = AmllIndex.parse(content.toString(Charsets.UTF_8))
                    saved = IndexManifest(digest, content.size.toLong(), now(), response.etag, response.modified)
                }
                synchronized(lock) {
                    if (sourceEpoch != epoch || source.value != selected) return@synchronized
                    val folder = File(indexes, selected.name)
                    content?.let { atomic(File(folder, "${saved.hash}.jsonl"), it) }
                    atomic(File(folder, "manifest.json"), json.encodeToString(saved).toByteArray())
                    folder.listFiles()?.filter { it.extension == "jsonl" && it.name != "${saved.hash}.jsonl" }?.forEach(File::delete)
                    manifest = saved
                    parsed?.let { rows = it }
                    mutableIndex.value = saved.state(selected, if (saved.hash == previous?.hash) LyricsIndexStatus.Current else LyricsIndexStatus.Updated)
                    if (saved.hash != previous?.hash) revision.value++
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                synchronized(lock) {
                    if (sourceEpoch == epoch && source.value == selected) mutableIndex.value = mutableIndex.value.copy(
                        status = LyricsIndexStatus.Failed, checkedAt = now(), error = error.message ?: "索引更新失败，请重试或切换获取源")
                }
            }
        }
    }

    private fun isCurrent(selected: LyricsFetchSource, epoch: Long) = synchronized(lock) { source.value == selected && sourceEpoch == epoch }
    private data class Download(val code: Int, val bytes: ByteArray, val etag: String?, val modified: String?)
    private suspend fun fetch(request: Request, lyric: Boolean, selected: LyricsFetchSource, epoch: Long, cacheGeneration: Long? = null): Download {
        val call = client.newCall(request)
        synchronized(lock) {
            if (source.value != selected || sourceEpoch != epoch) throw CancellationException("获取源已切换")
            if (cacheGeneration != null && cacheGeneration != cacheEpoch) throw IOException("缓存已清除，下载已取消")
            (if (lyric) lyricCalls else indexCalls).add(call)
        }
        return try {
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val result = response.use {
                                val limit = if (lyric) 2 * 1024 * 1024 else 20 * 1024 * 1024
                                val bytes = it.body?.byteStream()?.use { input ->
                                    val output = java.io.ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        require(output.size() + count <= limit) { "文件超出大小限制" }
                                        output.write(buffer, 0, count)
                                    }
                                    output.toByteArray()
                                } ?: byteArrayOf()
                                Download(it.code, bytes, it.header("ETag"), it.header("Last-Modified"))
                            }
                            if (continuation.isActive) continuation.resume(result)
                        } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                    }
                })
            }
        } finally { synchronized(lock) { (if (lyric) lyricCalls else indexCalls).remove(call) } }
    }

    fun choice(scope: String, track: Track): Flow<LyricsChoice> = choices.observe(scope, track.id.value).map {
        it?.let { row -> runCatching { json.decodeFromString<LyricsChoice>(row.choiceJson) }.getOrNull() } ?: LyricsChoice()
    }
    suspend fun choose(scope: String, track: Track, value: LyricsChoice) {
        require(value.mode !in setOf(LyricsChoiceMode.Amll, LyricsChoiceMode.Online) || value.candidate != null)
        choices.save(LyricsChoiceEntity(scope, track.id.value, json.encodeToString(value.copy(offsetMs = value.offsetMs.coerceIn(-600_000, 600_000)))))
    }

    suspend fun search(query: String): List<LyricsCandidate> = withContext(Dispatchers.IO) {
        AmllIndex.search(synchronized(lock) { rows }, query)
    }

    suspend fun preview(candidate: LyricsCandidate): LyricsDocument = withContext(Dispatchers.IO) {
        if (candidate.onlineSource != null) return@withContext requireNotNull(online).preview(candidate)
        val selected = source.filterNotNull().first()
        val (epoch, cacheGeneration) = synchronized(lock) { sourceEpoch to cacheEpoch }
        val path = AmllIndex.path(selected, candidate) ?: error("此获取源无法获取该歌词")
        val file = File(File(lyrics, selected.name), hash((candidate.rawFile + "|" + path).toByteArray()) + ".ttml")
        val cached = synchronized(lock) { file.takeIf(File::exists)?.readText() }
        var lines = cached?.let { runCatching { AmllTtmlParser.parse(it) }.getOrNull() }
        if (lines == null) {
            if (cached != null) synchronized(lock) { file.delete() }
            val response = fetch(Request.Builder().url(baseUrl(selected) + path).build(), true, selected, epoch, cacheGeneration)
            ensureActive()
            require(response.code == 200) { "歌词获取失败（HTTP ${response.code}），可在歌词设置中切换获取源" }
            lines = AmllTtmlParser.parse(response.bytes.toString(Charsets.UTF_8))
            synchronized(lock) {
                if (epoch != sourceEpoch || source.value != selected) throw CancellationException("获取源已切换")
                if (cacheGeneration != cacheEpoch) throw IOException("缓存已清除，下载已取消")
                atomic(file, response.bytes)
                validatedFiles[file] = file.length() to file.lastModified()
            }
            refreshUsage()
        }
        ensureActive()
        if (!isCurrent(selected, epoch)) throw CancellationException("获取源已切换")
        LyricsDocument(lines, LyricsOrigin.Amll, candidate)
    }

    private data class Observation(val source: LyricsFetchSource, val choice: LyricsChoice, val amll: Boolean, val online: OnlineLyricsPreference)

    fun observe(scope: String, track: Track): Flow<LyricsState> = channelFlow {
        var completed: Observation? = null
        var completedDocument: LyricsDocument? = null
        combine(source.filterNotNull(), revision, choice(scope, track), settings.amllEnabled, settings.onlineLyricsPreference) { selected, _, choice, enabled, preference ->
            Observation(selected, choice, enabled, preference)
        }.collectLatest { request ->
            val manual = request.choice.mode != LyricsChoiceMode.Automatic
            val old = completed
            if (old != null && old.choice == request.choice && old.source == request.source &&
                (manual || (old == request && completedDocument?.origin != LyricsOrigin.FnMusic))) return@collectLatest
            send(LyricsState(loading = true))
            coroutineScope {
                val nas = async { latestFnDocument(track) }
                var finalShown = false
                val displayLock = Any()
                val showNas = launch {
                    val document = nas.await()
                    synchronized(displayLock) { if (!finalShown) trySend(LyricsState(document, loading = request.choice.mode != LyricsChoiceMode.FnMusic)) }
                }
                var error: String? = null
                val result = try {
                    when (request.choice.mode) {
                        LyricsChoiceMode.FnMusic -> nas.await()
                        LyricsChoiceMode.Amll, LyricsChoiceMode.Online -> preview(requireNotNull(request.choice.candidate)).shifted(request.choice.offsetMs)
                        LyricsChoiceMode.Automatic -> {
                            val options = online?.automatic(track, request.online).orEmpty()
                            val words = options.firstOrNull { it.hasAccurateWords }
                            val amll = if (words == null && request.amll) {
                                if (synchronized(lock) { manifest == null }) updateIndex(false)
                                else launch { updateIndex(false) }
                                val candidate = AmllIndex.match(synchronized(lock) { rows }, track)
                                try { candidate?.let { preview(it) }?.takeIf { it.hasAccurateWords } }
                                catch (e: CancellationException) { throw e }
                                catch (_: Exception) { null }
                            } else null
                            (words ?: amll ?: options.firstOrNull())?.shifted(request.choice.offsetMs) ?: nas.await()
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message; nas.await() }
                ensureActive()
                synchronized(displayLock) {
                    finalShown = true
                    completed = request; completedDocument = result
                    trySend(LyricsState(result, error = error))
                }
                showNas.cancel(); nas.cancel()
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun latestFnDocument(track: Track): LyricsDocument {
        val lines = try { fnLyrics(track.id) } catch (e: CancellationException) { throw e } catch (_: Exception) { emptyList() }
        return LyricsDocument(lines, LyricsOrigin.FnMusic)
    }

    private fun LyricsDocument.shifted(offset: Long) = copy(offsetMs = offset, lines = lines.map { line ->
        line.copy(timeMs = line.timeMs?.plus(offset), endTimeMs = line.endTimeMs?.plus(offset),
            segments = line.segments.map { it.copy(startMs = it.startMs + offset, endMs = it.endMs + offset) })
    })

    suspend fun clearCache() = withContext(Dispatchers.IO) {
        synchronized(lock) {
            cacheEpoch++
            validatedFiles.clear()
            lyricCalls.forEach(Call::cancel)
            val deleted = lyrics.listFiles()?.map(File::deleteRecursively)?.all { it } ?: true
            refreshUsage()
            check(deleted) { "部分歌词缓存未能清除，请重试" }
        }
    }
    private fun refreshUsage() = synchronized(lock) {
        val files = lyrics.walkTopDown().filter { it.isFile && it.extension == "ttml" }.filter {
            val stamp = it.length() to it.lastModified()
            if (validatedFiles[it] == stamp) true
            else runCatching { AmllTtmlParser.parse(it.readText()); validatedFiles[it] = stamp; true }.getOrDefault(false)
        }.toList()
        mutableUsage.value = LyricsCacheUsage(files.size, files.sumOf(File::length))
    }
    companion object { private const val INDEX_AGE = 24L * 60 * 60 * 1000 }
}
