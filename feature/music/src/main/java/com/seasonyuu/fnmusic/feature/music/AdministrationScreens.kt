package com.seasonyuu.fnmusic.feature.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
private fun AdminPage(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val pullState = rememberPullToRefreshState()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Box(Modifier.padding(horizontal = 20.dp)) { PageTitle(title, onBack, actions = actions) }
        if (onRefresh == null) {
            Column(
                Modifier.weight(1f).verticalScroll(scrollState)
                    .padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 12.dp, bottom = 20.dp, includeTopInset = false)),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                content = content,
            )
        } else {
            PullToRefreshBox(
                modifier = Modifier.weight(1f),
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = pullState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    )
                },
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(scrollState)
                        .padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 12.dp, bottom = 20.dp, includeTopInset = false)),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun LibraryAdministrationScreen(api: MusicAdministration, onBack: () -> Unit) {
    var folders by remember { mutableStateOf<List<MusicFolder>?>(null) }
    var tasks by remember { mutableStateOf<List<MusicScanTask>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<MusicFolder?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun reload() { folders = api.folders(); tasks = api.scanTasks() }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block(); error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "操作失败，请重试" }
            finally { busy = false }
        }
    }
    LaunchedEffect(api) { try { reload() } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "加载失败" } }
    LaunchedEffect(api, editing) {
        if (editing == null) while (true) {
            kotlinx.coroutines.delay(5000)
            try { tasks = api.scanTasks() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "任务状态更新失败" }
        }
    }
    BackHandler(editing != null) { if (!busy) editing = null }
    val original = folders?.firstOrNull { it.guid == editing }
    if (editing != null && folders != null) {
        if (!editing.isNullOrEmpty() && original == null) {
            AdminPage("音乐库管理", { editing = null }) { Text("这个音乐文件夹已不可用，请返回刷新。") }
        } else key(editing) {
            var path by rememberSaveable { mutableStateOf(original?.path.orEmpty()) }
            var metadata by rememberSaveable { mutableStateOf(original?.metadataPreference ?: "cloud_preferred") }
            var lyrics by rememberSaveable { mutableStateOf(original?.autoDownloadLyric ?: false) }
            AdminPage("音乐文件夹设置", { if (!busy) editing = null }) {
                OutlinedTextField(path, { path = it }, label = { Text("音乐文件夹路径") }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = readableTextFieldColors())
                Text("元数据刮削偏好", style = MaterialTheme.typography.titleMedium)
                listOf("cloud_preferred" to "优先使用线上数据", "local_only" to "仅使用本地数据").let { modes ->
                    (if (metadata == "local_preferred") modes + ("local_preferred" to "优先使用本地数据") else modes).forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(metadata == value, { metadata = value }, enabled = !busy); Text(label) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("自动下载歌词", Modifier.weight(1f)); Switch(lyrics, { lyrics = it }, enabled = !busy) }
                Text("为没有内置歌词或外挂歌词文件的歌曲自动获取歌词。")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { action { api.saveFolder(original, path, metadata, lyrics); editing = null; reload() } }, enabled = !busy && path.isNotBlank()) { Text("保存修改") }
            }
        }
    } else AdminPage("音乐库管理", onBack) {
        Button(onClick = { editing = ""; error = null }, enabled = !busy && folders != null) { Text("添加音乐文件夹") }
        folders?.forEach { folder ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(folder.name.ifBlank { folder.path.trimEnd('/').substringAfterLast('/').ifBlank { folder.path } }, style = MaterialTheme.typography.titleMedium)
                    Text(folder.path)
                    if (folder.contentLastChangedAt > 0) Text("最近更新：${formatAdminTime(folder.contentLastChangedAt)}")
                    Row {
                        TextButton(colors = readableTextButtonColors(), onClick = { editing = folder.guid; error = null }, enabled = !busy) { Text("编辑") }
                        TextButton(colors = readableTextButtonColors(), onClick = { action { api.scanFolder(folder.guid); message = "已提交扫描任务"; reload() } }, enabled = !busy) { Text("扫描音乐库") }
                        TextButton(colors = readableTextButtonColors(), onClick = { deleting = folder }, enabled = !busy) { Text("移除") }
                    }
                }
            }
        }
        if (folders?.isEmpty() == true) Text("尚未添加音乐文件夹")
        if (folders == null && error == null) CircularProgressIndicator()
        tasks.forEach { task ->
            Text("${task.name.ifBlank { "音乐库扫描" }}：" + when {
                task.canceled -> "已取消"
                task.done && task.failed > 0 -> "已结束，${task.failed} 项失败"
                task.done -> "已完成"
                task.total > 0 -> "${task.completed + task.failed} / ${task.total}"
                else -> "正在扫描"
            })
        }
        message?.let { Text(it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(colors = readableTextButtonColors(), onClick = { action { reload() } }, enabled = !busy) { Text("刷新") }
    }
    deleting?.let { folder -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("移除音乐文件夹？") }, text = { Text("从音乐库移除 ${folder.name.ifBlank { folder.path.trimEnd('/').substringAfterLast('/').ifBlank { folder.path } }}，不会删除原始音乐文件。") }, confirmButton = { TextButton(colors = readableTextButtonColors(), onClick = { deleting = null; action { api.removeFolder(folder.guid); reload() } }) { Text("移除") } }, dismissButton = { TextButton(colors = readableTextButtonColors(), onClick = { deleting = null }) { Text("取消") } }) }
}

@Composable
private fun FolderAccessEditor(value: FolderAccess, folders: List<MusicFolder>, enabled: Boolean, onChange: (FolderAccess) -> Unit) {
    Text("音乐文件夹权限", style = MaterialTheme.typography.titleMedium)
    listOf("all" to "允许访问全部（包含将来新增的文件夹）", "partial" to "仅允许所选文件夹", "none" to "不允许访问").forEach { (mode, label) ->
        Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(value.mode == mode, { onChange(value.copy(mode = mode)) }, enabled = enabled); Text(label) }
    }
    if (value.mode == "partial") folders.forEach { folder ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(folder.guid in value.guids, { selected -> onChange(value.copy(guids = if (selected) (value.guids + folder.guid).distinct() else value.guids - folder.guid)) }, enabled = enabled)
            Text(folder.name.ifBlank { folder.path.trimEnd('/').substringAfterLast('/').ifBlank { folder.path } })
        }
    }
}

@Composable
internal fun UserAdministrationScreen(api: MusicAdministration, currentUserId: String?, onBack: () -> Unit) {
    var users by remember { mutableStateOf<List<ManagedMusicUser>?>(null) }
    var folders by remember { mutableStateOf<List<MusicFolder>>(emptyList()) }
    var defaults by remember { mutableStateOf(FolderAccess()) }
    var error by remember { mutableStateOf<String?>(null) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun reload() {
        val nextFolders = api.folders()
        val nextDefaults = api.defaultAccess()
        val nextUsers = api.users()
        folders = nextFolders; defaults = nextDefaults; users = nextUsers
    }
    fun action(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { block(); error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "操作失败，请重试" }
            finally { busy = false }
        }
    }
    fun refreshUsers() {
        if (busy || refreshing) return
        refreshing = true
        scope.launch {
            try { reload(); error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "刷新失败，请重试" }
            finally { refreshing = false }
        }
    }
    LaunchedEffect(api) { try { reload() } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "加载失败" } }
    BackHandler(editing != null) { if (!busy) editing = null }
    val editorVisible = editing != null && users != null
    AnimatedContent(
        targetState = editorVisible,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState) {
                (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { it / 4 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 4 })
            } else {
                (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { -it / 4 }) togetherWith
                    (fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 4 })
            }
        },
        label = "user-administration-page",
    ) { showEditor ->
        if (showEditor) key(editing) {
            val original = users?.firstOrNull { it.guid == editing }
            val defaultEditor = editing == "defaults"
            if (!editing.isNullOrEmpty() && !defaultEditor && original == null) {
                AdminPage("用户管理", { editing = null }) { Text("这个用户已不可用，请返回刷新。") }
            } else {
                val initial = if (defaultEditor || original == null) defaults else original.access
                var username by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
                var password by remember { mutableStateOf("") }
                var confirmation by remember { mutableStateOf("") }
                var visible by remember { mutableStateOf(false) }
                var mode by rememberSaveable { mutableStateOf(initial.mode) }
                var ids by rememberSaveable { mutableStateOf(ArrayList(initial.guids)) }
                val access = FolderAccess(mode, ids)
                AdminPage(if (defaultEditor) "新用户默认权限" else if (original == null) "新增用户" else "编辑用户", { if (!busy) editing = null }) {
                    if (!defaultEditor) {
                        OutlinedTextField(username, { username = it }, label = { Text("用户名") }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = readableTextFieldColors())
                        Text(if (original == null) "设置用户密码" else "修改密码（留空则保留）")
                        OutlinedTextField(password, { password = it }, label = { Text("新密码") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), trailingIcon = { TextButton(colors = readableTextButtonColors(), onClick = { visible = !visible }) { Text(if (visible) "隐藏" else "显示") } })
                        OutlinedTextField(confirmation, { confirmation = it }, label = { Text("确认密码") }, enabled = !busy, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(), colors = readableTextFieldColors())
                        if (original?.guid == currentUserId && password.isNotEmpty()) Text("修改自己的密码后将返回登录页。")
                    }
                    FolderAccessEditor(access, folders, !busy) { mode = it.mode; ids = ArrayList(it.guids) }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { action {
                        if (defaultEditor) api.saveDefaultAccess(access) else api.saveUser(original, username, password.takeIf { it.isNotEmpty() }, access)
                        password = ""; confirmation = ""; editing = null; reload()
                    } }, enabled = !busy && (defaultEditor || username.isNotBlank() && password == confirmation && (original != null || password.isNotEmpty()))) { Text("保存修改") }
                }
            }
        } else {
            AdminPage(
                title = "用户管理",
                onBack = onBack,
                actions = {
                    AppBarButton(
                        onClick = { if (users != null && !busy) { editing = ""; error = null } },
                        enabled = users != null && !busy,
                    ) { Icon(Icons.Rounded.Add, "新增用户") }
                    AppBarButton(
                        onClick = { if (users != null && !busy) { editing = "defaults"; error = null } },
                        enabled = users != null && !busy,
                    ) { Icon(Icons.Rounded.Settings, "权限管理") }
                },
                isRefreshing = refreshing,
                onRefresh = { refreshUsers() },
            ) {
                users?.forEach { user ->
                    OutlinedCard(onClick = { editing = user.guid; error = null }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            UserTitle(user.name, user.role, titleStyle = MaterialTheme.typography.titleMedium)
                            UserSubtitle(when (user.access.mode) {
                                "all" -> "音乐文件夹权限 · 全部文件夹"
                                "partial" -> "音乐文件夹权限 · ${user.access.guids.size} 个文件夹"
                                else -> "音乐文件夹权限 · 无权限"
                            })
                            user.lastAccessedAt?.takeIf { it > 0 }?.let {
                                Text("最近访问 · ${formatAdminTime(it)}", color = FnTextSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (users == null && error == null) CircularProgressIndicator()
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
internal fun ServerAdministrationScreen(api: MusicAdministration, onBack: () -> Unit) {
    var settings by remember { mutableStateOf<MusicServerSettings?>(null) }
    var name by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun load() { settings = api.serverSettings(); if (name == null) name = settings?.name }
    LaunchedEffect(api) { try { load() } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "加载失败" } }
    AdminPage("服务器设置", onBack) {
        if (settings != null) {
            OutlinedTextField(name.orEmpty(), { name = it; saved = false }, label = { Text("服务器名称") }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = readableTextFieldColors())
            Button(onClick = { busy = true; scope.launch {
                try { api.saveServerSettings(settings!!.copy(name = name!!.trim())); saved = true; error = null }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: "保存失败" }
                finally { busy = false }
            } }, enabled = !busy && !name.isNullOrBlank()) { Text("保存修改") }
            if (saved) Text("已保存")
        } else if (error == null) CircularProgressIndicator()
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (settings == null) TextButton(colors = readableTextButtonColors(), onClick = { scope.launch { try { load(); error = null } catch (cancelled: CancellationException) { throw cancelled } catch (failure: Exception) { error = failure.message ?: "加载失败" } } }) { Text("重试") }
    }
}

private fun formatAdminTime(value: Long): String = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(if (value > 1_000_000_000_000) value else value * 1000))
