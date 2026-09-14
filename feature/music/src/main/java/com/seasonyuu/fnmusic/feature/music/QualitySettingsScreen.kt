package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun QualitySettingsScreen(value: StreamingQualityPreference, onChange: suspend (StreamingQualityPreference) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 20.dp, bottom = 20.dp)), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageTitle("音质偏好", onBack)
        listOf(true to "Wi-Fi 播放", false to "移动网络播放").forEach { (wifi, title) ->
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StreamingQuality.entries.forEach { quality ->
                    FilterChip(selected = (if (wifi) value.wifi else value.mobile) == quality, onClick = {
                        busy = true
                        scope.launch {
                            try { onChange(if (wifi) value.copy(wifi = quality) else value.copy(mobile = quality)); error = false }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { error = true }
                            finally { busy = false }
                        }
                    }, enabled = !busy, label = { Text(if (quality == StreamingQuality.Original) "原始音质" else "标准音质") })
                }
            }
        }
        Text("原始音质直接播放原文件。标准音质请求服务器转码为 Opus 128 kbps，需服务器支持；不支持时会提示播放失败。")
        Text("设置用于随后加载的歌曲，不中断当前播放。")
        if (error) Text("音质偏好保存失败，请重试。", color = MaterialTheme.colorScheme.error)
    }
}
