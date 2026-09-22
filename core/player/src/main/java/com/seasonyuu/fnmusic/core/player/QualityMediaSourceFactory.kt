package com.seasonyuu.fnmusic.core.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import com.seasonyuu.fnmusic.core.model.StreamingQuality

@UnstableApi
internal class QualityMediaSourceFactory(context: android.content.Context) : MediaSource.Factory {
    private val delegate = DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, PlayerDependencies.dataSourceFactory()))
    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply { delegate.setDrmSessionManagerProvider(provider) }
    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply { delegate.setLoadErrorHandlingPolicy(policy) }
    override fun getSupportedTypes(): IntArray = delegate.supportedTypes
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri
        if (uri != null && uri.path?.endsWith("/track/stream") == true && PlayerDependencies.quality() == StreamingQuality.Standard) {
            val guid = uri.getQueryParameter("guid")
            if (!guid.isNullOrBlank()) {
                val target = uri.buildUpon().path(uri.path!!.removeSuffix("/stream") + "/hls/" + Uri.encode(guid) + "/preset.m3u8").clearQuery().build()
                return delegate.createMediaSource(mediaItem.buildUpon().setUri(target).setMimeType("application/x-mpegURL").build())
            }
        }
        return delegate.createMediaSource(mediaItem)
    }
}
