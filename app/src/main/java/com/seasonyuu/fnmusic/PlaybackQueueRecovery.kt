package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.RepeatMode
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.data.PlaybackQueueEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Restores local state before any network work; cloud refresh only changes metadata. */
internal class PlaybackQueueRecovery(
    private val json: Json,
    private val read: suspend () -> List<PlaybackQueueEntity>,
    private val write: suspend (List<PlaybackQueueEntity>) -> Unit,
    private val state: () -> PlayerState,
    private val playable: (Track) -> PlayableTrack,
    private val fetch: suspend (TrackId) -> Track,
    private val restore: (PlayerState, List<String?>) -> Unit,
    private val update: (List<Track>) -> Unit,
    private val roamIdAt: (Int) -> String? = { null },
    private val isActive: () -> Boolean = { true },
) {
    private val storageMutex = Mutex()
    private val recoveryMutex = Mutex()
    private var localRestored = false
    private var generation = 0L

    /** False means a transient failure; callers can retry without restoring playback again. */
    suspend fun recover(): Boolean = recoveryMutex.withLock {
        val requestGeneration = generation
        try {
            storageMutex.withLock {
                if (!isActive()) return false
                if (!localRestored) {
                    val initial = state()
                    val saved = read()
                    if (generation != requestGeneration || !isActive()) return false
                    // A user may have started playback while the database read was suspended.
                    if (initial.queue.isEmpty() && state().queue.isEmpty() &&
                        initial.playbackSessionId == state().playbackSessionId && saved.isNotEmpty()
                    ) {
                        val current = saved.indexOfFirst { it.isCurrent }.takeIf { it >= 0 } ?: 0
                        val settings = saved[current]
                        val isRoaming = saved.all { it.isRoaming && !it.roamId.isNullOrBlank() }
                        restore(PlayerState(
                            queue = saved.map { row ->
                                val track = row.trackJson?.let { payload ->
                                    runCatching { json.decodeFromString<Track>(payload) }.getOrNull()
                                }?.takeIf { it.id.value == row.trackGuid }
                                    ?: Track(TrackId(row.trackGuid), "正在加载歌曲…")
                                playable(track)
                            },
                            currentIndex = current,
                            positionMs = settings.positionMs,
                            shuffleEnabled = settings.shuffleEnabled,
                            repeatMode = runCatching { RepeatMode.valueOf(settings.repeatMode) }.getOrDefault(RepeatMode.Off),
                            isRoaming = isRoaming,
                        ), if (isRoaming) saved.map { it.roamId } else emptyList())
                    }
                    localRestored = true
                }
            }
            // Update each result immediately, prioritizing the visible current song.
            val snapshot = state()
            val ids = (listOfNotNull(snapshot.current) + snapshot.queue).map { it.track.id }.distinct()
            var complete = true
            for (id in ids) {
                val track = try {
                    fetch(id).takeIf { it.id == id }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
                if (generation != requestGeneration || !isActive()) return false
                if (track == null) {
                    complete = false
                    continue
                }
                // Consult the live queue: never bring back removed items or reset user playback.
                if (state().queue.any { it.track.id == id && it.track != track }) update(listOf(track))
            }
            if (generation != requestGeneration) return false
            persist()
            complete
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            false
        }
    }

    suspend fun persist() = storageMutex.withLock {
        if (!localRestored || !isActive()) return@withLock
        val current = state()
        write(current.queue.mapIndexed { index, item ->
            PlaybackQueueEntity(
                trackGuid = item.track.id.value,
                queueIndex = index,
                positionMs = if (index == current.currentIndex) current.positionMs else 0,
                isCurrent = index == current.currentIndex,
                shuffleEnabled = current.shuffleEnabled,
                repeatMode = current.repeatMode.name,
                trackJson = json.encodeToString(item.track),
                isRoaming = current.isRoaming,
                roamId = if (current.isRoaming) roamIdAt(index) else null,
            )
        })
    }

    suspend fun clear() {
        generation++
        storageMutex.withLock {
            localRestored = false
            write(emptyList())
        }
    }
}
