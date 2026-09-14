package com.seasonyuu.fnmusic.core.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class PlaybackCacheEvictorTest {
    @Test fun trackLimitCountsTracksInsteadOfFragmentsAndResizesImmediately() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "cache-test-${UUID.randomUUID()}")
        val evictor = PlaybackCacheEvictor(10_000, 2)
        val cache = SimpleCache(dir, evictor, StandaloneDatabaseProvider(context))
        fun add(key: String, position: Long = 0) {
            val hole = checkNotNull(cache.startReadWrite(key, position, 100))
            try {
                val file = cache.startFile(key, position, 100)
                file.writeBytes(ByteArray(100))
                cache.commitFile(file, 100)
            } finally { cache.releaseHoleSpan(hole) }
        }
        try {
            add("account|song-a|original")
            add("account|song-a|original", 100)
            add("account|song-b|original")
            assertEquals(300L, cache.cacheSpace)
            assertEquals(2, cache.keys.size)
            evictor.maxTracks = 1
            synchronized(cache) { evictor.trim(cache) }
            assertEquals(1, cache.keys.size)
            evictor.maxBytes = 0
            synchronized(cache) { evictor.trim(cache) }
            assertEquals(0L, cache.cacheSpace)
        } finally { cache.release(); dir.deleteRecursively() }
    }
    @Test fun clearingCachedTrackKeepsAnOpenReadAlive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "cache-read-test-${UUID.randomUUID()}")
        val cache = SimpleCache(dir, PlaybackCacheEvictor(10000), StandaloneDatabaseProvider(context))
        val key = "account|song|original"
        val expected = ByteArray(256) { it.toByte() }
        val hole = checkNotNull(cache.startReadWrite(key, 0, 256))
        val file = cache.startFile(key, 0, 256)
        file.writeBytes(expected); cache.commitFile(file, 256); cache.releaseHoleSpan(hole)
        val source = androidx.media3.datasource.cache.CacheDataSource.Factory().setCache(cache).createDataSource()
        try {
            source.open(androidx.media3.datasource.DataSpec.Builder().setUri("https://example.invalid/song").setKey(key).setLength(256).build())
            val actual = ByteArray(256)
            val first = source.read(actual, 0, 16)
            cache.removeResource(key)
            var position = first
            while (position < actual.size) {
                val count = source.read(actual, position, actual.size - position)
                if (count < 0) break
                position += count
            }
            assertEquals(256, position)
            assertArrayEquals(expected, actual)
            assertEquals(0L, cache.cacheSpace)
        } finally { source.close(); cache.release(); dir.deleteRecursively() }
    }

}
