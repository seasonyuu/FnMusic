package com.seasonyuu.fnmusic.feature.music

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.FnBackgroundTop
import com.seasonyuu.fnmusic.core.designsystem.CoverImage
import com.seasonyuu.fnmusic.core.designsystem.LiquidButton
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

/** Bounded and memory-only: cover URLs may include private server information. */
private val albumColors = object : LinkedHashMap<String, Color>(32, .75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Color>?) = size > 32
}

internal fun albumTrackNumber(track: Track, index: Int): String =
    (track.trackNo?.takeIf { it > 0 } ?: (index + 1)).toString()

internal fun albumDurationLabel(tracks: List<Track>): String? {
    val durations = tracks.map { it.durationSeconds }
    if (durations.isEmpty() || durations.any { !it.isFinite() || it <= 0 }) return null
    val seconds = durations.sum().toLong()
    return if (seconds < 60) "$seconds 秒" else "${seconds / 60} 分钟"
}

/** Quantized dominant color, with luminance capped for white text even on white artwork. */
internal fun coverTone(bitmap: Bitmap): Color {
    val sample = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
    try {
        val buckets = IntArray(4096)
        for (y in 0 until 32) for (x in 0 until 32) {
            val pixel = sample.getPixel(x, y)
            if (android.graphics.Color.alpha(pixel) < 128) continue
            val key = ((pixel shr 20) and 15) * 256 + ((pixel shr 12) and 15) * 16 + ((pixel shr 4) and 15)
            buckets[key]++
        }
        val key = buckets.indices.maxBy { buckets[it] }
        if (buckets[key] == 0) return Color(0xFF2D293A)
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV((key shr 8) * 17, ((key shr 4) and 15) * 17, (key and 15) * 17, hsv)
        hsv[1] = (hsv[1] * .65f).coerceAtMost(.65f)
        hsv[2] = (hsv[2] * .48f).coerceIn(.16f, .38f)
        return Color(android.graphics.Color.HSVToColor(hsv))
    } finally {
        if (sample !== bitmap) sample.recycle()
    }
}

/** Shared by playlist detail and its editor; only the active artwork drives the color. */
@Composable
internal fun rememberPlaylistCoverTone(coverId: String?, url: String?, photoPath: String? = null): Color {
    val context = LocalContext.current
    val isDefault = photoPath == null && coverId in (1..4).map { "playlist_default_$it" }
    val key = when {
        photoPath != null -> "photo:$photoPath"
        isDefault -> "default:$coverId"
        else -> url
    }
    var tone by remember(key) { mutableStateOf(albumColors[key] ?: Color(0xFF2D293A)) }
    LaunchedEffect(key) {
        if (key == null) return@LaunchedEffect
        albumColors[key]?.let { tone = it; return@LaunchedEffect }
        val bitmap = if (isDefault) {
            withContext(Dispatchers.Default) { com.seasonyuu.fnmusic.data.DefaultPlaylistCover.preview(context, requireNotNull(coverId)) }
        } else {
            val result = coil3.SingletonImageLoader.get(context).execute(
                ImageRequest.Builder(context).data(photoPath?.let { java.io.File(it) } ?: url)
                    .size(640).allowHardware(false).build(),
            )
            (result as? coil3.request.SuccessResult)?.image?.toBitmap()
        } ?: return@LaunchedEffect
        val result = withContext(Dispatchers.Default) { coverTone(bitmap) }
        albumColors[key] = result
        tone = result
    }
    return tone
}

@Composable
internal fun AlbumDetailScreen(
    album: Album,
    state: MusicUiState,
    playerState: PlayerState,
    coverUrl: (String?, Int) -> String?,
    onPlay: (List<Track>, Int) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val url = coverUrl(album.coverId, 640)
    var coverBitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    var tone by remember(url) { mutableStateOf(albumColors[url] ?: Color(0xFF2D293A)) }
    LaunchedEffect(url, coverBitmap) {
        val bitmap = coverBitmap ?: return@LaunchedEffect
        if (url != null && albumColors[url] == null) {
            val result = withContext(Dispatchers.Default) { coverTone(bitmap) }
            albumColors[url] = result
            tone = result
        }
    }
    PageNavigationAppearance(surfaceColor = lerp(tone, Color.Black, .16f))
    val background by animateColorAsState(tone, tween(250), label = "album-tone")
    val backdrop = rememberLayerBackdrop()
    val appBarBackdrop = rememberLayerBackdrop()
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val top = safe.calculateTopPadding()
    val hiddenEdgePx = with(LocalDensity.current) { (top + 8.dp).toPx() }
    val tracks = state.detailTracks
    // A temporary empty response must not clamp the saved position of the loaded album.
    val placeholderListState = rememberLazyListState()
    val context = LocalContext.current
    val request = remember(url, context) {
        ImageRequest.Builder(context).data(url).size(640, 640).allowHardware(false).build()
    }
    val cover: @Composable (Modifier) -> Unit = { modifier ->
        AsyncImage(
            model = request,
            contentDescription = "${album.name}封面",
            placeholder = painterResource(com.seasonyuu.fnmusic.core.designsystem.R.drawable.cover_placeholder),
            error = painterResource(com.seasonyuu.fnmusic.core.designsystem.R.drawable.cover_placeholder),
            onSuccess = { coverBitmap = it.result.image.toBitmap() },
            contentScale = ContentScale.Crop,
            // A 2D shadow survives backdrop replay without elevation/light-source shifts.
            modifier = modifier.dropShadow(
                shape = RoundedCornerShape(8.dp),
                shadow = Shadow(radius = 12.dp, color = Color.Black.copy(alpha = .20f), offset = DpOffset(0.dp, 4.dp)),
            ).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(8.dp)),
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val coverSize = minOf(maxWidth * .70f, 320.dp)
        val horizontal = if (compact) 20.dp else 48.dp
        Box(Modifier.fillMaxSize().layerBackdrop(appBarBackdrop)) {
            LazyColumn(
                state = if (tracks.isEmpty()) placeholderListState else listState,
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
                    .background(Brush.verticalGradient(listOf(background, lerp(background, Color.Black, .16f))))
                    .testTag("library-detail-list"),
                contentPadding = edgeToEdgeContentPadding(top = 76.dp, bottom = 36.dp),
            ) {
                item(key = "header") {
                    if (compact) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = horizontal), horizontalAlignment = Alignment.CenterHorizontally) {
                            cover(Modifier.size(coverSize))
                            Spacer(Modifier.height(20.dp))
                            AlbumHeading(album, tracks, onPlay)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            cover(Modifier.size(264.dp))
                            Box(Modifier.weight(1f)) { AlbumHeading(album, tracks, onPlay) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(Modifier.padding(horizontal = horizontal), color = Color.White.copy(alpha = .18f))
                }
                when {
                    state.detailLoading && tracks.isEmpty() -> item(key = "loading") {
                        Column(Modifier.padding(horizontal = horizontal).semantics { contentDescription = "正在加载曲目" }) {
                            repeat(6) {
                                Box(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 18.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = .10f)))
                            }
                        }
                    }
                    state.detailError != null -> item(key = "error") {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.detailError, color = Color.White)
                            TextButton(colors = readableTextButtonColors(), onClick = onRetry) { Text("重试", color = Color.White) }
                        }
                    }
                    tracks.isEmpty() -> item(key = "empty") {
                        Text("暂无曲目", Modifier.fillMaxWidth().padding(24.dp), color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center)
                    }
                }
                val multiDisc = tracks.mapNotNull { it.discNo?.takeIf { disc -> disc > 0 } }.distinct().size > 1
                tracks.forEachIndexed { index, track ->
                    if (multiDisc && (index == 0 || track.discNo != tracks[index - 1].discNo)) {
                        item(key = "disc-$index") {
                            Text(track.discNo?.let { "光盘 $it" } ?: "其他曲目", Modifier.padding(horizontal = horizontal, vertical = 16.dp), color = Color.White.copy(alpha = .8f))
                        }
                    }
                    item(key = "track-$index-${track.id.value}") {
                        // Suppress actions once the row is entirely under the top system-bar mask.
                        val enabled by remember(listState, track.id, index, hiddenEdgePx) {
                            derivedStateOf {
                                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "track-$index-${track.id.value}" }
                                info == null || info.offset + info.size + listState.layoutInfo.beforeContentPadding > hiddenEdgePx
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = horizontal).heightIn(min = 56.dp)
                                .clickable(enabled = enabled) { onPlay(tracks, index) }.testTag("album-track-$index"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.width(30.dp), contentAlignment = Alignment.CenterStart) {
                                if (playerState.current?.track?.id == track.id) AlbumPlayingIndicator(playerState.isPlaying)
                                else Text(albumTrackNumber(track, index), color = Color.White.copy(alpha = .75f), fontSize = 15.sp)
                            }
                            Text(track.title, Modifier.weight(1f).padding(vertical = 14.dp), color = Color.White, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TrackMoreMenu(track, backdrop = backdrop, enabled = enabled,
                                icon = Icons.Rounded.MoreHoriz, description = "${track.title}更多操作",
                                tint = Color.White.copy(alpha = .8f))
                        }
                        HorizontalDivider(Modifier.padding(start = horizontal + 30.dp, end = horizontal), color = Color.White.copy(alpha = .15f))
                    }
                }
                if (tracks.isNotEmpty()) item(key = "footer") {
                    Column(Modifier.padding(horizontal = horizontal, vertical = 24.dp).testTag("album-footer"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(listOfNotNull("${tracks.size} 首歌", albumDurationLabel(tracks)).joinToString("，"), color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                        album.releaseDate?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color.White.copy(alpha = .75f), fontSize = 13.sp) }
                    }
                }
            }
            MusicAppBarBlur(backdrop, fullAppBarBlur = true, modifier = Modifier.align(Alignment.TopCenter), tint = background)
        }
        androidx.compose.runtime.CompositionLocalProvider(
            LocalAppBarBackdrop provides appBarBackdrop,
            androidx.compose.material3.LocalContentColor provides Color.White,
        ) {
            MusicAppBar(null, onBack = onBack, fullAppBarBlur = true, drawBackgroundBlur = false,
                modifier = Modifier.padding(start = horizontal, end = horizontal, top = top)
                    .testTag("detail-app-bar"))
        }
    }
}

/**
 * A playlist is presented as a collection of songs, rather than a generic metadata
 * record. Keeping it alongside [AlbumDetailScreen] makes the two detail pages share
 * the same artwork-led hierarchy and edge-to-edge treatment.
 */
@Composable
internal fun PlaylistDetailScreen(
    playlist: Playlist,
    state: MusicUiState,
    playerState: PlayerState,
    coverUrl: (String?, Int) -> String?,
    onPlay: (List<Track>, Int) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPurge: () -> Unit,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val url = coverUrl(playlist.coverId, 640)
    val tone = rememberPlaylistCoverTone(playlist.coverId, url)
    PageNavigationAppearance(surfaceColor = lerp(tone, Color.Black, .16f))
    val background by animateColorAsState(tone, tween(250), label = "playlist-tone")
    val backdrop = rememberLayerBackdrop()
    val appBarBackdrop = rememberLayerBackdrop()
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val top = safe.calculateTopPadding()
    val hiddenEdgePx = with(LocalDensity.current) { (top + 8.dp).toPx() }
    val tracks = state.detailTracks
    val placeholderListState = rememberLazyListState()
    val cover: @Composable (Modifier) -> Unit = { modifier ->
        PlaylistCoverImage(
            coverId = playlist.coverId,
            coverUrl = coverUrl,
            title = playlist.name,
            modifier = modifier.dropShadow(
                shape = RoundedCornerShape(8.dp),
                shadow = Shadow(radius = 12.dp, color = Color.Black.copy(alpha = .20f), offset = DpOffset(0.dp, 4.dp)),
            ).clip(RoundedCornerShape(8.dp)).border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(8.dp)),
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        val coverSize = minOf(maxWidth * .70f, 320.dp)
        val horizontal = if (compact) 20.dp else 48.dp
        Box(Modifier.fillMaxSize().layerBackdrop(appBarBackdrop)) {
            LazyColumn(
                state = if (tracks.isEmpty()) placeholderListState else listState,
                modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)
                    .background(Brush.verticalGradient(listOf(background, lerp(background, Color.Black, .16f))))
                    .testTag("library-detail-list"),
                contentPadding = edgeToEdgeContentPadding(top = 76.dp, bottom = 36.dp),
            ) {
                item(key = "header") {
                    if (compact) {
                        Column(Modifier.fillMaxWidth().padding(horizontal = horizontal), horizontalAlignment = Alignment.CenterHorizontally) {
                            cover(Modifier.size(coverSize))
                            Spacer(Modifier.height(20.dp))
                            PlaylistHeading(playlist, tracks, onPlay)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                            cover(Modifier.size(264.dp))
                            Box(Modifier.weight(1f)) { PlaylistHeading(playlist, tracks, onPlay) }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(Modifier.padding(horizontal = horizontal), color = Color.White.copy(alpha = .18f))
                }
                state.playlistMessage?.let { message ->
                    item(key = "playlist-message") {
                        Text(message, Modifier.padding(horizontal = horizontal, vertical = 12.dp), color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
                    }
                }
                when {
                    state.detailLoading && tracks.isEmpty() -> item(key = "loading") {
                        Column(Modifier.padding(horizontal = horizontal).semantics { contentDescription = "正在加载曲目" }) {
                            repeat(6) {
                                Box(Modifier.fillMaxWidth().height(56.dp).padding(vertical = 18.dp)
                                    .clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = .10f)))
                            }
                        }
                    }
                    state.detailError != null -> item(key = "error") {
                        Text(state.detailError, Modifier.fillMaxWidth().padding(24.dp), color = Color.White, textAlign = TextAlign.Center)
                    }
                    tracks.isEmpty() -> item(key = "empty") {
                        Text("暂无曲目", Modifier.fillMaxWidth().padding(24.dp), color = Color.White.copy(alpha = .8f), textAlign = TextAlign.Center)
                    }
                }
                itemsIndexed(tracks, key = { index, track -> "playlist-track-$index-${track.id.value}" }) { index, track ->
                    val enabled by remember(listState, track.id, index, hiddenEdgePx) {
                        derivedStateOf {
                            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "playlist-track-$index-${track.id.value}" }
                            info == null || info.offset + info.size + listState.layoutInfo.beforeContentPadding > hiddenEdgePx
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = horizontal).heightIn(min = 56.dp)
                            .clickable(enabled = enabled) { onPlay(tracks, index) }.testTag("playlist-track-$index"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.padding(vertical = 6.dp).size(44.dp).clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
                            CoverImage(coverUrl(track.coverId, 120), null,
                                Modifier.fillMaxSize().testTag("playlist-track-cover-$index"))
                            if (playerState.current?.track?.id == track.id) {
                                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .4f)), contentAlignment = Alignment.Center) {
                                    AlbumPlayingIndicator(playerState.isPlaying)
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                            Text(track.title, color = Color.White, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            track.artists.joinToString(" / ") { it.name }.takeIf { it.isNotBlank() }?.let { artists ->
                                Text(artists, color = Color.White.copy(alpha = .7f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        TrackMoreMenu(track, sourcePlaylist = playlist.id, backdrop = backdrop, enabled = enabled,
                            icon = Icons.Rounded.MoreHoriz, description = "更多操作", tint = Color.White.copy(alpha = .8f))
                    }
                    HorizontalDivider(
                        Modifier.padding(start = horizontal + 56.dp, end = horizontal),
                        color = Color.White.copy(alpha = .15f),
                    )
                }
                if (tracks.isNotEmpty()) item(key = "footer") {
                    Text(
                        listOfNotNull("${tracks.size} 首歌", albumDurationLabel(tracks)).joinToString("，"),
                        Modifier.padding(horizontal = horizontal, vertical = 24.dp).testTag("playlist-footer"),
                        color = Color.White.copy(alpha = .75f),
                        fontSize = 13.sp,
                    )
                }
            }
            MusicAppBarBlur(backdrop, fullAppBarBlur = true, modifier = Modifier.align(Alignment.TopCenter), tint = background)
        }
        CompositionLocalProvider(
            LocalAppBarBackdrop provides appBarBackdrop,
            androidx.compose.material3.LocalContentColor provides Color.White,
        ) {
            MusicAppBar(null, onBack = onBack, fullAppBarBlur = true, drawBackgroundBlur = false, actions = {
                PlaylistActionsMenu(state.playlistBusy, onEdit, onPurge, onDelete)
            }, modifier = Modifier.padding(start = horizontal, end = horizontal, top = top).testTag("detail-app-bar"))
        }
    }
}

@Composable
private fun PlaylistHeading(playlist: Playlist, tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(playlist.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text("我的歌单", color = Color.White.copy(alpha = .9f), fontSize = 19.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(listOfNotNull(playlist.trackCount?.let { "$it 首歌曲" }, albumDurationLabel(tracks)).joinToString(" · "), color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onPlay(tracks, 0) }, enabled = tracks.isNotEmpty(),
            modifier = Modifier.width(160.dp).heightIn(min = 48.dp).testTag("playlist-play"),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF332A27)),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text("播放", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AlbumHeading(album: Album, tracks: List<Track>, onPlay: (List<Track>, Int) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(album.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(album.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }, color = Color.White.copy(alpha = .9f), fontSize = 19.sp, textAlign = TextAlign.Center)
        album.releaseDate?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }?.let {
            Spacer(Modifier.height(6.dp))
            Text("$it 年", color = Color.White.copy(alpha = .75f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { onPlay(tracks, 0) }, enabled = tracks.isNotEmpty(),
            modifier = Modifier.width(160.dp).heightIn(min = 48.dp).testTag("album-play"),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF332A27)),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(4.dp))
            Text("播放", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AlbumPlayingIndicator(playing: Boolean) {
    val phase = if (playing) {
        val transition = rememberInfiniteTransition(label = "album-playing")
        val value by transition.animateFloat(0f, 6.283f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "bars")
        value
    } else 0f
    Canvas(Modifier.size(18.dp).semantics { contentDescription = if (playing) "正在播放" else "已暂停" }) {
        repeat(4) { index ->
            val height = if (playing) (.3f + .6f * ((sin(phase + index * 1.7f) + 1f) / 2f)) else .35f
            val x = size.width * (index + .5f) / 4f
            drawLine(Color.White, Offset(x, size.height * (1f - height)), Offset(x, size.height), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}
