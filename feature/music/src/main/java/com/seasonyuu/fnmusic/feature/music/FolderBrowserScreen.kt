package com.seasonyuu.fnmusic.feature.music

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun FolderBrowserScreen(
    api: FolderBrowserActions,
    canManage: Boolean,
    wide: Boolean,
    coverUrl: (String?, Int) -> String?,
    onBack: () -> Unit,
    onTrackInfo: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    favoriteOverrides: Map<TrackId, Boolean> = emptyMap(),
    initialPath: String? = null,
    initialTrack: TrackId? = null,
    authorizationTarget: FolderAuthorizationTarget? = null,
    isActive: Boolean = true,
    onCatalogChanged: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onCatalogChanged)
    val owner = api.accountKey
    val state = rememberSaveable(owner, saver = listSaver(
        save = { it: FolderBrowserState -> it.save() },
        restore = { saved -> FolderBrowserState(api, scope, { changed() }, initialPath, initialTrack).apply { restore(saved) } },
    )) { FolderBrowserState(api, scope, { changed() }, initialPath, initialTrack) }
    LaunchedEffect(state) { state.start() }
    DisposableEffect(state) { onDispose { state.dispose() } }
    val back = { if (!state.up()) onBack() }
    BackHandler(isActive && (state.selectionMode || state.path != null)) { back() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbar.showSnackbar(message)
            if (state.message == message) state.message = null
        }
    }
    var deleting by remember(owner) { mutableStateOf<FolderSelection?>(null) }
    var permanentConfirmation by remember(owner) { mutableStateOf(false) }
    var adding by remember(owner) { mutableStateOf<FolderSelection?>(null) }
    var authorizing by rememberSaveable(owner) { mutableStateOf(false) }
    val background = FnSurface.toArgb()
    val foreground = FnTextPrimary.toArgb()
    val accent = FnAccent.toArgb()
    val authorization = rememberLauncherForActivityResult(FolderAuthorizationContract()) { result ->
        authorizing = false
        when (result) {
            is FolderAuthorizationResult.Success -> { state.message = "已更新授权，正在重新检查目录"; state.refresh() }
            FolderAuthorizationResult.Cancel, FolderAuthorizationResult.Closed -> state.message = "已取消文件夹授权"
            else -> state.message = "授权未完成，请重新检查目录权限"
        }
    }
    val authorize: (() -> Unit)? = if (canManage && authorizationTarget != null) {
        {
            if (!authorizing) runCatching {
                val request = FolderAuthorizationRequest.create(authorizationTarget, state.roots.map { it.path })
                authorizing = true
                authorization.launch(FolderAuthorizationLaunch(request, background, foreground, accent))
            }.onFailure { authorizing = false; state.message = "无法打开文件夹授权，请检查连接" }
        }
    } else null

    Box(Modifier.fillMaxSize().testTag("folder-browser")) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            MusicAppBar(if (state.selectionMode) "已选 ${state.selection.size} 项" else "文件夹", onBack = back,
                modifier = Modifier.zIndex(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).padding(horizontal = 20.dp),
                actions = {
                    if (state.selectionMode) {
                        AppBarButton(onClick = state::selectAll, enabled = state.usable && !state.busy) { Text("全选") }
                        AppBarButton(onClick = state::clearSelection) { Text("完成") }
                    } else {
                        AppBarButton(onClick = state::refresh, enabled = !state.busy && !state.rootsLoading) { Icon(Icons.Rounded.Refresh, "刷新") }
                        AppBarMenu(FolderSort.entries.map { LiquidMenuItem(it.name, it.label, selected = state.sort == it, enabled = !state.busy) },
                            { state.changeSort(FolderSort.valueOf(it)) }) { Icon(Icons.Rounded.SortByAlpha, "排序") }
                        if (state.path != null) AppBarButton(onClick = state::beginSelection, enabled = state.usable && !state.busy) { Icon(Icons.Rounded.Checklist, "多选") }
                    }
                })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (wide) IconButton(onClick = { state.treeCollapsed = !state.treeCollapsed }) {
                    Icon(Icons.Rounded.AccountTree, if (state.treeCollapsed) "展开文件列表" else "收起文件列表")
                }
                TextButton(onClick = { state.navigate(null) }) { Text("音乐文件夹") }
                state.breadcrumbs.forEach { crumb ->
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(16.dp), tint = FnTextSecondary)
                    TextButton(onClick = { state.navigate(crumb.path) }) {
                        Text(crumb.label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                    }
                }
            }
            if (state.selectionMode) FolderSelectionToolbar(state, canManage,
                onPlaylist = { adding = it }, onDelete = { deleting = it; permanentConfirmation = false })
            if (state.busy || state.loading || state.rootsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(Modifier.weight(1f).fillMaxWidth()) {
                if (wide && !state.treeCollapsed) {
                    FolderTree(state, Modifier.width(240.dp).fillMaxHeight())
                    VerticalDivider(color = FnTextSecondary.copy(alpha = .12f))
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    when {
                        state.rootsError != null -> FolderError(state.rootsError!!, state::refresh)
                        state.path == null -> FolderRoots(state)
                        state.error != null -> FolderError(state.error!!, state::refresh,
                            authorize = authorize.takeIf { state.root?.accessStatus == 3 })
                        state.content != null -> FolderContents(state, canManage, coverUrl, favoriteOverrides, onTrackInfo, onToggleFavorite,
                            onPlaylist = { adding = it }, onDelete = { deleting = it; permanentConfirmation = false })
                        !state.loading && !state.rootsLoading -> FolderError("无法打开此目录", state::refresh)
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = LocalBottomOverlayPadding.current + 12.dp))
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null; permanentConfirmation = false },
            title = { Text(if (permanentConfirmation) "永久删除文件？" else "删除 ${target.size} 项") },
            text = { Text(if (permanentConfirmation) "将删除所选歌曲以及所选文件夹内的歌曲文件。此操作无法通过“重新加入音乐库”撤销。"
                else "文件夹操作包含子目录中的歌曲。仅从音乐库移除会保留原始文件，之后可重新加入。") },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    if (!permanentConfirmation) TextButton(onClick = {
                        if (canManage) state.delete(target, false)
                        deleting = null
                    }, enabled = canManage && !state.busy) { Text("仅从音乐库移除") }
                    TextButton(onClick = {
                        if (!permanentConfirmation) permanentConfirmation = true
                        else { if (canManage) state.delete(target, true); deleting = null; permanentConfirmation = false }
                    }, enabled = canManage && !state.busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text(if (permanentConfirmation) "确认永久删除" else "移除并删除文件")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null; permanentConfirmation = false }) { Text("取消") } },
        )
    }
    adding?.let { target ->
        FolderPlaylistPicker(api, state, target, onDismiss = { adding = null })
    }
}

@Composable
private fun FolderRoots(state: FolderBrowserState) {
    val list = rememberFolderScroll(state, "", !state.rootsLoading, state.roots.size)
    LazyColumn(state = list, contentPadding = edgeToEdgeContentPadding(horizontal = 16.dp, bottom = 20.dp, includeTopInset = false)) {
        item { Text("共 ${state.roots.size} 个音乐文件夹", Modifier.padding(12.dp), color = FnTextSecondary) }
        if (!state.rootsLoading && state.roots.isEmpty()) item { Text("暂无可访问的音乐文件夹，请联系管理员添加音乐库或授予访问权限", Modifier.padding(12.dp)) }
        items(state.roots, key = { it.guid }) { root ->
            FolderRow(root.name.ifEmpty { root.path.substringAfterLast('/') }, "位于：${root.path}",
                onClick = { state.navigate(root.path) }, onLongClick = null)
        }
    }
}

private data class FolderTreeEntry(val path: String, val label: String, val depth: Int, val deleted: Boolean)

@Composable
private fun FolderTree(state: FolderBrowserState, modifier: Modifier) {
    val rows = remember(state.roots, state.expandedPaths, state.treeContents) {
        buildList {
            val seen = mutableSetOf<String>()
            fun addNode(path: String, name: String, depth: Int, deleted: Boolean) {
                if (!seen.add(path)) return
                add(FolderTreeEntry(path, name, depth, deleted))
                if (path in state.expandedPaths) state.treeContents[path]?.folders?.forEach { addNode(it.path, it.name, depth + 1, it.isAdminDeleted) }
            }
            state.roots.forEach { addNode(it.path, it.name.ifEmpty { it.path.substringAfterLast('/') }, 0, false) }
        }
    }
    LazyColumn(modifier.testTag("folder-tree"), contentPadding = edgeToEdgeContentPadding(bottom = 16.dp, includeTopInset = false)) {
        item { Text("文件列表", Modifier.padding(16.dp), style = MaterialTheme.typography.labelLarge, color = FnTextSecondary) }
        items(rows, key = { it.path }) { row ->
            Row(Modifier.fillMaxWidth().background(if (state.path == row.path) FnAccent.copy(alpha = .12f) else Color.Transparent)
                .padding(start = (row.depth.coerceAtMost(5) * 12).dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { state.toggleTree(row.path) }, enabled = !row.deleted) {
                    if (row.path in state.treeLoading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(if (row.path in state.expandedPaths) Icons.Rounded.ExpandMore else Icons.Rounded.ChevronRight,
                        if (row.path in state.expandedPaths) "折叠${row.label}" else "展开${row.label}")
                }
                TextButton(onClick = { state.navigate(row.path) }, enabled = !row.deleted, modifier = Modifier.weight(1f).semantics { selected = state.path == row.path }) {
                    Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(row.label, Modifier.weight(1f), color = FnTextPrimary.copy(alpha = if (row.deleted) .38f else 1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (row.path in state.treeErrors) TextButton(onClick = { state.loadTree(row.path) },
                colors = ButtonDefaults.textButtonColors(contentColor = FnTextPrimary)) { Text("加载失败，重试") }
        }
    }
}

@Composable
private fun FolderContents(state: FolderBrowserState, canManage: Boolean, coverUrl: (String?, Int) -> String?,
    favoriteOverrides: Map<TrackId, Boolean>, onTrackInfo: (Track) -> Unit, onToggleFavorite: (Track) -> Unit,
    onPlaylist: (FolderSelection) -> Unit, onDelete: (FolderSelection) -> Unit) {
    val content = state.content ?: return
    key(state.path) {
        val list = rememberFolderScroll(state, state.path.orEmpty(), !state.loading, content.rowCount + 1)
        val highlighted = state.highlightTrack
        LaunchedEffect(highlighted, content) {
            if (highlighted != null) {
                val index = content.tracks.indexOfFirst { it.id == highlighted }
                if (index >= 0) list.scrollToItem(1 + content.folders.size + index)
            }
        }
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().testTag("folder-content"),
            contentPadding = edgeToEdgeContentPadding(horizontal = 12.dp, bottom = 24.dp, includeTopInset = false)) {
            item {
                Text("共 ${content.rowCount} 项 · 目录下 ${content.folder.itemCount} 首歌曲", Modifier.padding(12.dp), color = FnTextSecondary)
            }
            if (content.rowCount == 0) item { Text("此文件夹中暂无歌曲", Modifier.padding(12.dp)) }
            items(content.folders, key = { "folder:${it.path}" }) { folder ->
                val selected = folder.path in state.selection.folderPaths
                val target = FolderSelection(folderPaths = listOf(folder.path))
                Row(Modifier.fillMaxWidth().background(if (selected) FnAccent.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(12.dp)), verticalAlignment = Alignment.CenterVertically) {
                    if (state.selectionMode) Checkbox(selected, onCheckedChange = { state.toggle(folder) }, enabled = state.usable && !state.busy)
                    Box(Modifier.weight(1f)) {
                        FolderRow(folder.name, "${folder.itemCount} 首歌曲" + if (folder.isAdminDeleted) " · 已从音乐库移除" else "",
                            onClick = {
                                if (state.selectionMode) state.toggle(folder)
                                else if (folder.isAdminDeleted) state.message = "已从音乐库移除，请重新加入"
                                else state.navigate(folder.path)
                            }, onLongClick = { state.toggle(folder) },
                            trailing = { if (!state.selectionMode) FolderItemMenu(state, target, canManage, onPlaylist, onDelete) })
                    }
                }
            }
            items(content.tracks, key = { "track:${it.id.value}" }) { track ->
                val selected = track.id in state.selection.trackIds
                val favorite = favoriteOverrides[track.id] ?: track.isFavorite
                Row(Modifier.fillMaxWidth().background(if (selected || track.id == highlighted) FnAccent.copy(alpha = .12f) else Color.Transparent, RoundedCornerShape(12.dp)), verticalAlignment = Alignment.CenterVertically) {
                    if (state.selectionMode) Checkbox(selected, onCheckedChange = { state.toggle(track) }, enabled = state.usable && !state.busy)
                    Column(Modifier.weight(1f)) {
                        TrackRow(track, coverUrl(track.coverId ?: track.album?.coverId, 120), onClick = {
                            if (state.selectionMode) state.toggle(track)
                            else if (track.isAvailable) state.play(FolderSelection(trackIds = listOf(track.id)), FolderPlaybackMode.Play)
                            else state.message = "此歌曲不可播放，请先重新加入音乐库"
                        }, horizontalPadding = 8.dp, onLongClick = { state.toggle(track) }) {
                            if (!state.selectionMode) Row {
                                IconButton(onClick = { onToggleFavorite(track.copy(isFavorite = favorite)) }, enabled = track.isAvailable && !state.busy) {
                                    Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏", tint = if (favorite) FnAccentIcon else FnTextSecondary)
                                }
                                FolderItemMenu(state, FolderSelection(trackIds = listOf(track.id)), canManage, onPlaylist, onDelete, onInfo = { onTrackInfo(track) })
                            }
                        }
                        if (track.isAdminDeleted) Text("已从音乐库移除", Modifier.padding(start = 64.dp, bottom = 8.dp), color = FnTextSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(name: String, subtitle: String, onClick: () -> Unit, onLongClick: (() -> Unit)?, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
        .padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).background(FnAccent.copy(alpha = .10f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Folder, null, tint = FnAccentIcon)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, color = FnTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        if (trailing != null) trailing() else Icon(Icons.Rounded.ChevronRight, null, tint = FnTextSecondary)
    }
}

private fun FolderBrowserState.filteredSelection(target: FolderSelection, removed: Boolean): FolderSelection {
    val content = content ?: return FolderSelection()
    return FolderSelection(
        content.folders.filter { it.path in target.folderPaths && it.isAdminDeleted == removed }.map { it.path },
        content.tracks.filter { it.id in target.trackIds && if (removed) it.isAdminDeleted else it.isAvailable }.map { it.id },
    )
}

@Composable
private fun FolderItemMenu(state: FolderBrowserState, target: FolderSelection, canManage: Boolean,
    onPlaylist: (FolderSelection) -> Unit, onDelete: (FolderSelection) -> Unit, onInfo: (() -> Unit)? = null) {
    val available = state.filteredSelection(target, removed = false)
    val removed = state.filteredSelection(target, removed = true)
    val enabled = state.usable && !state.busy
    AppBarMenu(buildList {
        if (!available.isEmpty) {
            add(LiquidMenuItem("play", "播放", enabled = enabled))
            add(LiquidMenuItem("next", "下一首播放", enabled = enabled))
            add(LiquidMenuItem("append", "加入播放列表", enabled = enabled))
            add(LiquidMenuItem("playlist", "添加到歌单", enabled = enabled))
            if (canManage) add(LiquidMenuItem("delete", "删除", destructive = true, enabled = enabled))
        }
        if (canManage && !removed.isEmpty) add(LiquidMenuItem("recover", "重新加入音乐库", enabled = enabled))
        if (onInfo != null) add(LiquidMenuItem("info", "歌曲信息", enabled = enabled))
    }, { id -> when (id) {
        "play" -> state.play(available, FolderPlaybackMode.Play)
        "next" -> state.play(available, FolderPlaybackMode.Next)
        "append" -> state.play(available, FolderPlaybackMode.Append)
        "playlist" -> onPlaylist(available)
        "delete" -> onDelete(available)
        "recover" -> state.recover(removed)
        "info" -> onInfo?.invoke()
    } }, showBackground = false) { Icon(Icons.Rounded.MoreVert, "更多操作") }
}

@Composable
private fun FolderSelectionToolbar(state: FolderBrowserState, canManage: Boolean, onPlaylist: (FolderSelection) -> Unit, onDelete: (FolderSelection) -> Unit) {
    val available = state.filteredSelection(state.selection, removed = false)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { state.play(available, FolderPlaybackMode.Play) }, enabled = !available.isEmpty && !state.busy && state.usable) {
            Icon(Icons.Rounded.PlayArrow, null); Text("播放")
        }
        TextButton(onClick = { state.play(available, FolderPlaybackMode.Append) }, enabled = !available.isEmpty && !state.busy && state.usable) { Text("加入队列") }
        TextButton(onClick = { onPlaylist(available) }, enabled = !available.isEmpty && !state.busy && state.usable) { Text("加入歌单") }
        if (!state.selection.isEmpty) FolderItemMenu(state, state.selection, canManage, onPlaylist, onDelete)
    }
}

@Composable
private fun rememberFolderScroll(state: FolderBrowserState, location: String, ready: Boolean, itemCount: Int): LazyListState {
    val list = remember(state, location) { LazyListState() }
    var restored by remember(state, location) { mutableStateOf(false) }
    LaunchedEffect(ready, itemCount) {
        if (ready && !restored) {
            state.scrollPositions[location]?.let { (index, offset) ->
                list.scrollToItem(index.coerceAtMost((itemCount - 1).coerceAtLeast(0)), offset)
            }
            restored = true
        }
    }
    LaunchedEffect(list, restored) {
        if (restored) snapshotFlow { list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset }.collect {
            state.scrollPositions[location] = it
        }
    }
    return list
}

@Composable
private fun FolderError(message: String, retry: () -> Unit, authorize: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.FolderOff, null, Modifier.size(40.dp), tint = FnTextSecondary)
        Spacer(Modifier.height(16.dp))
        Text(message)
        TextButton(onClick = retry) { Text("重新检查") }
        if (authorize != null) TextButton(onClick = authorize) { Text("去授权") }
    }
}

@Composable
private fun FolderPlaylistPicker(api: FolderBrowserActions, state: FolderBrowserState, target: FolderSelection, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var playlists by remember { mutableStateOf<List<Playlist>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf<Playlist?>(null) }
    LaunchedEffect(api, retry) {
        try { playlists = api.playlists(); error = null }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { error = "加载歌单失败，请重试" }
    }
    AlertDialog(onDismissRequest = { if (!state.busy && !creating) onDismiss() }, title = { Text("添加到歌单") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("已选择 ${target.size} 项，包含文件夹内的歌曲")
                if (playlists == null && error == null || creating || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.actionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { error = null; retry++ }) { Text("重试") } }
                LazyColumn(Modifier.heightIn(max = 220.dp)) {
                    items(playlists.orEmpty(), key = { it.id.value }) { playlist ->
                        TextButton(onClick = { state.addToPlaylist(target, playlist, onDismiss) }, enabled = !state.busy && !creating, modifier = Modifier.fillMaxWidth()) {
                            Text(playlist.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (created == null) OutlinedTextField(name, { name = it }, label = { Text("新歌单名称") }, singleLine = true, enabled = !creating && !state.busy)
                TextButton(onClick = {
                    scope.launch {
                        creating = true
                        try {
                            // Keep the created ID if adding fails; retry must not create duplicate playlists.
                            val playlist = created ?: api.createPlaylist(name).also { created = it }
                            state.addToPlaylist(target, playlist, onDismiss)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { error = "创建歌单失败，请确认结果后重试" }
                        finally { creating = false }
                    }
                }, enabled = !creating && !state.busy && (created != null || name.isNotBlank())) {
                    Text(if (created == null) "新建歌单并加入" else "加入刚创建的歌单")
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss, enabled = !creating && !state.busy) { Text("取消") } })
}
