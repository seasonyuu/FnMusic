package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.*
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import java.io.IOException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun normalizedLibraryPath(path: String) = "/" + path.trim('/').split('/').filter { it.isNotEmpty() }.joinToString("/")
internal fun directoryGroup(path: String): String {
    val volume = path.trim('/').substringBefore('/')
    return when {
        volume.startsWith("vol00") -> "外接存储"
        volume.startsWith("vol02") -> "远程挂载"
        volume.startsWith("vol") -> "存储空间 ${volume.removePrefix("vol")}"
        else -> "其他位置"
    }
}
internal fun selectableLibraryPath(path: String, existing: List<String>): Boolean {
    val normalized = normalizedLibraryPath(path)
    val parts = normalized.trim('/').split('/')
    val remote = parts.first().startsWith("vol02") || parts.first().startsWith("vol00")
    return parts.size >= (if (remote) 2 else 3) && existing.none { normalizedLibraryPath(it) == normalized }
}
internal val MusicFolder.displayName get() = name.ifBlank { path.trimEnd('/').substringAfterLast('/').ifBlank { path } }
internal val MusicScanTask.active get() = !done || cancelling
internal val MusicScanTask.phaseName get() = when (type) {
    "fileScan" -> "文件扫描"
    "cloudScrape" -> "元数据刮削"
    "cloudScrapeCorrection" -> "元数据校正"
    "lyricDownload" -> "歌词下载"
    else -> "音乐库任务"
}
internal val MusicScanTask.statusText get() = when {
    cancelling -> "取消中"
    canceled -> "已取消"
    done && failed > 0 -> "已结束，$failed 项失败"
    done -> "已完成"
    total <= 0 -> "等待或正在准备"
    else -> "正在处理 ${(completed.toLong() + failed).coerceAtMost(total.toLong())} / $total · 失败 $failed 项"
}
internal val MusicScanTask.progress get() = if (total <= 0) null else
    ((completed.toLong() + failed).toFloat() / total).coerceIn(0f, if (active) .99f else 1f)

internal data class LibraryDraft(val original: MusicFolder? = null, val path: String = "",
    val metadata: String = "cloud_preferred", val lyrics: Boolean = true) {
    val valid get() = path.isNotBlank() && metadata in setOf("cloud_preferred", "local_only", "local_preferred")
    val dirty get() = if (original == null) path.isNotEmpty() || metadata != "cloud_preferred" || !lyrics else
        path != original.path || metadata != original.metadataPreference || lyrics != original.autoDownloadLyric
}

/** Owned by one authenticated administration instance; no directory data is persisted. */
internal class LibraryAdministrationState(private val api: MusicAdministration, private val scope: CoroutineScope,
    private val onCatalogChanged: () -> Unit = {}, private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    var folders by mutableStateOf<List<MusicFolder>?>(null); private set
    var tasks by mutableStateOf(emptyList<MusicScanTask>()); private set
    var listError by mutableStateOf<String?>(null); private set
    var taskError by mutableStateOf<String?>(null); private set
    var refreshing by mutableStateOf(false); private set
    var operations by mutableStateOf(emptySet<String>()); private set
    var message by mutableStateOf<String?>(null)
    var actionError by mutableStateOf<String?>(null)
    var draft by mutableStateOf<LibraryDraft?>(null)
    var editError by mutableStateOf<String?>(null); private set
    var editingGuid by mutableStateOf<String?>(null); private set
    var editLoading by mutableStateOf(false); private set
    var pickerOpen by mutableStateOf(false)
    var roots by mutableStateOf<List<AuthorizedMusicDirectory>?>(null); private set
    var directory by mutableStateOf<String?>(null); private set
    var children by mutableStateOf(emptyList<MusicDirectory>()); private set
    var directoryLoading by mutableStateOf(false); private set
    var directoryError by mutableStateOf<String?>(null); private set
    var pendingScans by mutableStateOf(emptyMap<String, Set<String>>()); private set
    val directoryScrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private val taskMutex = Mutex()
    private val pendingSince = mutableMapOf<String, Long>()
    fun saveEditorSnapshot(): List<String> = listOf(editingGuid.orEmpty(), draft?.path.orEmpty(),
        draft?.metadata.orEmpty(), (draft?.lyrics ?: true).toString(),
        draft?.original?.let { Json.encodeToString(it) }.orEmpty(), pickerOpen.toString(), directory.orEmpty(), saving.toString())
    fun restoreEditorSnapshot(values: List<String>) {
        editingGuid = values[0].ifEmpty { null }
        if (values[2].isNotEmpty()) draft = LibraryDraft(values[4].takeIf { it.isNotEmpty() }?.let { Json.decodeFromString<MusicFolder>(it) }, values[1], values[2], values[3].toBoolean())
        pickerOpen = values[5].toBoolean()
        directory = values[6].ifEmpty { null }
        if (values[7].toBoolean()) editError = "请求结果待确认，请刷新状态后再操作"
        if (draft == null) editingGuid?.let(::edit)
        if (pickerOpen) browse(directory)
    }
    private var directoryJob: Job? = null
    private var directoryGeneration = 0
    private var editJob: Job? = null
    val saving get() = "save" in operations
    val existingPaths get() = folders.orEmpty().filter { it.guid != draft?.original?.guid }.map { it.path }
    val canSelectDirectory get() = directory != null && !directoryLoading && directoryError == null &&
        roots.orEmpty().any { normalizedLibraryPath(directory!!) == normalizedLibraryPath(it.path) || normalizedLibraryPath(directory!!).startsWith(normalizedLibraryPath(it.path) + "/") } &&
        selectableLibraryPath(directory!!, existingPaths)

    private fun failure(e: Exception, fallback: String) = if (e is IOException) "连接失败，请重试" else fallback
    suspend fun refresh() = coroutineScope {
        if (refreshing) return@coroutineScope
        refreshing = true
        try {
            listOf(launch {
                try { folders = api.folders(); listError = null }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { listError = failure(e, "加载音乐文件夹失败，请重试") }
            }, launch { refreshTasks() }).joinAll()
        } finally { refreshing = false }
    }
    fun reload() { scope.launch { refresh() } }
    suspend fun refreshTasks() = taskMutex.withLock {
        try {
            val next = api.scanTasks()
            val completed = tasks.any { old -> old.active && old.id.isNotBlank() &&
                (next.none { it.id == old.id } || next.any { it.id == old.id && !it.active && !it.canceled }) }
            tasks = next
            pendingScans = pendingScans.filter { (guid, known) -> next.none { it.libraryGuid == guid && (it.id !in known || it.active) } }
            val expired = pendingScans.keys.filter { monotonicMillis() - (pendingSince[it] ?: monotonicMillis()) >= 30_000 }
            if (expired.isNotEmpty()) {
                pendingScans = pendingScans - expired.toSet()
                actionError = "暂未发现提交的扫描任务，请刷新确认状态后再决定是否重新扫描"
            }
            pendingSince.keys.retainAll(pendingScans.keys)
            taskError = null
            if (completed) { onCatalogChanged(); folders = api.folders() }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { taskError = failure(e, "任务状态更新失败，请重试") }
    }
    private fun action(key: String, block: suspend () -> Unit) {
        if (key in operations) return
        operations = operations + key
        actionError = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val text = if (e is IOException) "请求结果待确认，请刷新状态后再操作" else "操作失败，请重试"
                if (key == "save") editError = text else actionError = text
                refresh()
            } finally { operations = operations - key }
        }
    }
    fun create() {
        if (folders == null || folders!!.size >= 200) { actionError = "最多只能添加 200 个文件夹"; return }
        editingGuid = null; draft = LibraryDraft(); editError = null
        pickerOpen = true; browse(null)
    }
    fun edit(guid: String) {
        editJob?.cancel(); editingGuid = guid; draft = null; editError = null; editLoading = true
        editJob = scope.launch {
            try { val folder = api.folderDetail(guid); draft = LibraryDraft(folder, folder.path, folder.metadataPreference, folder.autoDownloadLyric) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { editError = "读取文件夹详情失败，请重试" }
            finally { editLoading = false }
        }
    }
    fun closeEditor() { if (!saving) { editJob?.cancel(); editingGuid = null; draft = null; editLoading = false; editError = null } }
    fun save() {
        val value = draft ?: return
        if (!value.valid || folders == null || (value.original == null && folders.orEmpty().size >= 200)) return
        if (existingPaths.any { normalizedLibraryPath(it) == normalizedLibraryPath(value.path) }) { editError = "这个文件夹已添加"; return }
        action("save") {
            api.saveFolder(value.original, value.path, value.metadata, value.metadata != "local_only" && value.lyrics)
            draft = null; editingGuid = null; editError = null
            message = if (value.original == null) "已添加音乐文件夹" else "已保存修改"
            onCatalogChanged(); refresh()
        }
    }
    fun scan(guid: String? = null) {
        val affected = if (guid == null) folders.orEmpty().map { it.guid } else listOf(guid)
        action(if (guid == null) "scan-all" else "scan:$guid") {
            val known = tasks.map { it.id }.toSet()
            if (guid == null) api.scanAllFolders() else api.scanFolder(guid)
            pendingScans = pendingScans + affected.associateWith { known }
            affected.forEach { pendingSince[it] = monotonicMillis() }
            message = "已提交，等待任务更新"; refreshTasks()
        }
    }
    fun remove(folder: MusicFolder) = action("remove:${folder.guid}") {
        api.removeFolder(folder.guid); pendingScans = pendingScans - folder.guid
        message = "已移除音乐文件夹，原始文件保留"; onCatalogChanged(); refresh()
    }
    fun cancel(task: MusicScanTask) { if (task.active && !task.cancelling && task.id.isNotBlank()) action("task:${task.id}") { api.cancelTask(task.id); message = "已提交取消请求"; refreshTasks() } }
    fun retry(task: MusicScanTask) { if (task.done && !task.cancelling && task.retryable && task.id.isNotBlank()) action("task:${task.id}") { api.retryTask(task.id); message = "已提交重试请求"; refreshTasks() } }
    fun rebuild() = action("rebuild") {
        val result = api.rebuildSearchIndex()
        message = "搜索索引已重建" + listOfNotNull(result.trackCount?.let { "$it 首歌曲" }, result.albumCount?.let { "$it 张专辑" }, result.artistCount?.let { "$it 位艺术家" }).joinToString("，").let { if (it.isEmpty()) "" else "：$it" }
        onCatalogChanged()
    }
    fun browse(path: String?) {
        directoryJob?.cancel()
        val generation = ++directoryGeneration
        directory = path; children = emptyList(); directoryError = null; directoryLoading = true
        directoryJob = scope.launch {
            try {
                if (path == null || roots == null) { val result = api.authorizedDirectories(); if (generation == directoryGeneration) roots = result }
                if (path != null) { val result = api.childDirectories(path); if (generation == directoryGeneration) children = result }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (generation == directoryGeneration) directoryError = "无法访问此目录，请检查连接或音乐应用的目录权限" }
            finally { if (generation == directoryGeneration) directoryLoading = false }
        }
    }
    fun directoryUp() {
        val current = directory ?: return
        val root = roots.orEmpty().filter { normalizedLibraryPath(current).startsWith(normalizedLibraryPath(it.path) + "/") || normalizedLibraryPath(current) == normalizedLibraryPath(it.path) }.maxByOrNull { it.path.length }
        browse(if (root == null || normalizedLibraryPath(root.path) == normalizedLibraryPath(current)) null else current.trimEnd('/').substringBeforeLast('/'))
    }
    fun chooseDirectory() { if (canSelectDirectory) { draft = draft?.copy(path = directory!!); pickerOpen = false } }
}
