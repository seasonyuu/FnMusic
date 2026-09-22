package com.seasonyuu.fnmusic.core.player

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.ListenableFuture
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.PlayerController
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.PlaybackStatus
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
    private val mutableOutput = MutableStateFlow(com.seasonyuu.fnmusic.core.model.OutputState())
    override val outputState = mutableOutput.asStateFlow()
    private fun outputCommand(operation: String, fill: Bundle.() -> Unit = {}) = withController { controller ->
        val result = controller.sendCustomCommand(OutputCommands.command, Bundle().apply { putString("operation", operation); fill() })
        result.addListener({
            runCatching { result.get() }.getOrNull()?.let {
                if (it.resultCode == SessionResult.RESULT_SUCCESS) mutableOutput.value = OutputCommands.decode(it.extras)
            }
        }, ContextCompat.getMainExecutor(appContext))
    }
    override fun scanOutputs(start: Boolean) = outputCommand("scan") { putBoolean("enabled", start) }
    override fun selectOutput(deviceId: String) = outputCommand("select") { putString("id", deviceId) }
    override fun submitOutputPin(pin: String) = outputCommand("pin") { putString("pin", pin) }
    override fun setOutputVolume(volume: Float) = outputCommand("volume") { putFloat("volume", volume) }
    override fun cancelOutputConnection() = outputCommand("cancel")
    override fun useLocalOutput() = outputCommand("local")
    private val appContext = context.applicationContext
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()
    private val controllerFuture: ListenableFuture<MediaController>
    private var queue: List<PlayableTrack> = emptyList()
    private var playbackHistory: List<PlayableTrack> = emptyList()
    private var observedCurrent: PlayableTrack? = null
    private var playbackSessionId = 0L
    private var isRoaming = false
    private var pendingTimeline = false
    private var pendingRemovalOrder: List<String>? = null
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
                if (controllerFuture.isDone) { publishState(); outputCommand("get") }
            }
        }
    }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publishState()
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val qualityError = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }
                .firstOrNull { it.startsWith("服务器无法提供标准音质") }
            mutableState.value = mutableState.value.copy(error = qualityError ?: "播放失败，请检查网络后重试")
        }
    }

    override fun play(items: List<PlayableTrack>, startIndex: Int, isRoaming: Boolean) {
        if (items.isEmpty()) return
        pendingRemovalOrder = null
        queue = items.map { it.asNewQueueEntry() }
        playbackSessionId++
        playbackHistory = emptyList()
        observedCurrent = queue[startIndex.coerceIn(queue.indices)]
        this.isRoaming = isRoaming
        publishPendingState(
            startIndex,
            0L,
            if (isRoaming) false else state.value.shuffleEnabled,
            if (isRoaming) RepeatMode.Off else state.value.repeatMode,
            PlaybackStatus.Buffering,
        )
        val requestSession = playbackSessionId
        withController { controller ->
            if (requestSession != playbackSessionId) return@withController
            controller.setMediaItems(queue.map { it.toMediaItem() }, startIndex.coerceIn(queue.indices), 0L)
            pendingTimeline = false
            if (isRoaming) {
                controller.shuffleModeEnabled = false
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
            controller.prepare()
            controller.play()
            publishState()
        }
    }

    fun restore(
        items: List<PlayableTrack>,
        startIndex: Int,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
    ) = restore(items, startIndex, positionMs, shuffleEnabled, repeatMode, isRoaming = false)

    override fun restore(
        items: List<PlayableTrack>,
        startIndex: Int,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: RepeatMode,
        isRoaming: Boolean,
    ) {
        if (items.isEmpty()) return
        pendingRemovalOrder = null
        queue = items.map { it.asNewQueueEntry() }
        playbackSessionId++
        playbackHistory = emptyList()
        observedCurrent = queue[startIndex.coerceIn(queue.indices)]
        this.isRoaming = isRoaming
        publishPendingState(
            startIndex,
            positionMs,
            if (isRoaming) false else shuffleEnabled,
            if (isRoaming) RepeatMode.Off else repeatMode,
            PlaybackStatus.Paused,
        )
        val requestSession = playbackSessionId
        withController { controller ->
            if (requestSession != playbackSessionId) return@withController
            // Restoring a queue must never inherit an old Media3 play intent. The
            // service can outlive the activity, so setMediaItems/prepare would
            // otherwise resume a session that was playing before this restore.
            controller.playWhenReady = false
            controller.setMediaItems(queue.map { it.toMediaItem() }, startIndex.coerceIn(queue.indices), positionMs.coerceAtLeast(0))
            pendingTimeline = false
            controller.shuffleModeEnabled = if (isRoaming) false else shuffleEnabled
            controller.repeatMode = when (if (isRoaming) RepeatMode.Off else repeatMode) {
                RepeatMode.Off -> Player.REPEAT_MODE_OFF
                RepeatMode.All -> Player.REPEAT_MODE_ALL
                RepeatMode.One -> Player.REPEAT_MODE_ONE
            }
            controller.prepare()
            controller.playWhenReady = false
            publishState()
        }
    }

    private fun publishPendingState(
        index: Int,
        position: Long,
        shuffle: Boolean,
        repeat: RepeatMode,
        playbackStatus: PlaybackStatus,
    ) {
        pendingTimeline = true
        mutableState.value = PlayerState(
            queue = queue,
            playbackSessionId = playbackSessionId,
            currentIndex = index.coerceIn(queue.indices),
            playbackStatus = playbackStatus,
            positionMs = position.coerceAtLeast(0),
            durationMs = (queue[index.coerceIn(queue.indices)].track.durationSeconds * 1000).toLong(),
            shuffleEnabled = shuffle,
            repeatMode = repeat,
            isRoaming = isRoaming,
        )
    }

    override fun updateTracks(tracks: List<PlayableTrack>) {
        val byId = tracks.associateBy { it.track.id }
        fun PlayableTrack.updated(): PlayableTrack {
            val updated = byId[track.id] ?: return this
            return copy(track = updated.track, coverUrl = updated.coverUrl)
        }
        val updatedQueue = queue.map { it.updated() }
        if (updatedQueue == queue) return
        queue = updatedQueue
        playbackHistory = playbackHistory.map { it.updated() }
        observedCurrent = observedCurrent?.updated()
        mutableState.value = mutableState.value.copy(queue = queue, playbackHistory = playbackHistory)
        val requestSession = playbackSessionId
        withController { controller ->
            if (requestSession != playbackSessionId) return@withController
            queue.forEachIndexed { index, item ->
                if (index < controller.mediaItemCount && item.track.id in byId) {
                    val existing = controller.getMediaItemAt(index)
                    val metadata = item.toMediaItem().mediaMetadata
                    if (existing.mediaId == item.track.id.value && existing.mediaMetadata != metadata) {
                        // Keep the source URI and playback state; only replace metadata.
                        controller.replaceMediaItem(index, existing.buildUpon().setMediaMetadata(metadata).build())
                    }
                }
            }
            publishState()
        }
    }

    override fun clear() {
        pendingRemovalOrder = null
        playbackSessionId++
        pendingTimeline = false
        queue = emptyList()
        playbackHistory = emptyList()
        observedCurrent = null
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
            prepareHistoryTransition(queue[index])
            controller.seekTo(index, 0L)
            controller.play()
        }
    }
    override fun skipToHistoryItem(index: Int) = withController { controller ->
        val target = playbackHistory.getOrNull(index) ?: return@withController
        val queueIndex = queue.indexOfFirst { it.queueEntryId == target.queueEntryId }
        if (queueIndex < 0) return@withController
        if (controller.shuffleModeEnabled) {
            editShuffle(controller, QueueSessionCommands.REPLAY, listOf(target.queueEntryId), expectedCurrent = true)
            return@withController
        }
        // Keep the pending suffix intact when replaying an already played item.
        // [A, B, C*, D] -> [B, C, A*, D], rather than seeking back to A in place.
        val destination = historyReplayDestination(queueIndex, controller.currentMediaItemIndex, queue.size)
        prepareHistoryTransition(target)
        queue = queue.toMutableList().also { it.add(destination, it.removeAt(queueIndex)) }
        controller.moveMediaItem(queueIndex, destination)
        controller.seekTo(destination, 0L)
        controller.play()
    }
    override fun clearPlaybackHistory() {
        playbackHistory = emptyList()
        publishState()
    }
    override fun playNext(item: PlayableTrack) = withController { controller ->
        val entry = item.asNewQueueEntry()
        if (queue.isEmpty()) {
            queue = listOf(entry)
            playbackSessionId++
            playbackHistory = emptyList()
            observedCurrent = entry
            controller.setMediaItem(entry.toMediaItem())
            controller.prepare()
            controller.play()
        } else {
            val insertAt = (controller.currentMediaItemIndex + 1).coerceIn(0, queue.size)
            queue = queue.toMutableList().also { it.add(insertAt, entry) }
            controller.addMediaItem(insertAt, entry.toMediaItem())
            if (controller.shuffleModeEnabled) editShuffle(controller, QueueSessionCommands.NEXT, listOf(entry.queueEntryId))
        }
    }
    override fun append(items: List<PlayableTrack>) = withController { controller ->
        if (items.isEmpty()) return@withController
        val entries = items.map { it.asNewQueueEntry() }
        if (queue.isEmpty()) {
            queue = entries
            playbackSessionId++
            playbackHistory = emptyList()
            observedCurrent = entries.first()
            controller.setMediaItems(entries.map { it.toMediaItem() })
            controller.prepare()
            controller.play()
        } else {
            queue = queue + entries
            controller.addMediaItems(entries.map { it.toMediaItem() })
            if (controller.shuffleModeEnabled) editShuffle(controller, QueueSessionCommands.APPEND, entries.map { it.queueEntryId })
        }
    }
    override fun moveQueueItem(fromIndex: Int, toIndex: Int) = withController { controller ->
        if (fromIndex !in queue.indices || toIndex !in queue.indices || fromIndex == toIndex) {
            return@withController
        }
        if (controller.shuffleModeEnabled) {
            editShuffle(
                controller, QueueSessionCommands.MOVE, listOf(queue[fromIndex].queueEntryId),
                target = queue[toIndex].queueEntryId, expectedCurrent = true,
            )
            return@withController
        }
        val currentIndex = controller.currentMediaItemIndex
        val upcoming = (currentIndex + 1 until queue.size).toList() +
            if (controller.repeatMode == Player.REPEAT_MODE_ALL) (0 until currentIndex).toList() else emptyList()
        val destination = if (fromIndex in upcoming && toIndex in upcoming) {
            naturalQueueMoveDestination(upcoming, fromIndex, toIndex)
        } else toIndex
        queue = queue.toMutableList().also { items ->
            items.add(destination, items.removeAt(fromIndex))
        }
        // Media3 owns the active index, including the case where the currently
        // playing item crosses another queue entry, so publishState remains the
        // single source of truth for the UI after the move.
        controller.moveMediaItem(fromIndex, destination)
    }
    override fun removeFromQueue(index: Int) = withController { controller ->
        if (index in queue.indices) {
            val removed = queue[index]
            if (controller.shuffleModeEnabled) {
                pendingRemovalOrder = mutableState.value.orderedQueueIndices.mapNotNull { queue.getOrNull(it)?.queueEntryId }
                    .filterNot { it == removed.queueEntryId }
            }
            queue = queue.toMutableList().also { it.removeAt(index) }
            playbackHistory = playbackHistory.filterNot { it.queueEntryId == removed.queueEntryId }
            controller.removeMediaItem(index)
            if (queue.isEmpty()) mutableState.value = PlayerState()
        }
    }
    override fun setRoaming(enabled: Boolean) {
        isRoaming = enabled
        publishState()
    }
    private var qualityRevision = 0L
    fun refreshPendingQuality() = withController { controller ->
        qualityRevision++
        for (index in 0 until controller.mediaItemCount) {
            if (index == controller.currentMediaItemIndex) continue
            val item = controller.getMediaItemAt(index)
            val uri = item.localConfiguration?.uri ?: continue
            // A fragment changes MediaSource identity without adding any server query parameter.
            controller.replaceMediaItem(index, item.buildUpon().setUri(uri.buildUpon().fragment("quality-$qualityRevision").build()).build())
        }
    }

    override fun setShuffle(enabled: Boolean) = withController {
        pendingRemovalOrder = null
        it.shuffleModeEnabled = enabled
    }
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
                .setExtras(Bundle().apply { putString(QUEUE_ENTRY_ID, queueEntryId) })
                .also { builder -> coverUrl?.let { builder.setArtworkUri(it.toUri()) } }
                .build(),
        )
        .build()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun publishState() {
        if (pendingTimeline) return
        val controller = runCatching { if (controllerFuture.isDone) controllerFuture.get() else null }.getOrNull() ?: return
        val current = queue.getOrNull(controller.currentMediaItemIndex)
        val nativeOrder = controller.playbackOrderIndices()
        val pendingOrder = pendingRemovalOrder
        val nativeIds = nativeOrder.mapNotNull { queue.getOrNull(it)?.queueEntryId }
        if (pendingOrder == nativeIds || !controller.shuffleModeEnabled) pendingRemovalOrder = null
        // MediaController temporarily replaces shuffled order with natural order
        // while masking a removal. Preserve the known order until the session replies.
        val publishedOrder = pendingRemovalOrder?.mapNotNull { id ->
            queue.indexOfFirst { it.queueEntryId == id }.takeIf { it >= 0 }
        } ?: nativeOrder
        if (current?.queueEntryId != observedCurrent?.queueEntryId) {
            playbackHistory = updatedPlaybackHistory(playbackHistory, observedCurrent, current)
            observedCurrent = current
        }
        val selectedAudio = controller.currentTracks.groups.asSequence()
            .filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
            .flatMap { group -> (0 until group.length).asSequence().filter { group.isTrackSelected(it) }.map { group.getTrackFormat(it) } }
            .firstOrNull()
        val streamedAudio = selectedAudio?.takeIf { it.sampleMimeType == "audio/opus" }?.let { format ->
            com.seasonyuu.fnmusic.core.model.AudioSpec(format = "OPUS", codec = "opus",
                sampleRate = format.sampleRate.takeIf { it > 0 }, channel = format.channelCount.takeIf { it > 0 },
                bitrate = format.averageBitrate.takeIf { it > 0 }?.toLong())
        }
        mutableState.value = PlayerState(
            playbackAudioSpec = streamedAudio,
            outputDeviceName = (mutableOutput.value.output as? com.seasonyuu.fnmusic.core.model.PlaybackOutput.AirPlay)?.name,
            queue = queue,
            playbackHistory = playbackHistory,
            playbackSessionId = playbackSessionId,
            currentIndex = controller.currentMediaItemIndex,
            playbackStatus = controller.playbackStatus(),
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
            playbackOrder = publishedOrder,
        )
    }

    private fun Player.playbackStatus(): PlaybackStatus = playbackStatus(playbackState, isPlaying, playWhenReady)

    private fun prepareHistoryTransition(target: PlayableTrack) {
        val previous = observedCurrent ?: mutableState.value.current
        if (previous?.queueEntryId == target.queueEntryId) return
        playbackHistory = updatedPlaybackHistory(playbackHistory, previous, target)
        observedCurrent = target
    }

    private fun PlayableTrack.asNewQueueEntry() = copy(queueEntryId = java.util.UUID.randomUUID().toString())

    private fun editShuffle(
        controller: MediaController,
        operation: String,
        entries: List<String>,
        target: String? = null,
        expectedCurrent: Boolean = false,
    ) {
        val requestSession = playbackSessionId
        val args = Bundle().apply {
            putString(QueueSessionCommands.OPERATION, operation)
            putStringArrayList(QueueSessionCommands.ENTRIES, ArrayList(entries))
            putString(QueueSessionCommands.TARGET, target)
            if (expectedCurrent) putString(QueueSessionCommands.CURRENT, queue.getOrNull(controller.currentMediaItemIndex)?.queueEntryId)
        }
        val result = controller.sendCustomCommand(QueueSessionCommands.editShuffle, args)
        result.addListener({
            if (requestSession == playbackSessionId) {
                if (runCatching { result.get().resultCode }.getOrNull() != SessionResult.RESULT_SUCCESS) {
                    mutableState.value = mutableState.value.copy(error = "队列已变化，请重试")
                }
                publishState()
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun withController(block: (MediaController) -> Unit) {
        controllerFuture.addListener({ runCatching { block(controllerFuture.get()) } }, ContextCompat.getMainExecutor(appContext))
    }
}

internal fun playbackStatus(
    playbackState: Int,
    isPlaying: Boolean,
    playWhenReady: Boolean,
): PlaybackStatus = when {
    playbackState == Player.STATE_BUFFERING && playWhenReady -> PlaybackStatus.Buffering
    isPlaying -> PlaybackStatus.Playing
    playbackState == Player.STATE_ENDED -> PlaybackStatus.Ended
    playbackState == Player.STATE_IDLE -> PlaybackStatus.Idle
    else -> PlaybackStatus.Paused
}
