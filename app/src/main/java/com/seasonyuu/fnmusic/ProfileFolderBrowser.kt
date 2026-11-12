package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.core.network.isMusicPermissionDenied
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Re-check account ownership before publishing a response or touching the local queue. */
internal class ProfileFolderBrowser(
    private val account: () -> String,
    private val enabled: () -> Boolean,
    private val canManage: () -> Boolean,
    private val delegate: FolderBrowser,
    private val playTracks: (List<Track>, FolderPlaybackMode) -> Unit,
    private val loadPlaylists: suspend () -> List<Playlist>,
    private val newPlaylist: suspend (String) -> Playlist,
    private val catalogChanged: suspend (PlaylistId?) -> Unit,
    private val onForbidden: () -> Unit,
) : FolderBrowserActions {
    override val accountKey: String get() = account()

    private suspend fun <T> guarded(manage: Boolean = false, block: suspend () -> T): T {
        val owner = accountKey
        check(enabled()) { "服务器暂不支持文件夹视图" }
        if (manage) check(canManage()) { "当前账户没有管理权限" }
        try {
            val result = block()
            currentCoroutineContext().ensureActive()
            check(owner == accountKey) { "账号已切换，请重新打开文件夹" }
            return result
        } catch (failure: Exception) {
            if (owner == runCatching { accountKey }.getOrNull() && failure.isMusicPermissionDenied()) onForbidden()
            throw failure
        }
    }

    override suspend fun roots() = guarded { delegate.roots() }
    override suspend fun content(path: String, sort: FolderSort) = guarded { delegate.content(path, sort) }
    override suspend fun playableTracks(selection: FolderSelection) = guarded { delegate.playableTracks(selection) }
    override suspend fun playlists() = guarded { loadPlaylists() }
    override suspend fun createPlaylist(name: String): Playlist {
        require(name.isNotBlank()) { "请输入歌单名称" }
        val result = guarded { newPlaylist(name.trim()) }
        catalogChanged(result.id)
        return result
    }

    override suspend fun play(selection: FolderSelection, mode: FolderPlaybackMode): FolderPlaybackResult {
        val tracks = folderQueueTracks(playableTracks(selection))
        val batch = tracks.take(FOLDER_QUEUE_LIMIT)
        if (batch.isNotEmpty()) playTracks(batch, mode)
        // Exactly 1000 can also mean the server truncated a larger directory.
        return FolderPlaybackResult(batch.size, tracks.size >= FOLDER_QUEUE_LIMIT)
    }

    override suspend fun addToPlaylist(selection: FolderSelection, playlistId: PlaylistId) {
        guarded { delegate.addToPlaylist(selection, playlistId) }
        catalogChanged(playlistId)
    }

    override suspend fun delete(selection: FolderSelection, permanently: Boolean) {
        guarded(manage = true) { delegate.delete(selection, permanently) }
        catalogChanged(null)
    }

    override suspend fun recover(selection: FolderSelection) {
        guarded(manage = true) { delegate.recover(selection) }
        catalogChanged(null)
    }
}
