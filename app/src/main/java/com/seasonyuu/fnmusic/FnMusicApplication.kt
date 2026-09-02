package com.seasonyuu.fnmusic

import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.seasonyuu.fnmusic.core.player.PlayerDependencies
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class FnMusicApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var graph: AppGraph

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        PlayerDependencies.initialize(
            context = this,
            httpClient = graph.network.httpClient,
            cacheBytes = runBlocking { graph.settings.currentCacheBytes() },
            eventReporter = { id -> graph.events.reportPlayed(id) },
        )
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = graph.network.httpClient))
            }
            .crossfade(true)
            .build()
}
