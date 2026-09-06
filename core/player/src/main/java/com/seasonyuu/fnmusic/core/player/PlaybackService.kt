package com.seasonyuu.fnmusic.core.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.seasonyuu.fnmusic.core.model.TrackId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var reportedMediaId: String? = null
    private var progressReportJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(PlayerDependencies.dataSourceFactory()))
            .build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val id = player.currentMediaItem?.mediaId.orEmpty()
                if (isPlaying && id.isNotBlank() && reportedMediaId != id) {
                    progressReportJob?.cancel()
                    progressReportJob = scope.launch {
                        delay(1_000)
                        if (player.currentMediaItem?.mediaId == id && player.currentPosition > 0 && reportedMediaId != id) {
                            reportedMediaId = id
                            runCatching { PlayerDependencies.onTrackPlayed(TrackId(id)) }
                        }
                    }
                } else if (!isPlaying) {
                    progressReportJob?.cancel()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                progressReportJob?.cancel()
                if (mediaItem?.mediaId != reportedMediaId) reportedMediaId = null
            }
        })
        session = MediaSession.Builder(this, player)
            .setCallback(QueueSessionCallback(player, packageName))
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        progressReportJob?.cancel()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }
}
