package com.seasonyuu.fnmusic.core.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan

/** Limits whole tracks as well as bytes; all entry points run under the cache monitor. */
@UnstableApi
internal class PlaybackCacheEvictor(var maxBytes: Long, var maxTracks: Int = 0) : CacheEvictor {
    override fun requiresCacheSpanTouches() = true
    override fun onCacheInitialized() = Unit
    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) = Unit
    override fun onSpanAdded(cache: Cache, span: CacheSpan) = trim(cache)
    override fun onSpanRemoved(cache: Cache, span: CacheSpan) = Unit
    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) = Unit

    fun trim(cache: Cache) {
        val resources = cache.keys.mapNotNull { key ->
            cache.getCachedSpans(key).takeIf { it.isNotEmpty() }?.let { spans ->
                Resource(key, spans.maxOf { it.lastTouchTimestamp }, spans.sumOf { it.length })
            }
        }
        // A key has account | track | representation. Multiple encodings count as one track.
        val tracks = resources.groupBy { it.key.substringBeforeLast('|') }
            .values.sortedBy { group -> group.maxOf { it.touched } }
        var bytes = resources.sumOf { it.bytes }
        var count = tracks.size
        for (group in tracks) {
            if (bytes <= maxBytes && (maxTracks == 0 || count <= maxTracks)) break
            group.forEach { cache.removeResource(it.key); bytes -= it.bytes }
            count--
        }
    }
    private data class Resource(val key: String, val touched: Long, val bytes: Long)
}
