package com.seasonyuu.fnmusic.feature.music

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.seasonyuu.fnmusic.core.model.*

@Composable
internal fun CarProjectionDialog(actions: CarProjectionActions, onDismiss: () -> Unit) {
    val state by actions.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val openLink = rememberAboutLinkOpener()
    var showOtherDisplays by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf<ProjectionDisplay?>(null) }
    val displays = if (state.permission == ProjectionPermission.Granted) {
        state.displays.filter { showOtherDisplays || it.carDisplay }
    } else emptyList()
    LaunchedEffect(displays) {
        selected = displays.firstOrNull { it.id == selected?.id && it.connectionToken == selected?.connectionToken }
            ?: displays.firstOrNull { it.available }
    }
    LaunchedEffect(state.launched) { if (state.launched) onDismiss() }
    DisposableEffect(actions, lifecycle) {
        actions.refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) actions.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    fun openShizuku() {
        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent == null) openLink("https://shizuku.rikka.app/zh-hans/guide/setup/")
        else try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            Toast.makeText(context, "无法打开 Shizuku，请从桌面打开", Toast.LENGTH_LONG).show()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text("投屏到车机") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state.permission) {
                    ProjectionPermission.Unavailable -> {
                        Text("请在 Shizuku 中启动服务。首次设置可使用手机无线调试配对。")
                    }
                    ProjectionPermission.Unsupported -> {
                        Text("请升级 Shizuku 至支持 API 13 或更高版本的服务。")
                    }
                    ProjectionPermission.Required -> {
                        Text("投屏需要 Shizuku 授权，请先允许 FnMusic 使用 Shizuku。")
                        TextButton(colors = readableTextButtonColors(), onClick = ::openShizuku) { Text("打开 Shizuku") }
                    }
                    ProjectionPermission.Granted -> Text("Shizuku 已授权", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.permission == ProjectionPermission.Granted) {
                    Text("先连接车机，再选择显示屏。FnMusic 将在所选屏幕上打开。")
                    if (displays.isEmpty()) {
                        Text(if (state.displays.isEmpty()) "尚未发现外接显示屏，请确认车机投屏已连接。"
                            else "未识别到已知车机屏幕，可查看其他显示屏。")
                    }
                    displays.forEach { display ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                                .selectable(
                                    selected = selected == display,
                                    enabled = display.available && !state.busy,
                                    role = Role.RadioButton,
                                    onClick = { selected = display },
                                ).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = selected == display, onClick = null, enabled = display.available && !state.busy)
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(display.description, style = MaterialTheme.typography.titleSmall)
                                Text(display.name, style = MaterialTheme.typography.bodySmall)
                                Text("displayId ${display.id} · ${display.width} × ${display.height}" +
                                    if (display.available) "" else " · 已关闭",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Row {
                        TextButton(colors = readableTextButtonColors(), enabled = !state.busy,
                            onClick = { showOtherDisplays = !showOtherDisplays }) {
                            Text(if (showOtherDisplays) "仅显示已知车机屏幕" else "查看其他显示屏")
                        }
                        TextButton(colors = readableTextButtonColors(), enabled = !state.busy, onClick = actions::refresh) { Text("刷新") }
                    }
                }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (state.busy) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("正在打开 FnMusic…")
                    }
                }
            }
        },
        confirmButton = {
            when (state.permission) {
                ProjectionPermission.Unavailable -> {
                    TextButton(colors = readableTextButtonColors(), enabled = !state.busy, onClick = ::openShizuku) { Text("设置 Shizuku") }
                }
                ProjectionPermission.Unsupported -> {
                    TextButton(colors = readableTextButtonColors(), enabled = !state.busy, onClick = ::openShizuku) { Text("打开 Shizuku") }
                }
                ProjectionPermission.Required -> {
                    TextButton(colors = readableTextButtonColors(), enabled = !state.busy, onClick = actions::authorize) { Text("授权") }
                }
                ProjectionPermission.Granted -> {
                    TextButton(colors = readableTextButtonColors(),
                        enabled = !state.busy && selected?.available == true,
                        onClick = { selected?.let(actions::project) }) { Text("投屏") }
                }
            }
        },
        dismissButton = {
            TextButton(colors = readableTextButtonColors(), enabled = !state.busy, onClick = onDismiss) { Text("取消") }
        },
    )
}
