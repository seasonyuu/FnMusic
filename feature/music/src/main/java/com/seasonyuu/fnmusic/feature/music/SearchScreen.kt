package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.SubcomposeAsyncImage
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.data.SearchItem
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.Capsule

@Composable
internal fun SearchScreen(
    state: SearchUiState,
    coverUrl: (String?, Int) -> String?,
    onSearch: (String) -> Unit,
    onFilter: (SearchFilter) -> Unit,
    onSubmit: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onMore: (Track) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    val filterState = rememberLazyListState()
    val density = LocalDensity.current
    val headerTint = FnBackgroundTop
    val headerBackdrop = rememberLayerBackdrop()
    val glass = currentLiquidGlassMaterial()
    val searchSurface = FnSurface
    // Keep the paging collection outside the generation key so the header can
    // observe the current category's item count and reveal filters as soon as
    // a paged response arrives (including after deleting and re-entering text).
    val pages = state.pages.collectAsLazyPagingItems()
    var headerHeightPx by remember { mutableIntStateOf(0) }
    val headerHeight = with(density) { headerHeightPx.toDp() }
    val animatedHeaderSpacer by animateDpAsState(
        targetValue = headerHeight + 16.dp,
        animationSpec = tween(durationMillis = 220),
        label = "search-header-spacer-height",
    )
    // This tracks the compact search-header mode. Once entered by focusing the
    // field, it remains active until the close button is explicitly tapped.
    var searchFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // A reused composition can retain the IME focus from the previous page.
        // Reset the compact mode before measuring the overlay header so its
        // initial title and list padding agree.
        focus.clearFocus(force = true)
        searchFocused = false
    }
    val orderedFilters = state.orderedFilters()
    val hasSearchResults = when (state.filter) {
        SearchFilter.Best -> state.bestResults.isNotEmpty() || state.categoryCounts.values.any { it > 0 }
        // Category requests do not carry the mixed-result totals. Keep using
        // those totals when available so switching categories does not remove
        // the filter bar while the new paging stream is loading.
        else -> pages.itemCount > 0 || state.categoryCounts.values.any { it > 0 }
    }
    val showFilterBar = searchFocused && hasSearchResults
    LaunchedEffect(state.filter, orderedFilters) {
        orderedFilters.indexOf(state.filter).takeIf { it >= 0 }?.let { filterState.animateScrollToItem(it) }
    }
    // Save the key alongside the scroll state: returning from a detail must not reset it.
    var previousQuery by rememberSaveable { mutableStateOf(state.query.trim()) }
    var previousFilter by rememberSaveable { mutableStateOf(state.filter.name) }
    LaunchedEffect(state.query.trim(), state.filter) {
        if (previousQuery != state.query.trim() || previousFilter != state.filter.name) {
            listState.scrollToItem(0)
            previousQuery = state.query.trim()
            previousFilter = state.filter.name
        }
    }
    fun finishInput() {
        keyboard?.hide()
        // Force the field out of focus so IME submission and the close button
        // immediately restore the compact, titled search header.
        focus.clearFocus(force = true)
    }
    val closeSlotWidth by animateDpAsState(
        targetValue = if (searchFocused) 60.dp else 0.dp,
        animationSpec = tween(durationMillis = 220),
        label = "search-close-slot-width",
    )
    Box(
        Modifier.fillMaxSize().testTag("search-page"),
    ) {
        // Keep the results behind the header so the list can scroll under its blur.
        key(state.generation) {
            val best = state.filter == SearchFilter.Best
            val songs = if (best) state.bestResults.mapNotNull { (it as? SearchItem.TrackItem)?.value }
                else pages.itemSnapshotList.items.mapNotNull { (it as? SearchItem.TrackItem)?.value }
            fun open(item: SearchItem) {
                finishInput()
                when (item) {
                    is SearchItem.TrackItem -> songs.indexOfFirst { it.id == item.value.id }.takeIf { it >= 0 }?.let { onPlay(songs, it) }
                    is SearchItem.AlbumItem -> onAlbum(item.value)
                    is SearchItem.ArtistItem -> onArtist(item.value)
                    is SearchItem.PlaylistItem -> onPlaylist(item.value)
                }
            }
            Box(
                Modifier.fillMaxSize()
                    .layerBackdrop(headerBackdrop)
                    .background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom))),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("search-results"),
                    state = listState,
                    contentPadding = edgeToEdgeContentPadding(
                        bottom = 20.dp,
                        includeTopInset = false,
                    ),
                ) {
                item(key = "search-header-spacer") {
                    // Leave a small breathing room below the translucent overlay so
                    // empty/loading messages never begin underneath the header.
                    Spacer(Modifier.height(animatedHeaderSpacer))
                }
                when {
                    state.query.isBlank() -> item { SearchMessage("搜索你的音乐", "输入歌曲、歌手、专辑或歌单名称") }
                    state.status == SearchStatus.Loading -> item { SearchLoading() }
                    state.status == SearchStatus.Error -> item { SearchMessage("搜索失败", state.error.orEmpty(), onRetry) }
                    state.status == SearchStatus.Ready && best -> {
                        if (state.bestResults.isEmpty()) {
                            item {
                                SearchEmpty(
                                    state.query,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(durationMillis = 180),
                                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        fadeOutSpec = tween(durationMillis = 120),
                                    ),
                                )
                            }
                        }
                        items(state.bestResults, key = { it.searchKey() }) { item ->
                            SearchResultRow(
                                item,
                                coverUrl,
                                { open(item) },
                                onMore,
                                modifier = Modifier.animateItem(
                                    fadeInSpec = tween(durationMillis = 220),
                                    placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    fadeOutSpec = tween(durationMillis = 140),
                                ),
                            )
                        }
                    }
                    state.status == SearchStatus.Ready -> {
                        items(pages.itemCount, key = pages.itemKey { it.searchKey() }) { index ->
                            pages[index]?.let { item ->
                                SearchResultRow(
                                    item,
                                    coverUrl,
                                    { open(item) },
                                    onMore,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(durationMillis = 220),
                                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        fadeOutSpec = tween(durationMillis = 140),
                                    ),
                                )
                            }
                        }
                        when (val refresh = pages.loadState.refresh) {
                            is LoadState.Loading -> item { SearchLoading() }
                            is LoadState.Error -> item { SearchMessage("搜索失败", refresh.error.message.orEmpty(), pages::retry) }
                            else -> if (pages.itemCount == 0) item {
                                SearchEmpty(
                                    state.query,
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(durationMillis = 180),
                                        placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        fadeOutSpec = tween(durationMillis = 120),
                                    ),
                                )
                            }
                        }
                        if (pages.itemCount > 0) when (val append = pages.loadState.append) {
                            is LoadState.Loading -> item { SearchLoading() }
                            is LoadState.Error -> item { SearchMessage("加载更多失败", append.error.message.orEmpty(), pages::retry) }
                            else -> Unit
                        }
                    }
                }
            }
        }
        }
        Box(
            Modifier.fillMaxWidth()
                .align(Alignment.TopCenter)
                .zIndex(1f)
                .onSizeChanged { headerHeightPx = it.height }
                .drawPlainBackdrop(
                    backdrop = headerBackdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = { blur(48.dp.toPx()) },
                    onDrawSurface = { drawRect(headerTint.copy(alpha = 0.52f)) },
                ),
        ) {
            Column(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(top = 12.dp),
            ) {
            AnimatedVisibility(
                visible = !searchFocused,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Text(
                    "搜索",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    color = FnTextPrimary,
                )
            }
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val backdrop = LocalAppBarBackdrop.current ?: rememberLayerBackdrop()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .then(
                            if (glass.enabled) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { Capsule() },
                                    effects = {
                                        colorControls(brightness = glass.brightness, saturation = 1.5f)
                                        blur(1.dp.toPx() * glass.blurScale)
                                        lens(4.dp.toPx(), 6.dp.toPx())
                                    },
                                    onDrawSurface = {
                                        drawRect(searchSurface.copy(alpha = glass.surfaceAlpha))
                                    },
                                )
                            } else {
                                Modifier.background(FnSurface, CircleShape)
                                    .border(1.dp, FnBorder.copy(alpha = FnBorder.alpha * .5f), CircleShape)
                            },
                        )
                ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("search-input")
                        .onFocusChanged {
                            if (it.isFocused) searchFocused = true
                        },
                    placeholder = { Text("搜索歌曲、歌手、专辑、歌单", maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = FnTextPrimary) },
                    trailingIcon = if (state.query.isNotEmpty()) {
                        { IconButton(onClick = { onSearch("") }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Cancel, "清空搜索", tint = FnTextSecondary)
                        } }
                    } else null,
                    singleLine = true,
                    shape = CircleShape,
                    textStyle = LocalTextStyle.current.copy(fontSize = 16.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        cursorColor = FnTextPrimary,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit(); finishInput() }),
                )
                }
                Box(
                    modifier = Modifier
                        .width(closeSlotWidth),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    SearchCloseButton(searchFocused) {
                        onSearch("")
                        finishInput()
                        searchFocused = false
                    }
                }
            }
            // Leave breathing room below the search field whenever filters are
            // hidden. The spacer collapses as the filter row appears so the
            // header height changes continuously with the transition.
            AnimatedVisibility(
                visible = !showFilterBar,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Spacer(Modifier.height(12.dp))
            }
            // Keep the filter row's layout height stable while it fades so the
            // list spacer never lags behind the overlay during the transition.
            AnimatedVisibility(
                visible = showFilterBar,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LazyRow(
                    state = filterState,
                    modifier = Modifier.fillMaxWidth().testTag("search-filters")
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(orderedFilters, key = { it.name }) { filter ->
                        val active = state.filter == filter
                        Surface(
                            onClick = { onFilter(filter) },
                            shape = CircleShape,
                            color = if (active) FnAccent else androidx.compose.ui.graphics.Color.Transparent,
                            // Keep the selected label consistent with the dark
                            // theme foreground even when the page is in light mode.
                            contentColor = if (active) FnDarkPalette.primary else FnTextPrimary,
                            modifier = Modifier.heightIn(min = 48.dp).testTag("search-filter-${filter.name}")
                                .semantics { selected = active; role = Role.Tab },
                        ) {
                            Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                                Text(filter.label, fontSize = 15.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
            HorizontalDivider(color = FnBorder.copy(alpha = FnBorder.alpha * .5f))
            }
        }
    }
}

@Composable
private fun SearchCloseButton(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
    ) {
        // Sample the app-bar sibling backdrop. Sampling LocalFnBackdrop here would
        // include this page's own RenderNode and can form a cycle on Android 16.
        val backdrop = LocalAppBarBackdrop.current ?: rememberLayerBackdrop()
        LiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = Modifier.padding(start = 8.dp).size(52.dp).testTag("search-close"),
            contentPadding = PaddingValues(0.dp),
            surfaceColor = FnSurface,
        ) {
            Icon(Icons.Rounded.Close, "关闭搜索", tint = FnTextPrimary)
        }
    }
}

@Composable
private fun SearchResultRow(
    item: SearchItem,
    coverUrl: (String?, Int) -> String?,
    onClick: () -> Unit,
    onMore: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title: String
    val subtitle: String
    val coverId: String?
    val placeholder: ImageVector
    when (item) {
        is SearchItem.TrackItem -> {
            title = item.value.title
            subtitle = "歌曲 · ${item.value.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }}"
            coverId = item.value.coverId
            placeholder = Icons.Rounded.MusicNote
        }
        is SearchItem.AlbumItem -> {
            title = item.value.name
            subtitle = "专辑 · ${item.value.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }}"
            coverId = item.value.coverId
            placeholder = Icons.Rounded.Album
        }
        is SearchItem.ArtistItem -> {
            title = item.value.name
            subtitle = "歌手"
            coverId = item.value.coverId
            placeholder = Icons.Rounded.Mic
        }
        is SearchItem.PlaylistItem -> {
            title = item.value.name
            subtitle = item.value.trackCount?.let { "歌单 · $it 首歌曲" } ?: "歌单"
            coverId = item.value.coverId
            placeholder = Icons.Rounded.LibraryMusic
        }
    }
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 76.dp).testTag("search-row-${item.searchKey()}")
                .clickable(onClickLabel = if (item is SearchItem.TrackItem) "播放$title" else "打开$title", onClick = onClick)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = if (item is SearchItem.ArtistItem) CircleShape else RoundedCornerShape(6.dp)
            val fallback: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().background(FnCard), contentAlignment = Alignment.Center) {
                    Icon(placeholder, null, Modifier.size(28.dp), tint = FnTextSecondary)
                }
            }
            SubcomposeAsyncImage(
                model = coverUrl(coverId, 160), contentDescription = null,
                modifier = Modifier.size(52.dp).clip(shape), contentScale = ContentScale.Crop,
                loading = { fallback() }, error = { fallback() },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, color = FnTextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, fontSize = 13.sp, color = FnTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (item is SearchItem.TrackItem) {
                IconButton(onClick = { onMore(item.value) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.MoreHoriz, "更多操作：$title", tint = FnTextSecondary)
                }
            } else {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = FnTextSecondary)
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 84.dp, end = 20.dp), color = FnBorder.copy(alpha = FnBorder.alpha * .5f))
    }
}

@Composable
private fun SearchEmpty(query: String, modifier: Modifier = Modifier) =
    SearchMessage("没有找到“${query.trim()}”", "试试其他关键词或分类", modifier = modifier)

@Composable
private fun SearchMessage(
    title: String,
    detail: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = FnTextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(detail, color = FnTextSecondary, style = MaterialTheme.typography.bodyMedium)
        if (onRetry != null) TextButton(onClick = onRetry, colors = readableTextButtonColors()) { Text("重试") }
    }
}

@Composable
private fun SearchLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp).testTag("search-loading"), color = FnTextSecondary, strokeWidth = 2.dp)
    }
}
