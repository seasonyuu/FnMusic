package com.seasonyuu.fnmusic.feature.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Alignment
import com.seasonyuu.fnmusic.core.designsystem.LiquidToggle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.seasonyuu.fnmusic.core.designsystem.LiquidMenuItem
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun LibraryAdministrationScreen(api: MusicAdministration, onBack: () -> Unit, onCatalogChanged: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onCatalogChanged)
    val state = rememberSaveable(api, saver = listSaver(
        save = { it: LibraryAdministrationState -> it.saveEditorSnapshot() },
        restore = { saved -> LibraryAdministrationState(api, scope, onCatalogChanged = { changed() }).apply { restoreEditorSnapshot(saved) } },
    )) { LibraryAdministrationState(api, scope, onCatalogChanged = { changed() }) }
    val owner = LocalLifecycleOwner.current
    var showTasks by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<MusicFolder?>(null) }
    var rebuild by remember { mutableStateOf(false) }
    var discard by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            state.refresh()
            while (true) { delay(5000); state.refreshTasks() }
        }
    }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); state.message = null } }
    val closeEditor = { if (!state.saving) { if (state.draft?.dirty == true) discard = true else state.closeEditor() } }
    BackHandler(state.pickerOpen) { if (state.directory != null) state.directoryUp() else state.pickerOpen = false }
    BackHandler(!state.pickerOpen && (state.draft != null || state.editingGuid != null)) { closeEditor() }
    val route = when {
        state.pickerOpen && LocalConfiguration.current.screenWidthDp < 840 -> 2
        state.draft != null || state.editingGuid != null -> 1
        else -> 0
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(targetState = route, modifier = Modifier.fillMaxSize(), transitionSpec = {
            val forward = targetState > initialState
            ((fadeIn(tween(240)) + slideInHorizontally(tween(280)) { if (forward) it else -it / 4 }) togetherWith
                (fadeOut(tween(200)) + slideOutHorizontally(tween(280)) { if (forward) -it / 4 else it }))
                .using(SizeTransform(clip = false))
        }, label = "library-page") { page ->
        when (page) {
            2 -> DirectoryPicker(state)
            1 -> LibraryEditor(state, closeEditor)
            else -> LibraryPage("音乐库管理", onBack, actions = {
                AppBarButton(onClick = state::create, enabled = state.folders != null && state.folders.orEmpty().size < 200) { Icon(Icons.Rounded.Add, "添加文件夹") }
                AppBarMenu(listOf(
                    LiquidMenuItem("scan", "扫描全部", enabled = !state.folders.isNullOrEmpty() && "scan-all" !in state.operations && state.pendingScans.isEmpty() && state.tasks.none { it.active } && state.operations.none { it.startsWith("scan:") }),
                    LiquidMenuItem("rebuild", "重建搜索索引", enabled = "rebuild" !in state.operations),
                ), { if (it == "scan") state.scan() else rebuild = true }) { Icon(Icons.Rounded.MoreVert, "更多维护操作") }
            }) {
                PullToRefreshBox(isRefreshing = state.refreshing && state.folders != null, onRefresh = state::reload, modifier = Modifier.fillMaxSize()) {
                    LazyColumn(contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = 12.dp, bottom = 24.dp, includeTopInset = false), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            val active = state.tasks.count { it.active }
                            OutlinedCard(onClick = { showTasks = true }, modifier = Modifier.fillMaxWidth()) {
                                ListItem(headlineContent = { Text(if (active > 0) "$active 项任务进行中" else "扫描任务") },
                                    supportingContent = { Text(if (state.pendingScans.isNotEmpty()) "已提交，等待任务更新" else "查看扫描、元数据与歌词任务") },
                                    leadingContent = { Icon(Icons.Rounded.Sync, null) }, trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) })
                            }
                        }
                        if ("rebuild" in state.operations || "scan-all" in state.operations) item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if ("rebuild" in state.operations) "正在重建搜索索引…" else "正在提交全部扫描…")
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                        state.listError?.let { error -> item { LibraryError(error, state::reload) } }
                        state.taskError?.let { error -> item { LibraryError(error, state::reload) } }
                        state.actionError?.let { error -> item { LibraryError(error) { state.actionError = null; state.reload() } } }
                        if (state.folders == null && state.listError == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                        if (state.folders?.isEmpty() == true) item {
                            Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Icon(Icons.Rounded.FolderOpen, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Text("添加音乐，开启畅听", style = MaterialTheme.typography.titleLarge)
                                Text("选择 NAS 上存放音乐的文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Button(onClick = state::create) { Text("添加文件夹") }
                            }
                        }
                        items(state.folders.orEmpty(), key = { it.guid }) { folder ->
                            val busy = "remove:${folder.guid}" in state.operations || "scan:${folder.guid}" in state.operations
                            val related = state.tasks.filter { it.libraryGuid == folder.guid && it.active }
                            Column(Modifier.fillMaxWidth()) {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                                        .clickable(enabled = !busy) { state.edit(folder.guid) }
                                        .padding(horizontal = 12.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .65f), modifier = Modifier.fillMaxSize())
                                        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.size(22.dp).offset(x = 5.dp, y = 4.dp))
                                    }
                                    Spacer(Modifier.width(16.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                                            Text(folder.displayName, style = MaterialTheme.typography.titleMedium)
                                            if (folder.contentLastChangedAt > 0) Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .07f),
                                            ) {
                                                Text("更新于 ${formatLibraryUpdatedAt(folder.contentLastChangedAt)}",
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        Text(folder.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (folder.accessStatus != 0) Text(
                                            if (folder.accessStatus == null) "访问状态未知" else "不可访问，请检查目录权限或挂载状态",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (folder.accessStatus == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                        )
                                        if (folder.guid in state.pendingScans) Text("已提交，等待任务更新", style = MaterialTheme.typography.labelMedium)
                                        related.forEach { task -> Text("${task.phaseName} · ${task.statusText}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    AppBarMenu(listOf(
                                        LiquidMenuItem("scan", "扫描此文件夹", enabled = !busy && related.isEmpty() && folder.guid !in state.pendingScans && "scan-all" !in state.operations),
                                        LiquidMenuItem("remove", "移除文件夹", enabled = !busy, destructive = true),
                                    ), { if (it == "scan") state.scan(folder.guid) else deleting = folder }, showBackground = false) { Icon(Icons.Rounded.MoreVert, "${folder.displayName} 的操作") }
                                }
                                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(
            bottom = maxOf(LocalBottomOverlayPadding.current, WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()) + 12.dp,
        ).imePadding())
    }
    if (state.pickerOpen && LocalConfiguration.current.screenWidthDp >= 840) Dialog(onDismissRequest = { state.pickerOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 1000.dp).fillMaxWidth(.9f).fillMaxHeight(.85f), shape = MaterialTheme.shapes.extraLarge) { DirectoryPicker(state, wide = true) }
    }
    if (showTasks) ModalBottomSheet(onDismissRequest = { showTasks = false }) {
        Text("扫描任务", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.taskError?.let { item { LibraryError(it, state::reload) } }
            state.actionError?.let { item { LibraryError(it, state::reload) } }
            if (state.tasks.isEmpty()) item { Text(if (state.pendingScans.isEmpty()) "暂无扫描任务" else "已提交，等待任务更新") }
            state.tasks.groupBy { it.libraryGuid }.forEach { (guid, tasks) ->
                item(key = "group:$guid") { Text(state.folders.orEmpty().find { it.guid == guid }?.displayName ?: "其他音乐库任务", style = MaterialTheme.typography.titleMedium) }
                itemsIndexed(tasks, key = { index, task -> "task:$guid:${task.id}:$index" }) { _, task ->
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(task.phaseName, style = MaterialTheme.typography.titleSmall)
                        Text(task.statusText, style = MaterialTheme.typography.bodyMedium)
                        if (task.active) { val progress = task.progress; if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth()) }
                        if (task.canceledCount > 0) Text("取消 ${task.canceledCount} 项", style = MaterialTheme.typography.bodySmall)
                        Row {
                            if (task.active && !task.cancelling) TextButton(colors = readableTextButtonColors(), onClick = { state.cancel(task) }, enabled = task.id.isNotBlank() && "task:${task.id}" !in state.operations) { Text("取消任务") }
                            if (task.done && !task.cancelling && task.retryable) TextButton(colors = readableTextButtonColors(), onClick = { state.retry(task) }, enabled = task.id.isNotBlank() && "task:${task.id}" !in state.operations) { Text("重试失败任务") }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
    deleting?.let { folder -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("移除音乐文件夹？") },
        text = { Text("${folder.displayName}\n${folder.path}\n\n只移除曲库配置，不会删除原始音乐文件。") },
        confirmButton = { TextButton(colors = readableTextButtonColors(), onClick = { deleting = null; state.remove(folder) }) { Text("移除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(colors = readableTextButtonColors(), onClick = { deleting = null }) { Text("取消") } }) }
    if (rebuild) AlertDialog(onDismissRequest = { rebuild = false }, title = { Text("重建搜索索引？") }, text = { Text("重新建立歌曲、专辑和艺术家的搜索索引，可能需要一些时间。") },
        confirmButton = { TextButton(colors = readableTextButtonColors(), onClick = { rebuild = false; state.rebuild() }) { Text("重建") } }, dismissButton = { TextButton(colors = readableTextButtonColors(), onClick = { rebuild = false }) { Text("取消") } })
    if (discard) AlertDialog(onDismissRequest = { discard = false }, title = { Text("放弃未保存的修改？") },
        confirmButton = { TextButton(colors = readableTextButtonColors(), onClick = { discard = false; state.closeEditor() }) { Text("放弃修改") } }, dismissButton = { TextButton(colors = readableTextButtonColors(), onClick = { discard = false }) { Text("继续编辑") } })
}

@Composable
private fun LibraryPage(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Box(Modifier.padding(horizontal = 20.dp)) { PageTitle(title, onBack, actions = actions) }
        content()
    }
}

@Composable
private fun LibraryError(message: String, retry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) { Text(message, color = MaterialTheme.colorScheme.onErrorContainer); TextButton(colors = readableTextButtonColors(), onClick = retry) { Text("重新检查") } }
    }
}

private fun metadataLabel(value: String) = when (value) { "cloud_preferred" -> "优先使用线上数据"; "local_only" -> "仅使用本地数据"; "local_preferred" -> "优先使用本地数据（兼容设置）"; else -> "未知元数据策略" }

@Composable
private fun LibraryEditor(state: LibraryAdministrationState, onBack: () -> Unit) {
    var lastDraft by remember { mutableStateOf(state.draft) }
    SideEffect { state.draft?.let { lastDraft = it } }
    val isEditing = state.editingGuid != null || lastDraft?.original != null
    LibraryPage(if (isEditing) "音乐文件夹设置" else "添加音乐文件夹", onBack) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (state.editLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.editError?.let { LibraryError(it) { state.editingGuid?.takeIf { state.draft == null }?.let(state::edit) ?: state.reload() } }
            (state.draft ?: lastDraft)?.let { draft ->
                Text("音乐文件夹", style = MaterialTheme.typography.titleMedium)
                OutlinedCard(onClick = { state.pickerOpen = true; if (state.roots == null) state.browse(null) }, enabled = !state.saving) {
                    ListItem(headlineContent = { Text(draft.path.ifBlank { "请选择音乐文件夹" }) }, leadingContent = { Icon(Icons.Rounded.FolderOpen, null) }, trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) })
                }
                Text("元数据设置", style = MaterialTheme.typography.titleMedium)
                if (draft.metadata !in setOf("cloud_preferred", "local_only", "local_preferred")) Text("当前策略无法识别，请选择下方选项后保存。", color = MaterialTheme.colorScheme.error)
                val modes = listOf("cloud_preferred", "local_only") + if (draft.original?.metadataPreference == "local_preferred") listOf("local_preferred") else emptyList()
                modes.forEach { value ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).selectable(selected = draft.metadata == value, enabled = !state.saving, role = Role.RadioButton) { state.draft = draft.copy(metadata = value) }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(draft.metadata == value, onClick = null, enabled = !state.saving)
                        Spacer(Modifier.width(12.dp))
                        Column { Text(metadataLabel(value)); if (value == "cloud_preferred") Text("本地数据仅作为识别参考", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (draft.metadata != "local_only") {
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text("自动下载歌词"); Text("对无内置歌词或外挂歌词文件的歌曲，自动下载歌词", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        LiquidToggle(draft.lyrics, { state.draft = draft.copy(lyrics = it) }, enabled = !state.saving)
                    }
                }
            }
        }
        Button(onClick = state::save, enabled = state.draft?.valid == true && state.folders != null && !state.saving,
            modifier = Modifier.fillMaxWidth().padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 12.dp, bottom = 20.dp, includeTopInset = false))) {
            Text(if (state.saving) "正在保存…" else if (isEditing) "保存修改" else "添加文件夹")
        }
    }
}

private data class DirectoryFrame(
    val path: String?,
    val roots: List<AuthorizedMusicDirectory>?,
    val children: List<MusicDirectory>,
    val loading: Boolean,
    val error: String?,
    val selectable: Boolean,
)

@Composable
private fun DirectoryPicker(state: LibraryAdministrationState, wide: Boolean = false) {
    val scrollPositions = state.directoryScrollPositions
    val frame = DirectoryFrame(state.directory, state.roots, state.children, state.directoryLoading, state.directoryError, state.canSelectDirectory)
    AnimatedContent(frame, contentKey = { it.path }, modifier = Modifier.fillMaxSize(), transitionSpec = {
        val forward = targetState.path.orEmpty().length > initialState.path.orEmpty().length
        ((fadeIn(tween(180)) + slideInHorizontally(tween(240)) { if (forward) it / 5 else -it / 5 }) togetherWith
            (fadeOut(tween(140)) + slideOutHorizontally(tween(240)) { if (forward) -it / 5 else it / 5 }))
            .using(SizeTransform(clip = true))
    }, label = "library-directory") { directory ->
        val pathKey = directory.path.orEmpty()
        val position = scrollPositions[pathKey] ?: (0 to 0)
        val listState = rememberLazyListState(position.first, position.second)
        DisposableEffect(pathKey) { onDispose { scrollPositions[pathKey] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset } }
        LibraryPage("选择音乐文件夹", { state.pickerOpen = false }) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(colors = readableTextButtonColors(), onClick = { state.browse(null) }) { Text("授权位置") }
                val parts = directory.path?.trim('/')?.split('/').orEmpty()
                parts.forEachIndexed { index, label ->
                    Text("/")
                    val target = "/" + parts.take(index + 1).joinToString("/")
                    val authorized = directory.roots.orEmpty().any { normalizedLibraryPath(target) == normalizedLibraryPath(it.path) || normalizedLibraryPath(target).startsWith(normalizedLibraryPath(it.path) + "/") }
                    TextButton(colors = readableTextButtonColors(), onClick = { state.browse(target) }, enabled = authorized) { Text(label) }
                }
            }
            Row(Modifier.weight(1f)) {
                if (wide) LazyColumn(Modifier.width(230.dp), contentPadding = PaddingValues(12.dp)) {
                    directory.roots.orEmpty().groupBy { directoryGroup(it.path) }.forEach { (group, roots) ->
                        item { Text(group, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp)) }
                        items(roots, key = { it.path }) { root -> TextButton(colors = readableTextButtonColors(), onClick = { state.browse(root.path) }) { Text(root.name.ifBlank { root.path.trimEnd('/').substringAfterLast('/') }) } }
                    }
                }
                LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (directory.path != null) item { TextButton(colors = readableTextButtonColors(), onClick = state::directoryUp) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null); Text("上一级") } }
                    if (directory.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    directory.error?.let { item { LibraryError(it) { state.browse(directory.path) } } }
                    if (!directory.loading && directory.error == null) {
                        if (directory.path == null) {
                            if (directory.roots?.isEmpty() == true) item {
                                Text("请前往 NAS 系统设置 → 应用 → 音乐，设置允许访问的文件夹。")
                                TextButton(colors = readableTextButtonColors(), onClick = { state.browse(null) }) { Text("重新检查") }
                            }
                            directory.roots.orEmpty().groupBy { directoryGroup(it.path) }.forEach { (group, roots) ->
                                item { Text(group, style = MaterialTheme.typography.titleSmall) }
                                items(roots, key = { it.path }) { root -> DirectoryRow(root.path, root.name.ifBlank { root.path.trimEnd('/').substringAfterLast('/') }, state) }
                            }
                        } else {
                            if (directory.children.isEmpty()) item { Text("此文件夹没有子文件夹", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(directory.children, key = { it.path }) { child -> DirectoryRow(child.path, child.name, state) }
                        }
                    }
                }
            }
            Column(Modifier.padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 12.dp, bottom = 20.dp, includeTopInset = false)), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                directory.path?.let { path -> Text(path, style = MaterialTheme.typography.bodySmall) }
                if (directory.path != null && !directory.selectable && directory.error == null && !directory.loading) Text("此位置不可选择：已添加或属于存储根目录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = state::chooseDirectory, enabled = directory.selectable, modifier = Modifier.fillMaxWidth()) { Text("选择此文件夹") }
            }
        }
    }
}

@Composable
private fun DirectoryRow(path: String, name: String, state: LibraryAdministrationState) {
    val exists = state.existingPaths.any { normalizedLibraryPath(it) == normalizedLibraryPath(path) }
    ListItem(modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { state.browse(path) }, headlineContent = { Text(name) },
        supportingContent = if (exists) { { Text("已添加 · 可继续浏览子文件夹") } } else null,
        leadingContent = { Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "浏览子文件夹") })
}

private fun formatLibraryUpdatedAt(value: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(if (value > 1_000_000_000_000) value else value * 1000))
