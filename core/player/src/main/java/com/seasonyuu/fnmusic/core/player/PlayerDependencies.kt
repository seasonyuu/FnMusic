package com.seasonyuu.fnmusic.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.TransferListener
import android.net.Uri
import androidx.media3.datasource.ResolvingDataSource
import com.seasonyuu.fnmusic.core.model.StreamingQuality
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSink
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.seasonyuu.fnmusic.core.model.TrackId
import okhttp3.OkHttpClient
import java.io.File

@UnstableApi
object PlayerDependencies {
    private var cache: SimpleCache? = null
    private val evictor = PlaybackCacheEvictor(512L * 1024L * 1024L)
    @Volatile private var writeEnabled = true
    @Volatile private var accountKey = "unresolved"
    private val mutableUsage = MutableStateFlow(0L to 0)
    val usage = mutableUsage.asStateFlow()

    fun setAccount(key: String) { accountKey = key; refreshUsage() }

    fun configureCache(enabled: Boolean, bytes: Long, tracks: Int) {
        writeEnabled = enabled
        cache?.let { current -> synchronized(current) {
            evictor.maxBytes = bytes
            evictor.maxTracks = tracks
            evictor.trim(current)
        } }
        refreshUsage()
    }

    fun refreshUsage() {
        cache?.let { current -> synchronized(current) {
            val keys = current.keys.filter { it.startsWith("$accountKey|") && current.getCachedSpans(it).isNotEmpty() }
            mutableUsage.value = keys.sumOf { key -> current.getCachedSpans(key).sumOf { it.length } } to keys.map { it.substringBeforeLast('|') }.distinct().size
        } }
    }

    fun clearCache() {
        cache?.let { current -> synchronized(current) {
            current.keys.filter { it.startsWith("$accountKey|") }.forEach(current::removeResource)
        } }
        refreshUsage()
    }
    lateinit var upstreamFactory: OkHttpDataSource.Factory
        private set
    @Volatile var quality: () -> StreamingQuality = { StreamingQuality.Original }
    var prepareTranscode: suspend (String) -> Unit = {}
    var maintainTranscode: suspend (String?, Long) -> Unit = { _, _ -> }
    var onTrackPlayed: suspend (TrackId) -> Unit = {}

    fun initialize(
        context: Context,
        httpClient: OkHttpClient,
        cacheBytes: Long = 512L * 1024L * 1024L,
        eventReporter: suspend (TrackId) -> Unit,
    ) {
        upstreamFactory = OkHttpDataSource.Factory(httpClient)
        onTrackPlayed = eventReporter
        if (cache == null) {
            cache = SimpleCache(
                File(context.cacheDir, "media"),
                evictor.apply { maxBytes = cacheBytes },
                StandaloneDatabaseProvider(context),
            )
        }
    }

    fun dataSourceFactory(): DataSource.Factory = DataSource.Factory {
        val current = checkNotNull(cache) { "PlayerDependencies has not been initialized" }
        val resolving = ResolvingDataSource.Factory(upstreamFactory) { spec ->
            if (spec.uri.lastPathSegment == "preset.m3u8") {
                val segments = spec.uri.pathSegments
                val index = segments.indexOf("hls")
                if (index >= 0) segments.getOrNull(index + 1)?.let { guid -> kotlinx.coroutines.runBlocking { prepareTranscode(guid) } }
            }
            spec
        }
        val cached = CacheDataSource.Factory()
            .setCache(current)
            .setCacheKeyFactory { spec ->
                val namespace = accountKey
                val segments = spec.uri.pathSegments
                val hls = segments.indexOf("hls")
                val track = spec.key ?: spec.uri.getQueryParameter("guid") ?: if (hls >= 0) segments.getOrNull(hls + 1).orEmpty() else spec.uri.toString()
                "$namespace|$track|${spec.uri.path}"
            }
            .setUpstreamDataSourceFactory(resolving)
            .setCacheWriteDataSinkFactory {
                val sink = CacheDataSink(current, CacheDataSink.DEFAULT_FRAGMENT_SIZE)
                object : DataSink {
                    var opened = false
                    override fun open(dataSpec: DataSpec) {
                        if (writeEnabled) { sink.open(dataSpec); opened = true }
                    }
                    override fun write(buffer: ByteArray, offset: Int, length: Int) {
                        if (opened && !writeEnabled) close()
                        if (opened) sink.write(buffer, offset, length)
                    }
                    override fun close() {
                        if (opened) { opened = false; sink.close(); refreshUsage() }
                    }
                }
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()
        val uncached = resolving.createDataSource()
        object : DataSource {
            private var active: DataSource = cached
            override fun addTransferListener(listener: TransferListener) { cached.addTransferListener(listener); uncached.addTransferListener(listener) }
            override fun open(dataSpec: DataSpec): Long {
                // HLS manifests describe a live transcode session and must never survive it in cache.
                active = if (dataSpec.uri.lastPathSegment?.endsWith(".m3u8") == true) uncached else cached
                return active.open(dataSpec)
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int) = active.read(buffer, offset, length)
            override fun getUri(): Uri? = active.uri
            override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders
            override fun close() = active.close()
        }
    }
}
