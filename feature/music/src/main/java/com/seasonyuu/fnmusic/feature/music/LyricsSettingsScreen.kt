package com.seasonyuu.fnmusic.feature.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
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
internal fun AmllSettingsContent(actions: LyricsActions, cacheContent: @Composable () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val enabled by actions.amllEnabled.collectAsState(initial = false)
    var saving by remember { mutableStateOf(false) }
    val controlsEnabled = enabled && !saving
    val index by actions.index.collectAsState()
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    val backdrop = LocalAppBarBackdrop.current ?: rememberLayerBackdrop()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(controlsEnabled) {
        if (!controlsEnabled) { sourceMenuExpanded = false }
    }
    Column(Modifier.fillMaxWidth()) {
        LyricsSettingsGroup {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("从 AMLL 获取歌词", style = MaterialTheme.typography.titleMedium)
                    Text(if (enabled) "在线来源无逐词歌词时自动匹配" else "已关闭自动匹配", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
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
            Text("此开关仅控制自动获取，手动绑定不受影响。", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
        }
        LyricsSettingsDetails(visible = enabled) {
            Row(
                Modifier.fillMaxWidth(),
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
            LyricsSettingsGroup() {
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
        }
    }
    cacheContent()
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

/** Keep the section gap inside the height animation so collapsing leaves no empty space. */
@Composable
internal fun LyricsSettingsDetails(visible: Boolean, content: @Composable ColumnScope.() -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(tween(260, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun LyricsSettingsGroup(enabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
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
internal fun LyricsSettingsScreen(onBack: () -> Unit, onOnline: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Box(Modifier.padding(horizontal = 20.dp)) { PageTitle("歌词", onBack) }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 12.dp, bottom = 20.dp, includeTopInset = false))) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.035f))
                .clickable(onClick = onOnline).testTag("lyrics-online-entry").padding(16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("在线歌词搜索", style = MaterialTheme.typography.titleMedium)
                    Text("利用在线资源搜索逐字歌词，达到更优秀的体验",
                        style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                }
                Icon(Icons.Rounded.ChevronRight, null, tint = FnTextSecondary)
            }
        }
    }
}
