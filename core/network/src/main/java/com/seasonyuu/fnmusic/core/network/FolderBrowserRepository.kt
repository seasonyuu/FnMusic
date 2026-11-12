package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.serialization.Serializable

@Serializable
data class FolderNodeDto(
    val path: String,
    val name: String = "",
    val itemCount: Int = 0,
    val isAdminDeleted: Boolean = false,
    val folders: List<FolderNodeDto> = emptyList(),
    val tracks: List<TrackDto> = emptyList(),
) {
    fun toNode() = MusicDirectoryNode(path, name.ifEmpty { path.substringAfterLast('/') }, itemCount, isAdminDeleted)
}

@Serializable
data class FolderContentDto(val folder: FolderNodeDto, val sort: String = "title,asc")

@Serializable
data class FolderPlayableTracksDto(val tracks: List<TrackDto> = emptyList())

@Serializable
data class FolderSelectionRequest(val folderPaths: List<String>, val trackGUIDs: List<String>)

@Serializable
data class FolderPlaylistRequest(val folderPaths: List<String>, val trackGUIDs: List<String>, val playlistGUID: String)

@Serializable
data class FolderDeleteRequest(val folderPaths: List<String>, val trackGUIDs: List<String>, val isPermanent: Boolean)

class FolderBrowserRepository(private val network: NetworkRuntime) : FolderBrowser {
    override suspend fun roots() = network.api.folderViewRoots().requireData().list

    override suspend fun content(path: String, sort: FolderSort): MusicDirectoryContent {
        require(path.isNotEmpty())
        val data = network.api.folderViewContent(path, sort.wireValue).requireData()
        return MusicDirectoryContent(data.folder.toNode(), data.folder.folders.map { it.toNode() },
            data.folder.tracks.map { it.toDomain() }, FolderSort.fromWire(data.sort))
    }

    override suspend fun playableTracks(selection: FolderSelection): List<Track> {
        if (selection.isEmpty) return emptyList()
        return network.api.folderPlayableTracks(selection.request()).requireData().tracks.map { it.toDomain() }
    }

    override suspend fun addToPlaylist(selection: FolderSelection, playlistId: PlaylistId) {
        val request = selection.request()
        network.accountMutationApi.folderAddToPlaylist(FolderPlaylistRequest(request.folderPaths, request.trackGUIDs, playlistId.value)).requireSuccess()
    }

    override suspend fun delete(selection: FolderSelection, permanently: Boolean) {
        val request = selection.request()
        network.accountMutationApi.folderDelete(FolderDeleteRequest(request.folderPaths, request.trackGUIDs, permanently)).requireSuccess()
    }

    override suspend fun recover(selection: FolderSelection) {
        network.accountMutationApi.folderRecover(selection.request()).requireSuccess()
    }
}

private fun FolderSelection.request(): FolderSelectionRequest {
    val selection = distinct()
    require(!selection.isEmpty) { "请选择文件夹或歌曲" }
    return FolderSelectionRequest(selection.folderPaths, selection.trackIds.map { it.value })
}
