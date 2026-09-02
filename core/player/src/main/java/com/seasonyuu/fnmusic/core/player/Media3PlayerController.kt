package com.seasonyuu.fnmusic.core.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.PlayerController
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Media3PlayerController(context: Context) : PlayerController {
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()
    private val controllerFuture: ListenableFuture<MediaController>
    private var queue: List<PlayableTrack> = emptyList()
    private var isRoaming = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture.addListener({
            runCatching { controllerFuture.get() }.getOrNull()?.addListener(listener)
            publishState()
        }, ContextCompat.getMainExecutor(appContext))
        scope.launch {
            while (true) {
                delay(500)
                if (controllerFuture.isDone) publishState()
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publishState()
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            mutableState.value = mutableState.value.copy(error = "播放失败，请检查网络后重试")
        }
    }

    override fun play(items: List<PlayableTrack>, startIndex: Int, isRoaming: Boolean) {
        if (items.isEmpty()) return
        queue = items
        this.isRoaming = isRoaming
        withController { controller ->
            controller.setMediaItems(items.map { it.toMediaItem() }, startIndex.coerceIn(items.indices), 0L)
            if (isRoaming) {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
            controller.prepare()
            controller.play()
        }
    }

    override fun restore(
        items: List<PlayableTrack>,
        startIndex: Int,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
    ) {
        if (items.isEmpty()) return
        queue = items
        isRoaming = false
        withController { controller ->
            controller.setMediaItems(items.map { it.toMediaItem() }, startIndex.coerceIn(items.indices), positionMs.coerceAtLeast(0))
            controller.shuffleModeEnabled = shuffleEnabled
            controller.repeatMode = when (repeatMode) {
                RepeatMode.Off -> Player.REPEAT_MODE_OFF
                RepeatMode.All -> Player.REPEAT_MODE_ALL
                RepeatMode.One -> Player.REPEAT_MODE_ONE
            }
            controller.prepare()
            controller.pause()
        }
    }

    override fun clear() {
        queue = emptyList()
        isRoaming = false
        mutableState.value = PlayerState()
        withController { controller ->
            controller.stop()
            controller.clearMediaItems()
        }
    }

    override fun pause() = withController(MediaController::pause)
    override fun resume() = withController(MediaController::play)
    override fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0)) }
    override fun skipNext() = withController(MediaController::seekToNextMediaItem)
    override fun skipPrevious() = withController(MediaController::seekToPreviousMediaItem)
    override fun skipTo(index: Int) = withController { controller ->
        if (index in queue.indices) {
            controller.seekTo(index, 0L)
            controller.play()
        }
    }
    override fun playNext(item: PlayableTrack) = withController { controller ->
        if (queue.isEmpty()) {
            queue = listOf(item)
            controller.setMediaItem(item.toMediaItem())
            controller.prepare()
            controller.play()
        } else {
            val insertAt = (controller.currentMediaItemIndex + 1).coerceIn(0, queue.size)
            queue = queue.toMutableList().also { it.add(insertAt, item) }
            controller.addMediaItem(insertAt, item.toMediaItem())
        }
    }
    override fun append(items: List<PlayableTrack>) = withController { controller ->
        if (items.isEmpty()) return@withController
        if (queue.isEmpty()) {
            queue = items
            controller.setMediaItems(items.map { it.toMediaItem() })
            controller.prepare()
            controller.play()
        } else {
            queue = queue + items
            controller.addMediaItems(items.map { it.toMediaItem() })
        }
    }
    override fun removeFromQueue(index: Int) = withController { controller ->
        if (index in queue.indices) {
            queue = queue.toMutableList().also { it.removeAt(index) }
            controller.removeMediaItem(index)
            if (queue.isEmpty()) mutableState.value = PlayerState()
        }
    }
    override fun keepCurrentOnly() = withController { controller ->
        val currentIndex = controller.currentMediaItemIndex
        val current = queue.getOrNull(currentIndex) ?: return@withController
        val wasPlaying = controller.isPlaying
        val position = controller.currentPosition.coerceAtLeast(0)
        queue = listOf(current)
        controller.setMediaItem(current.toMediaItem(), position)
        controller.prepare()
        if (wasPlaying) controller.play() else controller.pause()
    }
    override fun setRoaming(enabled: Boolean) {
        isRoaming = enabled
        publishState()
    }
    override fun setShuffle(enabled: Boolean) = withController { it.shuffleModeEnabled = enabled }
    override fun setRepeatMode(mode: RepeatMode) = withController {
        it.repeatMode = when (mode) {
            RepeatMode.Off -> Player.REPEAT_MODE_OFF
            RepeatMode.All -> Player.REPEAT_MODE_ALL
            RepeatMode.One -> Player.REPEAT_MODE_ONE
        }
    }

    private fun PlayableTrack.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(track.id.value)
        .setUri(streamUrl)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(track.title)
                .setArtist(track.artists.joinToString(" / ") { it.name })
                .setAlbumTitle(track.album?.name)
                .also { builder -> coverUrl?.let { builder.setArtworkUri(it.toUri()) } }
                .build(),
        )
        .build()

    private fun publishState() {
        val controller = runCatching { if (controllerFuture.isDone) controllerFuture.get() else null }.getOrNull() ?: return
        mutableState.value = PlayerState(
            queue = queue,
            currentIndex = controller.currentMediaItemIndex,
            isPlaying = controller.isPlaying,
            positionMs = controller.currentPosition.coerceAtLeast(0),
            durationMs = controller.duration.coerceAtLeast(0),
            shuffleEnabled = controller.shuffleModeEnabled,
            repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatMode.All
                Player.REPEAT_MODE_ONE -> RepeatMode.One
                else -> RepeatMode.Off
            },
            isRoaming = isRoaming,
            error = mutableState.value.error,
        )
    }

    private fun withController(block: (MediaController) -> Unit) {
        controllerFuture.addListener({ runCatching { block(controllerFuture.get()) } }, ContextCompat.getMainExecutor(appContext))
    }
}
