package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.serialization.json.*

/** Contracts verified against the official Music Web client's settings service. */
class MusicAdministrationRepository(private val network: NetworkRuntime) : MusicAdministration {
    private val read get() = network.api
    private val write get() = network.accountMutationApi
    override suspend fun scanTasks() = read.adminTasks().requireData().items().mapNotNull { value ->
        val item = value.jsonObject
        if (item.string("type") !in setOf("fileScan", "cloudScrape", "cloudScrapeCorrection", "lyricDownload")) return@mapNotNull null
        MusicScanTask(item.string("name"), (item["ext"] as? JsonObject)?.string("libraryGUID").orEmpty(),
            item["total"]?.jsonPrimitive?.intOrNull ?: 0,
            item["successCount"]?.jsonPrimitive?.intOrNull ?: 0,
            item["failCount"]?.jsonPrimitive?.intOrNull ?: 0,
            item["done"]?.jsonPrimitive?.booleanOrNull ?: false,
            item["canceled"]?.jsonPrimitive?.booleanOrNull ?: false,
            item.string("id"), item.string("type"), item["cancelling"]?.jsonPrimitive?.booleanOrNull ?: false,
            item["retryable"]?.jsonPrimitive?.booleanOrNull ?: false, item["canceledCount"]?.jsonPrimitive?.intOrNull ?: 0,
            item["createdAt"]?.jsonPrimitive?.longOrNull ?: 0, item["doneAt"]?.jsonPrimitive?.longOrNull ?: 0)
    }
    override suspend fun authorizedDirectories() = read.authorizedDirectories().requireData().items().map { value ->
        val item = value.jsonObject
        AuthorizedMusicDirectory(item.string("path"), item["storageType"]?.jsonPrimitive?.intOrNull ?: -1,
            item["cloudStorageType"]?.jsonPrimitive?.intOrNull ?: 0, item.string("permission"), item.string("comment"))
    }
    override suspend fun childDirectories(parent: String) = read.childDirectories(parent).requireData().items().map { value ->
        val item = value.jsonObject
        MusicDirectory(item.string("path"), item.string("name"))
    }
    override suspend fun folderDetail(guid: String) = parseFolder(read.folderDetail(guid).requireData())
    override suspend fun folders() = read.adminFolders().requireData().items().map { parseFolder(it.jsonObject) }
    override suspend fun scanAllFolders() { write.scanAllFolders().requireSuccess() }
    override suspend fun cancelTask(taskId: String) { write.cancelTask(buildJsonObject { put("taskId", taskId) }).requireSuccess() }
    override suspend fun retryTask(taskId: String) { write.retryTask(buildJsonObject { put("taskId", taskId) }).requireSuccess() }
    override suspend fun rebuildSearchIndex(): SearchIndexResult {
        val data = write.rebuildSearchIndex().requireData()
        return SearchIndexResult(data["trackCount"]?.jsonPrimitive?.intOrNull,
            data["albumCount"]?.jsonPrimitive?.intOrNull, data["artistCount"]?.jsonPrimitive?.intOrNull)
    }
    override suspend fun saveFolder(original: MusicFolder?, path: String, metadataPreference: String, autoDownloadLyric: Boolean) {
        require(path.isNotBlank()) { "请选择音乐文件夹" }
        require(metadataPreference in setOf("cloud_preferred", "local_preferred", "local_only"))
        val body = buildJsonObject {
            original?.let { put("guid", it.guid) }
            put("path", path); put("metadataPreference", metadataPreference); put("autoDownloadLyric", metadataPreference != "local_only" && autoDownloadLyric)
        }
        (if (original == null) write.createFolder(body) else write.editFolder(body)).requireSuccess()
    }
    override suspend fun removeFolder(guid: String) { write.deleteFolder(buildJsonObject { put("guid", guid) }).requireSuccess() }
    override suspend fun scanFolder(guid: String) { write.scanFolder(buildJsonObject { put("guid", guid) }).requireSuccess() }
    override suspend fun users() = read.adminUsers().requireData().items().map { value ->
        val item = value.jsonObject
        ManagedMusicUser(item.string("guid"), item.string("name"), item.string("role"),
            item["lastAccessedAt"]?.jsonPrimitive?.longOrNull,
            parseAccess(item["sharedLibraryAccess"]))
    }
    override suspend fun saveUser(original: ManagedMusicUser?, username: String, password: String?, access: FolderAccess) {
        require(username.isNotBlank()) { "请输入用户名" }
        require(original != null || !password.isNullOrEmpty()) { "请输入密码" }
        val body = buildJsonObject {
            original?.let { put("guid", it.guid) }
            put("username", username)
            password?.takeIf { it.isNotEmpty() }?.let { put("password", it.sha256()) }
            put("sharedLibraryAccess", access.toJson())
        }
        (if (original == null) write.createUser(body) else write.editUser(body)).requireSuccess()
    }
    override suspend fun defaultAccess() = parseAccess(read.defaultUserSettings().requireData()["defaultSharedLibraryAccess"])
    override suspend fun saveDefaultAccess(access: FolderAccess) { write.saveDefaultUserSettings(buildJsonObject { put("defaultSharedLibraryAccess", access.toJson()) }).requireSuccess() }
    override suspend fun serverSettings(): MusicServerSettings {
        val data = read.adminServerSettings().requireData()
        return MusicServerSettings(data.string("name"), data.string("lang"))
    }
    override suspend fun saveServerSettings(value: MusicServerSettings) {
        require(value.name.isNotBlank()) { "请输入服务器名称" }
        write.saveAdminServerSettings(buildJsonObject { put("name", value.name); put("lang", value.lang) }).requireSuccess()
    }
}

internal fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull.orEmpty()
private fun JsonElement.items(): List<JsonElement> = when (this) {
    is JsonArray -> toList()
    is JsonObject -> (get("list") as? JsonArray)?.toList() ?: error("服务器返回的列表格式无法识别")
    else -> error("服务器返回的列表格式无法识别")
}
internal fun parseAccess(value: JsonElement?): FolderAccess {
    val item = value as? JsonObject ?: return FolderAccess()
    val mode = item.string("mode")
    require(mode in setOf("none", "all", "partial")) { "无法识别音乐文件夹权限" }
    val ids = (item["sharedLibraries"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.string("guid") }
        ?: (item["guids"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()
    return FolderAccess(mode, ids)
}
private fun FolderAccess.toJson() = buildJsonObject {
    require(mode in setOf("none", "all", "partial"))
    put("mode", mode); putJsonArray("guids") { if (mode == "partial") guids.distinct().forEach { add(it) } }
}

fun Throwable.isMusicPermissionDenied(): Boolean = this is retrofit2.HttpException && code() == 403 || this is MusicError.Business && code == 403

private fun parseFolder(item: JsonObject) = MusicFolder(
    item.string("guid"), item.string("name"), item.string("path"),
    item.string("metadataPreference").ifBlank { if (item["enableCloudMetadata"]?.jsonPrimitive?.booleanOrNull == false) "local_only" else "cloud_preferred" },
    item["autoDownloadLyric"]?.jsonPrimitive?.booleanOrNull ?: false,
    item["contentLastChangedAt"]?.jsonPrimitive?.longOrNull ?: 0,
    item["accessStatus"]?.jsonPrimitive?.intOrNull,
)
