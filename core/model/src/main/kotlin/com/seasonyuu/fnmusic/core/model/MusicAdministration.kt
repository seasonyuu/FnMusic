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

data class MusicScanTask(val name: String, val libraryGuid: String, val total: Int, val completed: Int, val failed: Int, val done: Boolean, val canceled: Boolean)

interface MusicAdministration {
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
