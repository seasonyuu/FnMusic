package com.seasonyuu.fnmusic.feature.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

private enum class SearchSource(val label: String, val online: OnlineLyricsSource?) {
    Netease("网易云", OnlineLyricsSource.Netease), QQ("QQ", OnlineLyricsSource.QQ), Kugou("酷狗", OnlineLyricsSource.Kugou), Amll("AMLL", null)
}
private data class SourceResults(val loading: Boolean = false, val rows: List<LyricsCandidate> = emptyList(), val error: String? = null)

/** Exactly one modal at a time. Each search modal owns and cancels its request scope. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsPickerDialog(track: Track, actions: LyricsActions, playing: LyricsState?, onDismiss: () -> Unit) {
    val choice by remember(actions, track.id) { actions.choice(track) }.collectAsState(LyricsChoice())
    var searching by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val openedAccount = remember { actions.accountKey }
    fun save(value: LyricsChoice, close: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                check(openedAccount == actions.accountKey) { "账号已切换" }
                actions.choose(track, value); error = null
                if (close) onDismiss()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "歌词选择保存失败，请重试" }
            finally { saving = false }
        }
    }
    if (searching) {
        LyricsSearchSheet(track, actions, saving, error, onBack = { searching = false }, onApply = { candidate ->
            save(choice.copy(mode = if (candidate.onlineSource == null) LyricsChoiceMode.Amll else LyricsChoiceMode.Online, candidate = candidate), true)
        })
    } else {
        ModalBottomSheet(onDismissRequest = onDismiss, containerColor = FnSurface, contentColor = FnTextPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), modifier = Modifier.testTag("lyrics-picker")) {
            LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("选择歌词", style = MaterialTheme.typography.titleLarge); Text(track.title, color = FnTextSecondary) }
                item {
                    Text("当前选择：${when (choice.mode) { LyricsChoiceMode.Automatic -> "自动匹配"; LyricsChoiceMode.FnMusic -> "飞牛音乐"; else -> choice.candidate?.onlineSource?.label ?: "AMLL" }}")
                    playing?.document?.let { document ->
                        Text("当前歌词：${document.origin.label}", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                        document.candidate?.authors?.takeIf { it.isNotEmpty() }?.let { Text("制作者：${it.joinToString("、")}", style = MaterialTheme.typography.bodySmall) }
                    }
                }
                item { FilterChip(selected = choice.mode == LyricsChoiceMode.Automatic, enabled = !saving,
                    onClick = { save(choice.copy(mode = LyricsChoiceMode.Automatic, candidate = null), true) }, label = { Text("自动匹配") }) }
                item { FilterChip(selected = choice.mode == LyricsChoiceMode.FnMusic, enabled = !saving,
                    onClick = { save(choice.copy(mode = LyricsChoiceMode.FnMusic, candidate = null), true) }, label = { Text("使用飞牛歌词") }) }
                item { OutlinedButton(onClick = { searching = true }, enabled = !saving, colors = readableOutlinedButtonColors(), modifier = Modifier.fillMaxWidth().testTag("lyrics-manual-search")) { Text("手动搜索歌词") } }
                item {
                    Text("外部歌词时间偏移：${choice.offsetMs} ms", style = MaterialTheme.typography.bodyMedium)
                    Text("正值延后，仅作用于外部歌词", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                    Row {
                        TextButton(onClick = { save(choice.copy(offsetMs = choice.offsetMs - 100), false) }, enabled = !saving, colors = readableTextButtonColors()) { Text("−100 ms") }
                        TextButton(onClick = { save(choice.copy(offsetMs = 0), false) }, enabled = !saving, colors = readableTextButtonColors()) { Text("重置") }
                        TextButton(onClick = { save(choice.copy(offsetMs = choice.offsetMs + 100), false) }, enabled = !saving, colors = readableTextButtonColors()) { Text("+100 ms") }
                    }
                }
                (error ?: playing?.error)?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LyricsSearchSheet(track: Track, actions: LyricsActions, saving: Boolean, saveError: String?, onBack: () -> Unit, onApply: (LyricsCandidate) -> Unit) {
    val index by actions.index.collectAsState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf(track.title + " " + track.artists.joinToString(" ") { it.name }) }
    var sources by remember { mutableStateOf<Set<SearchSource>>(emptySet()) }
    var results by remember { mutableStateOf<Map<SearchSource, SourceResults>>(emptyMap()) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<LyricsCandidate?>(null) }
    var preview by remember { mutableStateOf<LyricsDocument?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    fun search(downloadIndex: Boolean = false) {
        job?.cancel(); generation++
        val current = generation
        val selectedSources = sources
        val text = query.trim()
        selected = null; results = emptyMap()
        if (text.isEmpty()) return
        job = scope.launch {
            selectedSources.map { source -> launch {
                results = results + (source to SourceResults(loading = true))
                try {
                    val rows = if (source.online != null) actions.searchOnline(source.online, text) else {
                        if (index.version == null && downloadIndex) actions.updateIndex()
                        check(actions.index.value.version != null) { "尚无索引，请获取索引后搜索" }
                        actions.search(text)
                    }
                    if (current == generation) results = results + (source to SourceResults(rows = rows))
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { if (current == generation) results = results + (source to SourceResults(error = e.message ?: "搜索失败")) }
            } }.joinAll()
        }
    }
    LaunchedEffect(Unit) {
        val preference = actions.onlinePreference.first()
        val amll = actions.amllEnabled.first()
        sources = SearchSource.entries.filter { it.online?.let { source -> preference.enabled && source in preference.sources } ?: amll }.toSet().ifEmpty { setOf(SearchSource.Netease) }
        search()
    }
    LaunchedEffect(index.source) {
        if (results.isNotEmpty()) { job?.cancel(); generation++; results = emptyMap(); selected = null }
    }
    LaunchedEffect(selected, index.source) {
        preview = null; previewError = null; previewing = false
        val row = selected ?: return@LaunchedEffect
        previewing = true
        try { preview = actions.preview(row) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { previewError = e.message ?: "预览失败，请重试" }
        finally { previewing = false }
    }
    fun back() { if (selected != null) selected = null else onBack() }
    ModalBottomSheet(onDismissRequest = onBack, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FnSurface, contentColor = FnTextPrimary, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.testTag("lyrics-search-sheet")) {
        BackHandler { back() }
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (selected == null) "手动搜索歌词" else "歌词预览", style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { back() }, colors = readableTextButtonColors()) { Text(if (selected == null) "返回" else "返回结果") }
            }
            if (selected == null) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth().testTag("lyrics-search-query"), singleLine = true,
                    label = { Text("歌名、歌手或专辑") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchSource.entries.forEach { source -> FilterChip(selected = source in sources,
                        onClick = { sources = if (source in sources) sources - source else sources + source }, label = { Text(source.label) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { search() }, enabled = sources.isNotEmpty() && query.isNotBlank(), colors = readableOutlinedButtonColors(), modifier = Modifier.testTag("lyrics-search-submit")) { Text("搜索") }
                    if (SearchSource.Amll in sources && index.version == null) OutlinedButton(onClick = { search(true) }, enabled = index.status != LyricsIndexStatus.Checking,
                        colors = readableOutlinedButtonColors()) { Text("获取索引并搜索") }
                }
                LazyColumn(Modifier.weight(1f).testTag("lyrics-search-results"), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SearchSource.entries.forEach { source ->
                        val result = results[source] ?: return@forEach
                        item { Text(source.label, style = MaterialTheme.typography.titleSmall)
                            if (result.loading) Text("正在搜索…", color = FnTextSecondary)
                            else if (result.error != null) Text(result.error, color = MaterialTheme.colorScheme.error)
                            else if (result.rows.isEmpty()) Text("没有找到匹配歌词", color = FnTextSecondary) }
                        items(result.rows) { candidate ->
                            OutlinedButton(onClick = { selected = candidate }, colors = readableOutlinedButtonColors(), enabled = candidate.availableFrom(index.source), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(candidate.titles.joinToString(" / "))
                                    Text(candidate.artists.joinToString("、"), style = MaterialTheme.typography.bodySmall)
                                    Text(candidate.albums.joinToString(" / "), style = MaterialTheme.typography.bodySmall)
                                    if (candidate.durationMs > 0) Text("${candidate.durationMs / 60000}:${(candidate.durationMs / 1000 % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall)
                                    if (candidate.authors.isNotEmpty()) Text("制作者：${candidate.authors.joinToString("、")}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            } else {
                if (previewing) Text("正在加载预览…")
                (previewError ?: saveError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                preview?.let { document ->
                    Text("${document.origin.label} · ${if (document.hasAccurateWords) "逐词歌词" else "逐行歌词"}", color = FnTextSecondary)
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(document.lines) { line -> Column { Text(line.text); line.translation?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = FnTextSecondary) } } }
                    }
                    OutlinedButton(onClick = { document.candidate?.let(onApply) }, enabled = !saving, colors = readableOutlinedButtonColors(),
                        modifier = Modifier.fillMaxWidth().testTag("lyrics-preview-apply")) { Text("使用此歌词") }
                }
            }
        }
    }
}
