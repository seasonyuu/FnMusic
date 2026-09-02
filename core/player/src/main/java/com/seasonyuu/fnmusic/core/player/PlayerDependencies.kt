package com.seasonyuu.fnmusic.core.player

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.seasonyuu.fnmusic.core.model.TrackId
import okhttp3.OkHttpClient
import java.io.File

@UnstableApi
object PlayerDependencies {
    private var cache: SimpleCache? = null
    lateinit var upstreamFactory: OkHttpDataSource.Factory
        private set
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
                LeastRecentlyUsedCacheEvictor(cacheBytes),
                StandaloneDatabaseProvider(context),
            )
        }
    }

    fun dataSourceFactory(): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(checkNotNull(cache) { "PlayerDependencies has not been initialized" })
        .setUpstreamDataSourceFactory(upstreamFactory)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
