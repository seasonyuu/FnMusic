package com.seasonyuu.fnmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MusicDirectoryNode(
    val path: String,
    val name: String,
    /** Recursive song count, not the number of immediate children. */
    val itemCount: Int = 0,
    val isAdminDeleted: Boolean = false,
)

data class MusicDirectoryContent(
    val folder: MusicDirectoryNode,
    val folders: List<MusicDirectoryNode>,
    val tracks: List<Track>,
    val sort: FolderSort,
) {
    val rowCount: Int get() = folders.size + tracks.size
}

@Serializable
enum class FolderSort(val wireValue: String, val label: String) {
    TitleAscending("title,asc", "标题 A–Z"),
    TitleDescending("title,desc", "标题 Z–A"),
    AddedDescending("createdAt,desc", "最新添加"),
    AddedAscending("createdAt,asc", "最早添加"),
    ModifiedDescending("updatedAt,desc", "最近修改"),
    ModifiedAscending("updatedAt,asc", "最早修改");

    companion object {
        fun fromWire(value: String?) = entries.firstOrNull { it.wireValue == value } ?: TitleAscending
    }
}

@Serializable
data class FolderSelection(
    val folderPaths: List<String> = emptyList(),
    val trackIds: List<TrackId> = emptyList(),
) {
    val size: Int get() = folderPaths.distinct().size + trackIds.distinct().size
    val isEmpty: Boolean get() = size == 0

    // Paths are opaque server identities. In particular, never trim a filename's spaces.
    fun distinct() = copy(folderPaths = folderPaths.filter { it.isNotEmpty() }.distinct(), trackIds = trackIds.distinct())
}

enum class FolderPlaybackMode { Play, Next, Append }
data class FolderPlaybackResult(val count: Int, val limited: Boolean)

const val FOLDER_QUEUE_LIMIT = 1_000

fun folderQueueTracks(tracks: List<Track>): List<Track> = tracks.filter { it.isAvailable }.distinctBy { it.id }

/** Music-library browsing, deliberately separate from administrator scan configuration. */
interface FolderBrowser {
    suspend fun roots(): List<MusicFolder>
    suspend fun content(path: String, sort: FolderSort): MusicDirectoryContent
    suspend fun playableTracks(selection: FolderSelection): List<Track>
    suspend fun addToPlaylist(selection: FolderSelection, playlistId: PlaylistId)
    suspend fun delete(selection: FolderSelection, permanently: Boolean)
    suspend fun recover(selection: FolderSelection)
}

interface FolderBrowserActions : FolderBrowser {
    val accountKey: String
    suspend fun play(selection: FolderSelection, mode: FolderPlaybackMode): FolderPlaybackResult
    suspend fun playlists(): List<Playlist>
    suspend fun createPlaylist(name: String): Playlist
}
