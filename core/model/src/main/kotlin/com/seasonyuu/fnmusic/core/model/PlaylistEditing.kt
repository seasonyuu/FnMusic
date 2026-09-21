package com.seasonyuu.fnmusic.core.model

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data class PlaylistTrackKey(val id: TrackId, val occurrence: Int)

fun List<Track>.playlistKeys(): List<PlaylistTrackKey> {
    val counts = mutableMapOf<TrackId, Int>()
    return map { track ->
        val occurrence = counts.getOrDefault(track.id, 0)
        counts[track.id] = occurrence + 1
        PlaylistTrackKey(track.id, occurrence)
    }
}

@Serializable
data class PlaylistOrder(val enabled: Boolean = false, val keys: List<PlaylistTrackKey> = emptyList()) {
    fun reconcile(tracks: List<Track>): PlaylistOrder {
        val current = tracks.playlistKeys()
        val available = current.toSet()
        val retained = keys.filter { it in available }.distinct()
        val retainedSet = retained.toSet()
        return copy(keys = retained + current.filter { it !in retainedSet })
    }

    fun apply(tracks: List<Track>): List<Track> {
        if (!enabled) return tracks
        val byKey = tracks.playlistKeys().zip(tracks).toMap()
        return reconcile(tracks).keys.mapNotNull(byKey::get)
    }
}

@Serializable
data class PlaylistEditDraft(
    val account: String,
    val original: Playlist,
    val serverTracks: List<Track>,
    val originalOrder: PlaylistOrder,
    val name: String = original.name,
    val coverId: String? = original.coverId,
    val photoPath: String? = null,
    val usePhoto: Boolean = false,
    val uploadedCoverId: String? = null,
    val order: PlaylistOrder = originalOrder,
    val removed: Set<TrackId> = emptySet(),
    val selected: Set<TrackId> = emptySet(),
    // Checkpoints let a retry resume after successful server operations.
    val metadataSaved: Boolean = false,
    val removalSaved: Boolean = false,
    val orderSaved: Boolean = false,
    val completedSteps: List<String> = emptyList(),
) {
    val remaining: List<Track> get() = serverTracks.filterNot { it.id in removed }
    val dirty: Boolean get() = name.trim() != original.name || coverId != original.coverId ||
        order != originalOrder || removed.isNotEmpty() || usePhoto
    val valid: Boolean get() = name.trim().length in 1..32
    val editingLocked: Boolean get() = completedSteps.isNotEmpty()
    fun select(id: TrackId) = copy(selected = if (id in selected) selected - id else selected + id)
    fun removeSelected() = copy(removed = removed + selected, selected = emptySet())
    fun move(from: Int, to: Int): PlaylistEditDraft {
        if (!order.enabled) return this
        val keys = order.reconcile(remaining).keys.toMutableList()
        if (from !in keys.indices || to !in keys.indices) return this
        keys.add(to, keys.removeAt(from))
        return copy(order = order.copy(keys = keys))
    }
}

data class PlaylistEditorState(
    val id: PlaylistId? = null,
    val draft: PlaylistEditDraft? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val complete: Boolean = false,
    val preparingCover: Boolean = false,
) {
    val busy: Boolean get() = saving || preparingCover
}

interface PlaylistEditActions {
    val state: StateFlow<PlaylistEditorState>
    fun open(id: PlaylistId)
    fun retryLoad()
    fun update(draft: PlaylistEditDraft)
    fun selectPhoto(uri: String) {}
    fun save()
    fun close()
}
