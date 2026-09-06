package com.seasonyuu.fnmusic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.RepeatMode
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.core.player.Media3PlayerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class PlaybackQueueInteractionTest {
    /** Exercises the real MediaController -> MediaSession -> ExoPlayer shuffle order. */
    @Test fun shuffledQueueEditsMatchActualNextTrackAndPreserveDuplicateEntries() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val samples = ByteArray(8000 * 2 * 10)
        val wav = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            .put("RIFF".toByteArray()).putInt(36 + samples.size).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples.size).array() + samples
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val offset = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull() ?: 0
                return MockResponse().setHeader("Content-Type", "audio/wav")
                    .setBody(Buffer().write(wav, offset, wav.size - offset))
                    .apply {
                        if (offset > 0) {
                            setResponseCode(206)
                            setHeader("Content-Range", "bytes $offset-${wav.lastIndex}/${wav.size}")
                        }
                    }
            }
        }
        server.start()
        (context as FnMusicApplication).graph.network.activateBaseUrl(server.url("/"), allowPrivateLanHttp = true)
        val controller = withContext(Dispatchers.Main) { Media3PlayerController(context) }
        var step = 0
        suspend fun awaitState(predicate: (PlayerState) -> Boolean): PlayerState {
            step++
            return try {
                withTimeout(10_000) { controller.state.first(predicate) }.also { state ->
                    println("Queue step $step: current=${state.currentIndex}, upcoming=${state.upcomingQueueIndices}, " +
                        "history=${state.playbackHistory.map { entry -> state.queue.indexOfFirst { it.queueEntryId == entry.queueEntryId } }}")
                }
            } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
                val state = controller.state.value
                throw AssertionError("Step $step: current=${state.currentIndex}, order=${state.playbackOrder}, " +
                    "upcoming=${state.upcomingQueueIndices}, size=${state.queue.size}, shuffle=${state.shuffleEnabled}, " +
                    "duration=${state.durationMs}, error=${state.error}", failure)
            }
        }
        fun song(id: String) = PlayableTrack(Track(TrackId(id), id), server.url("/$id.wav").toString())
        try {
            val duplicate = song("duplicate")
            val entries = listOf(duplicate, duplicate) + (2..7).map { song("song-$it") }
            withContext(Dispatchers.Main) { controller.restore(entries, 7, 1234, true, RepeatMode.All) }
            var state = awaitState { it.durationMs > 0 && it.shuffleEnabled && it.playbackOrder.size == 8 }
            assertEquals(8, state.queue.map { it.queueEntryId }.distinct().size)
            assertEquals(7, state.upcomingQueueIndices.size)
            assertFalse(state.isPlaying)

            // The last natural index still has seven successors in this shuffled cycle.
            val expectedFirst = state.upcomingIds().first()
            withContext(Dispatchers.Main) { controller.skipNext() }
            state = awaitState { it.current?.queueEntryId == expectedFirst }
            assertFalse(state.isPlaying)

            val beforeMove = state.upcomingIds()
            val movedId = beforeMove.last()
            val from = state.upcomingQueueIndices.last()
            val to = state.upcomingQueueIndices.first()
            val currentId = state.current!!.queueEntryId
            val position = state.positionMs
            withContext(Dispatchers.Main) { controller.moveQueueItem(from, to) }
            state = awaitState { it.upcomingIds() == listOf(movedId) + beforeMove.dropLast(1) }
            assertEquals(currentId, state.current?.queueEntryId)
            assertEquals(position, state.positionMs)
            withContext(Dispatchers.Main) { controller.skipNext() }
            state = awaitState { it.current?.queueEntryId == movedId }

            val beforeNext = state.upcomingIds()
            val oldIds = state.queue.map { it.queueEntryId }.toSet()
            withContext(Dispatchers.Main) { controller.playNext(duplicate) }
            state = awaitState { it.queue.size == 9 && it.upcomingIds().firstOrNull()?.let { id -> id !in oldIds } == true }
            val insertedId = state.upcomingIds().first()
            // Wait for the custom command, rather than accepting Media3's temporary random insertion.
            state = awaitState { it.upcomingIds() == listOf(insertedId) + beforeNext }
            assertEquals(3, state.queue.count { it.track.id == duplicate.track.id })
            withContext(Dispatchers.Main) { controller.skipNext() }
            state = awaitState { it.current?.queueEntryId == insertedId }

            val beforeAppend = state.upcomingIds()
            withContext(Dispatchers.Main) { controller.append(listOf(song("tail-one"), song("tail-two"))) }
            state = awaitState {
                it.queue.size == 11 && it.upcomingIds().take(beforeAppend.size) == beforeAppend &&
                    it.upcomingQueueIndices.takeLast(2).map { index -> it.queue[index].track.title } == listOf("tail-one", "tail-two")
            }
            val duplicates = state.queue.filter { it.track.id == duplicate.track.id }
            val removeIndex = state.queue.indexOfFirst { it.track.id == duplicate.track.id && it.queueEntryId != insertedId }
            val removedId = state.queue[removeIndex].queueEntryId
            withContext(Dispatchers.Main) { controller.removeFromQueue(removeIndex) }
            state = awaitState { it.queue.size == 10 }
            assertEquals(insertedId, state.current?.queueEntryId)
            assertEquals(duplicates.map { it.queueEntryId }.filterNot { it == removedId }.toSet(),
                state.queue.filter { it.track.id == duplicate.track.id }.map { it.queueEntryId }.toSet())

            // Replaying history keeps the remaining shuffled suffix intact.
            val replay = state.playbackHistory.first()
            val nextAfterReplay = state.upcomingIds().filterNot { it == replay.queueEntryId }.first()
            println("Replay target=${state.queue.indexOfFirst { it.queueEntryId == replay.queueEntryId }}, next=${state.queue.indexOfFirst { it.queueEntryId == nextAfterReplay }}")
            withContext(Dispatchers.Main) { controller.skipToHistoryItem(0) }
            state = awaitState { it.current?.queueEntryId == replay.queueEntryId && it.upcomingIds().firstOrNull() == nextAfterReplay }
            assertEquals(nextAfterReplay, state.upcomingIds().first())
            withContext(Dispatchers.Main) { controller.pause(); controller.skipNext() }
            state = awaitState { it.current?.queueEntryId == nextAfterReplay }

            withContext(Dispatchers.Main) { controller.setShuffle(false) }
            state = awaitState { !it.shuffleEnabled }
            assertEquals(state.queue.indices.toList(), state.playbackOrder)
            val sequentialNext = state.upcomingIds().first()
            withContext(Dispatchers.Main) { controller.skipNext() }
            state = awaitState { it.current?.queueEntryId == sequentialNext }
            assertNull(state.error)
        } finally {
            withContext(Dispatchers.Main) { controller.clear() }
            server.shutdown()
        }
    }

    private fun PlayerState.upcomingIds() = upcomingQueueIndices.map { queue[it].queueEntryId }
}
