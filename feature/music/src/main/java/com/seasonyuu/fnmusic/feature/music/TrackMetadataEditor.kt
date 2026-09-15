package com.seasonyuu.fnmusic.feature.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun TrackMetadataEditor(
    track: Track,
    loadOptions: suspend () -> TrackTagOptions,
    save: suspend (Track, TrackMetadataEdit) -> TrackMetadata,
    onSaved: (TrackMetadata) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable(track.id.value) { mutableStateOf(track.title) }
    var album by rememberSaveable(track.id.value) { mutableStateOf(track.album?.name.orEmpty()) }
    var year by rememberSaveable(track.id.value) { mutableStateOf(track.year?.toString().orEmpty()) }
    var trackNo by rememberSaveable(track.id.value) { mutableStateOf(track.trackNo?.toString().orEmpty()) }
    var discNo by rememberSaveable(track.id.value) { mutableStateOf(track.discNo?.toString().orEmpty()) }
    var artists by rememberSaveable(track.id.value) { mutableStateOf(track.artists.map { it.id.value }) }
    var genres by rememberSaveable(track.id.value) { mutableStateOf(track.genres.map { it.guid }) }
    var options by remember { mutableStateOf<TrackTagOptions?>(null) }
    var optionError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var picker by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(reload) {
        optionError = null
        try { options = loadOptions() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { optionError = failure.message ?: "加载标签失败" }
    }
    fun validNumber(value: String) = value.isBlank() || (value.toIntOrNull()?.let { it >= 0 } == true)
    val valid = title.isNotBlank() && listOf(year, trackNo, discNo).all(::validNumber)
    BackHandler(enabled = saving) { }
    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("编辑歌曲信息") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("名称") }, enabled = !saving,
                    singleLine = true, isError = title.isBlank(), modifier = Modifier.fillMaxWidth(), colors = readableTextFieldColors())
                OutlinedTextField(album, { album = it }, label = { Text("专辑") }, enabled = !saving,
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = readableTextFieldColors())
                val artistOptions = (track.artists + options?.artists.orEmpty()).distinctBy { it.id }
                val genreOptions = (track.genres + options?.genres.orEmpty()).distinctBy { it.guid }
                OutlinedButton(colors = readableOutlinedButtonColors(), onClick = { picker = "歌手" }, enabled = !saving && options != null, modifier = Modifier.fillMaxWidth()) {
                    Text("歌手：" + artistOptions.filter { it.id.value in artists }.joinToString(" / ") { it.name }.ifEmpty { "未设置" })
                }
                listOf(Triple("年份", year, { v: String -> year = v }),
                    Triple("歌曲序号", trackNo, { v: String -> trackNo = v }),
                    Triple("光盘序号", discNo, { v: String -> discNo = v })).forEach { (label, value, change) ->
                    OutlinedTextField(value, change, label = { Text(label) }, singleLine = true, enabled = !saving,
                        isError = !validNumber(value), supportingText = if (!validNumber(value)) ({ Text("请输入非负整数，或留空") }) else null,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(), colors = readableTextFieldColors())
                }
                OutlinedButton(colors = readableOutlinedButtonColors(), onClick = { picker = "风格" }, enabled = !saving && options != null, modifier = Modifier.fillMaxWidth()) {
                    Text("风格：" + genreOptions.filter { it.guid in genres }.joinToString(" / ") { it.name }.ifEmpty { "未设置" })
                }
                if (options == null && optionError == null) Text("正在加载歌手和风格…")
                optionError?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(colors = readableTextButtonColors(), onClick = { reload++ }) { Text("重试加载标签") } }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(colors = readableTextButtonColors(), enabled = valid && !saving, onClick = {
                saving = true
                error = null
                scope.launch {
                    try {
                        onSaved(save(track, TrackMetadataEdit(title.trim(), album.trim().ifEmpty { null },
                            artists, genres, year.toIntOrNull(), trackNo.toIntOrNull(), discNo.toIntOrNull())))
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "保存失败，请重试" }
                    finally { saving = false }
                }
            }) { Text(if (saving) "正在保存…" else "保存") }
        },
        dismissButton = { TextButton(colors = readableTextButtonColors(), enabled = !saving, onClick = onDismiss) { Text("取消") } },
    )
    picker?.let { kind ->
        val entries = if (kind == "歌手") (track.artists + options?.artists.orEmpty()).distinctBy { it.id }.map { it.id.value to it.name }
            else (track.genres + options?.genres.orEmpty()).distinctBy { it.guid }.map { it.guid to it.name }
        var query by remember(kind) { mutableStateOf("") }
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { picker = null }, title = { Text("选择$kind") },
            text = { Column {
                OutlinedTextField(query, { query = it }, label = { Text("搜索$kind") }, singleLine = true, colors = readableTextFieldColors())
                androidx.compose.foundation.lazy.LazyColumn {
                    entries.filter { it.second.contains(query, ignoreCase = true) }.forEach { (id, name) ->
                        item(key = id) {
                            val selected = if (kind == "歌手") artists else genres
                            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Checkbox(id in selected, { checked ->
                                    val updated = if (checked) selected + id else selected - id
                                    if (kind == "歌手") artists = updated else genres = updated
                                })
                                Text(name, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    if (entries.isEmpty()) item { Text("暂无可选$kind") }
                }
            } },
            confirmButton = { TextButton(colors = readableTextButtonColors(), onClick = { picker = null }) { Text("完成") } },
        )
    }
}
