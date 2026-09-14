package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.AppearancePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AppearanceSettingsScreen(value: AppearancePreference, onChange: suspend (AppearancePreference) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        Box(Modifier.padding(horizontal = 20.dp)) { PageTitle("外观", onBack) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(edgeToEdgeContentPadding(horizontal = 20.dp, top = 12.dp, bottom = 20.dp, includeTopInset = false)), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf(AppearancePreference.Light to "浅色", AppearancePreference.Dark to "深色", AppearancePreference.System to "跟随系统").forEach { (mode, title) ->
                OutlinedCard(onClick = {
                saving = true
                scope.launch {
                    try { onChange(mode); error = false }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                    finally { saving = false }
                }
            }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    RadioButton(selected = mode == value, onClick = null)
                    Text(title, Modifier.padding(start = 12.dp))
                }
            }
                }
            if (error) Text("外观保存失败，请重新选择。", color = MaterialTheme.colorScheme.error)
        }
    }
}
