package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.*
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun folderContains(root: String, path: String): Boolean =
    path == root || path.startsWith(root.trimEnd('/') + "/")

internal data class FolderBreadcrumb(val path: String, val label: String)

/** One instance belongs to one account and one navigation entry. Nothing is cached on disk. */
internal class FolderBrowserState(
    private val api: FolderBrowserActions,
    private val scope: CoroutineScope,
    private val changed: () -> Unit = {},
    initialPath: String? = null,
    initialTrack: TrackId? = null,
) {
    private val owner = api.accountKey
    var roots by mutableStateOf<List<MusicFolder>>(emptyList()); private set
    var rootsLoading by mutableStateOf(false); private set
    var rootsError by mutableStateOf<String?>(null); private set
    var path by mutableStateOf(initialPath); private set
    var sort by mutableStateOf(FolderSort.TitleAscending); private set
    var content by mutableStateOf<MusicDirectoryContent?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var selectionMode by mutableStateOf(false); private set
    var selection by mutableStateOf(FolderSelection()); private set
    var expandedPaths by mutableStateOf<Set<String>>(emptySet()); private set
    var treeCollapsed by mutableStateOf(false)
    var treeContents by mutableStateOf<Map<String, MusicDirectoryContent>>(emptyMap()); private set
    var treeLoading by mutableStateOf<Set<String>>(emptySet()); private set
    var treeErrors by mutableStateOf<Set<String>>(emptySet()); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    var actionError by mutableStateOf<String?>(null); private set
    var highlightTrack by mutableStateOf(initialTrack); private set
    val scrollPositions = mutableMapOf<String, Pair<Int, Int>>()
    private var rootsJob: Job? = null
    private var contentJob: Job? = null
    private var actionJob: Job? = null
    private val treeJobs = mutableMapOf<String, Job>()
    private var rootsGeneration = 0
    private var contentGeneration = 0
    private var treeGeneration = 0
    private var disposed = false

    val root: MusicFolder? get() = path?.let { current -> roots.filter { folderContains(it.path, current) }.maxByOrNull { it.path.length } }
    val usable: Boolean get() = !loading && !rootsLoading && rootsError == null && error == null && content != null && (root?.accessStatus ?: 0) == 0
    val breadcrumbs: List<FolderBreadcrumb> get() {
        val base = root ?: return emptyList()
        val current = path ?: return emptyList()
        val result = mutableListOf(FolderBreadcrumb(base.path, base.name.ifEmpty { base.path.substringAfterLast('/') }))
        var parent = base.path.trimEnd('/')
        current.removePrefix(parent).split('/').filter { it.isNotEmpty() }.forEach {
            parent += "/$it"
            result += FolderBreadcrumb(parent, it)
        }
        return result
    }

    private fun active(): Boolean = !disposed && runCatching { api.accountKey == owner }.getOrDefault(false)

    fun start() = refresh()

    fun refresh() {
        rootsJob?.cancel()
        contentJob?.cancel()
        contentGeneration++
        invalidateTree()
        val generation = ++rootsGeneration
        rootsLoading = true
        rootsError = null
        rootsJob = scope.launch {
            try {
                val result = api.roots()
                currentCoroutineContext().ensureActive()
                if (!active() || generation != rootsGeneration) return@launch
                roots = result
                if (path != null && root == null) {
                    path = null; content = null; clearSelection()
                    message = "该目录已不在可访问的音乐库中"
                }
                revealCurrentPath()
                loadContent()
                expandedPaths.filter { it != path }.forEach(::loadTree)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (active() && generation == rootsGeneration) rootsError = "加载音乐文件夹失败，请检查连接后重试"
            } finally {
                if (active() && generation == rootsGeneration) rootsLoading = false
            }
        }
    }

    fun navigate(target: String?) {
        if (target != null && roots.none { folderContains(it.path, target) }) return
        path = target
        highlightTrack = null
        content = null
        clearSelection()
        revealCurrentPath()
        loadContent()
    }

    private fun revealCurrentPath() {
        val ancestors = breadcrumbs.map { it.path }
        expandedPaths = expandedPaths + ancestors
        ancestors.filter { it != path }.forEach(::loadTree)
    }

    fun up(): Boolean {
        if (selectionMode) { clearSelection(); return true }
        if (path == null) return false
        navigate(breadcrumbs.dropLast(1).lastOrNull()?.path)
        return true
    }

    fun changeSort(value: FolderSort) {
        if (sort == value) return
        sort = value
        clearSelection()
        loadContent()
    }

    fun loadContent() {
        contentJob?.cancel()
        val generation = ++contentGeneration
        val current = path
        error = null
        loading = false
        if (current == null) { content = null; return }
        if (root == null) return
        when (root?.accessStatus) {
            1 -> error = "音乐库目录已丢失，请检查存储空间或联系管理员"
            2 -> error = "暂无访问音乐库文件夹权限"
            3 -> error = "音乐应用尚未获得此目录的授权"
            null, 0 -> Unit
            else -> error = "此音乐库当前不可访问，请联系管理员"
        }
        if (error != null) { content = null; return }
        loading = true
        val requestedSort = sort
        contentJob = scope.launch {
            try {
                val result = api.content(current, requestedSort)
                currentCoroutineContext().ensureActive()
                if (!active() || generation != contentGeneration || current != path) return@launch
                content = result
                sort = result.sort
                treeContents = treeContents + (current to result)
                // A refresh may remove selected items; never act on invisible stale identities.
                selection = FolderSelection(selection.folderPaths.filter { p -> result.folders.any { it.path == p } },
                    selection.trackIds.filter { id -> result.tracks.any { it.id == id } })
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (active() && generation == contentGeneration) error = "加载文件夹内容失败，请重试"
            } finally {
                if (active() && generation == contentGeneration) loading = false
            }
        }
    }

    fun toggleTree(target: String) {
        if (target in expandedPaths) expandedPaths = expandedPaths - target
        else { expandedPaths = expandedPaths + target; loadTree(target) }
    }

    fun loadTree(target: String) {
        if (target in treeContents || target in treeLoading) return
        val targetRoot = roots.filter { folderContains(it.path, target) }.maxByOrNull { it.path.length } ?: return
        if ((targetRoot.accessStatus ?: 0) != 0) return
        val generation = treeGeneration
        treeLoading = treeLoading + target
        treeErrors = treeErrors - target
        treeJobs[target] = scope.launch {
            try {
                val result = api.content(target, FolderSort.TitleAscending)
                currentCoroutineContext().ensureActive()
                if (active() && generation == treeGeneration) treeContents = treeContents + (target to result)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (active() && generation == treeGeneration) treeErrors = treeErrors + target }
            finally { if (active() && generation == treeGeneration) treeLoading = treeLoading - target }
        }
    }

    private fun invalidateTree() {
        treeGeneration++
        treeJobs.values.forEach { it.cancel() }
        treeJobs.clear()
        treeContents = emptyMap(); treeLoading = emptySet(); treeErrors = emptySet()
    }

    fun beginSelection() { if (usable && !busy) selectionMode = true }
    fun clearSelection() { selectionMode = false; selection = FolderSelection() }
    fun toggle(folder: MusicDirectoryNode) {
        if (!usable || busy) return
        selectionMode = true
        selection = selection.copy(folderPaths = if (folder.path in selection.folderPaths) selection.folderPaths - folder.path else selection.folderPaths + folder.path)
    }
    fun toggle(track: Track) {
        if (!usable || busy) return
        selectionMode = true
        selection = selection.copy(trackIds = if (track.id in selection.trackIds) selection.trackIds - track.id else selection.trackIds + track.id)
    }
    fun selectAll() {
        if (!usable || busy) return
        selectionMode = true
        val all = FolderSelection(content!!.folders.map { it.path }, content!!.tracks.map { it.id })
        selection = if (selection.size == all.size) FolderSelection() else all
    }

    fun play(target: FolderSelection, mode: FolderPlaybackMode) = action {
        val result = api.play(target, mode)
        when {
            result.count == 0 -> "没有可播放的歌曲"
            result.limited -> "已处理 ${result.count} 首歌曲，单次最多添加 $FOLDER_QUEUE_LIMIT 首"
            mode == FolderPlaybackMode.Next -> "已将 ${result.count} 首歌曲设为下一首播放"
            mode == FolderPlaybackMode.Append -> "已将 ${result.count} 首歌曲加入播放列表"
            else -> "正在播放 ${result.count} 首歌曲"
        }
    }

    fun delete(target: FolderSelection, permanently: Boolean) = action(refresh = true) {
        api.delete(target, permanently)
        if (permanently) "已移除并删除文件" else "已从音乐库移除，原始文件保留"
    }

    fun recover(target: FolderSelection) = action(refresh = true) {
        api.recover(target)
        "正在恢复歌曲，请稍后刷新音乐库查看"
    }

    fun addToPlaylist(target: FolderSelection, playlist: Playlist, onSuccess: () -> Unit) = action {
        api.addToPlaylist(target, playlist.id)
        onSuccess()
        changed()
        "已加入${playlist.name}"
    }

    private fun action(refresh: Boolean = false, block: suspend () -> String) {
        if (busy || !active()) return
        busy = true
        actionError = null
        val location = path
        actionJob = scope.launch {
            try {
                val result = block()
                currentCoroutineContext().ensureActive()
                if (!active()) return@launch
                if (location == path) clearSelection()
                message = result
                if (refresh) { changed(); refresh() }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) {
                if (active()) {
                    actionError = "操作未完成，请刷新确认结果后重试"
                    message = actionError
                }
            } finally { if (active()) busy = false }
        }
    }

    fun save(): List<String> = listOf(owner, path.orEmpty(), sort.name, treeCollapsed.toString(),
        Json.encodeToString(expandedPaths.toList()),
        Json.encodeToString(scrollPositions.mapValues { listOf(it.value.first, it.value.second) }), highlightTrack?.value.orEmpty())

    fun restore(saved: List<String>) {
        if (saved.size != 7 || saved[0] != owner) return
        runCatching {
            path = saved[1].ifEmpty { null }
            sort = FolderSort.valueOf(saved[2])
            treeCollapsed = saved[3].toBoolean()
            expandedPaths = Json.decodeFromString<List<String>>(saved[4]).toSet()
            Json.decodeFromString<Map<String, List<Int>>>(saved[5]).forEach { (key, value) ->
                if (value.size == 2) scrollPositions[key] = value[0].coerceAtLeast(0) to value[1].coerceAtLeast(0)
            }
            highlightTrack = saved[6].takeIf { it.isNotEmpty() }?.let(::TrackId)
        }
    }

    fun clearHighlight() { highlightTrack = null }
    fun dispose() {
        disposed = true
        rootsJob?.cancel(); contentJob?.cancel(); actionJob?.cancel()
        treeJobs.values.forEach { it.cancel() }
    }
}
