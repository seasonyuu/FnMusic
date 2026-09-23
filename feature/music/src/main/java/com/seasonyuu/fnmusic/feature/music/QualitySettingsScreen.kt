package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary
import com.seasonyuu.fnmusic.core.model.StreamingQuality
import com.seasonyuu.fnmusic.core.model.StreamingQualityPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun QualitySettingsScreen(value: StreamingQualityPreference, onChange: suspend (StreamingQualityPreference) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val options = listOf(StreamingQuality.Original to "原始音质", StreamingQuality.Standard to "标准音质")

    fun select(quality: StreamingQuality, wifi: Boolean) {
        if (busy || (if (wifi) value.wifi else value.mobile) == quality) return
        busy = true
        scope.launch {
            try {
                onChange(if (wifi) value.copy(wifi = quality) else value.copy(mobile = quality))
                error = false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = true
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Box(Modifier.padding(horizontal = 20.dp)) { PageTitle("音质偏好", onBack) }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(
                edgeToEdgeContentPadding(horizontal = 20.dp, top = 20.dp, bottom = 20.dp, includeTopInset = false),
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsCard {
                Text("Wi-Fi 播放", style = MaterialTheme.typography.titleMedium)
                SettingsChoiceGrid(options, value.wifi, !busy) { select(it, wifi = true) }
            }
            SettingsCard {
                Text("移动网络播放", style = MaterialTheme.typography.titleMedium)
                SettingsChoiceGrid(options, value.mobile, !busy) { select(it, wifi = false) }
            }
            SettingsCard {
                Text("音质说明", style = MaterialTheme.typography.titleMedium)
                Text("原始音质直接播放原文件。标准音质由服务器转码为 Opus 128 kbps，需服务器支持；不支持时会提示播放失败。", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                Text("更改会应用到随后加载的歌曲，不中断当前播放。", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
            }
            if (error) Text("音质偏好保存失败，请重试。", color = MaterialTheme.colorScheme.error)
        }
    }
}
