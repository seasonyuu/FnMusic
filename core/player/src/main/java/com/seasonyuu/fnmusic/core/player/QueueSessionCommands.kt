package com.seasonyuu.fnmusic.core.player

import android.os.Bundle
import android.os.Handler
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionError
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture

internal const val QUEUE_ENTRY_ID = "com.seasonyuu.fnmusic.queue.entry"

/** Read the timeline's order, never infer shuffled successors from media-item indices. */
internal fun Player.playbackOrderIndices(): List<Int> {
    val timeline = currentTimeline
    val result = ArrayList<Int>(timeline.windowCount)
    var index = timeline.getFirstWindowIndex(shuffleModeEnabled)
    while (index != C.INDEX_UNSET && result.size < timeline.windowCount) {
        result.add(index)
        index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffleModeEnabled)
    }
    return result
}

internal object QueueSessionCommands {
    val editShuffle = SessionCommand("com.seasonyuu.fnmusic.EDIT_SHUFFLE", Bundle.EMPTY)
    const val OPERATION = "operation"
    const val MOVE = "move"
    const val NEXT = "next"
    const val APPEND = "append"
    const val REPLAY = "replay"
    const val ENTRIES = "entries"
    const val TARGET = "target"
    const val CURRENT = "current"
}

/** MediaController cannot set ExoPlayer's shuffle order; edit it on the session thread. */
@androidx.annotation.OptIn(UnstableApi::class)
internal class QueueSessionCallback(private val player: ExoPlayer, private val packageName: String,
    private val outputs: PlaybackOutputs? = null) : MediaSession.Callback {
    override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
        val result = super.onConnect(session, controller)
        if (controller.packageName != packageName || controller.uid != android.os.Process.myUid()) return result
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(result.availableSessionCommands.buildUpon().add(QueueSessionCommands.editShuffle)
                .apply { if (outputs != null) add(OutputCommands.command) }.build())
            .setAvailablePlayerCommands(result.availablePlayerCommands)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (customCommand == OutputCommands.command && controller.packageName == packageName &&
            controller.uid == android.os.Process.myUid() && outputs != null) {
            return Futures.immediateFuture(outputs.command(args))
        }
        if (customCommand != QueueSessionCommands.editShuffle || controller.packageName != packageName) {
            return super.onCustomCommand(session, controller, customCommand, args)
        }
        val operation = args.getString(QueueSessionCommands.OPERATION)
        if (operation != QueueSessionCommands.NEXT && operation != QueueSessionCommands.APPEND) {
            return Futures.immediateFuture(SessionResult(editOrder(args)))
        }
        // Session playlist resolution is asynchronous. A custom command can arrive
        // before the preceding addMediaItems has reached ExoPlayer.
        val entries = args.getStringArrayList(QueueSessionCommands.ENTRIES)
            ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_BAD_VALUE))
        fun entriesReady(): Boolean = entries.all { id ->
            (0 until player.mediaItemCount).any {
                player.getMediaItemAt(it).mediaMetadata.extras?.getString(QUEUE_ENTRY_ID) == id
            }
        }
        if (entriesReady()) return Futures.immediateFuture(SessionResult(editOrder(args)))
        val result = SettableFuture.create<SessionResult>()
        val handler = Handler(player.applicationLooper)
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (entriesReady()) result.set(SessionResult(editOrder(args)))
            }
        }
        val timeout = Runnable { result.set(SessionResult(SessionError.ERROR_INVALID_STATE)) }
        player.addListener(listener)
        handler.postDelayed(timeout, 3_000L)
        result.addListener({
            player.removeListener(listener)
            handler.removeCallbacks(timeout)
        }, { action -> handler.post(action) })
        return result
    }

    private fun editOrder(args: Bundle): Int {
        if (!player.shuffleModeEnabled) return SessionError.ERROR_INVALID_STATE
        val ids = (0 until player.mediaItemCount).map {
            player.getMediaItemAt(it).mediaMetadata.extras?.getString(QUEUE_ENTRY_ID)
        }
        val requested = args.getStringArrayList(QueueSessionCommands.ENTRIES) ?: return SessionError.ERROR_BAD_VALUE
        val entries = requested.map { ids.indexOf(it) }
        if (entries.isEmpty() || entries.any { it < 0 } || entries.distinct().size != entries.size) {
            return SessionError.ERROR_BAD_VALUE
        }
        val current = player.currentMediaItemIndex
        val expectedCurrent = args.getString(QueueSessionCommands.CURRENT)
        if (expectedCurrent != null && ids.getOrNull(current) != expectedCurrent) return SessionError.ERROR_INVALID_STATE
        val nativeOrder = player.playbackOrderIndices()
        // Rotating a repeat-all cycle preserves playback, while making append mean
        // the end of the entire visible upcoming queue rather than the natural array.
        val position = nativeOrder.indexOf(current)
        val order = if (player.repeatMode == Player.REPEAT_MODE_ALL && position >= 0) {
            nativeOrder.drop(position) + nativeOrder.take(position)
        } else nativeOrder
        val operation = args.getString(QueueSessionCommands.OPERATION)
        val updated = when (operation) {
            QueueSessionCommands.MOVE -> {
                val target = ids.indexOf(args.getString(QueueSessionCommands.TARGET))
                if (entries.size != 1 || target < 0 || current in entries || target == current) return SessionError.ERROR_BAD_VALUE
                if (order.indexOf(entries.single()) <= order.indexOf(current) ||
                    order.indexOf(target) <= order.indexOf(current)
                ) return SessionError.ERROR_INVALID_STATE
                moveInPlaybackOrder(order, entries.single(), target)
            }
            QueueSessionCommands.NEXT, QueueSessionCommands.REPLAY -> placeInPlaybackOrder(order, entries, current)
            QueueSessionCommands.APPEND -> placeInPlaybackOrder(order, entries, after = null)
            else -> return SessionError.ERROR_BAD_VALUE
        }
        player.setShuffleOrder(DefaultShuffleOrder(updated.toIntArray(), System.nanoTime()))
        if (operation == QueueSessionCommands.REPLAY) {
            player.seekTo(entries.single(), 0L)
            player.play()
        }
        return SessionResult.RESULT_SUCCESS
    }
}
