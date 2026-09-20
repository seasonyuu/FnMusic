package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun LyricsPickerDialog(track: Track, actions: LyricsActions, playing: LyricsState?, onDismiss: () -> Unit) {
    val openedAccount = remember { actions.accountKey }
    val index by actions.index.collectAsState()
    val choice by remember(actions, track.id) { actions.choice(track) }.collectAsState(LyricsChoice())
    var query by remember(track.id) { mutableStateOf(track.title + " " + track.artists.joinToString(" ") { it.name }) }
    var candidates by remember { mutableStateOf<List<LyricsCandidate>>(emptyList()) }
    var selected by remember { mutableStateOf<LyricsCandidate?>(null) }
    var preview by remember { mutableStateOf<LyricsDocument?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    fun save(value: LyricsChoice, close: Boolean = true) {
        saving = true
        scope.launch {
            try {
                check(openedAccount == actions.accountKey) { "账号已切换，请重新选择歌词" }
                actions.choose(track, value); error = null; if (close) onDismiss() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "歌词选择保存失败，请重试" }
            finally { saving = false }
        }
    }
    LaunchedEffect(query, index.source, index.version) {
        searching = true; candidates = emptyList()
        try { delay(250); candidates = actions.search(query) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "搜索失败，请在歌词设置中检查索引或切换获取源" }
        finally { searching = false }
    }
    LaunchedEffect(selected, index.source) {
        preview = null
        val candidate = selected ?: return@LaunchedEffect
        previewing = true
        try { preview = actions.preview(candidate); error = null }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "无法预览此歌词，请重试或切换获取源" }
        finally { previewing = false }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("选择歌词") }, modifier = Modifier.testTag("lyrics-picker"),
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium)
                Text("获取源：${index.source.label}")
                playing?.document?.let { document ->
                    Text("当前歌词：${if (document.origin == LyricsOrigin.Amll) "AMLL TTML DB" else "飞牛音乐"}")
                    document.candidate?.authors?.takeIf { it.isNotEmpty() }?.let { Text("制作者：${it.joinToString("、")}") }
                }
                Text("当前选择：${when (choice.mode) { LyricsChoiceMode.Automatic -> "自动匹配"; LyricsChoiceMode.Amll -> "指定 AMLL"; LyricsChoiceMode.FnMusic -> "飞牛音乐" }}")
                playing?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                FilterChip(selected = choice.mode == LyricsChoiceMode.Automatic, enabled = !saving, onClick = { save(choice.copy(mode = LyricsChoiceMode.Automatic, candidate = null)) }, label = { Text("自动匹配") })
                FilterChip(selected = choice.mode == LyricsChoiceMode.FnMusic, enabled = !saving, onClick = { save(choice.copy(mode = LyricsChoiceMode.FnMusic, candidate = null)) }, label = { Text("使用飞牛歌词") })
                Text("AMLL 时间偏移：${choice.offsetMs} ms（正值延后）")
                Row {
                    TextButton(colors = readableTextButtonColors(), enabled = !saving, onClick = { save(choice.copy(offsetMs = choice.offsetMs - 100), false) }) { Text("−100 ms") }
                    TextButton(colors = readableTextButtonColors(), enabled = !saving, onClick = { save(choice.copy(offsetMs = 0), false) }) { Text("重置") }
                    TextButton(colors = readableTextButtonColors(), enabled = !saving, onClick = { save(choice.copy(offsetMs = choice.offsetMs + 100), false) }) { Text("+100 ms") }
                }
                OutlinedTextField(query, { query = it; selected = null }, label = { Text("搜索歌名、歌手或专辑") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (searching) Text("正在搜索…")
                else if (candidates.isEmpty()) Text(if (index.version == null) "暂无索引，请在歌词设置中检查更新" else "没有找到匹配歌词")
                if (selected == null) candidates.forEach { candidate ->
                    OutlinedButton(colors = readableOutlinedButtonColors(), onClick = { selected = candidate }, enabled = !saving && candidate.availableFrom(index.source), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(candidate.titles.joinToString(" / "))
                            Text(candidate.artists.joinToString("、"))
                            Text(candidate.albums.joinToString(" / "))
                            if (!candidate.availableFrom(index.source)) Text("此获取源无法获取")
                            if (candidate.authors.isNotEmpty()) Text("制作者：${candidate.authors.joinToString("、")}")
                        }
                    }
                }
                if (selected != null) TextButton(colors = readableTextButtonColors(), onClick = { selected = null }) { Text("返回搜索结果") }
                if (previewing) Text("正在加载预览…")
                preview?.let { document ->
                    HorizontalDivider()
                    Text("歌词预览", style = MaterialTheme.typography.titleMedium)
                    document.lines.forEach { line ->
                        Text(line.text)
                        line.translation?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(colors = readableTextButtonColors(), onClick = { uri.openUri("https://github.com/amll-dev/amll-ttml-db") }) { Text("AMLL TTML DB · 歌词来源") }
            }
        }, confirmButton = {
            if (preview != null) TextButton(colors = readableTextButtonColors(), enabled = !saving && !previewing, onClick = {
                preview?.candidate?.let { save(choice.copy(mode = LyricsChoiceMode.Amll, candidate = it)) }
            }) { Text("使用此歌词") }
        }, dismissButton = { TextButton(colors = readableTextButtonColors(), onClick = onDismiss) { Text("关闭") } })
}
