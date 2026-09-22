package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.core.network.isMusicPermissionDenied
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async

internal class ProfileAdministration(
    private val scope: CoroutineScope,
    private val delegate: MusicAdministration,
    private val canManage: () -> Boolean,
    private val currentUserId: () -> String?,
    private val onForbidden: () -> Unit,
    private val onOwnPasswordChanged: suspend (String) -> Unit,
    private val onOwnNameChanged: suspend (String) -> Unit,
    private val onServerChanged: (String) -> Unit,
) : MusicAdministration {
    private suspend fun <T> guarded(block: suspend () -> T): T = scope.async {
        check(canManage()) { "当前账户没有管理权限" }
        try { block() } catch (failure: Exception) {
            if (failure.isMusicPermissionDenied()) onForbidden()
            throw failure
        }
    }.await()
    override suspend fun authorizedDirectories() = guarded { delegate.authorizedDirectories() }
    override suspend fun childDirectories(parent: String) = guarded { delegate.childDirectories(parent) }
    override suspend fun folderDetail(guid: String) = guarded { delegate.folderDetail(guid) }
    override suspend fun scanAllFolders() = guarded { delegate.scanAllFolders() }
    override suspend fun cancelTask(taskId: String) = guarded { delegate.cancelTask(taskId) }
    override suspend fun retryTask(taskId: String) = guarded { delegate.retryTask(taskId) }
    override suspend fun rebuildSearchIndex() = guarded { delegate.rebuildSearchIndex() }
    override suspend fun scanTasks() = guarded { delegate.scanTasks() }
    override suspend fun folders() = guarded { delegate.folders() }
    override suspend fun saveFolder(original: MusicFolder?, path: String, metadataPreference: String, autoDownloadLyric: Boolean) = guarded { delegate.saveFolder(original, path, metadataPreference, autoDownloadLyric) }
    override suspend fun removeFolder(guid: String) = guarded { delegate.removeFolder(guid) }
    override suspend fun scanFolder(guid: String) = guarded { delegate.scanFolder(guid) }
    override suspend fun users() = guarded { delegate.users() }
    override suspend fun saveUser(original: ManagedMusicUser?, username: String, password: String?, access: FolderAccess) = guarded {
        delegate.saveUser(original, username, password, access)
        if (original != null && original.guid == currentUserId()) {
            if (!password.isNullOrEmpty()) onOwnPasswordChanged(username)
            else if (username != original.name) onOwnNameChanged(username)
        }
    }
    override suspend fun defaultAccess() = guarded { delegate.defaultAccess() }
    override suspend fun saveDefaultAccess(access: FolderAccess) = guarded { delegate.saveDefaultAccess(access) }
    override suspend fun serverSettings() = guarded { delegate.serverSettings() }
    override suspend fun saveServerSettings(value: MusicServerSettings) = guarded { delegate.saveServerSettings(value); onServerChanged(value.name) }
}
