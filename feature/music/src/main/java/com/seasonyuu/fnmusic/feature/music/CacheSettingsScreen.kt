package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.PlaybackCachePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun CacheSettingsScreen(value: PlaybackCachePreference, usage: Pair<Long, Int>, onChange: suspend (PlaybackCachePreference) -> Unit, onClear: suspend () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    fun perform(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); error = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "操作失败，请重试。" }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Box(Modifier.padding(horizontal = 20.dp)) { PageTitle("自动缓存歌曲", onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 12.dp, bottom = 20.dp, includeTopInset = false)), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("自动缓存播放中的歌曲", Modifier.weight(1f))
            Switch(value.enabled, { perform { onChange(value.copy(enabled = it)) } }, enabled = !busy)
        }
        Text("关闭后仍可读取已有缓存。缓存用于临时播放，不是永久下载。")
        Text("缓存容量上限", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(128L, 512L, 1024L, 2048L).forEach { mib ->
                FilterChip(value.bytes == mib * 1024 * 1024, { perform { onChange(value.copy(bytes = mib * 1024 * 1024)) } }, enabled = !busy, label = { Text(if (mib < 1024) "$mib MiB" else "${mib / 1024} GiB") })
            }
        }
        Text("缓存歌曲数上限", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 100, 500, 1000).forEach { count ->
                FilterChip(value.tracks == count, { perform { onChange(value.copy(tracks = count)) } }, enabled = !busy, label = { Text(if (count == 0) "不限制" else "$count 首") })
            }
        }
        Text("已缓存 ${usage.second} 首 · ${usage.first / (1024 * 1024)} MiB")
        Text("修改上限立即生效，优先清理较久未使用的歌曲。播放中的歌曲可能重新缓存。")
        OutlinedButton(colors = readableOutlinedButtonColors(), onClick = { confirm = true }, enabled = !busy) { Text("清除已缓存歌曲") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("清除已缓存歌曲？") }, text = { Text("清除当前账户的临时缓存，当前播放继续。") }, confirmButton = { TextButton(colors = readableTextButtonColors(), onClick = { confirm = false; perform(onClear) }) { Text("清除") } }, dismissButton = { TextButton(colors = readableTextButtonColors(), onClick = { confirm = false }) { Text("取消") } })
}
