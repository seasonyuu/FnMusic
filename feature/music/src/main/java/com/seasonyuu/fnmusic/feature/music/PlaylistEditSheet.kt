package com.seasonyuu.fnmusic.feature.music

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.*
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun PlaylistTrackKey.rowKey() = "${id.value.length}:${id.value}:$occurrence"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistEditSheet(
    id: PlaylistId,
    actions: PlaylistEditActions,
    coverUrl: (String?, Int) -> String?,
    onDismiss: () -> Unit,
) {
    val state by actions.state.collectAsState()
    LaunchedEffect(id) { actions.open(id) }
    val current = state.takeIf { it.id == id } ?: PlaylistEditorState(loading = true)
    var confirmDiscard by rememberSaveable(id.value) { mutableStateOf(false) }
    fun dismiss() {
        if (current.busy) return
        if (current.draft?.dirty == true) confirmDiscard = true
        else { actions.close(); onDismiss() }
    }
    val latest by rememberUpdatedState(current)
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded), confirmValueChange = { value ->
        if (value != SheetValue.Hidden) true
        else if (latest.busy) false
        else if (latest.draft?.dirty == true) { confirmDiscard = true; false }
        else true
    })
    LaunchedEffect(current.complete) {
        if (current.complete) { actions.close(); onDismiss() }
    }
    val artwork = current.draft
    val photoPath = artwork?.photoPath?.takeIf { artwork.usePhoto }
    val tone = rememberPlaylistCoverTone(artwork?.coverId, coverUrl(artwork?.coverId, 640), photoPath)
    val background by animateColorAsState(tone, tween(250), label = "playlist-editor-tone")
    PlaylistEditorArtworkTheme(background) {
    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        containerColor = FnSurface,
        contentColor = FnTextPrimary,
        dragHandle = null,
        modifier = Modifier.statusBarsPadding().imePadding(),
    ) {
        BackHandler { dismiss() }
        val backdrop = rememberLayerBackdrop()
        val draft = current.draft
        val editable = draft != null && !current.busy && !draft.editingLocked
        Column(Modifier.fillMaxHeight(.96f)
            .background(Brush.verticalGradient(listOf(background, lerp(background, Color.Black, .16f))))
            .testTag("playlist-editor-sheet")) {
            CompositionLocalProvider(LocalAppBarBackdrop provides backdrop) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppBarButton(::dismiss, enabled = !current.busy) { Icon(Icons.Rounded.Close, "取消") }
                    Text("编辑歌单", Modifier.weight(1f).padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    LiquidButton(actions::save, backdrop = backdrop,
                        enabled = draft?.valid == true && !current.busy,
                        tint = FnAccent, surfaceColor = FnSurface,
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(48.dp).testTag("playlist-editor-done")) {
                        if (current.busy) CircularProgressIndicator(Modifier.size(24.dp).semantics { contentDescription = if (current.preparingCover) "读取照片中" else "保存中" }, color = FnTextPrimary, strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Done, if (current.error != null && draft != null) "重试保存" else "完成",
                            tint = FnTextPrimary.copy(alpha = if (draft?.valid == true) 1f else .38f))
                    }
                }
            }
            current.error?.let { error ->
                Text(error, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("playlist-editor-error"), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (draft == null) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (current.loading) CircularProgressIndicator()
                    else TextButton(onClick = actions::retryLoad) { Text("重新加载") }
                }
            } else {
                PlaylistEditContent(draft, editable, coverUrl, actions::update, actions::selectPhoto,
                    Modifier.weight(1f).layerBackdrop(backdrop))
            }
            HorizontalDivider(color = FnTextSecondary.copy(alpha = .15f))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("已选择 ${draft?.selected?.size ?: 0} 首", style = MaterialTheme.typography.bodyMedium)
                    Text("仅从歌单移除，不删除音乐文件", color = FnTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
                FilledTonalButton(
                    onClick = { draft?.let { actions.update(it.removeSelected()) } },
                    enabled = editable && !draft?.selected.isNullOrEmpty(),
                    modifier = Modifier.testTag("playlist-editor-remove"),
                ) { Icon(Icons.Rounded.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("移除所选") }
            }
        }
    }
    if (confirmDiscard) {
        AlertDialog(onDismissRequest = { confirmDiscard = false }, title = { Text("放弃未保存的修改？") },
            text = { Text(if (current.draft?.completedSteps?.isNotEmpty() == true) "已提交到服务器的修改无法撤销，其余修改将被放弃。" else "歌单名称、封面、歌曲移除和本地排序的修改将不会保存。") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; actions.close(); onDismiss() }) { Text("放弃修改") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("继续编辑") } })
    }
    }
}

@Composable
private fun PlaylistEditorArtworkTheme(background: Color, content: @Composable () -> Unit) {
    // Like the detail page, artwork is darkened for consistently readable light text.
    FnMusicTheme(darkTheme = true) {
        val palette = FnDarkPalette.copy(surface = background, top = background,
            bottom = lerp(background, Color.Black, .16f), navigation = background)
        val tintedContainer = FnAccent.copy(alpha = .14f).compositeOver(background)
        CompositionLocalProvider(LocalFnPalette provides palette) {
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(
                background = background, surface = background,
                surfaceDim = background, surfaceBright = background,
                surfaceContainerLowest = palette.bottom, surfaceContainerLow = background,
                surfaceContainer = background,
                surfaceContainerHigh = palette.pressed.compositeOver(background),
                surfaceContainerHighest = palette.pressed.compositeOver(background),
                primaryContainer = tintedContainer, secondaryContainer = tintedContainer,
                tertiaryContainer = tintedContainer,
            ), content = content)
        }
    }
}

@Composable
private fun PlaylistEditContent(
    draft: PlaylistEditDraft,
    editable: Boolean,
    coverUrl: (String?, Int) -> String?,
    update: (PlaylistEditDraft) -> Unit,
    selectPhoto: (String) -> Unit,
    modifier: Modifier,
) {
    val listState = rememberLazyListState()
    val remaining = draft.remaining
    val serverKeys = remaining.playlistKeys()
    val tracks = serverKeys.zip(remaining).toMap()
    val keys = if (draft.order.enabled) draft.order.reconcile(remaining).keys else serverKeys
    val latestDraft by rememberUpdatedState(draft)
    val latestKeys by rememberUpdatedState(keys)
    val latestUpdate by rememberUpdatedState(update)
    var dragging by remember { mutableStateOf<PlaylistTrackKey?>(null) }
    var dragCenter by remember { mutableFloatStateOf(0f) }
    val edge = with(LocalDensity.current) { 64.dp.toPx() }
    fun moveAtPointer() {
        val key = dragging ?: return
        val from = latestKeys.indexOf(key)
        val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.key != key.rowKey() && latestKeys.any { it.rowKey() == item.key } &&
                dragCenter in item.offset.toFloat()..(item.offset + item.size).toFloat()
        } ?: return
        val to = latestKeys.indexOfFirst { it.rowKey() == target.key }
        val center = target.offset + target.size / 2f
        if ((to > from && dragCenter > center) || (to < from && dragCenter < center)) {
            latestUpdate(latestDraft.move(from, to))
        }
    }
    LaunchedEffect(dragging) {
        if (dragging != null) while (true) {
            val info = listState.layoutInfo
            val velocity = when {
                dragCenter < info.viewportStartOffset + edge -> -((info.viewportStartOffset + edge - dragCenter) / 5).coerceAtMost(24f)
                dragCenter > info.viewportEndOffset - edge -> ((dragCenter - info.viewportEndOffset + edge) / 5).coerceAtMost(24f)
                else -> 0f
            }
            if (velocity != 0f) { listState.scrollBy(velocity); moveAtPointer() }
            delay(16)
        }
    }
    LaunchedEffect(editable, draft.order.enabled) { if (!editable || !draft.order.enabled) dragging = null }
    LazyColumn(modifier.fillMaxWidth().testTag("playlist-editor-list"), state = listState, contentPadding = PaddingValues(bottom = 20.dp)) {
        item(key = "metadata") {
            PlaylistCoverPager(draft, editable, coverUrl, update, selectPhoto)
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(draft.name, { if (it.length <= 32) update(draft.copy(name = it)) },
                    enabled = editable, label = { Text("歌单名称") }, singleLine = true,
                    supportingText = { Text("${draft.name.length} / 32") },
                    modifier = Modifier.fillMaxWidth().testTag("playlist-editor-name"), colors = readableTextFieldColors())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("自定义排序", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    LiquidToggle(draft.order.enabled, { update(draft.copy(order = draft.order.copy(enabled = it))) },
                        enabled = editable, modifier = Modifier.testTag("playlist-custom-order").semantics { contentDescription = "自定义排序" })
                }
                Text("开启后可拖动右侧手柄调整歌曲顺序。顺序仅保存在本机，不会同步到服务器或其他设备；关闭后恢复服务器顺序，再次开启可恢复本机排序。",
                    color = FnTextSecondary, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("歌曲 · ${remaining.size}", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { update(draft.copy(selected = if (draft.selected.size == remaining.map { it.id }.distinct().size) emptySet() else remaining.map { it.id }.toSet())) }, enabled = editable && remaining.isNotEmpty()) {
                        Text(if (remaining.isNotEmpty() && draft.selected.size == remaining.map { it.id }.distinct().size) "取消全选" else "全选")
                    }
                }
            }
        }
        if (keys.isEmpty()) item { Text("暂无曲目", Modifier.padding(24.dp), color = FnTextSecondary) }
        itemsIndexed(keys, key = { _, key -> key.rowKey() }) { index, key ->
            val track = tracks.getValue(key)
            val isDragging = dragging == key
            Row(Modifier.fillMaxWidth().zIndex(if (isDragging) 1f else 0f)
                .graphicsLayer {
                    if (isDragging) listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key.rowKey() }?.let {
                        translationY = dragCenter - (it.offset + it.size / 2f)
                    }
                }.background(if (isDragging) FnCard else Color.Transparent)
                .clickable(enabled = editable) { update(draft.select(track.id)) }
                .semantics { selected = track.id in draft.selected }
                .testTag("playlist-edit-track-$index").padding(start = 16.dp, end = 8.dp).heightIn(min = 72.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(if (track.id in draft.selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    null, Modifier.size(32.dp).padding(4.dp), tint = if (track.id in draft.selected) FnAccentIcon else FnTextSecondary)
                Spacer(Modifier.width(10.dp))
                CoverImage(coverUrl(track.coverId, 120), null, Modifier.size(44.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }, color = FnTextSecondary,
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (draft.order.enabled) {
                    Box(Modifier.size(48.dp).testTag("playlist-drag-$index")
                        .semantics {
                            contentDescription = "调整${track.title}顺序"
                            if (editable) customActions = listOfNotNull(
                                if (index > 0) CustomAccessibilityAction("上移") { update(draft.move(index, index - 1)); true } else null,
                                if (index < keys.lastIndex) CustomAccessibilityAction("下移") { update(draft.move(index, index + 1)); true } else null,
                            )
                        }.pointerInput(key, editable) {
                            if (editable) detectDragGestures(
                                onDragStart = {
                                    listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key.rowKey() }?.let {
                                        dragCenter = it.offset + it.size / 2f; dragging = key
                                    }
                                },
                                onDrag = { change, delta -> change.consume(); dragCenter += delta.y; moveAtPointer() },
                                onDragCancel = { dragging = null }, onDragEnd = { dragging = null },
                            )
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DragHandle, null, tint = FnTextSecondary) }
                }
            }
            HorizontalDivider(Modifier.padding(start = 58.dp, end = 20.dp), color = FnTextSecondary.copy(alpha = .15f))
        }
    }
}

@Composable
private fun PlaylistCoverPager(
    draft: PlaylistEditDraft,
    editable: Boolean,
    coverUrl: (String?, Int) -> String?,
    update: (PlaylistEditDraft) -> Unit,
    selectPhoto: (String) -> Unit,
) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) selectPhoto(uri.toString())
    }
    key(draft.original.id, draft.photoPath) {
        PlaylistCoverPagerContent(draft, editable, coverUrl, update) {
            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}

private data class CoverPage(val label: String, val coverId: String? = null, val photoPath: String? = null)

@Composable
private fun PlaylistCoverPagerContent(
    draft: PlaylistEditDraft,
    editable: Boolean,
    coverUrl: (String?, Int) -> String?,
    update: (PlaylistEditDraft) -> Unit,
    choosePhoto: () -> Unit,
) {
    // A picked photo replaces the current-artwork slot; it never adds another page.
    val pages = remember(draft.original.id, draft.original.coverId, draft.photoPath) {
        listOf(CoverPage("选择封面")) +
            CoverPage("当前封面", draft.original.coverId, photoPath = draft.photoPath) +
            (1..4).map { CoverPage("默认封面 $it", "playlist_default_$it") }
    }
    val initialPage = if (draft.usePhoto) 1 else pages.indexOfFirst { it.label != "选择封面" && it.photoPath == null && it.coverId == draft.coverId }.coerceAtLeast(1)
    val pager = rememberPagerState(initialPage = initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    val latestDraft by rememberUpdatedState(draft)
    val latestUpdate by rememberUpdatedState(update)
    val latestEditable by rememberUpdatedState(editable)
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page ->
            if (page > 0 && latestEditable) {
                val choice = pages[page]
                val usePhoto = choice.photoPath != null
                if (latestDraft.usePhoto != usePhoto || (!usePhoto && choice.coverId != latestDraft.coverId)) {
                    latestUpdate(latestDraft.copy(coverId = if (usePhoto) latestDraft.coverId else choice.coverId, usePhoto = usePhoto))
                }
            }
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardSize = (maxWidth * .66f).coerceAtMost(240.dp)
            HorizontalPager(
                state = pager,
                pageSize = PageSize.Fixed(cardSize),
                contentPadding = PaddingValues(horizontal = (maxWidth - cardSize) / 2),
                pageSpacing = 16.dp,
                userScrollEnabled = editable,
                modifier = Modifier.testTag("playlist-cover-pager"),
            ) { page ->
                val choice = pages[page]
                val label = choice.label
                Box(Modifier.size(cardSize).testTag("playlist-cover-page-$page")
                    .clickable(enabled = editable) {
                        if (page == 0) choosePhoto()
                        else scope.launch { pager.animateScrollToPage(page) }
                    }.semantics { contentDescription = label; selected = page > 0 && pager.settledPage == page },
                    contentAlignment = Alignment.Center) {
                    if (page == 0) {
                        Column(Modifier.fillMaxSize().background(FnCard, RoundedCornerShape(12.dp)),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Rounded.AddPhotoAlternate, null, Modifier.size(48.dp), tint = FnAccentIcon)
                            Spacer(Modifier.height(12.dp))
                            Text("选择封面", style = MaterialTheme.typography.titleMedium)
                            Text("从相册选择", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
                        }
                    } else if (choice.photoPath != null) CoverImage(java.io.File(choice.photoPath).toURI().toString(), null, Modifier.fillMaxSize())
                    else PlaylistCoverImage(choice.coverId, coverUrl, "", Modifier.fillMaxSize())
                    if (page > 0) {
                        val role = when {
                            choice.photoPath != null -> "新照片 · 待保存"
                            page == 1 -> "当前使用"
                            else -> choice.label
                        }
                        Surface(
                            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = .65f), contentColor = Color.White,
                        ) {
                            Text(role, Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        if (pager.settledPage == page) Surface(
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                            shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ) {
                            Icon(Icons.Rounded.Check, "本次选中的封面", Modifier.padding(6.dp).size(20.dp))
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pager.pageCount) { page ->
                Box(Modifier.size(40.dp).testTag("playlist-cover-indicator").clickable(enabled = editable) { scope.launch { pager.animateScrollToPage(page) } }
                    .semantics { contentDescription = pages[page].label + "页"; selected = pager.settledPage == page },
                    contentAlignment = Alignment.Center) {
                    Box(Modifier.size(if (pager.settledPage == page) 7.dp else 5.dp)
                        .background(if (pager.settledPage == page) FnTextPrimary else FnTextSecondary.copy(alpha = .35f), CircleShape))
                }
            }
        }
    }
}
