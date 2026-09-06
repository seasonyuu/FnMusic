package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.data.PlaybackQueueEntity
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueRecoveryTest {
    private class Fixture {
        val track = Track(TrackId("one"), "Cached title")
        var rows = listOf(PlaybackQueueEntity("one", 0, positionMs = 1234, isCurrent = true,
            trackJson = Json.encodeToString(track)))
        var state = PlayerState()
        var restores = 0
        var updates = 0
        var fetch: suspend (TrackId) -> Track = { track }
        var read: suspend () -> List<PlaybackQueueEntity> = { rows }
        val recovery = PlaybackQueueRecovery(
            json = Json,
            read = { read() },
            write = { rows = it },
            state = { state },
            playable = { PlayableTrack(it, "https://example.test/${it.id.value}") },
            fetch = { fetch(it) },
            restore = { state = it.copy(playbackSessionId = state.playbackSessionId + 1); restores++ },
            update = { tracks ->
                updates++
                state = state.copy(queue = state.queue.map { item ->
                    tracks.firstOrNull { it.id == item.track.id }?.let { item.copy(track = it) } ?: item
                })
            },
        )
    }

    @Test fun `saving before local restoration never deletes saved queue`() = runBlocking {
        val f = Fixture()
        f.recovery.persist()
        assertEquals(1, f.rows.size)
    }

    @Test fun `cache appears before network completes and changed metadata preserves playback`() = runBlocking {
        val f = Fixture()
        val response = CompletableDeferred<Track>()
        val started = CompletableDeferred<Unit>()
        f.fetch = { started.complete(Unit); response.await() }
        val job = async { f.recovery.recover() }
        started.await()
        assertEquals("Cached title", f.state.current?.track?.title)
        assertEquals(1234L, f.state.positionMs)
        f.state = f.state.copy(positionMs = 9000, isPlaying = true)
        f.recovery.persist()
        assertEquals(1, f.rows.size)
        response.complete(f.track.copy(title = "Cloud title"))
        assertTrue(job.await())
        assertEquals("Cloud title", f.state.current?.track?.title)
        assertEquals(9000L, f.state.positionMs)
        assertTrue(f.state.isPlaying)
        assertEquals(1, f.restores)
        assertEquals("Cloud title", Json.decodeFromString<Track>(f.rows.single().trackJson!!).title)
    }

    @Test fun `unchanged cloud metadata does not replace player items`() = runBlocking {
        val f = Fixture()
        assertTrue(f.recovery.recover())
        assertEquals(0, f.updates)
    }

    @Test fun `failed cloud update keeps cache and can retry without restoring again`() = runBlocking {
        val f = Fixture()
        f.fetch = { error("offline") }
        assertFalse(f.recovery.recover())
        assertEquals(f.track, f.state.current?.track)
        f.fetch = { f.track.copy(title = "Updated") }
        assertTrue(f.recovery.recover())
        assertEquals("Updated", f.state.current?.track?.title)
        assertEquals(1, f.restores)
    }

    @Test fun `user queue changes during cloud fetch are not overwritten`() = runBlocking {
        val f = Fixture()
        f.fetch = {
            f.state = PlayerState(queue = listOf(PlayableTrack(Track(TrackId("two"), "New song"), "url")),
                currentIndex = 0, positionMs = 777, isPlaying = true, playbackSessionId = 2)
            f.track.copy(title = "Updated")
        }
        f.recovery.recover()
        assertEquals("two", f.state.current?.track?.id?.value)
        assertEquals(777L, f.state.positionMs)
        assertEquals(0, f.updates)
    }

    @Test fun `legacy or corrupt metadata keeps queue and position then upgrades cache`() = runBlocking {
        for (payload in listOf(null, "invalid")) {
            val f = Fixture()
            f.rows = f.rows.map { it.copy(trackJson = payload) }
            f.recovery.recover()
            assertEquals(f.track, f.state.current?.track)
            assertEquals(1234L, f.state.positionMs)
            assertNotNull(f.rows.single().trackJson)
        }
    }

    @Test fun `user playback during disk read wins over saved queue`() = runBlocking {
        val f = Fixture()
        f.read = {
            f.state = PlayerState(queue = listOf(PlayableTrack(Track(TrackId("two"), "New"), "url")),
                currentIndex = 0, playbackSessionId = 1)
            f.rows
        }
        f.recovery.recover()
        assertEquals("two", f.state.current?.track?.id?.value)
        assertEquals(0, f.restores)
    }

    @Test fun `logout during a network request cannot resurrect the queue`() = runBlocking {
        val f = Fixture()
        f.fetch = {
            f.recovery.clear()
            f.state = PlayerState()
            f.track.copy(title = "Late response")
        }
        assertFalse(f.recovery.recover())
        f.recovery.persist()
        assertTrue(f.rows.isEmpty())
        assertNull(f.state.current)
        assertEquals(0, f.updates)
    }

    @Test fun `saving while disk restoration is suspended waits for restored state`() = runBlocking {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        f.read = { entered.complete(Unit); release.await(); f.rows }
        val restore = async { f.recovery.recover() }
        entered.await()
        val save = async(start = CoroutineStart.UNDISPATCHED) { f.recovery.persist() }
        assertFalse(save.isCompleted)
        release.complete(Unit)
        restore.await()
        save.await()
        assertEquals("one", f.rows.single().trackGuid)
        assertEquals(1234L, f.rows.single().positionMs)
    }

}
