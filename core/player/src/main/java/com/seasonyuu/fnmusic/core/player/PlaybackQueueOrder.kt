package com.seasonyuu.fnmusic.core.player

import com.seasonyuu.fnmusic.core.model.PlayableTrack

/** Chronological, session-local history; the active song is never a history row. */
internal fun updatedPlaybackHistory(
    history: List<PlayableTrack>,
    previous: PlayableTrack?,
    current: PlayableTrack?,
): List<PlayableTrack> {
    if (previous?.queueEntryId == current?.queueEntryId) return history
    val remaining = history.filterNot { it.queueEntryId == previous?.queueEntryId || it.queueEntryId == current?.queueEntryId }
    return if (previous == null) remaining else remaining + previous
}

/** Reorders timeline indices, without treating their numeric values as playback positions. */
internal fun moveInPlaybackOrder(order: List<Int>, from: Int, to: Int): List<Int> {
    val source = order.indexOf(from)
    val destination = order.indexOf(to)
    if (source < 0 || destination < 0 || source == destination) return order
    return order.toMutableList().also { it.add(destination, it.removeAt(source)) }
}

/** Places newly added entries together, preserving the relative order of every existing entry. */
internal fun placeInPlaybackOrder(order: List<Int>, added: List<Int>, after: Int?): List<Int> {
    val remaining = order.filterNot { it in added }.toMutableList()
    val destination = after?.let { remaining.indexOf(it).takeIf { index -> index >= 0 }?.plus(1) } ?: remaining.size
    remaining.addAll(destination, added)
    return remaining
}

/** Converts a move in a cyclic upcoming list to Media3's post-removal insertion index. */
internal fun naturalQueueMoveDestination(upcoming: List<Int>, from: Int, to: Int): Int {
    val movingDown = upcoming.indexOf(from) < upcoming.indexOf(to)
    return if (movingDown) to + if (from > to) 1 else 0
    else to - if (from < to) 1 else 0
}

/** Move a history entry next to the active item without rewinding the pending suffix. */
internal fun historyReplayDestination(historyIndex: Int, currentIndex: Int, size: Int): Int {
    require(historyIndex in 0 until size && currentIndex in 0 until size)
    return if (historyIndex <= currentIndex) currentIndex else currentIndex + 1
}
