package com.seasonyuu.fnmusic.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MusicFolder(
    val guid: String,
    val name: String = "",
    val path: String = "",
    val metadataPreference: String = "cloud_preferred",
    val autoDownloadLyric: Boolean = false,
    val contentLastChangedAt: Long = 0,
    val accessStatus: Int? = null,
)
@Serializable
data class FolderAccess(val mode: String = "none", val guids: List<String> = emptyList())
@Serializable
data class ManagedMusicUser(
    val guid: String,
    val name: String,
    val role: String,
    val lastAccessedAt: Long? = null,
    val access: FolderAccess = FolderAccess(),
)
@Serializable
data class MusicServerSettings(val name: String, val lang: String = "")

data class MusicScanTask(val name: String, val libraryGuid: String, val total: Int, val completed: Int, val failed: Int, val done: Boolean, val canceled: Boolean,
    val id: String = "", val type: String = "fileScan", val cancelling: Boolean = false,
    val retryable: Boolean = false, val canceledCount: Int = 0,
    val createdAt: Long = 0, val doneAt: Long = 0,
)

data class AuthorizedMusicDirectory(val path: String, val storageType: Int, val cloudStorageType: Int = 0,
    val permission: String = "", val name: String = "")
data class MusicDirectory(val path: String, val name: String)
data class SearchIndexResult(val trackCount: Int?, val albumCount: Int?, val artistCount: Int?)

interface MusicAdministration {
    suspend fun authorizedDirectories(): List<AuthorizedMusicDirectory>
    suspend fun childDirectories(parent: String): List<MusicDirectory>
    suspend fun folderDetail(guid: String): MusicFolder
    suspend fun scanAllFolders()
    suspend fun cancelTask(taskId: String)
    suspend fun retryTask(taskId: String)
    suspend fun rebuildSearchIndex(): SearchIndexResult
    suspend fun scanTasks(): List<MusicScanTask>
    suspend fun folders(): List<MusicFolder>
    suspend fun saveFolder(original: MusicFolder?, path: String, metadataPreference: String, autoDownloadLyric: Boolean)
    suspend fun removeFolder(guid: String)
    suspend fun scanFolder(guid: String)
    suspend fun users(): List<ManagedMusicUser>
    suspend fun saveUser(original: ManagedMusicUser?, username: String, password: String?, access: FolderAccess)
    suspend fun defaultAccess(): FolderAccess
    suspend fun saveDefaultAccess(access: FolderAccess)
    suspend fun serverSettings(): MusicServerSettings
    suspend fun saveServerSettings(value: MusicServerSettings)
}
