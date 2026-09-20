package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun OnlineLyricsSettingsScreen(actions: LyricsActions, onBack: () -> Unit) {
    val preference by actions.onlinePreference.collectAsState(OnlineLyricsPreference())
    val usage by actions.onlineCacheUsage.collectAsState()
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun save(value: OnlineLyricsPreference) {
        if (saving) return
        saving = true
        scope.launch {
            try { actions.setOnlinePreference(value); error = null }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "设置保存失败，请重试" }
            finally { saving = false }
        }
    }
    CollectionPage("在线歌词搜索", onBack, headingGone = { true }) { _, top ->
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .verticalScroll(rememberScrollState()).padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = top, bottom = 20.dp, includeTopInset = false)),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LyricsSettingsGroup {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("自动搜索在线歌词", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    CompositionLocalProvider(LocalFnBackdrop provides null) {
                        LiquidToggle(checked = preference.enabled, onCheckedChange = { save(preference.copy(enabled = it)) },
                            modifier = Modifier.testTag("online-lyrics-toggle").semantics { contentDescription = "自动搜索在线歌词" })
                    }
                }
                Text("自动搜索会向所选平台发送歌曲名称和歌手；手动绑定不受此开关影响。", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
            }
            LyricsSettingsGroup(enabled = preference.enabled) {
                Text("歌词来源", style = MaterialTheme.typography.titleMedium)
                OnlineLyricsSource.entries.forEach { source ->
                    val checked = source in preference.sources
                    val enabled = preference.enabled && !saving && !(checked && preference.sources.size == 1)
                    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) {
                        save(preference.copy(sources = if (checked) preference.sources - source else preference.sources + source))
                    }.testTag("online-source-${source.name}").padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(source.label, Modifier.weight(1f))
                        Checkbox(checked, onCheckedChange = null, enabled = enabled)
                    }
                }
                Text("至少选择一个来源。优先采用逐词歌词，同等质量按网易云、QQ、酷狗顺序选择。", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
            }
            LyricsSettingsGroup {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("在线歌词缓存", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { confirm = true }, enabled = !clearing && (usage.count > 0 || usage.bytes > 0), colors = readableOutlinedButtonColors(),
                        modifier = Modifier.testTag("online-cache-clear")) { Text(if (clearing) "正在清除…" else "清除缓存") }
                }
                Text(lyricBytes(usage.bytes), style = MaterialTheme.typography.headlineSmall)
                Text("已缓存 ${usage.count} 份歌词", style = MaterialTheme.typography.bodyMedium, color = FnTextSecondary)
                Text("包含三家来源的歌词与搜索缓存", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("清除在线歌词缓存？") },
        text = { Text("清除网易云、QQ、酷狗的歌词及搜索缓存。保留 AMLL 数据、歌曲绑定和时间偏移，当前歌词与音频缓存不受影响。") },
        confirmButton = { TextButton(onClick = {
            confirm = false; clearing = true
            scope.launch {
                try { actions.clearOnlineCache(); error = null }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "部分缓存未能清除，请重试" }
                finally { clearing = false }
            }
        }, colors = readableTextButtonColors()) { Text("清除") } },
        dismissButton = { TextButton(onClick = { confirm = false }, colors = readableTextButtonColors()) { Text("取消") } })
}
