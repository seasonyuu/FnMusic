package com.seasonyuu.fnmusic.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
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
    private lateinit var outputs: PlaybackOutputs
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var reportedMediaId: String? = null
    private var progressReportJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification_music)
            },
        )
        lateinit var playbackPlayer: ExoPlayer
        outputs = PlaybackOutputs(this, player = { playbackPlayer })
        val renderers = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: android.content.Context, enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean): androidx.media3.exoplayer.audio.AudioSink =
                AirPlayAudioSink(androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false).build()) { outputs.connection }
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(QualityMediaSourceFactory(this))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        playbackPlayer = player
        scope.launch {
            while (true) {
                delay(10_000)
                try { if (player.currentMediaItem?.localConfiguration?.uri?.scheme in listOf("http", "https"))
                    PlayerDependencies.maintainTranscode(player.currentMediaItem?.mediaId, player.currentPosition / 1000) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { /* Retry heartbeats on the next interval. */ }
            }
        }
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && outputs.connection != null) {
                    // Re-decode from the estimated audible position after clearing remote PCM.
                    val position = player.currentPosition
                    player.stop(); player.seekTo(position); player.prepare()
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val id = player.currentMediaItem?.mediaId.orEmpty()
                if (isPlaying && id.isNotBlank() && reportedMediaId != id) {
                    progressReportJob?.cancel()
                    progressReportJob = scope.launch {
                        delay(1_000)
                        if (player.currentMediaItem?.mediaId == id && player.currentPosition > 0 && reportedMediaId != id) {
                            reportedMediaId = id
                            if (player.currentMediaItem?.localConfiguration?.uri?.scheme in listOf("http", "https"))
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
        val artworkLoader = CacheBitmapLoader(DataSourceBitmapLoader(
            DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get(),
            DefaultDataSource.Factory(this, PlayerDependencies.upstreamFactory), null, 512))
        scope.publishAirPlayNowPlaying(player, artworkLoader) { outputs.connection }
        session = MediaSession.Builder(this, player)
            .setCallback(QueueSessionCallback(player, packageName, outputs))
            // Cover endpoints need the same cookies, signing and connection policy as
            // playback. The default Media3 HTTP loader has none of those credentials.
            .setBitmapLoader(artworkLoader)
            .apply {
                packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                    setSessionActivity(
                        PendingIntent.getActivity(
                            this@PlaybackService,
                            0,
                            launchIntent.setAction(ACTION_OPEN_PLAYER)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
            }
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        if (::outputs.isInitialized) outputs.close()
        progressReportJob?.cancel()
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.seasonyuu.fnmusic.OPEN_PLAYER"
    }
}
