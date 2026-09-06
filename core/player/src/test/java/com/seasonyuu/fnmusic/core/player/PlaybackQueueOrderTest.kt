package com.seasonyuu.fnmusic.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId

class PlaybackQueueOrderTest {
    private fun song(id: String) = PlayableTrack(Track(TrackId(id), id), "https://music.invalid/$id")

    @Test fun shuffledUpcomingUsesPlaybackPositionsIncludingIndicesBeforeCurrent() {
        val state = com.seasonyuu.fnmusic.core.model.PlayerState(
            queue = (0..4).map { song("$it") }, currentIndex = 4,
            shuffleEnabled = true, playbackOrder = listOf(2, 4, 0, 3, 1),
        )
        assertEquals(listOf(0, 3, 1), state.upcomingQueueIndices)
        assertEquals(true, state.canSkipNext)
        assertEquals(listOf(2, 4, 3, 1, 0), moveInPlaybackOrder(state.playbackOrder, 0, 1))
        assertEquals(listOf(2, 4, 1, 0, 3), moveInPlaybackOrder(state.playbackOrder, 1, 0))
    }

    @Test fun nextAndAppendKeepTheExistingShuffledSequence() {
        val order = listOf(5, 2, 4, 0, 6, 3, 1)
        assertEquals(listOf(2, 4, 5, 6, 0, 3, 1), placeInPlaybackOrder(order, listOf(5, 6), after = 4))
        assertEquals(listOf(2, 4, 0, 3, 1, 5, 6), placeInPlaybackOrder(order, listOf(5, 6), after = null))
    }

    @Test fun repeatAllWrapsUpcomingButNeverDuplicatesCurrent() {
        val state = com.seasonyuu.fnmusic.core.model.PlayerState(
            queue = (0..3).map { song("$it") }, currentIndex = 3,
            playbackOrder = listOf(2, 3, 0, 1), repeatMode = com.seasonyuu.fnmusic.core.model.RepeatMode.All,
        )
        assertEquals(listOf(0, 1, 2), state.upcomingQueueIndices)
        assertEquals(emptyList<Int>(), state.copy(queue = listOf(song("0")), currentIndex = 0, playbackOrder = listOf(0)).upcomingQueueIndices)
    }

    @Test fun duplicateSongsHaveIndependentIdentityAcrossMovesAndMetadataChanges() {
        val first = song("A")
        val second = song("A")
        assertNotEquals(first.queueEntryId, second.queueEntryId)
        assertEquals(first.queueEntryId, first.copy(track = first.track.copy(title = "Updated")).queueEntryId)
        assertEquals(listOf(first), updatedPlaybackHistory(emptyList(), first, second))
        assertEquals(listOf(first, second), updatedPlaybackHistory(listOf(first), second, song("B")))
    }

    @Test fun movesAcrossRepeatBoundaryPreserveTheRequestedCyclicOrder() {
        for (size in 3..8) for (current in 0 until size) {
            val queue = (0 until size).toList()
            val upcoming = queue.drop(current + 1) + queue.take(current)
            for (from in upcoming) for (to in upcoming) {
                val expected = moveInPlaybackOrder(upcoming, from, to)
                val actual = queue.toMutableList()
                actual.add(naturalQueueMoveDestination(upcoming, from, to), actual.removeAt(from))
                val currentPosition = actual.indexOf(current)
                assertEquals("size=$size current=$current from=$from to=$to", expected,
                    actual.drop(currentPosition + 1) + actual.take(currentPosition))
            }
        }
    }

    @Test fun replayRemovesSelectedHistoryAndAppendsPreviousCurrent() {
        val a = song("A")
        val b = song("B")
        val c = song("C")
        assertEquals(listOf(b, c), updatedPlaybackHistory(listOf(a, b), c, a))
    }

    @Test fun progressAndReorderEventsDoNotDuplicateHistory() {
        val a = song("A")
        val b = song("B")
        assertEquals(listOf(a), updatedPlaybackHistory(listOf(a), b, b))
    }

    @Test fun historyGrowsChronologicallyAndEmptySessionDoesNotInheritHistory() {
        val a = song("A")
        val b = song("B")
        val c = song("C")
        val history = updatedPlaybackHistory(updatedPlaybackHistory(emptyList(), a, b), b, c)
        assertEquals(listOf(a, b), history)
        assertEquals(emptyList<PlayableTrack>(), updatedPlaybackHistory(emptyList(), null, c))
    }

    @Test fun replayOldestPreservesPendingSuffix() {
        val queue = mutableListOf("A", "B", "C", "D", "E")
        val destination = historyReplayDestination(0, 2, queue.size)
        queue.add(destination, queue.removeAt(0))
        assertEquals(listOf("B", "C", "A", "D", "E"), queue)
        assertEquals(listOf("D", "E"), queue.drop(destination + 1))
    }

    @Test fun replayMostRecentDoesNotRequeueCurrent() {
        val queue = mutableListOf("A", "B", "C", "D")
        val destination = historyReplayDestination(1, 2, queue.size)
        queue.add(destination, queue.removeAt(1))
        assertEquals(listOf("A", "C", "B", "D"), queue)
        assertEquals(listOf("D"), queue.drop(destination + 1))
    }

    @Test fun replayHistoryBeyondCurrentAfterPreviousNavigation() {
        val queue = mutableListOf("A", "B", "C", "D", "E")
        val destination = historyReplayDestination(3, 1, queue.size)
        queue.add(destination, queue.removeAt(3))
        assertEquals(listOf("A", "B", "D", "C", "E"), queue)
        assertEquals("D", queue[destination])
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidHistoryIndexIsRejected() { historyReplayDestination(-1, 0, 3) }
}
