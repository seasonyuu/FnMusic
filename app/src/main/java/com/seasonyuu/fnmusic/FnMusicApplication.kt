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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import com.seasonyuu.fnmusic.core.model.SessionState
import com.seasonyuu.fnmusic.core.network.sha256

@HiltAndroidApp
class FnMusicApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var graph: AppGraph

    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        PlayerDependencies.initialize(
            context = this,
            httpClient = graph.network.httpClient,
            cacheBytes = runBlocking { graph.settings.currentCacheBytes() },
            eventReporter = { id -> graph.events.reportPlayed(id) },
        )
        val transcoding = com.seasonyuu.fnmusic.core.network.MusicTranscoding(graph.network)
        PlayerDependencies.prepareTranscode = transcoding::prepare
        PlayerDependencies.maintainTranscode = transcoding::maintain
        settingsScope.launch {
            graph.settings.streamingQuality.collect { preference ->
                PlayerDependencies.quality = {
                    val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
                    val wifi = connectivity.getNetworkCapabilities(connectivity.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
                    if (wifi) preference.wifi else preference.mobile
                }
                graph.player.refreshPendingQuality()
            }
        }
        settingsScope.launch {
            graph.settings.playbackCache.collect { PlayerDependencies.configureCache(it.enabled, it.bytes, it.tracks) }
        }
        settingsScope.launch {
            graph.session.state.collect { state ->
                if (state is SessionState.Unresolved) transcoding.reset()
                if (state is SessionState.Ready) {
                    PlayerDependencies.setAccount((state.profile.endpoint.toString() + "|" + state.profile.username).sha256())
                }
            }
        }
    }

    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = graph.network.httpClient))
            }
            .crossfade(true)
            .build()
}
