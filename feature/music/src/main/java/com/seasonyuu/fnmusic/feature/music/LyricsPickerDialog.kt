package com.seasonyuu.fnmusic.feature.music

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.text.Normalizer
import java.util.Locale

internal data class LyricsSheetVisual(val surface: Color, val cover: Bitmap? = null)
internal enum class LyricsSearchSource(val label: String, val online: OnlineLyricsSource?) {
    Netease("网易云", OnlineLyricsSource.Netease), QQ("QQ音乐", OnlineLyricsSource.QQ), Kugou("酷狗", OnlineLyricsSource.Kugou), Amll("AMLL", null)
}
private data class SourceResults(val loading: Boolean = false, val rows: List<LyricsCandidate> = emptyList(), val error: String? = null)
private val SheetSecondary = Color.White.copy(alpha = .6f)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

internal fun rankLyricsCandidates(rows: List<LyricsCandidate>, track: Track): List<LyricsCandidate> {
    fun normalize(text: String) = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
    val title = normalize(track.title)
    val artists = track.artists.map { normalize(it.name) }.filter(String::isNotBlank)
    val album = track.album?.name?.let(::normalize)
    return rows.sortedWith(compareByDescending<LyricsCandidate> { it.titles.any { name -> normalize(name) == title } }
        .thenByDescending { row -> artists.isNotEmpty() && artists.all { artist -> row.artists.any { normalize(it) == artist } } }
        .thenByDescending { row -> !album.isNullOrBlank() && row.albums.any { normalize(it) == album } })
}

@Composable
private fun LyricsSheetTheme(visual: LyricsSheetVisual, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, onPrimary = Color.Black,
        surface = visual.surface, onSurface = Color.White, onSurfaceVariant = SheetSecondary,
        secondaryContainer = Color.White.copy(alpha = .12f), onSecondaryContainer = Color.White), content = content)
}

/** Disable the framework's clickable handle ripple without changing content feedback. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    surface: Color,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val contentRipple = LocalRippleConfiguration.current
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        ModalBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState,
            containerColor = surface, contentColor = Color.White, shape = SheetShape, modifier = modifier) {
            CompositionLocalProvider(LocalRippleConfiguration provides contentRipple) { content() }
        }
    }
}

@Composable
private fun SheetRow(title: String, subtitle: String? = null, icon: ImageVector, selected: Boolean = false,
    enabled: Boolean = true, tag: String = "", onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(if (selected) Color.White.copy(alpha = .1f) else Color.Transparent)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick).testTag(tag).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = if (enabled) Color.White else SheetSecondary, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = SheetSecondary) }
        }
        if (selected) Icon(Icons.Rounded.Check, "已选择", Modifier.size(20.dp))
    }
}

/** The search modal is mounted only after the selection modal has finished hiding. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsPickerDialog(track: Track, actions: LyricsActions, playing: LyricsState?, onDismiss: () -> Unit,
    visual: LyricsSheetVisual = LyricsSheetVisual(FnNavigationSurface)) {
    val choice by remember(actions, track.id) { actions.choice(track) }.collectAsState(LyricsChoice())
    var searching by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var transitioning by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var offsetExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val openedAccount = remember { actions.accountKey }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun save(value: LyricsChoice, close: Boolean) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                check(openedAccount == actions.accountKey) { "账号已切换" }
                actions.choose(track, value); error = null
                if (close) {
                    sheetState.hide()
                    onDismiss()
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "歌词选择保存失败，请重试" }
            finally { saving = false }
        }
    }
    LyricsSheetTheme(visual) {
        if (searching) {
            LyricsSearchSheet(track, actions, visual, sheetState, saving, error, onBack = { searching = false }, onApply = { candidate ->
                save(choice.copy(mode = if (candidate.onlineSource == null) LyricsChoiceMode.Amll else LyricsChoiceMode.Online, candidate = candidate), true)
            })
        } else {
            LyricsBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, surface = visual.surface,
                modifier = Modifier.testTag("lyrics-picker")) {
                LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text("选择歌词", style = MaterialTheme.typography.titleLarge) }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .08f)), contentAlignment = Alignment.Center) {
                                visual.cover?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                    ?: Icon(Icons.Rounded.MusicNote, null, tint = SheetSecondary)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artists.joinToString("、") { it.name }, color = SheetSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item {
                        val document = playing?.document
                        Text(when {
                            document != null && document.lines.isNotEmpty() -> "${document.origin.label} · ${if (document.hasAccurateWords) "逐词歌词" else "逐行歌词"}${if (playing.loading) " · 正在匹配…" else ""}"
                            playing?.loading == true -> "正在获取歌词…"
                            else -> "暂无可用歌词"
                        }, color = SheetSecondary, style = MaterialTheme.typography.bodySmall)
                        document?.candidate?.authors?.takeIf { it.isNotEmpty() }?.let { Text("制作者：${it.joinToString("、")}", color = SheetSecondary, style = MaterialTheme.typography.bodySmall) }
                        if (choice.mode == LyricsChoiceMode.Amll || choice.mode == LyricsChoiceMode.Online) {
                            Text("已绑定 ${choice.candidate?.onlineSource?.label ?: "AMLL"} · ${choice.candidate?.titles?.firstOrNull().orEmpty()}", color = SheetSecondary, style = MaterialTheme.typography.bodySmall)
                            if (document?.origin == LyricsOrigin.FnMusic && !playing.loading) Text("绑定暂不可用，正在使用飞牛歌词；绑定已保留", color = SheetSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item {
                        Column(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .04f))) {
                            SheetRow("自动匹配", "按歌词质量自动选择来源", Icons.Rounded.AutoAwesome,
                                choice.mode == LyricsChoiceMode.Automatic, !saving, onClick = { save(choice.copy(mode = LyricsChoiceMode.Automatic, candidate = null), true) })
                            SheetRow("使用飞牛歌词", "仅使用音乐库提供的歌词", Icons.Rounded.LibraryMusic,
                                choice.mode == LyricsChoiceMode.FnMusic, !saving, onClick = { save(choice.copy(mode = LyricsChoiceMode.FnMusic, candidate = null), true) })
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(enabled = !saving && !transitioning) {
                            transitioning = true
                            scope.launch { try { sheetState.hide(); searching = true } finally { transitioning = false } }
                        }.testTag("lyrics-manual-search").padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Rounded.Search, null, modifier = Modifier.size(22.dp))
                            Text("手动搜索歌词", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Icon(Icons.Rounded.ChevronRight, null, tint = SheetSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                    if (playing?.document?.let { it.origin != LyricsOrigin.FnMusic && it.lines.isNotEmpty() } == true) item {
                        Column {
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { offsetExpanded = !offsetExpanded }.testTag("lyrics-offset-toggle").padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Icon(Icons.Rounded.Tune, null, modifier = Modifier.size(22.dp))
                                Text("歌词时间调整", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Text("${choice.offsetMs} ms", color = SheetSecondary)
                                Icon(if (offsetExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = SheetSecondary, modifier = Modifier.size(20.dp))
                            }
                            AnimatedVisibility(offsetExpanded, enter = expandVertically(tween(240)) + fadeIn(), exit = shrinkVertically(tween(180)) + fadeOut()) {
                                Column(Modifier.padding(horizontal = 16.dp)) {
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        OutlinedButton(onClick = { save(choice.copy(offsetMs = choice.offsetMs - 100), false) }, enabled = !saving) { Text("提前") }
                                        Text("${choice.offsetMs} ms", style = MaterialTheme.typography.titleMedium)
                                        OutlinedButton(onClick = { save(choice.copy(offsetMs = choice.offsetMs + 100), false) }, enabled = !saving) { Text("延后") }
                                    }
                                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                        Text("每次 0.1 秒，正值延后", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = SheetSecondary)
                                        TextButton(onClick = { save(choice.copy(offsetMs = 0), false) }, enabled = !saving && choice.offsetMs != 0L) { Text("重置") }
                                    }
                                }
                            }
                        }
                    }
                    (error ?: playing?.error)?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsSearchSheet(track: Track, actions: LyricsActions, visual: LyricsSheetVisual, sheetState: SheetState,
    saving: Boolean, saveError: String?, onBack: () -> Unit, onApply: (LyricsCandidate) -> Unit) {
    val index by actions.index.collectAsState()
    val onlinePreference by actions.onlinePreference.collectAsState(OnlineLyricsPreference())
    val tabOrder = onlinePreference.orderedSources.map { platform -> LyricsSearchSource.entries.first { it.online == platform } } + LyricsSearchSource.Amll
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    var query by remember { mutableStateOf((track.title + " " + track.artists.joinToString(" ") { it.name }).trim()) }
    var submitted by remember { mutableStateOf(query.trim()) }
    var source by remember { mutableStateOf(LyricsSearchSource.Netease) }
    var ready by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<Map<LyricsSearchSource, SourceResults>>(emptyMap()) }
    val listStates = remember { LyricsSearchSource.entries.associateWith { LazyListState() }.toMutableMap() }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<LyricsCandidate?>(null) }
    var preview by remember { mutableStateOf<LyricsDocument?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var previewing by remember { mutableStateOf(false) }
    var previewAttempt by remember { mutableIntStateOf(0) }
    var closing by remember { mutableStateOf(false) }
    fun cancelSearch() {
        job?.cancel(); generation++
        results = results.filterValues { !it.loading }
    }
    fun search(downloadIndex: Boolean = false) {
        cancelSearch()
        val current = generation
        val target = source
        val text = submitted
        if (text.isEmpty()) return
        if (target == LyricsSearchSource.Amll && index.version == null && !downloadIndex) return
        results = results + (target to SourceResults(loading = true))
        job = scope.launch {
            try {
                val rows = if (target.online != null) actions.searchOnline(target.online, text) else {
                    if (index.version == null && downloadIndex) actions.updateIndex()
                    check(actions.index.value.version != null) { "尚无索引，请获取索引后搜索" }
                    actions.search(text)
                }
                ensureActive()
                if (current == generation) results = results + (target to SourceResults(rows = rankLyricsCandidates(rows, track)))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current == generation) results = results + (target to SourceResults(error = e.message ?: "搜索失败")) }
        }
    }
    fun submit() {
        cancelSearch(); results = emptyMap(); submitted = query.trim()
        LyricsSearchSource.entries.forEach { listStates[it] = LazyListState() }
        keyboard?.hide(); search()
    }
    fun switch(target: LyricsSearchSource) {
        if (target == source) return
        cancelSearch(); source = target
        if (results[target] == null) search()
    }
    fun close() {
        if (closing) return
        closing = true; cancelSearch(); selected = null
        scope.launch { sheetState.hide(); onBack() }
    }
    LaunchedEffect(Unit) {
        val saved = actions.choice(track).first()
        val preference = actions.onlinePreference.first()
        source = if (saved.mode == LyricsChoiceMode.Amll) LyricsSearchSource.Amll
        else if (saved.mode == LyricsChoiceMode.Online) LyricsSearchSource.entries.firstOrNull { it.online == saved.candidate?.onlineSource } ?: LyricsSearchSource.Netease
        else if (preference.enabled && preference.enabledSources.isNotEmpty()) LyricsSearchSource.entries.first { it.online == preference.enabledSources.first() }
        else if (actions.amllEnabled.first()) LyricsSearchSource.Amll else LyricsSearchSource.Netease
        ready = true; search()
    }
    var mirror by remember { mutableStateOf(index.source) }
    LaunchedEffect(index.source) {
        if (mirror != index.source) {
            mirror = index.source
            results = results - LyricsSearchSource.Amll
            listStates[LyricsSearchSource.Amll] = LazyListState()
            if (selected?.onlineSource == null) selected = null
            if (source == LyricsSearchSource.Amll && ready) { cancelSearch(); search() }
        }
    }
    LaunchedEffect(selected, previewAttempt) {
        preview = null; previewError = null; previewing = false
        val row = selected ?: return@LaunchedEffect
        previewing = true
        try { val document = actions.preview(row); ensureActive(); preview = document }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { previewError = e.message ?: "预览失败，请重试" }
        finally { previewing = false }
    }
    fun back() { if (selected != null) selected = null else close() }
    LyricsBottomSheet(onDismissRequest = { cancelSearch(); onBack() }, sheetState = sheetState,
        surface = visual.surface, modifier = Modifier.testTag("lyrics-search-sheet")) {
        BackHandler { back() }
        Column(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { back() }, enabled = !closing) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, if (selected == null) "返回" else "返回结果") }
                Text(if (selected == null) "搜索歌词" else "歌词预览", style = MaterialTheme.typography.titleLarge)
            }
            AnimatedContent(targetState = selected, modifier = Modifier.weight(1f), transitionSpec = {
                (fadeIn(tween(180)) + slideInHorizontally(tween(220)) { if (targetState == null) -it / 12 else it / 12 }) togetherWith
                    (fadeOut(tween(120)) + slideOutHorizontally(tween(180)) { if (targetState == null) it / 12 else -it / 12 })
            }, label = "lyrics-preview") { row ->
                if (row == null) Column(Modifier.fillMaxSize()) {
                    TextField(query, { query = it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("lyrics-search-query"), singleLine = true,
                        placeholder = { Text("歌名、歌手或专辑") }, shape = RoundedCornerShape(16.dp),
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.White.copy(alpha = .08f), unfocusedContainerColor = Color.White.copy(alpha = .06f), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        trailingIcon = { IconButton(onClick = { submit() }, enabled = query.isNotBlank() && ready, modifier = Modifier.testTag("lyrics-search-submit")) { Icon(Icons.Rounded.Search, "搜索") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }))
                    SourceTabs(source, tabOrder, enabled = ready, onSelect = ::switch)
                    Crossfade(source, modifier = Modifier.weight(1f), animationSpec = tween(140), label = "lyrics-source") { tab ->
                        val result = results[tab]
                        LazyColumn(Modifier.fillMaxSize().testTag("lyrics-search-results"), state = listStates.getValue(tab), contentPadding = PaddingValues(vertical = 12.dp)) {
                            item {
                                when {
                                    tab == LyricsSearchSource.Amll && index.version == null -> Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text("AMLL 尚未下载索引", style = MaterialTheme.typography.titleMedium)
                                        Text("从当前镜像获取索引后即可搜索", color = SheetSecondary)
                                        OutlinedButton(onClick = { search(true) }, enabled = index.status != LyricsIndexStatus.Checking && result?.loading != true) { Text("获取索引并搜索") }
                                        result?.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                    }
                                    result?.loading == true || !ready -> Row(Modifier.padding(vertical = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        LoadingIndicator(Modifier.size(32.dp)); Text("正在搜索…", color = SheetSecondary)
                                    }
                                    result?.error != null -> Column(Modifier.padding(vertical = 24.dp)) {
                                        Text(result.error, color = SheetSecondary)
                                        TextButton(onClick = { search() }) { Text("重试") }
                                    }
                                    else -> Text(if (result?.rows.isNullOrEmpty()) "没有找到匹配歌词" else "${result!!.rows.size} 个结果", color = SheetSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                                }
                            }
                            items(result?.rows.orEmpty()) { candidate ->
                                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = tab == source && !closing && candidate.availableFrom(index.source)) {
                                    keyboard?.hide(); selected = candidate
                                }.padding(vertical = 16.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(candidate.titles.joinToString(" / "), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(listOf(candidate.artists.joinToString("、"), candidate.albums.joinToString(" / ")).filter(String::isNotBlank).joinToString(" · "), color = SheetSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (candidate.durationMs > 0) Text("${candidate.durationMs / 60000}:${(candidate.durationMs / 1000 % 60).toString().padStart(2, '0')}", color = SheetSecondary, style = MaterialTheme.typography.bodySmall)
                                    Icon(Icons.Rounded.ChevronRight, null, tint = SheetSecondary, modifier = Modifier.size(18.dp))
                                }
                                HorizontalDivider(color = Color.White.copy(alpha = .06f))
                            }
                        }
                    }
                } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item {
                            Text(row.titles.joinToString(" / "), style = MaterialTheme.typography.titleLarge)
                            Text(row.artists.joinToString("、"), color = SheetSecondary)
                            if (row.albums.isNotEmpty()) Text(row.albums.joinToString(" / "), color = SheetSecondary, style = MaterialTheme.typography.bodySmall)
                            Text("${row.onlineSource?.label ?: "AMLL"}${preview?.let { " · " + if (it.hasAccurateWords) "逐词歌词" else "逐行歌词" }.orEmpty()}", color = SheetSecondary, style = MaterialTheme.typography.bodySmall)
                            if (row.authors.isNotEmpty()) Text("制作者：${row.authors.joinToString("、")}", color = SheetSecondary, style = MaterialTheme.typography.bodySmall)
                        }
                        if (previewing) item { LoadingIndicator() }
                        previewError?.let { message -> item { Text(message, color = SheetSecondary); TextButton(onClick = { previewAttempt++ }) { Text("重试") } } }
                        items(preview?.lines.orEmpty()) { line -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(line.text, style = MaterialTheme.typography.titleMedium)
                            line.translation?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = SheetSecondary) }
                        } }
                    }
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { onApply(row) }, enabled = !saving && preview != null && selected == row && !previewing, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).heightIn(min = 48.dp).testTag("lyrics-preview-apply")) { Text(if (saving) "正在保存…" else "使用此歌词") }
                }
            }
        }
    }
}

@Composable
private fun SourceTabs(source: LyricsSearchSource, order: List<LyricsSearchSource>, enabled: Boolean, onSelect: (LyricsSearchSource) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        val fontScale = LocalDensity.current.fontScale
        val tabs: @Composable () -> Unit = {
            order.forEach { tab ->
                Tab(selected = source == tab, onClick = { onSelect(tab) }, enabled = enabled,
                    selectedContentColor = Color.White, unselectedContentColor = SheetSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).testTag("lyrics-tab-${tab.name}"), text = { Text(tab.label, maxLines = 1) })
            }
        }
        if (maxWidth < 320.dp || fontScale > 1.2f) {
            ScrollableTabRow(selectedTabIndex = order.indexOf(source), containerColor = Color.Transparent, contentColor = Color.White, edgePadding = 0.dp, tabs = tabs)
        } else TabRow(selectedTabIndex = order.indexOf(source), containerColor = Color.Transparent, contentColor = Color.White, tabs = tabs)
    }
}
