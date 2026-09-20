package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

internal fun lyricBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.2f MiB", bytes / (1024.0 * 1024))
}

@Composable
internal fun AmllSettingsScreen(actions: LyricsActions, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val enabled by actions.amllEnabled.collectAsState(initial = false)
    var saving by remember { mutableStateOf(false) }
    val controlsEnabled = enabled && !saving
    val index by actions.index.collectAsState()
    val usage by actions.cacheUsage.collectAsState()
    val scope = rememberCoroutineScope()
    var clearing by remember { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    val backdrop = LocalAppBarBackdrop.current ?: rememberLayerBackdrop()
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(controlsEnabled) {
        if (!controlsEnabled) { sourceMenuExpanded = false; confirm = false }
    }
    CollectionPage(title = "AMLL 设置", onBack = onBack, headingGone = { true }) { _, contentTop ->
        Column(Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState())
            .padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = contentTop, bottom = 20.dp, includeTopInset = false)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LyricsSettingsGroup {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("从 AMLL 获取歌词", style = MaterialTheme.typography.titleMedium)
                        Text(if (enabled) "优先匹配逐词歌词" else "已关闭，使用飞牛歌词", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                    }
                    CompositionLocalProvider(LocalFnBackdrop provides null) {
                        LiquidToggle(checked = enabled, onCheckedChange = { value ->
                            if (!saving) {
                                saving = true
                                scope.launch {
                                    try { actions.setAmllEnabled(value); error = null }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { error = "设置保存失败，请重试" }
                                    finally { saving = false }
                                }
                            }
                        }, modifier = Modifier.testTag("lyrics-amll-toggle").semantics { contentDescription = "从 AMLL 获取歌词" })
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().alpha(if (controlsEnabled) 1f else 0.38f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("获取源", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "切换后自动更新索引",
                        style = MaterialTheme.typography.bodySmall,
                        color = FnTextSecondary,
                    )
                }
                LiquidMenu(
                    expanded = sourceMenuExpanded && controlsEnabled,
                    onDismissRequest = { sourceMenuExpanded = false },
                    onExpandedChange = { sourceMenuExpanded = it && controlsEnabled },
                    backdrop = backdrop,
                    transition = LiquidMenuTransition.Attached,
                    items = LyricsFetchSource.entries.map { source ->
                        LiquidMenuItem(source.name, source.label, selected = source == index.source, enabled = controlsEnabled && !switching)
                    },
                    onSelect = { id ->
                        val source = LyricsFetchSource.valueOf(id)
                        switching = true
                        scope.launch {
                            try { actions.setSource(source); error = null }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "获取源保存失败，请重试" }
                            finally { switching = false }
                        }
                    },
                    trigger = { toggle ->
                        AppBarButton(toggle, enabled = controlsEnabled && !switching, menuAnchor = this, modifier = Modifier.testTag("lyrics-source-menu")) {
                            Text(index.source.label)
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Rounded.ExpandMore, contentDescription = "切换歌词获取源")
                        }
                    },
                )
            }
            LyricsSettingsGroup(enabled = controlsEnabled) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("搜索索引", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(colors = readableOutlinedButtonColors(), enabled = controlsEnabled && !updating && !switching && index.status != LyricsIndexStatus.Checking, onClick = {
                        updating = true
                        scope.launch {
                            try { actions.updateIndex(); error = null }
                            catch (e: CancellationException) { throw e }
                            catch (_: Exception) { error = "索引更新失败，请重试" }
                            finally { updating = false }
                        }
                    }, modifier = Modifier.testTag("lyrics-index-update")) {
                        Text(if (index.status == LyricsIndexStatus.Failed) "重试" else "检查更新")
                    }
                }
                val statusColor = when (index.status) {
                    LyricsIndexStatus.Failed -> MaterialTheme.colorScheme.error
                    LyricsIndexStatus.Current, LyricsIndexStatus.Updated -> Color(0xFF79C99E)
                    else -> FnTextSecondary
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (index.status == LyricsIndexStatus.Checking) {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = FnTextSecondary)
                    } else {
                        Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                    }
                    Text(when (index.status) {
                        LyricsIndexStatus.Missing -> "尚未下载索引"
                        LyricsIndexStatus.Checking -> "正在检查更新…"
                        LyricsIndexStatus.Current -> "已是最新"
                        LyricsIndexStatus.Updated -> "索引已更新"
                        LyricsIndexStatus.Failed -> "索引更新失败"
                    }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("lyrics-index-status"))
                }
                Text(index.checkedAt?.let { "上次检查于${lyricCheckTime(it)}" } ?: "尚未检查",
                    style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                if (index.status == LyricsIndexStatus.Failed) {
                    Text(if (index.version != null) "仍可使用已保存索引" else "暂时无法搜索歌词",
                        style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                    index.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    Text("可重试或切换获取源", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                }
                Spacer(Modifier.height(4.dp))
                LyricsIndexDetail("版本", index.version ?: "未下载", "lyrics-index-version", monospace = true)
                LyricsIndexDetail("文件大小", lyricBytes(index.bytes), "lyrics-index-size")
                Text("更新状态以当前获取源为准", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary,
                    modifier = Modifier.padding(top = 4.dp))
            }
            LyricsSettingsGroup(enabled = controlsEnabled) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("歌词缓存", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(colors = readableOutlinedButtonColors(), enabled = controlsEnabled && !clearing && (usage.count > 0 || usage.bytes > 0),
                        onClick = { confirm = true }, modifier = Modifier.testTag("lyrics-cache-clear")) {
                        Text(if (clearing) "正在清除…" else "清除缓存")
                    }
                }
                Text(if (usage.count == 0 && usage.bytes == 0L) "暂无缓存" else lyricBytes(usage.bytes),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Medium,
                    modifier = Modifier.testTag("lyrics-cache-usage"))
                Text("已缓存 ${usage.count} 份歌词", style = MaterialTheme.typography.bodyMedium, color = FnTextSecondary)
                Text("清除后会在需要时重新下载", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary,
                    modifier = Modifier.padding(top = 4.dp))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("关于 AMLL", style = MaterialTheme.typography.titleSmall, color = FnTextSecondary)
                Text(
                    "AMLL TTML DB 是由社区共同维护的逐词歌词库。FnMusic 使用其中的歌词时间轴与翻译，为播放提供逐词歌词显示。感谢歌词作者与项目贡献者。",
                    style = MaterialTheme.typography.bodySmall,
                    color = FnTextSecondary,
                )
                OutlinedButton(colors = readableOutlinedButtonColors(), onClick = {
                    try { uriHandler.openUri("https://github.com/amll-dev/amll-ttml-db") }
                    catch (_: Exception) { error = "无法打开浏览器，请稍后重试" }
                }, modifier = Modifier.testTag("lyrics-amll-project-link")) {
                    Text("访问 GitHub 仓库")
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text("将在浏览器中打开，可了解项目、浏览歌词或参与贡献。", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("清除歌词缓存？") },
        text = { Text("清除所有获取源的歌词副本，不包含搜索索引和音频缓存。歌曲选择与时间偏移将保留，当前歌词和音乐播放不受影响。需要时会重新下载歌词。") },
        confirmButton = { TextButton(colors = readableTextButtonColors(), onClick = {
            confirm = false; clearing = true
            scope.launch {
                try { actions.clearCache(); error = null }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "部分缓存未能清除，请重试" }
                finally { clearing = false }
            }
        }) { Text("清除") } }, dismissButton = { TextButton(colors = readableTextButtonColors(), onClick = { confirm = false }) { Text("取消") } })
}

@Composable
private fun LyricsSettingsGroup(enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.38f).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f), RoundedCornerShape(20.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun LyricsIndexDetail(label: String, value: String, tag: String, monospace: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
        Text(value, Modifier.testTag(tag), style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default)
    }
}

private fun lyricCheckTime(timestamp: Long): String {
    val checked = Calendar.getInstance().apply { timeInMillis = timestamp }
    val now = Calendar.getInstance()
    val today = checked.get(Calendar.YEAR) == now.get(Calendar.YEAR) && checked.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(timestamp))
    return if (today) "今天 $time" else "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))} $time"
}

@Composable
internal fun LyricsSettingsScreen(actions: LyricsActions, onBack: () -> Unit, onAmll: () -> Unit) {
    val enabled by actions.amllEnabled.collectAsState(initial = false)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Box(Modifier.padding(horizontal = 20.dp)) { PageTitle("歌词", onBack) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 12.dp, bottom = 20.dp, includeTopInset = false))) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f))
                    .clickable(onClick = onAmll).testTag("lyrics-amll-entry")
                    .padding(16.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AMLL 设置", style = MaterialTheme.typography.titleMedium)
                    Text(if (enabled) "已开启 · 获取源、搜索索引与缓存" else "已关闭 · 获取源、搜索索引与缓存",
                        style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = FnTextSecondary)
            }
        }
    }
}
