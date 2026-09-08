package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.text.style.TextAlign
import android.graphics.RuntimeShader
import android.graphics.Paint as FrameworkPaint
import com.seasonyuu.fnmusic.core.model.TrackMetadataEdit
import com.seasonyuu.fnmusic.core.model.TrackTagOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect
import com.seasonyuu.fnmusic.core.designsystem.CoverImage
import com.seasonyuu.fnmusic.core.designsystem.dynamicBottomBarNestedScrollConnection
import com.seasonyuu.fnmusic.core.designsystem.FnAccent
import com.seasonyuu.fnmusic.core.designsystem.FnBackgroundBottom
import com.seasonyuu.fnmusic.core.designsystem.FnBackgroundTop
import com.seasonyuu.fnmusic.core.designsystem.FnCard
import com.seasonyuu.fnmusic.core.designsystem.FnGradientBackground
import com.seasonyuu.fnmusic.core.designsystem.FnIcons
import com.seasonyuu.fnmusic.core.designsystem.FnProgressiveSystemBars
import com.seasonyuu.fnmusic.core.designsystem.LiquidBottomTab
import com.seasonyuu.fnmusic.core.designsystem.LiquidBottomTabs
import com.seasonyuu.fnmusic.core.designsystem.LocalFnBackdrop
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary
import com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary
import com.seasonyuu.fnmusic.core.designsystem.MiniPlayer
import com.seasonyuu.fnmusic.core.designsystem.rememberDynamicBottomBarState
import com.seasonyuu.fnmusic.core.designsystem.R
import com.seasonyuu.fnmusic.core.designsystem.TrackRow
import com.seasonyuu.fnmusic.core.model.Album
import com.seasonyuu.fnmusic.core.model.Artist
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.resolveLyricTimeline
import com.seasonyuu.fnmusic.core.model.LyricLine
import com.seasonyuu.fnmusic.core.model.RepeatMode
import com.seasonyuu.fnmusic.core.model.Playlist
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.core.model.TrackMetadata
import com.seasonyuu.fnmusic.core.model.TrackSort
import com.seasonyuu.fnmusic.core.model.AlbumSort
import com.seasonyuu.fnmusic.core.model.SearchSuggestions
import androidx.compose.material3.LocalContentColor
import com.seasonyuu.fnmusic.core.model.SearchType
import com.seasonyuu.fnmusic.core.model.AlbumId
import com.seasonyuu.fnmusic.core.model.ArtistId
import com.seasonyuu.fnmusic.core.model.PlaylistId
import com.seasonyuu.fnmusic.data.SearchItem
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.imePadding
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class MusicDestination(val label: String, val icon: ImageVector) {
    Home("首页", FnIcons.Home),
    Library("曲库", FnIcons.Library),
    Search("搜索", FnIcons.Search),
    Favorites("收藏", FnIcons.Favorite),
    More("更多", FnIcons.More),
}

internal val LyricBlurRadiusKey = SemanticsPropertyKey<Float>("LyricBlurRadius")
internal var SemanticsPropertyReceiver.lyricBlurRadius by LyricBlurRadiusKey
private const val LyricsFollowResumeDelayMillis = 8_000L

data class MusicUiState(
    val loading: Boolean = true,
    val tracks: List<Track> = emptyList(),
    val trackTotal: Int? = null,
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val favorites: List<Track> = emptyList(),
    val recent: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val searchQuery: String = "",
    val searchSuggestions: SearchSuggestions = SearchSuggestions(),
    val searchType: SearchType = SearchType.Track,
    val trackSort: TrackSort = TrackSort.RecentlyAdded,
    val albumSort: AlbumSort = AlbumSort.RecentlyUpdated,
    val favoriteOverrides: Map<TrackId, Boolean> = emptyMap(),
    val serverName: String = "飞牛音乐",
    val lyrics: List<LyricLine> = emptyList(),
    val cacheBytes: Long = 512L * 1024L * 1024L,
    val detailKey: DetailRequestKey? = null,
    val detailTracks: List<Track> = emptyList(),
    val detailPlaylist: Playlist? = null,
    val detailAlbum: Album? = null,
    val detailArtist: Artist? = null,
    val detailMetadata: TrackMetadata? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val roamLoading: Boolean = false,
    val playlistBusy: Boolean = false,
    val playlistMessage: String? = null,
    val error: String? = null,
)

private val LocalTrackAction = compositionLocalOf<(Track) -> Unit> { {} }
private val LocalBottomOverlayPadding = compositionLocalOf { 0.dp }

@Composable
private fun edgeToEdgeContentPadding(
    horizontal: Dp = 0.dp,
    top: Dp = 0.dp,
    bottom: Dp = 0.dp,
    includeTopInset: Boolean = true,
): PaddingValues {
    val safeDrawing = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    return PaddingValues(
        start = horizontal + safeDrawing.calculateStartPadding(layoutDirection),
        top = top + if (includeTopInset) safeDrawing.calculateTopPadding() else 0.dp,
        end = horizontal + safeDrawing.calculateEndPadding(layoutDirection),
        bottom = bottom + maxOf(
            safeDrawing.calculateBottomPadding(),
            LocalBottomOverlayPadding.current,
        ),
    )
}

@Composable
fun MusicLoadingScreen(modifier: Modifier = Modifier) {
    FnGradientBackground {
        Box(modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Image(painterResource(R.drawable.fn_music_logo), "飞牛音乐", Modifier.size(72.dp))
                Text("飞牛音乐", style = MaterialTheme.typography.headlineMedium, color = FnTextPrimary)
                androidx.compose.material3.CircularProgressIndicator(color = FnAccent)
                Text("正在加载音乐库…", color = FnTextSecondary)
            }
        }
    }
}

@Composable
fun MusicShell(
    state: MusicUiState,
    playerState: PlayerState,
    pagedTracks: Flow<PagingData<Track>>,
    pagedAlbums: Flow<PagingData<Album>>,
    pagedArtists: Flow<PagingData<Artist>>,
    pagedFavorites: Flow<PagingData<Track>>,
    pagedSearch: Flow<PagingData<SearchItem>>,
    coverUrl: (String?, Int) -> String?,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onSearchType: (SearchType) -> Unit,
    onTrackSort: (TrackSort) -> Unit,
    onAlbumSort: (AlbumSort) -> Unit,
    onRoam: () -> Unit,
    onPlayAllTracks: () -> Unit,
    onPlayAllFavorites: () -> Unit,
    onLoadAlbum: (AlbumId) -> Unit,
    onLoadArtist: (ArtistId) -> Unit,
    onLoadPlaylist: (PlaylistId) -> Unit,
    onCreatePlaylist: (String, String?, TrackId?) -> Unit,
    onUpdatePlaylist: (PlaylistId, String, String?) -> Unit,
    onDeletePlaylist: (PlaylistId) -> Unit,
    onAddTrackToPlaylist: (PlaylistId, TrackId) -> Unit,
    onRemoveTracksFromPlaylist: (PlaylistId, List<TrackId>) -> Unit,
    onPurgeInvalidPlaylistTracks: (PlaylistId) -> Unit,
    onLoadTrackMetadata: (TrackId) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onTogglePlayback: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkipToQueueItem: (Int) -> Unit,
    onSkipToHistoryItem: (Int) -> Unit,
    onClearPlaybackHistory: () -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onCacheSizeChange: (Long) -> Unit,
    onLogout: () -> Unit,
    onSaveTrackMetadata: (suspend (Track, TrackMetadataEdit) -> TrackMetadata)? = null,
    onLoadTrackTagOptions: suspend () -> TrackTagOptions = { TrackTagOptions(emptyList(), emptyList()) },
) {
    val navigation = rememberSaveable(saver = MusicNavigationState.Saver) { MusicNavigationState() }
    val pageStateHolder = rememberSaveableStateHolder()
    val destination = navigation.destination
    val detail = navigation.current.detail
    val keyboard = LocalSoftwareKeyboardController.current
    fun popPage() { navigation.pop()?.let { pageStateHolder.removeState(it.id) } }
    fun openMore(page: MorePage) {
        if (page == MorePage.Menu) popPage() else navigation.push(morePage = page)
    }
    var playerOpen by remember { mutableStateOf(false) }
    fun pushDetail(page: LibraryDetail) {
        playerOpen = false
        navigation.push(detail = page)
    }
    var playerComposed by remember { mutableStateOf(false) }
    var playerPage by remember { mutableStateOf(PlayerPage.NowPlaying) }
    var miniPlayerBounds by remember { mutableStateOf<Rect?>(null) }
    var miniCoverBounds by remember { mutableStateOf<Rect?>(null) }
    val playerMorphProgress = remember { Animatable(0f) }
    val lyricsMorphProgress = remember { Animatable(0f) }
    var actionTrack by remember { mutableStateOf<Track?>(null) }
    var queueActionEntryId by remember { mutableStateOf<String?>(null) }
    var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }
    var deletePlaylistCandidate by remember { mutableStateOf<Playlist?>(null) }
    var purgePlaylistCandidate by remember { mutableStateOf<Playlist?>(null) }
    val trackItems = pagedTracks.collectAsLazyPagingItems()
    val albumItems = pagedAlbums.collectAsLazyPagingItems()
    val artistItems = pagedArtists.collectAsLazyPagingItems()
    val favoriteItems = pagedFavorites.collectAsLazyPagingItems()
    val searchItems = pagedSearch.collectAsLazyPagingItems()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }
    fun navigate(target: MusicDestination, page: MorePage? = null, resetToRoot: Boolean = false) {
        keyboard?.hide()
        navigation.select(target)
        if (page != null || resetToRoot) {
            navigation.resetCurrent().forEach { pageStateHolder.removeState(it.id) }
            if (page != null && page != MorePage.Menu) navigation.push(morePage = page)
        }
    }
    fun openPlayer() {
        if (playerState.current != null && miniPlayerBounds != null && miniCoverBounds != null) {
            playerPage = PlayerPage.NowPlaying
            playerComposed = true
            playerOpen = true
        }
    }
    fun closePlayer() {
        playerOpen = false
    }
    LaunchedEffect(playerComposed) {
        if (!playerComposed) lyricsMorphProgress.snapTo(0f)
    }
    LaunchedEffect(navigation.current.id) {
        when (val page = navigation.current.detail) {
            is LibraryDetail.AlbumPage -> onLoadAlbum(page.album.id)
            is LibraryDetail.ArtistPage -> onLoadArtist(page.artist.id)
            is LibraryDetail.PlaylistPage -> onLoadPlaylist(page.playlist.id)
            is LibraryDetail.TrackPage -> onLoadTrackMetadata(page.track.id)
            else -> Unit
        }
    }
    BackHandler(enabled = playerComposed || navigation.canPop) {
        when {
            playerComposed && playerPage == PlayerPage.Queue -> playerPage = PlayerPage.NowPlaying
            playerComposed && playerPage == PlayerPage.Lyrics -> playerPage = PlayerPage.NowPlaying
            playerComposed -> closePlayer()
            else -> popPage()
        }
    }
    CompositionLocalProvider(
        LocalContentColor provides FnTextPrimary,
        LocalTrackAction provides { track ->
            queueActionEntryId = null
            actionTrack = track
        },
    ) {
        FnProgressiveSystemBars(showTopBlur = !playerComposed) {
            Box(Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (playerComposed && playerMorphProgress.value > 0.5f) {
                                Modifier.clearAndSetSemantics { }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    val compact = maxWidth < 600.dp
                    val expanded = maxWidth >= 840.dp
                    val dynamicBottomBarState = rememberDynamicBottomBarState()
                    val collapseDistancePx = with(LocalDensity.current) { 96.dp.toPx() }
                    val dynamicBottomBarConnection = remember(dynamicBottomBarState, collapseDistancePx) {
                        dynamicBottomBarNestedScrollConnection(dynamicBottomBarState, collapseDistancePx)
                    }
                    Row(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (compact) Modifier.nestedScroll(dynamicBottomBarConnection)
                                else Modifier,
                            ),
                    ) {
                        if (!compact) {
                            if (expanded) PermanentSidebar(destination, { navigate(it) }, state.serverName)
                            else MusicRail(destination) { navigate(it) }
                        }
                        Scaffold(
                            containerColor = Color.Transparent,
                            contentColor = FnTextPrimary,
                            contentWindowInsets = WindowInsets(0, 0, 0, 0),
                            snackbarHost = { SnackbarHost(snackbarHostState) },
                            bottomBar = {
                                if (compact) {
                                    val backdrop = LocalFnBackdrop.current
                                    if (backdrop != null) {
                                        Column(
                                            Modifier
                                                .navigationBarsPadding()
                                                .imePadding()
                                                .padding(bottom = 12.dp),
                                        ) {
                                            DynamicMusicBottomBar(
                                                state = playerState,
                                                selectedDestination = destination,
                                                expansionProgress = dynamicBottomBarState.expansionProgress,
                                                backdrop = backdrop,
                                                onDestinationSelected = { navigate(it) },
                                                onToggle = onTogglePlayback,
                                                onNext = onNext,
                                                onExpand = dynamicBottomBarState::expand,
                                                onOpenPlayer = ::openPlayer,
                                                playerMorphProgress = playerMorphProgress.value,
                                                onPlayerBoundsChanged = { miniPlayerBounds = it },
                                                onCoverBoundsChanged = { miniCoverBounds = it },
                                            )
                                        }
                                    }
                                } else {
                                    MiniPlayer(
                                        state = playerState,
                                        onToggle = onTogglePlayback,
                                        onNext = onNext,
                                        onOpen = ::openPlayer,
                                        modifier = Modifier.navigationBarsPadding().imePadding(),
                                        playerMorphProgress = playerMorphProgress.value,
                                        onPlayerBoundsChanged = { miniPlayerBounds = it },
                                        onCoverBoundsChanged = { miniCoverBounds = it },
                                    )
                                }
                            },
                        ) { padding ->
                            val sceneBackdrop = LocalFnBackdrop.current
                            CompositionLocalProvider(
                                LocalBottomOverlayPadding provides padding.calculateBottomPadding(),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .then(
                                            if (sceneBackdrop != null) Modifier.layerBackdrop(sceneBackdrop)
                                            else Modifier,
                                        )
                                        .background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom))),
                                ) {
                                    AnimatedContent(
                                        targetState = navigation.current,
                                        contentKey = { it.id },
                                        transitionSpec = {
                                            (fadeIn(tween(220)) + slideInHorizontally(tween(240)) { it / 6 }) togetherWith
                                                (fadeOut(tween(140)) + slideOutHorizontally(tween(150)) { -it / 12 })
                                        },
                                        label = "music-page",
                                    ) { entry ->
                                        pageStateHolder.SaveableStateProvider(entry.id) {
                                            val selected = entry.detail
                                            val detailState = state.forDetail(selected?.requestKey)
                                            val morePage = entry.morePage
                                            if (selected != null) {
                                                when (selected) {
                                                    is LibraryDetail.TrackPage -> TrackInfoScreen(
                                                        fallback = selected.track,
                                                        onSave = onSaveTrackMetadata?.let { save ->
                                                            { track, edit ->
                                                                save(track, edit).also {
                                                                    trackItems.refresh()
                                                                    albumItems.refresh()
                                                                    artistItems.refresh()
                                                                    favoriteItems.refresh()
                                                                    searchItems.refresh()
                                                                }
                                                            }
                                                        },
                                                        loadTagOptions = onLoadTrackTagOptions,
                                                        state = detailState,
                                                        coverUrl = coverUrl,
                                                        onBack = { popPage() },
                                                        onAlbum = { album -> pushDetail(LibraryDetail.AlbumPage(album)) },
                                                        onArtist = { artist -> pushDetail(LibraryDetail.ArtistPage(artist)) },
                                                    )
                                                    is LibraryDetail.PlaylistEditorPage -> PlaylistEditorScreen(
                                                        playlist = selected.playlist,
                                                        busy = state.playlistBusy,
                                                        message = state.playlistMessage,
                                                        coverUrl = coverUrl,
                                                        onBack = {
                                                            popPage()
                                                        },
                                                        onSave = { name, coverId ->
                                                            selected.playlist?.let { onUpdatePlaylist(it.id, name, coverId) }
                                                                ?: onCreatePlaylist(name, coverId, selected.initialTrackId)
                                                            popPage()
                                                        },
                                                    )
                                                    else -> LibraryDetailScreen(
                                                        detail = selected,
                                                        state = detailState,
                                                        coverUrl = coverUrl,
                                                        onPlay = onPlay,
                                                        onToggleFavorite = onToggleFavorite,
                                                        onEditPlaylist = { pushDetail(LibraryDetail.PlaylistEditorPage(it)) },
                                                        onDeletePlaylist = { deletePlaylistCandidate = it },
                                                        onPurgePlaylist = { purgePlaylistCandidate = it },
                                                        onRemoveTracksFromPlaylist = onRemoveTracksFromPlaylist,
                                                        onBack = { popPage() },
                                                    )
                                                }
                                            } else {
                                                when (entry.destination) {
                                                    MusicDestination.Home -> HomeScreen(
                                                        state,
                                                        coverUrl,
                                                        onPlay,
                                                        onToggleFavorite,
                                                        onRoam,
                                                        { navigate(MusicDestination.Favorites, resetToRoot = true) },
                                                        { navigate(MusicDestination.More, MorePage.Recent) },
                                                        {
                                                            onTrackSort(TrackSort.RecentlyAdded)
                                                            navigate(MusicDestination.Library, resetToRoot = true)
                                                        },
                                                        { navigate(MusicDestination.More, MorePage.Albums) },
                                                        { navigate(MusicDestination.More, MorePage.Playlists) },
                                                        { pushDetail(LibraryDetail.AlbumPage(it)) },
                                                        { pushDetail(LibraryDetail.PlaylistPage(it)) },
                                                    )
                                                    MusicDestination.Library -> PagingTrackScreen(
                                                        "音乐库", trackItems, state, coverUrl, onPlay, onToggleFavorite, onRefresh,
                                                        sort = state.trackSort,
                                                        totalCount = state.trackTotal,
                                                        showServerTotal = true,
                                                        onSort = onTrackSort,
                                                        onPlayAll = onPlayAllTracks,
                                                    )
                                                    MusicDestination.Search -> SearchScreen(
                                                        state,
                                                        searchItems,
                                                        coverUrl,
                                                        onSearch,
                                                        onSearchType,
                                                        onPlay,
                                                        onToggleFavorite,
                                                        { pushDetail(LibraryDetail.AlbumPage(it)) },
                                                        { pushDetail(LibraryDetail.ArtistPage(it)) },
                                                        { pushDetail(LibraryDetail.PlaylistPage(it)) },
                                                    )
                                                    MusicDestination.Favorites -> PagingTrackScreen(
                                                        "收藏", favoriteItems, state, coverUrl, onPlay, onToggleFavorite, onRefresh,
                                                        onPlayAll = onPlayAllFavorites,
                                                    )
                                                    MusicDestination.More -> when (morePage) {
                                                        MorePage.Menu -> MoreMenu(state) { openMore(it) }
                                                        MorePage.Recent -> TrackListScreen("最近播放", state.recent, state, coverUrl, onPlay, onToggleFavorite, { openMore(MorePage.Menu) })
                                                        MorePage.Albums -> AlbumGridScreen(
                                                            albumItems,
                                                            coverUrl,
                                                            state.albumSort,
                                                            onAlbumSort,
                                                            { openMore(MorePage.Menu) },
                                                        ) { pushDetail(LibraryDetail.AlbumPage(it)) }
                                                        MorePage.Artists -> ArtistGridScreen(artistItems, coverUrl, { openMore(MorePage.Menu) }) { pushDetail(LibraryDetail.ArtistPage(it)) }
                                                        MorePage.Playlists -> PlaylistGridScreen(
                                                            state.playlists,
                                                            state.playlistMessage,
                                                            coverUrl,
                                                            { openMore(MorePage.Menu) },
                                                            { pushDetail(LibraryDetail.PlaylistEditorPage(null)) },
                                                        ) { pushDetail(LibraryDetail.PlaylistPage(it)) }
                                                        MorePage.Settings -> SettingsScreen(state, onCacheSizeChange, onLogout) { openMore(MorePage.Menu) }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (playerComposed) {
                    val containerAnchor = miniPlayerBounds
                    val coverAnchor = miniCoverBounds
                    if (containerAnchor != null && coverAnchor != null) {
                        PlayerMorphOverlay(
                            state = playerState,
                            highResolutionCoverUrl = coverUrl(playerState.current?.track?.coverId, 1600)
                                ?: playerState.current?.coverUrl,
                            containerAnchor = containerAnchor,
                            coverAnchor = coverAnchor,
                            expandedTarget = playerOpen,
                            progress = playerMorphProgress,
                            contentCoverProgress = lyricsMorphProgress.value,
                            // The stable queue paints its own scrolling artwork. The overlay
                            // owns it only while morphing between player presentations.
                            showCover = playerPage != PlayerPage.Queue || lyricsMorphProgress.value < 0.999f,
                            followContentCover = playerPage != PlayerPage.NowPlaying,
                            onRequestClose = ::closePlayer,
                            onClosed = {
                                playerComposed = false
                                playerPage = PlayerPage.NowPlaying
                            },
                            onCoverClick = {
                                when (playerPage) {
                                    PlayerPage.Lyrics -> playerPage = PlayerPage.NowPlaying
                                    PlayerPage.Queue -> playerPage = PlayerPage.NowPlaying
                                    PlayerPage.NowPlaying -> Unit
                                }
                            },
                        ) { targetCoverModifier, lyricsCoverModifier, dragModifier, contentModifier ->
                            when (playerPage) {
                                PlayerPage.NowPlaying, PlayerPage.Lyrics, PlayerPage.Queue -> NowPlayingLyricsScreen(
                                    state = playerState,
                                    musicState = state,
                                    highResolutionCoverUrl = coverUrl(playerState.current?.track?.coverId, 1600)
                                        ?: playerState.current?.coverUrl,
                                    lyricsMode = playerPage == PlayerPage.Lyrics,
                                    queueMode = playerPage == PlayerPage.Queue,
                                    lyricsProgress = lyricsMorphProgress,
                                    modifier = contentModifier,
                                    dragModifier = dragModifier,
                                    largeCoverModifier = targetCoverModifier,
                                    lyricsCoverModifier = lyricsCoverModifier,
                                    onToggle = onTogglePlayback,
                                    onSeek = onSeek,
                                    onPrevious = onPrevious,
                                    onNext = onNext,
                                    onToggleShuffle = onToggleShuffle,
                                    onCycleRepeatMode = onCycleRepeatMode,
                                    onToggleFavorite = onToggleFavorite,
                                    onOpenLyrics = { playerPage = PlayerPage.Lyrics },
                                    onCloseLyrics = { playerPage = PlayerPage.NowPlaying },
                                    onCloseQueue = { playerPage = PlayerPage.NowPlaying },
                                    onOpenQueue = {
                                        playerPage = PlayerPage.Queue
                                    },
                                    onSelectQueueItem = onSkipToQueueItem,
                                    onSelectHistoryItem = onSkipToHistoryItem,
                                    onClearPlaybackHistory = onClearPlaybackHistory,
                                    onMoveQueueItem = onMoveQueueItem,
                                    onRemoveQueueItemDirect = onRemoveFromQueue,
                                    onRemoveQueueItem = { index, track ->
                                        queueActionEntryId = playerState.queue.getOrNull(index)?.queueEntryId
                                        actionTrack = track
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        actionTrack?.let { track ->
            TrackActionSheet(
                track = track,
                coverUrl = coverUrl(track.coverId, 160),
            onDismiss = {
                actionTrack = null
                queueActionEntryId = null
            },
                onPlayNext = { onPlayNext(track); actionTrack = null },
                onAddToQueue = { onAddToQueue(track); actionTrack = null },
                onAddToPlaylist = { playlistPickerTrack = track; actionTrack = null },
                onRemoveFromPlaylist = (detail as? LibraryDetail.PlaylistPage)?.playlist?.let { playlist ->
                    { onRemoveTracksFromPlaylist(playlist.id, listOf(track.id)); actionTrack = null }
                },
                onAlbum = track.album?.let { album ->
                    { pushDetail(LibraryDetail.AlbumPage(album)); actionTrack = null }
                },
                onArtist = track.artists.firstOrNull()?.let { artist ->
                    { pushDetail(LibraryDetail.ArtistPage(artist)); actionTrack = null }
                },
                onInfo = {
                    pushDetail(LibraryDetail.TrackPage(track))
                    actionTrack = null
                },
                onRemoveFromQueue = queueActionEntryId?.let { entryId ->
                    {
                        playerState.queue.indexOfFirst { it.queueEntryId == entryId }
                            .takeIf { it >= 0 && it != playerState.currentIndex }?.let(onRemoveFromQueue)
                        actionTrack = null
                        queueActionEntryId = null
                    }
                },
            )
        }
        playlistPickerTrack?.let { track ->
            PlaylistPickerSheet(
                track = track,
                playlists = state.playlists,
                disabledPlaylistId = (detail as? LibraryDetail.PlaylistPage)?.playlist?.id,
                busy = state.playlistBusy,
                onDismiss = { playlistPickerTrack = null },
                onSelect = { playlist ->
                    onAddTrackToPlaylist(playlist.id, track.id)
                    playlistPickerTrack = null
                },
                onCreate = {
                    playlistPickerTrack = null
                    pushDetail(LibraryDetail.PlaylistEditorPage(null, track.id))
                },
            )
        }
        deletePlaylistCandidate?.let { playlist ->
            AlertDialog(
                onDismissRequest = { deletePlaylistCandidate = null },
                title = { Text("删除歌单？") },
                text = { Text("“${playlist.name}”将被永久删除，音乐文件不会受到影响。") },
                confirmButton = {
                    TextButton(onClick = {
                        onDeletePlaylist(playlist.id)
                        deletePlaylistCandidate = null
                        popPage()
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deletePlaylistCandidate = null }) { Text("取消") } },
            )
        }
        purgePlaylistCandidate?.let { playlist ->
            AlertDialog(
                onDismissRequest = { purgePlaylistCandidate = null },
                title = { Text("清理失效歌曲？") },
                text = { Text("只会从歌单中移除已不存在或无权访问的条目，不会删除音乐文件。") },
                confirmButton = {
                    TextButton(onClick = {
                        onPurgeInvalidPlaylistTracks(playlist.id)
                        purgePlaylistCandidate = null
                    }) { Text("开始清理") }
                },
                dismissButton = { TextButton(onClick = { purgePlaylistCandidate = null }) { Text("取消") } },
            )
        }
    }
}

private enum class PlayerPage { NowPlaying, Lyrics, Queue }

@Composable
private fun PermanentSidebar(selected: MusicDestination, onSelect: (MusicDestination) -> Unit, title: String) {
    Column(
        Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(Color(0xE614121B))
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(8.dp)) {
            Image(painterResource(R.drawable.fn_music_logo), null, Modifier.size(32.dp))
            Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(12.dp))
        MusicDestination.entries.forEach { item ->
            NavigationDrawerItem(
                label = { Text(item.label) },
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, null) },
            )
        }
    }
}

@Composable
private fun MusicRail(selected: MusicDestination, onSelect: (MusicDestination) -> Unit) {
    NavigationRail(containerColor = Color(0xE614121B)) {
        MusicDestination.entries.forEach { item ->
            NavigationRailItem(
                selected = selected == item,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, item.label) },
                label = { Text(item.label) },
            )
        }
    }
}

@Composable
private fun HomeScreen(
    state: MusicUiState,
    coverUrl: (String?, Int) -> String?,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRoam: () -> Unit,
    onFavorites: () -> Unit,
    onRecent: () -> Unit,
    onRecentlyAdded: () -> Unit,
    onAlbums: () -> Unit,
    onPlaylists: () -> Unit,
    onAlbum: (Album) -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    // Keep the vertical page rhythm inset, but let horizontal carousels own the
    // full-width viewport so cards can slide under the screen edge instead of
    // being clipped at the page's 20dp content margin.
    LazyColumn(contentPadding = edgeToEdgeContentPadding(top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            LazyRow(
                modifier = Modifier.testTag("首页快捷入口"),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { RoamFeatureCard(state.roamLoading, onRoam) }
                item { FlowFeatureCard("收藏", FlowCardVariant.Favorites, FnIcons.Favorite, onFavorites) }
                item { FlowFeatureCard("最近播放", FlowCardVariant.Recent, FnIcons.Recent, onRecent) }
                item { FlowFeatureCard("最近添加", FlowCardVariant.RecentlyAdded, FnIcons.Library, onRecentlyAdded) }
            }
        }
        item { Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("最近添加") } }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardWidth = if (maxWidth < 420.dp) maxWidth - 48.dp else 340.dp
                RecentTracksGrid(
                    tracks = state.tracks.take(12),
                    state = state,
                    coverUrl = coverUrl,
                    cardWidth = cardWidth,
                    onPlay = { track -> onPlay(state.tracks, state.tracks.indexOf(track)) },
                    onToggleFavorite = onToggleFavorite,
                )
            }
        }
        item { Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("专辑", onAlbums) } }
        item { AlbumRow(state.albums, coverUrl, onAlbum) }
        item { Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("歌单", onPlaylists) } }
        item { PlaylistRow(state.playlists, coverUrl, onPlaylist) }
    }
}

@Composable
private fun RecentTracksGrid(
    tracks: List<Track>,
    state: MusicUiState,
    coverUrl: (String?, Int) -> String?,
    cardWidth: androidx.compose.ui.unit.Dp,
    onPlay: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        Text(
            "还没有最近添加的歌曲",
            color = FnTextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        )
        return
    }
    LazyHorizontalGrid(
        rows = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tracks, key = { it.id.value }) { track ->
            val favorite = state.favoriteOverrides[track.id] ?: track.isFavorite
            val onMore = LocalTrackAction.current
            Card(
                onClick = { onPlay(track) },
                modifier = Modifier.width(cardWidth).height(68.dp),
                colors = CardDefaults.cardColors(containerColor = FnCard, contentColor = FnTextPrimary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CoverImage(coverUrl(track.coverId, 120), track.title, Modifier.size(52.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                            color = FnTextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = { onToggleFavorite(track.copy(isFavorite = favorite)) },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(
                            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            if (favorite) "取消收藏" else "收藏",
                            tint = if (favorite) FnAccent else FnTextSecondary,
                        )
                    }
                    IconButton(
                        onClick = { onMore(track) },
                        modifier = Modifier.size(44.dp),
                    ) {
                        Icon(Icons.Rounded.MoreVert, "更多操作", tint = FnTextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoamFeatureCard(loading: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    // The Web card clips only its base/mask layer. Its cover-and-vinyl stack is
    // a sibling layer so it can protrude above the rounded card boundary.
    Box(Modifier.width(260.dp).height(140.dp)) {
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(132.dp)
                .semantics { contentDescription = "随机漫游" }
                .clickable(enabled = !loading, onClick = onClick),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .shadow(20.dp, shape, clip = false)
                    .clip(shape),
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFFF7F7F7), Color(0xFFDDDDDD), Color(0xFFB7B7B7)),
                        ),
                    ),
                )
                Image(
                    painterResource(R.drawable.home_roam_art),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .08f))),
                    ),
                )
            }
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 18.dp)
                    .offset(y = (-4).dp)
                    .height(120.dp)
                    .aspectRatio(1.19f)
                    .rotate(-2f),
            ) {
                Image(
                    painterResource(R.drawable.home_favorite_art),
                    null,
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 36.dp, top = 9.dp)
                        .fillMaxHeight(.78f)
                        .aspectRatio(1f),
                    contentScale = ContentScale.Fit,
                )
                Image(
                    painterResource(R.drawable.home_recent_art),
                    null,
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .shadow(10.dp, RoundedCornerShape(10.dp), clip = false)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(FnIcons.Roam, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    "漫游",
                    style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 16.dp)
                        .size(28.dp)
                        .semantics { contentDescription = "正在启动漫游" },
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

private enum class FlowCardVariant(
    val colors: List<Color>,
    val cycleMillis: Int,
    val scale: Float,
    val webSpeed: Float,
    val distortion: Float,
    val swirl: Float,
    val grainMixer: Float,
    val grainOverlay: Float,
) {
    Favorites(
        colors = listOf(Color(0xFFFF6B35), Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFD740)),
        cycleMillis = 12_000,
        scale = .9f,
        webSpeed = 1.8f,
        distortion = .6f,
        swirl = .2f,
        grainMixer = 0f,
        grainOverlay = 0f,
    ),
    Recent(
        colors = listOf(Color(0xFF0A2A1A), Color(0xFF1B5E3B), Color(0xFF77B5A0), Color(0xFF2ECC71)),
        cycleMillis = 16_000,
        scale = 1.5f,
        webSpeed = 1.3f,
        distortion = .5f,
        swirl = .08f,
        grainMixer = 0f,
        grainOverlay = 0f,
    ),
    RecentlyAdded(
        colors = listOf(Color(0xFF3C3C3C), Color(0xFF777777), Color(0xFFB7B7B7), Color(0xFF545454)),
        cycleMillis = 19_000,
        scale = 1.4f,
        webSpeed = 1.1f,
        distortion = .5f,
        swirl = .06f,
        grainMixer = 0f,
        grainOverlay = 0f,
    ),
}

/**
 * A direct Android RuntimeShader port of the WebGL shader used by fnOS Music's
 * homepage cards. The web version mixes moving color blobs with value noise,
 * center-weighted distortion and a subtle swirl. Keeping the shader here (as
 * opposed to approximating it with a sweep gradient) makes the motion and
 * color blending match the source much more closely on API 33+ devices.
 */
private const val FLOW_FRAGMENT_SHADER = """
uniform float2 u_resolution;
uniform float u_time;
uniform float4 u_color0;
uniform float4 u_color1;
uniform float4 u_color2;
uniform float4 u_color3;
uniform float u_distortion;
uniform float u_swirl;
uniform float u_scale;
uniform float u_grainMixer;
uniform float u_grainOverlay;

float hash21(float2 p) {
    p = fract(p * float2(0.3183099, 0.3678794)) + 0.1;
    p += dot(p, p + 19.19);
    return fract(p.x * p.y);
}

float valueNoise(float2 st) {
    float2 i = floor(st);
    float2 f = fract(st);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
}

float noise(float2 n, float2 seedOffset) {
    return valueNoise(n + seedOffset);
}

float2 rotate(float2 uv, float th) {
    return float2(cos(th) * uv.x + sin(th) * uv.y,
                  -sin(th) * uv.x + cos(th) * uv.y);
}

float2 getPosition(int i, float t) {
    float a = float(i) * 0.37;
    float b = 0.6 + fract(float(i) / 3.0) * 0.9;
    float c = 0.8 + fract(float(i + 1) / 4.0);
    float x = sin(t * b + a);
    float y = cos(t * c + a * 1.5);
    return 0.5 + 0.5 * float2(x, y);
}

half4 main(float2 fragCoord) {
    float minDim = min(u_resolution.x, u_resolution.y);
    float2 uv = (fragCoord - 0.5 * u_resolution) / minDim / u_scale + 0.5;
    float2 grainUV = uv * 1000.0;
    float grain = noise(grainUV, float2(0.0));
    float mixerGrain = 0.4 * u_grainMixer * (grain - 0.5);
    float t = 0.5 * (u_time + 41.5);
    float radius = smoothstep(0.0, 1.0, length(uv - 0.5));
    float center = 1.0 - radius;

    for (float i = 1.0; i <= 2.0; i += 1.0) {
        uv.x += u_distortion * center / i * sin(t + i * 0.4 * smoothstep(0.0, 1.0, uv.y)) * cos(0.2 * t + i * 2.4 * smoothstep(0.0, 1.0, uv.y));
        uv.y += u_distortion * center / i * cos(t + i * 2.0 * smoothstep(0.0, 1.0, uv.x));
    }

    float2 uvRotated = uv - 0.5;
    float angle = 3.0 * u_swirl * radius;
    uvRotated = rotate(uvRotated, -angle) + 0.5;

    float3 color = float3(0.0);
    float opacity = 0.0;
    float totalWeight = 0.0;

    float2 p0 = getPosition(0, t) + float2(mixerGrain);
    float d0 = pow(length(uvRotated - p0), 3.5);
    float w0 = 1.0 / (d0 + 0.001);
    color += u_color0.rgb * u_color0.a * w0;
    opacity += u_color0.a * w0;
    totalWeight += w0;

    float2 p1 = getPosition(1, t) + float2(mixerGrain);
    float d1 = pow(length(uvRotated - p1), 3.5);
    float w1 = 1.0 / (d1 + 0.001);
    color += u_color1.rgb * u_color1.a * w1;
    opacity += u_color1.a * w1;
    totalWeight += w1;

    float2 p2 = getPosition(2, t) + float2(mixerGrain);
    float d2 = pow(length(uvRotated - p2), 3.5);
    float w2 = 1.0 / (d2 + 0.001);
    color += u_color2.rgb * u_color2.a * w2;
    opacity += u_color2.a * w2;
    totalWeight += w2;

    float2 p3 = getPosition(3, t) + float2(mixerGrain);
    float d3 = pow(length(uvRotated - p3), 3.5);
    float w3 = 1.0 / (d3 + 0.001);
    color += u_color3.rgb * u_color3.a * w3;
    opacity += u_color3.a * w3;
    totalWeight += w3;

    color /= max(0.0001, totalWeight);
    opacity /= max(0.0001, totalWeight);
    float grainOverlay = valueNoise(rotate(grainUV, 1.0) + float2(3.0));
    grainOverlay = mix(grainOverlay, valueNoise(rotate(grainUV, 2.0) + float2(-1.0)), 0.5);
    grainOverlay = pow(grainOverlay, 1.3);
    float grainOverlayV = grainOverlay * 2.0 - 1.0;
    float3 grainOverlayColor = float3(step(0.0, grainOverlayV));
    float grainOverlayStrength = u_grainOverlay * abs(grainOverlayV);
    grainOverlayStrength = pow(grainOverlayStrength, 0.8);
    color = mix(color, grainOverlayColor, 0.35 * grainOverlayStrength);
    opacity += 0.5 * grainOverlayStrength;
    opacity = clamp(opacity, 0.0, 1.0);
    return half4(clamp(color, 0.0, 1.0), opacity);
}
"""

@Composable
private fun FlowFeatureCard(
    title: String,
    variant: FlowCardVariant,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.width(260.dp).height(140.dp)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(132.dp)
                .semantics { contentDescription = "$title 快捷入口" },
            colors = CardDefaults.cardColors(containerColor = Color.Transparent, contentColor = FnTextPrimary),
            shape = shape,
        ) {
            Box(Modifier.fillMaxSize()) {
                FlowingLightBackground(variant)
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = .42f),
                    modifier = Modifier.align(Alignment.Center).size(58.dp),
                )
                Text(
                    title,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
                    style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FlowingLightBackground(variant: FlowCardVariant) {
    val transition = rememberInfiniteTransition(label = "${variant.name}-flow")
    val elapsedSeconds by transition.animateFloat(
        initialValue = 0f,
        targetValue = variant.cycleMillis / 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(variant.cycleMillis, easing = LinearEasing),
            repeatMode = AnimationRepeatMode.Restart,
        ),
        label = "${variant.name}-phase",
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        FlowingLightShaderBackground(variant, elapsedSeconds)
    } else {
        FlowingLightFallback(variant, elapsedSeconds / (variant.cycleMillis / 1000f) * (Math.PI * 2).toFloat())
    }
}

@androidx.annotation.RequiresApi(33)
@Composable
private fun FlowingLightShaderBackground(variant: FlowCardVariant, elapsedSeconds: Float) {
    val shader = remember { RuntimeShader(FLOW_FRAGMENT_SHADER) }
    val paint = remember { FrameworkPaint() }
    Canvas(Modifier.fillMaxSize()) {
        shader.setFloatUniform("u_resolution", size.width, size.height)
        shader.setFloatUniform("u_time", elapsedSeconds * variant.webSpeed)
        shader.setFloatUniform("u_distortion", variant.distortion)
        shader.setFloatUniform("u_swirl", variant.swirl)
        shader.setFloatUniform("u_scale", variant.scale)
        shader.setFloatUniform("u_grainMixer", variant.grainMixer)
        shader.setFloatUniform("u_grainOverlay", variant.grainOverlay)
        variant.colors[0].let { color -> shader.setFloatUniform("u_color0", color.red, color.green, color.blue, color.alpha) }
        variant.colors[1].let { color -> shader.setFloatUniform("u_color1", color.red, color.green, color.blue, color.alpha) }
        variant.colors[2].let { color -> shader.setFloatUniform("u_color2", color.red, color.green, color.blue, color.alpha) }
        variant.colors[3].let { color -> shader.setFloatUniform("u_color3", color.red, color.green, color.blue, color.alpha) }
        drawIntoCanvas { canvas ->
            paint.shader = shader
            canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
        }
    }
}

@Composable
private fun FlowingLightFallback(variant: FlowCardVariant, phase: Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(variant.colors.first())
        val radius = size.maxDimension * (.7f + variant.scale * .12f)
        variant.colors.forEachIndexed { index, color ->
            val speed = .72f + index * .17f
            val angle = phase * speed + index * 1.61f
            val center = Offset(
                x = size.width * (.5f + .46f * cos(angle.toDouble()).toFloat()),
                y = size.height * (.5f + .42f * sin((angle * 1.13f).toDouble()).toFloat()),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = .82f),
                        color.copy(alpha = .34f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
        val progress = phase / (Math.PI * 2).toFloat()
        val sweepX = -size.width + progress * size.width * 3f
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = .11f), Color.Transparent),
                start = Offset(sweepX - size.width * .55f, size.height),
                end = Offset(sweepX + size.width * .55f, 0f),
            ),
        )
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .28f))))
    }
}

@Composable
private fun SectionTitle(title: String, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (onClick != null) Icon(Icons.Rounded.ChevronRight, "查看$title", tint = FnTextSecondary)
    }
}

@Composable
private fun TrackListScreen(
    title: String,
    tracks: List<Track>,
    state: MusicUiState,
    coverUrl: (String?, Int) -> String?,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    LazyColumn(contentPadding = edgeToEdgeContentPadding(top = 20.dp, bottom = 20.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text("${tracks.size} 首歌曲", color = FnTextSecondary)
                }
                FilledIconButton(onClick = { if (tracks.isNotEmpty()) onPlay(tracks, 0) }) { Icon(Icons.Rounded.PlayArrow, "播放全部") }
            }
        }
        items(tracks, key = { it.id.value }) { track ->
            FavoriteTrackRow(track, state, coverUrl, { onPlay(tracks, tracks.indexOf(track)) }, onToggleFavorite)
        }
        if (tracks.isEmpty()) item { EmptyPane("这里还没有歌曲") }
    }
}

@Composable
private fun PagingTrackScreen(
    title: String,
    tracks: LazyPagingItems<Track>,
    state: MusicUiState,
    coverUrl: (String?, Int) -> String?,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRefresh: () -> Unit,
    sort: TrackSort? = null,
    totalCount: Int? = null,
    showServerTotal: Boolean = false,
    onSort: ((TrackSort) -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
) {
    LazyColumn(contentPadding = edgeToEdgeContentPadding(top = 20.dp, bottom = 20.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            totalCount != null -> "共 $totalCount 首歌曲"
                            showServerTotal && state.loading -> "正在获取曲目总数…"
                            showServerTotal -> "${tracks.itemCount} 首歌曲"
                            else -> "${tracks.itemCount} 首已加载歌曲"
                        },
                        color = FnTextSecondary,
                    )
                }
                IconButton(onClick = { tracks.refresh(); onRefresh() }) { Icon(Icons.Rounded.Refresh, "刷新") }
                if (sort != null && onSort != null) TrackSortMenu(sort, onSort)
                FilledIconButton(
                    onClick = {
                        if (onPlayAll != null) onPlayAll()
                        else tracks.itemSnapshotList.items.takeIf(List<Track>::isNotEmpty)?.let { onPlay(it, 0) }
                    },
                    enabled = tracks.itemCount > 0,
                ) { Icon(Icons.Rounded.PlayArrow, "播放全部") }
            }
        }
        items(count = tracks.itemCount, key = tracks.itemKey { it.id.value }) { index ->
            tracks[index]?.let { track ->
                FavoriteTrackRow(track, state, coverUrl, {
                    val loaded = tracks.itemSnapshotList.items
                    val actualIndex = loaded.indexOfFirst { it.id == track.id }
                    if (actualIndex >= 0) onPlay(loaded, actualIndex)
                }, onToggleFavorite)
            }
        }
        when (val append = tracks.loadState.append) {
            is LoadState.Loading -> item { EmptyPane("正在加载更多…") }
            is LoadState.Error -> item { PagingErrorPane("加载更多失败：${append.error.message ?: "未知错误"}") { tracks.retry() } }
            else -> Unit
        }
        when (val refresh = tracks.loadState.refresh) {
            is LoadState.Loading -> if (tracks.itemCount == 0) item { EmptyPane("正在加载曲库…") }
            is LoadState.Error -> if (tracks.itemCount == 0) item { PagingErrorPane(refresh.error.message ?: "加载失败") { tracks.retry() } }
            else -> if (tracks.itemCount == 0) item { EmptyPane("这里还没有歌曲") }
        }
    }
}

@Composable
private fun FavoriteTrackRow(
    track: Track,
    state: MusicUiState,
    coverUrl: (String?, Int) -> String?,
    onPlay: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
) {
    val favorite = state.favoriteOverrides[track.id] ?: track.isFavorite
    TrackRow(track, coverUrl(track.coverId, 120), onPlay) {
        Row {
            IconButton(onClick = { onToggleFavorite(track.copy(isFavorite = favorite)) }) {
                Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏", tint = if (favorite) FnAccent else FnTextSecondary)
            }
            val onMore = LocalTrackAction.current
            IconButton(onClick = { onMore(track) }) {
                Icon(Icons.Rounded.MoreVert, "更多操作", tint = FnTextSecondary)
            }
        }
    }
}

@Composable
private fun TrackSortMenu(selected: TrackSort, onSelect: (TrackSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "排序") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                TrackSort.RecentlyAdded to "最新添加",
                TrackSort.OldestAdded to "最早添加",
                TrackSort.TitleAscending to "歌曲名 A–Z",
                TrackSort.TitleDescending to "歌曲名 Z–A",
            ).forEach { (sort, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(sort); expanded = false },
                    leadingIcon = { if (sort == selected) Icon(Icons.Rounded.PlayCircle, null, tint = FnAccent) },
                )
            }
        }
    }
}

@Composable
private fun SearchScreen(
    state: MusicUiState,
    results: LazyPagingItems<SearchItem>,
    coverUrl: (String?, Int) -> String?,
    onSearch: (String) -> Unit,
    onSearchType: (SearchType) -> Unit,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(top = 20.dp),
    ) {
        Text("搜索", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            placeholder = { Text("搜索歌曲、歌手、专辑、歌单") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
        )
        AnimatedVisibility(visible = state.searchQuery.isNotBlank() && !state.searchSuggestions.isEmpty()) {
            SearchSuggestionPanel(
                suggestions = state.searchSuggestions,
                coverUrl = coverUrl,
                onTrack = { track ->
                    val tracks = state.searchSuggestions.tracks
                    val index = tracks.indexOfFirst { it.id == track.id }
                    if (index >= 0) onPlay(tracks, index)
                },
                onAlbum = onAlbum,
                onArtist = onArtist,
                onPlaylist = onPlaylist,
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchType.entries.forEach { type ->
                FilterChip(
                    selected = state.searchType == type,
                    onClick = { onSearchType(type) },
                    label = { Text(type.label()) },
                )
            }
        }
        LazyColumn(contentPadding = edgeToEdgeContentPadding(bottom = 20.dp)) {
            items(count = results.itemCount, key = results.itemKey { item -> item.key() }) { index ->
                when (val item = results[index]) {
                    is SearchItem.TrackItem -> FavoriteTrackRow(item.value, state, coverUrl, {
                        val tracks = results.itemSnapshotList.items.mapNotNull { (it as? SearchItem.TrackItem)?.value }
                        val actualIndex = tracks.indexOfFirst { it.id == item.value.id }
                        if (actualIndex >= 0) onPlay(tracks, actualIndex)
                    }, onToggleFavorite)
                    is SearchItem.AlbumItem -> SearchCategoryRow(item.value.name, item.value.artists.joinToString(" / ") { it.name }, coverUrl(item.value.coverId, 120)) { onAlbum(item.value) }
                    is SearchItem.ArtistItem -> SearchCategoryRow(item.value.name, "${item.value.trackCount} 首歌曲", coverUrl(item.value.coverId, 120)) { onArtist(item.value) }
                    is SearchItem.PlaylistItem -> SearchCategoryRow(item.value.name, item.value.trackCount.countLabel(), coverUrl(item.value.coverId, 120)) { onPlaylist(item.value) }
                    null -> Unit
                }
            }
            when (val refresh = results.loadState.refresh) {
                is LoadState.Loading -> if (state.searchQuery.isNotBlank()) item { EmptyPane("正在搜索…") }
                is LoadState.Error -> item { PagingErrorPane(refresh.error.message ?: "搜索失败") { results.retry() } }
                else -> if (state.searchQuery.isNotBlank() && results.itemCount == 0) item { EmptyPane("没有找到相关内容") }
            }
        }
    }
}

private fun SearchSuggestions.isEmpty(): Boolean =
    tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()

@Composable
private fun SearchSuggestionPanel(
    suggestions: SearchSuggestions,
    coverUrl: (String?, Int) -> String?,
    onTrack: (Track) -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    Surface(
        color = FnCard,
        contentColor = FnTextPrimary,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        tonalElevation = 6.dp,
    ) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Text("快速匹配", style = MaterialTheme.typography.titleSmall, color = FnTextSecondary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            suggestions.tracks.take(2).forEach { track ->
                SearchCategoryRow(
                    track.title,
                    "歌曲 · ${track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }}",
                    coverUrl(track.coverId, 120),
                ) { onTrack(track) }
            }
            suggestions.albums.firstOrNull()?.let { album ->
                SearchCategoryRow(album.name, "专辑 · ${album.artists.joinToString(" / ") { it.name }}", coverUrl(album.coverId, 120)) { onAlbum(album) }
            }
            suggestions.artists.firstOrNull()?.let { artist ->
                SearchCategoryRow(artist.name, "歌手 · ${artist.trackCount} 首歌曲", coverUrl(artist.coverId, 120)) { onArtist(artist) }
            }
            suggestions.playlists.firstOrNull()?.let { playlist ->
                SearchCategoryRow(playlist.name, "歌单 · ${playlist.trackCount.countLabel()}", coverUrl(playlist.coverId, 120)) { onPlaylist(playlist) }
            }
        }
    }
}

@Composable
private fun AlbumRow(albums: List<Album>, coverUrl: (String?, Int) -> String?, onAlbum: (Album) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(albums, key = { it.id.value }) { album ->
            Column(Modifier.width(142.dp).clickable { onAlbum(album) }) {
                CoverImage(coverUrl(album.coverId, 320), album.name, Modifier.size(142.dp))
                Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                Text(album.artists.joinToString(" / ") { it.name }, color = FnTextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlists: List<Playlist>,
    coverUrl: (String?, Int) -> String?,
    onPlaylist: (Playlist) -> Unit,
) {
    if (playlists.isEmpty()) {
        Text("还没有歌单", color = FnTextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp))
        return
    }
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(playlists, key = { it.id.value }) { playlist ->
            Column(Modifier.width(142.dp).clickable { onPlaylist(playlist) }) {
                PlaylistCoverImage(playlist.coverId, coverUrl, playlist.name, Modifier.size(142.dp))
                Text(
                    playlist.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    playlist.trackCount.countLabel(),
                    color = FnTextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MoreMenu(state: MusicUiState, onNavigate: (MorePage) -> Unit) {
    LazyColumn(contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("更多", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold) }
        item { MoreEntry(Icons.Rounded.History, "最近播放", "${state.recent.size} 首") { onNavigate(MorePage.Recent) } }
        item { MoreEntry(Icons.Rounded.Album, "专辑", "${state.albums.size} 张") { onNavigate(MorePage.Albums) } }
        item { MoreEntry(FnIcons.Artist, "歌手", "${state.artists.size} 位") { onNavigate(MorePage.Artists) } }
        item { MoreEntry(Icons.AutoMirrored.Rounded.QueueMusic, "歌单", "${state.playlists.size} 个") { onNavigate(MorePage.Playlists) } }
        item { MoreEntry(Icons.Rounded.Settings, "设置", state.serverName) { onNavigate(MorePage.Settings) } }
    }
}

@Composable
private fun SettingsScreen(
    state: MusicUiState,
    onCacheSizeChange: (Long) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    LazyColumn(contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item { PageTitle("设置", onBack) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = FnCard, contentColor = FnTextPrimary)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("当前服务器", style = MaterialTheme.typography.titleMedium)
                    Text(state.serverName, color = FnTextSecondary)
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("临时播放缓存", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(128L, 512L, 1_024L, 2_048L).forEach { mib ->
                        val bytes = mib * 1024L * 1024L
                        FilterChip(
                            selected = state.cacheBytes == bytes,
                            onClick = { onCacheSizeChange(bytes) },
                            label = { Text(if (mib >= 1_024) "${mib / 1_024} GiB" else "$mib MiB") },
                        )
                    }
                }
                Text("缓存上限在下次启动时生效，不会创建永久下载。", color = FnTextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Button(onClick = onLogout) { Text("退出并清除凭据") } }
    }
}

@Composable
private fun MoreEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = FnCard, contentColor = FnTextPrimary),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = FnAccent)
            Column(Modifier.weight(1f)) { Text(title); Text(subtitle, color = FnTextSecondary, style = MaterialTheme.typography.bodySmall) }
            Icon(Icons.Rounded.ChevronRight, null, tint = FnTextSecondary)
        }
    }
}

/** The toolbar stays outside scrolling content so navigation and actions remain reachable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPageFrame(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom))),
    ) {
        TopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = FnBackgroundTop,
                titleContentColor = FnTextPrimary,
                navigationIconContentColor = FnTextPrimary,
                actionIconContentColor = FnTextPrimary,
            ),
            modifier = Modifier.testTag("detail-app-bar"),
        )
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun PageTitle(title: String, onBack: () -> Unit, subtitle: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
        Column {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = FnTextSecondary)
        }
    }
}

@Composable
private fun AlbumGridScreen(
    albums: LazyPagingItems<Album>,
    coverUrl: (String?, Int) -> String?,
    sort: AlbumSort,
    onSort: (AlbumSort) -> Unit,
    onBack: () -> Unit,
    onAlbum: (Album) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(top = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { PageTitle("专辑", onBack, "${albums.itemCount} 张已加载") }
            AlbumSortMenu(sort, onSort)
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(142.dp),
            contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(count = albums.itemCount, key = albums.itemKey { it.id.value }) { index ->
                albums[index]?.let { album ->
                    Column(Modifier.clickable { onAlbum(album) }) {
                        CoverImage(coverUrl(album.coverId, 360), album.name, Modifier.fillMaxWidth().height(142.dp))
                        Text(album.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(album.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }, color = FnTextSecondary, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumSortMenu(selected: AlbumSort, onSelect: (AlbumSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "专辑排序") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                AlbumSort.RecentlyUpdated to "最近更新",
                AlbumSort.OldestUpdated to "最早更新",
                AlbumSort.NameAscending to "专辑名 A–Z",
                AlbumSort.NameDescending to "专辑名 Z–A",
            ).forEach { (sort, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onSelect(sort); expanded = false },
                    leadingIcon = { if (sort == selected) Icon(Icons.Rounded.PlayCircle, null, tint = FnAccent) },
                )
            }
        }
    }
}

@Composable
private fun ArtistGridScreen(artists: LazyPagingItems<Artist>, coverUrl: (String?, Int) -> String?, onBack: () -> Unit, onArtist: (Artist) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(top = 12.dp),
    ) {
        Box(Modifier.padding(horizontal = 8.dp)) { PageTitle("歌手", onBack, "${artists.itemCount} 位已加载") }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(142.dp),
            contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(count = artists.itemCount, key = artists.itemKey { it.id.value }) { index ->
                artists[index]?.let { artist ->
                    Column(Modifier.clickable { onArtist(artist) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        CoverImage(coverUrl(artist.coverId, 360), artist.name, Modifier.size(142.dp))
                        Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text("${artist.trackCount} 首歌曲", color = FnTextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistGridScreen(
    playlists: List<Playlist>,
    message: String?,
    coverUrl: (String?, Int) -> String?,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onPlaylist: (Playlist) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(top = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { PageTitle("歌单", onBack, "${playlists.size} 个") }
            FilledIconButton(onClick = onCreate, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Add, "新建歌单")
            }
        }
        message?.let {
            Text(it, color = FnTextSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(142.dp),
            contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(playlists, key = { it.id.value }) { playlist ->
                Column(Modifier.clickable { onPlaylist(playlist) }) {
                    PlaylistCoverImage(playlist.coverId, coverUrl, playlist.name, Modifier.fillMaxWidth().height(142.dp))
                    Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                    Text(playlist.trackCount.countLabel(), color = FnTextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun PlaylistCoverImage(
    coverId: String?,
    coverUrl: (String?, Int) -> String?,
    title: String,
    modifier: Modifier,
) {
    val defaultIndex = coverId
        ?.takeIf { it.startsWith("playlist_default_") }
        ?.substringAfterLast('_')
        ?.toIntOrNull()
    if (defaultIndex == null) {
        CoverImage(coverUrl(coverId, 800), title, modifier)
        return
    }
    val colors = when (defaultIndex) {
        1 -> listOf(Color(0xFF8E2745), Color(0xFF351B35))
        2 -> listOf(Color(0xFF3B4D91), Color(0xFF20213F))
        3 -> listOf(Color(0xFF258578), Color(0xFF173B43))
        else -> listOf(Color(0xFFB0682E), Color(0xFF4A2635))
    }
    Box(
        modifier
            .background(Brush.linearGradient(colors), RoundedCornerShape(12.dp))
            .semantics { contentDescription = title },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.QueueMusic,
            contentDescription = null,
            tint = Color.White.copy(alpha = .9f),
            modifier = Modifier.fillMaxSize(.36f),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .size(12.dp)
                .background(Color.White.copy(alpha = .55f), RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun PlaylistEditorScreen(
    playlist: Playlist?,
    busy: Boolean,
    message: String?,
    coverUrl: (String?, Int) -> String?,
    onBack: () -> Unit,
    onSave: (String, String?) -> Unit,
) {
    var name by rememberSaveable(playlist?.id?.value) { mutableStateOf(playlist?.name.orEmpty()) }
    var selectedCoverId by rememberSaveable(playlist?.id?.value) {
        mutableStateOf(playlist?.coverId ?: "playlist_default_1")
    }
    val normalized = name.trim()
    val valid = normalized.isNotEmpty() && normalized.length <= 32
    val defaultCovers = remember { (1..4).map { "playlist_default_$it" } }
    DetailPageFrame(if (playlist == null) "新建歌单" else "编辑歌单", onBack) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom))),
            contentPadding = edgeToEdgeContentPadding(top = 20.dp, bottom = 36.dp, includeTopInset = false),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PlaylistCoverImage(selectedCoverId, coverUrl, normalized.ifBlank { "新歌单" }, Modifier.size(220.dp))
                    Text(
                        if (playlist == null) "为这组音乐取个名字" else "更新名称与默认封面",
                        color = FnTextSecondary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 32) name = it },
                        label = { Text("歌单名称") },
                        placeholder = { Text("请输入歌单名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("${name.length} / 32", color = FnTextSecondary, modifier = Modifier.align(Alignment.End))
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("默认封面", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(defaultCovers) { coverId ->
                            val selected = selectedCoverId == coverId
                            Card(
                                onClick = { selectedCoverId = coverId },
                                colors = CardDefaults.cardColors(containerColor = if (selected) FnAccent.copy(alpha = .22f) else FnCard),
                                shape = RoundedCornerShape(18.dp),
                            ) {
                                Box(Modifier.padding(6.dp)) {
                                    PlaylistCoverImage(coverId, coverUrl, "默认封面", Modifier.size(88.dp))
                                    Icon(
                                        if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        if (selected) "已选择" else "选择封面",
                                        tint = if (selected) FnAccent else FnTextSecondary,
                                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            message?.let { item { Text(it, color = FnTextSecondary, modifier = Modifier.padding(horizontal = 24.dp)) } }
            item {
                Button(
                    onClick = { onSave(normalized, selectedCoverId) },
                    enabled = valid && !busy,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
                ) {
                    Text(if (busy) "正在保存…" else "保存歌单")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistPickerSheet(
    track: Track,
    playlists: List<Playlist>,
    disabledPlaylistId: PlaylistId?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Playlist) -> Unit,
    onCreate: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF24202E),
        contentColor = FnTextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("添加到歌单", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Text(track.title, color = FnTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(10.dp))
            ActionSheetRow(Icons.Rounded.Add, "新建歌单", onCreate)
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(playlists, key = { it.id.value }) { playlist ->
                    val disabled = busy || playlist.id == disabledPlaylistId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "添加到歌单：${playlist.name}" }
                            .clickable(enabled = !disabled) { onSelect(playlist) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, tint = if (disabled) FnTextSecondary.copy(alpha = .45f) else FnAccent, modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (disabled) FnTextSecondary else FnTextPrimary)
                            Text(
                                if (playlist.id == disabledPlaylistId) "已在当前歌单中" else playlist.trackCount.countLabel(),
                                color = FnTextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                if (playlists.isEmpty()) item { EmptyPane("还没有歌单") }
            }
        }
    }
}

@Composable
private fun SearchCategoryRow(title: String, subtitle: String, cover: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverImage(cover, title, Modifier.size(52.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = FnTextSecondary, maxLines = 1, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = FnTextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackActionSheet(
    track: Track,
    coverUrl: String?,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)?,
    onRemoveFromQueue: (() -> Unit)?,
    onAlbum: (() -> Unit)?,
    onArtist: (() -> Unit)?,
    onInfo: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF24202E),
        contentColor = FnTextPrimary,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CoverImage(coverUrl, track.title, Modifier.size(64.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }, color = FnTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ActionSheetRow(Icons.AutoMirrored.Rounded.PlaylistPlay, "下一首播放", onPlayNext)
        ActionSheetRow(Icons.AutoMirrored.Rounded.QueueMusic, "加入待播队列", onAddToQueue)
        ActionSheetRow(Icons.AutoMirrored.Rounded.PlaylistAdd, "添加到歌单", onAddToPlaylist)
        onAlbum?.let { ActionSheetRow(Icons.Rounded.Album, "查看专辑", it) }
        onArtist?.let { ActionSheetRow(Icons.Rounded.Person, "查看歌手", it) }
        onRemoveFromPlaylist?.let { ActionSheetRow(Icons.Rounded.RemoveCircleOutline, "从歌单移除", it) }
        onRemoveFromQueue?.let { ActionSheetRow(Icons.Rounded.RemoveCircleOutline, "从待播队列移除", it) }
        ActionSheetRow(Icons.Rounded.Info, "歌曲信息", onInfo)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActionSheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, null, tint = FnAccent, modifier = Modifier.size(24.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TrackInfoScreen(
    fallback: Track,
    onSave: (suspend (Track, TrackMetadataEdit) -> TrackMetadata)?,
    loadTagOptions: suspend () -> TrackTagOptions,
    state: MusicUiState,
    coverUrl: (String?, Int) -> String?,
    onBack: () -> Unit,
    onAlbum: (Album) -> Unit,
    onArtist: (Artist) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    var savedMetadata by remember(fallback.id) { mutableStateOf<TrackMetadata?>(null) }
    var editing by rememberSaveable(fallback.id.value) { mutableStateOf(false) }
    val metadata = savedMetadata ?: state.detailMetadata?.takeIf { it.track.id == fallback.id }
    val track = metadata?.track ?: fallback
    val audio = metadata?.audioSpec ?: track.audioSpec
    if (editing && onSave != null) {
        TrackMetadataEditor(track, loadTagOptions, onSave,
            onSaved = { savedMetadata = it; editing = false }, onDismiss = { editing = false })
    }
    DetailPageFrame("歌曲信息", onBack, actions = {
        if (onSave != null) TextButton(onClick = { editing = true }, enabled = !state.detailLoading && metadata != null && state.detailError == null) { Text("编辑") }
    }) {
        if (state.detailLoading) {
            EmptyPane("正在加载详情…")
        } else {
            LazyColumn(
                Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom))),
                state = listState,
                contentPadding = edgeToEdgeContentPadding(top = 20.dp, bottom = 36.dp, includeTopInset = false),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CoverImage(coverUrl(track.coverId, 800), track.title, Modifier.size(232.dp))
                        Text(track.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Text(track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }, color = FnTextSecondary)
                    }
                }
                if (state.detailLoading) item { EmptyPane("正在读取音频信息…") }
                state.detailError?.let { item { EmptyPane(it) } }
                item {
                    InfoCard(
                        "音频",
                        listOfNotNull(
                            audio?.format?.uppercase()?.let { "格式" to it },
                            audio?.codec?.let { "编码" to it },
                            audio?.bitrate?.takeIf { it > 0 }?.let { "码率" to "${kotlin.math.round(it / 1000.0).toLong()} kbps" },
                            audio?.sampleRate?.let { "采样率" to formatSampleRate(it) },
                            audio?.bitDepth?.let { "位深" to "$it bit" },
                            audio?.channel?.let { "声道" to it.toString() },
                            audio?.size?.let { "文件大小" to formatBytes(it) },
                            (audio?.duration ?: track.durationSeconds).takeIf { it > 0 }?.let { "时长" to formatDuration((it * 1_000).toLong()) },
                        ).ifEmpty { listOf("音频信息" to "服务端未提供") },
                    )
                }
                item {
                    InfoCard(
                        "标签",
                        listOfNotNull(
                            "名称" to track.title,
                            track.album?.let { "专辑" to it.name },
                            track.artists.takeIf { it.isNotEmpty() }?.let { "歌手" to it.joinToString(" / ") { artist -> artist.name } },
                            track.year?.let { "年份" to it.toString() },
                            track.trackNo?.let { "歌曲序号" to it.toString() },
                            track.discNo?.let { "光盘序号" to it.toString() },
                            track.genres.takeIf { it.isNotEmpty() }?.let { "风格" to it.joinToString(" / ") { genre -> genre.name } },
                        ).ifEmpty { listOf("标签信息" to "暂无") },
                    )
                }
                item {
                    InfoCard("文件", listOfNotNull(
                        track.createdAt?.takeIf { it > 0 }?.let { "添加日期" to
                            java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) },
                        "文件位置" to (audio?.path?.takeIf { it.isNotBlank() } ?: "服务端未提供"),
                    ))
                }
                track.album?.let { album ->
                    item { InfoLink("专辑", album.name, coverUrl(album.coverId, 120)) { onAlbum(album) } }
                }
                track.artists.forEach { artist ->
                    item { InfoLink("歌手", artist.name, coverUrl(artist.coverId, 120)) { onArtist(artist) } }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, rows: List<Pair<String, String>>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = FnCard, contentColor = FnTextPrimary),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            rows.forEach { (label, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(label, color = FnTextSecondary, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(80.dp).alignByBaseline())
                    SelectionContainer(Modifier.weight(1f).alignByBaseline()) {
                        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoLink(label: String, value: String, cover: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = FnCard, contentColor = FnTextPrimary),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CoverImage(cover, value, Modifier.size(52.dp))
            Column(Modifier.weight(1f)) { Text(label, color = FnTextSecondary, style = MaterialTheme.typography.bodySmall); Text(value, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Icon(Icons.Rounded.ChevronRight, null, tint = FnTextSecondary)
        }
    }
}

private fun formatSampleRate(value: Int): String = if (value >= 1_000) "${value / 1_000.0} kHz" else "$value Hz"

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L * 1024L -> String.format(Locale.ROOT, "%.2f GiB", value / (1024.0 * 1024.0 * 1024.0))
    value >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MiB", value / (1024.0 * 1024.0))
    value >= 1024L -> String.format(Locale.ROOT, "%.1f KiB", value / 1024.0)
    else -> "$value B"
}

@Composable
private fun LibraryDetailScreen(
    detail: LibraryDetail,
    state: MusicUiState,
    coverUrl: (String?, Int) -> String?,
    onPlay: (List<Track>, Int) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onEditPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onPurgePlaylist: (Playlist) -> Unit,
    onRemoveTracksFromPlaylist: (PlaylistId, List<TrackId>) -> Unit,
    onBack: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val title: String
    val subtitle: String
    val metadata: String
    val coverId: String?
    var managedPlaylist: Playlist? = null
    when (detail) {
        is LibraryDetail.AlbumPage -> {
            val album = state.detailAlbum?.takeIf { it.id == detail.album.id } ?: detail.album
            title = album.name
            subtitle = album.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" }
            metadata = listOfNotNull("${album.trackCount} 首歌曲", album.releaseDate).joinToString(" · ")
            coverId = album.coverId
        }
        is LibraryDetail.ArtistPage -> {
            val artist = state.detailArtist?.takeIf { it.id == detail.artist.id } ?: detail.artist
            title = artist.name
            subtitle = "歌手"
            metadata = "${artist.albumCount} 张专辑 · ${artist.trackCount} 首歌曲"
            coverId = artist.coverId
        }
        is LibraryDetail.PlaylistPage -> {
            val playlist = state.detailPlaylist?.takeIf { it.id == detail.playlist.id } ?: detail.playlist
            managedPlaylist = playlist
            title = playlist.name
            subtitle = "我的歌单"
            metadata = playlist.trackCount.countLabel()
            coverId = playlist.coverId
        }
        is LibraryDetail.TrackPage -> return
        is LibraryDetail.PlaylistEditorPage -> return
    }
    var selectingTracks by remember(managedPlaylist?.id?.value) { mutableStateOf(false) }
    var selectedTrackIds by remember(managedPlaylist?.id?.value) { mutableStateOf(emptySet<TrackId>()) }
    val pageTitle = when (detail) {
        is LibraryDetail.AlbumPage -> "专辑"
        is LibraryDetail.ArtistPage -> "歌手"
        else -> "歌单"
    }
    DetailPageFrame(pageTitle, onBack) {
        if (state.detailLoading) {
            EmptyPane("正在加载详情…")
        } else {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom))),
            ) {
                val compact = maxWidth < 600.dp
                LazyColumn(
                    Modifier.fillMaxSize().testTag("library-detail-list"),
                    state = listState,
                    contentPadding = edgeToEdgeContentPadding(top = 12.dp, bottom = 36.dp, includeTopInset = false),
                ) {
                    item {
                        if (compact) {
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (managedPlaylist != null) {
                                    PlaylistCoverImage(coverId, coverUrl, title, Modifier.size(232.dp))
                                } else {
                                    CoverImage(coverUrl(coverId, 800), title, Modifier.size(232.dp))
                                }
                                DetailHeading(title, subtitle, metadata, state.detailTracks, onPlay)
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(32.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (managedPlaylist != null) {
                                    PlaylistCoverImage(coverId, coverUrl, title, Modifier.size(264.dp))
                                } else {
                                    CoverImage(coverUrl(coverId, 800), title, Modifier.size(264.dp))
                                }
                                Box(Modifier.weight(1f)) { DetailHeading(title, subtitle, metadata, state.detailTracks, onPlay) }
                            }
                        }
                    }
                    managedPlaylist?.let { playlist ->
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                item {
                                    OutlinedButton(
                                        onClick = {
                                            if (selectingTracks) {
                                                selectingTracks = false
                                                selectedTrackIds = emptySet()
                                            } else {
                                                selectingTracks = true
                                            }
                                        },
                                        enabled = state.detailTracks.isNotEmpty(),
                                        modifier = Modifier.height(48.dp),
                                    ) {
                                        Icon(if (selectingTracks) Icons.Rounded.Close else Icons.Rounded.CheckCircle, null, Modifier.padding(end = 6.dp))
                                        Text(if (selectingTracks) "取消多选" else "多选")
                                    }
                                }
                                if (selectingTracks) item {
                                    Button(
                                        onClick = {
                                            onRemoveTracksFromPlaylist(playlist.id, selectedTrackIds.toList())
                                            selectingTracks = false
                                            selectedTrackIds = emptySet()
                                        },
                                        enabled = selectedTrackIds.isNotEmpty() && !state.playlistBusy,
                                        modifier = Modifier.height(48.dp),
                                    ) {
                                        Icon(Icons.Rounded.RemoveCircleOutline, null, Modifier.padding(end = 6.dp))
                                        Text("移除 ${selectedTrackIds.size} 首")
                                    }
                                }
                                item {
                                    OutlinedButton(onClick = { onEditPlaylist(playlist) }, modifier = Modifier.height(48.dp)) {
                                        Icon(Icons.Rounded.Edit, null, Modifier.padding(end = 6.dp))
                                        Text("编辑")
                                    }
                                }
                                item {
                                    OutlinedButton(onClick = { onPurgePlaylist(playlist) }, modifier = Modifier.height(48.dp)) {
                                        Icon(Icons.Rounded.DeleteSweep, null, Modifier.padding(end = 6.dp))
                                        Text("清理失效")
                                    }
                                }
                                item {
                                    OutlinedButton(onClick = { onDeletePlaylist(playlist) }, modifier = Modifier.height(48.dp)) {
                                        Icon(Icons.Rounded.Delete, null, Modifier.padding(end = 6.dp), tint = MaterialTheme.colorScheme.error)
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        state.playlistMessage?.let { message ->
                            item { Text(message, color = FnTextSecondary, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
                        }
                    }
                    item {
                        Text(
                            "曲目",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                        )
                    }
                    when {
                        state.detailLoading -> item { EmptyPane("正在加载曲目…") }
                        state.detailError != null -> item { EmptyPane(state.detailError) }
                        state.detailTracks.isEmpty() -> item { EmptyPane("暂无曲目") }
                        else -> items(state.detailTracks, key = { it.id.value }) { track ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                if (selectingTracks) {
                                    androidx.compose.material3.Checkbox(
                                        checked = track.id in selectedTrackIds,
                                        onCheckedChange = { checked ->
                                            selectedTrackIds = if (checked) selectedTrackIds + track.id else selectedTrackIds - track.id
                                        },
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                                Box(Modifier.weight(1f)) {
                                    FavoriteTrackRow(
                                        track = track,
                                        state = state,
                                        coverUrl = coverUrl,
                                        onPlay = {
                                            if (selectingTracks) {
                                                selectedTrackIds = if (track.id in selectedTrackIds) selectedTrackIds - track.id else selectedTrackIds + track.id
                                            } else {
                                                onPlay(state.detailTracks, state.detailTracks.indexOf(track))
                                            }
                                        },
                                        onToggleFavorite = onToggleFavorite,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeading(
    title: String,
    subtitle: String,
    metadata: String,
    tracks: List<Track>,
    onPlay: (List<Track>, Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = FnTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(metadata, color = FnTextSecondary)
        Spacer(Modifier.height(6.dp))
        Button(onClick = { if (tracks.isNotEmpty()) onPlay(tracks, 0) }, enabled = tracks.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.padding(end = 6.dp))
            Text("播放全部")
        }
    }
}

@Composable
private fun PagingErrorPane(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

private fun SearchType.label(): String = when (this) {
    SearchType.Track -> "歌曲"
    SearchType.Album -> "专辑"
    SearchType.Artist -> "歌手"
    SearchType.Playlist -> "歌单"
}

private fun SearchItem.key(): String = when (this) {
    is SearchItem.TrackItem -> "track:${value.id.value}"
    is SearchItem.AlbumItem -> "album:${value.id.value}"
    is SearchItem.ArtistItem -> "artist:${value.id.value}"
    is SearchItem.PlaylistItem -> "playlist:${value.id.value}"
}

@Composable
private fun EmptyPane(text: String) {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { Text(text, color = FnTextSecondary) }
}

@Composable
private fun PlayerMorphOverlay(
    state: PlayerState,
    highResolutionCoverUrl: String?,
    containerAnchor: Rect,
    coverAnchor: Rect,
    expandedTarget: Boolean,
    progress: Animatable<Float, *>,
    contentCoverProgress: Float,
    showCover: Boolean,
    followContentCover: Boolean,
    onRequestClose: () -> Unit,
    onClosed: () -> Unit,
    onCoverClick: () -> Unit,
    content: @Composable (
        targetCoverModifier: Modifier,
        lyricsCoverModifier: Modifier,
        dragModifier: Modifier,
        contentModifier: Modifier,
    ) -> Unit,
) {
    val current = state.current ?: return
    val density = LocalDensity.current
    val animationScope = rememberCoroutineScope()
    val expressiveMotion = remember { MotionScheme.expressive() }
    val playbackCoverMotion = remember(expressiveMotion) {
        val materialSpec = expressiveMotion.defaultSpatialSpec<Float>()
        // The stock 0.8 damping produces only ~3px of overshoot on this cover.
        // Keep Material's stiffness, with a little more bounce for this hero motion.
        if (materialSpec is SpringSpec<Float>) {
            spring<Float>(dampingRatio = 0.7f, stiffness = materialSpec.stiffness)
        } else {
            materialSpec
        }
    }
    // Retarget from the current scale/velocity on rapid toggles; keep this state
    // across track changes. Only the large-cover endpoint uses the playback scale.
    val playbackCoverScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.73f,
        animationSpec = playbackCoverMotion,
        visibilityThreshold = 0.0001f,
        label = "player-playback-cover-scale",
    )
    // Geometry belongs to the player presentation, not the track. Keep the measured
    // anchors during a track change so the next frame cannot fall back to the mini cover.
    var targetCoverBounds by remember { mutableStateOf<Rect?>(null) }
    var lyricsCoverBounds by remember { mutableStateOf<Rect?>(null) }
    var contentCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var largeCoverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var lyricsCoverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun updateCoverBounds() {
        val root = contentCoordinates?.takeIf { it.isAttached } ?: return
        largeCoverCoordinates?.takeIf { it.isAttached }?.let {
            targetCoverBounds = root.localBoundingBoxOf(it, clipBounds = false)
        }
        lyricsCoverCoordinates?.takeIf { it.isAttached }?.let {
            if (followContentCover || lyricsCoverBounds == null) {
                lyricsCoverBounds = root.localBoundingBoxOf(it, clipBounds = false)
            }
        }
    }
    var dragOffsetPx by remember(current.track.id) { mutableFloatStateOf(0f) }
    var dismissVelocityPxPerSecond by remember(current.track.id) { mutableFloatStateOf(0f) }

    BoxWithConstraints(Modifier.fillMaxSize().testTag("player-morph-overlay")) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val dismissDistancePx = heightPx * 0.22f
        val dragFraction = (dragOffsetPx / dismissDistancePx).coerceIn(0f, 1f)
        val latestDragOffsetPx by rememberUpdatedState(dragOffsetPx)
        val dragState = rememberDraggableState { delta ->
            dragOffsetPx = (dragOffsetPx + delta).coerceAtLeast(0f)
        }
        val dragModifier = Modifier.draggable(
            state = dragState,
            orientation = Orientation.Vertical,
            enabled = progress.value > 0.85f,
            onDragStopped = { velocity ->
                val releasedDragFraction =
                    (latestDragOffsetPx / dismissDistancePx).coerceIn(0f, 1f)
                if (releasedDragFraction >= 0.55f || velocity > 1_400f) {
                    dismissVelocityPxPerSecond = velocity.coerceAtLeast(0f)
                    onRequestClose()
                } else {
                    animationScope.launch {
                        Animatable(latestDragOffsetPx).animateTo(
                            0f,
                            spring(dampingRatio = 0.82f, stiffness = 420f),
                            initialVelocity = velocity,
                        ) { dragOffsetPx = value }
                    }
                }
            },
        )

        LaunchedEffect(expandedTarget, targetCoverBounds) {
            if (expandedTarget) {
                if (targetCoverBounds != null) {
                    progress.animateTo(
                        1f,
                        expressiveMotion.slowSpatialSpec(),
                    )
                }
            } else {
                if (dragOffsetPx > 0f) {
                    val remainingVerticalPx = (containerAnchor.top - dragOffsetPx)
                        .coerceAtLeast(heightPx * 0.25f)
                    val progressVelocity =
                        -dismissVelocityPxPerSecond / remainingVerticalPx
                    progress.animateTo(
                        0f,
                        expressiveMotion.slowSpatialSpec(),
                        initialVelocity = progressVelocity,
                    )
                    dragOffsetPx = 0f
                    dismissVelocityPxPerSecond = 0f
                } else {
                    progress.animateTo(
                        0f,
                        expressiveMotion.slowSpatialSpec(),
                    )
                }
                onClosed()
            }
        }

        val p = progress.value.coerceIn(0f, 1f)
        val fullBounds = Rect(0f, 0f, widthPx, heightPx)
        val baseSurface = lerpRect(containerAnchor, fullBounds, p)
        val horizontalInsetPx = with(density) { 16.dp.toPx() } * dragFraction * p
        val surfaceBounds = Rect(
            left = baseSurface.left + horizontalInsetPx,
            top = baseSurface.top + dragOffsetPx * p,
            right = baseSurface.right - horizontalInsetPx,
            bottom = baseSurface.bottom + dragOffsetPx * p,
        )
        val startRadiusPx = containerAnchor.height / 2f
        val endRadiusPx = with(density) { 28.dp.toPx() }
        val radius = with(density) { lerpFloat(startRadiusPx, endRadiusPx, p).toDp() }

        Box(
            Modifier
                .fillMaxSize(),
        ) {
        Box(
            Modifier
                .offset { IntOffset(surfaceBounds.left.roundToInt(), surfaceBounds.top.roundToInt()) }
                .size(
                    with(density) { surfaceBounds.width.coerceAtLeast(1f).toDp() },
                    with(density) { surfaceBounds.height.coerceAtLeast(1f).toDp() },
                )
                .graphicsLayer {
                    alpha = morphInterval(p, 0.02f, 0.18f)
                    shape = RoundedCornerShape(radius)
                    clip = true
                    shadowElevation = 24.dp.toPx() * p
                }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF353B38), Color(0xFF202522), FnBackgroundBottom),
                    ),
                )
                .testTag("player-morph-surface"),
        )

        val contentAlpha = morphInterval(p, 0.48f, 0.82f)
        // Content and its surface share one origin throughout dismissal. Cover anchors stay
        // in this untransformed coordinate space, including while the lyrics header moves.
        val contentScale = (surfaceBounds.width / widthPx).coerceAtLeast(0.001f)
        content(
            Modifier
                .onGloballyPositioned {
                    largeCoverCoordinates = it
                    updateCoverBounds()
                }
                .graphicsLayer { alpha = 0f },
            Modifier
                .onGloballyPositioned {
                    lyricsCoverCoordinates = it
                    updateCoverBounds()
                }
                .graphicsLayer { alpha = 0f },
            dragModifier,
            Modifier.graphicsLayer {
                alpha = contentAlpha
                translationX = surfaceBounds.left
                translationY = surfaceBounds.top
                scaleX = contentScale
                scaleY = contentScale
                transformOrigin = TransformOrigin(0f, 0f)
            }.onGloballyPositioned {
                contentCoordinates = it
                updateCoverBounds()
            }.drawWithContent {
                val outline = Path().apply {
                    addRoundRect(RoundRect(
                        Rect(0f, 0f, size.width, surfaceBounds.height / contentScale),
                        CornerRadius(radius.toPx() / contentScale),
                    ))
                }
                clipPath(outline) { this@drawWithContent.drawContent() }
            }.testTag("player-morph-content"),
        )

        val largeCoverSlot = targetCoverBounds ?: coverAnchor
        val largeCoverTarget = Rect(
            center = largeCoverSlot.center,
            radius = largeCoverSlot.width * playbackCoverScale / 2f,
        )
        val targetCover = lyricsCoverBounds?.let { lyricsTarget ->
            lerpRect(
                largeCoverTarget,
                lyricsTarget,
                contentCoverProgress.coerceIn(0f, 1f),
            )
        } ?: largeCoverTarget
        val coverProgress = morphInterval(p, 0.04f, 1f)
        val transformedTarget = Rect(
            surfaceBounds.left + targetCover.left * contentScale,
            surfaceBounds.top + targetCover.top * contentScale,
            surfaceBounds.left + targetCover.right * contentScale,
            surfaceBounds.top + targetCover.bottom * contentScale,
        )
        val coverBounds = lerpRect(coverAnchor, transformedTarget, coverProgress)
        CoverImage(
            highResolutionCoverUrl,
            current.track.title,
            Modifier
                .offset { IntOffset(coverBounds.left.roundToInt(), coverBounds.top.roundToInt()) }
                .size(
                    with(density) { coverBounds.width.coerceAtLeast(1f).toDp() },
                    with(density) { coverBounds.height.coerceAtLeast(1f).toDp() },
                )
                .clip(RoundedCornerShape(with(density) { lerpFloat(8.dp.toPx(), 14.dp.toPx(), p).toDp() }))
                .graphicsLayer { alpha = if (showCover) 1f else 0f }
                .then(
                    if (contentCoverProgress >= 0.5f && showCover) {
                        Modifier.clickable(onClick = onCoverClick)
                    } else {
                        Modifier
                    },
                )
                .testTag("player-morph-cover")
                .then(if (contentCoverProgress < 0.5f) dragModifier else Modifier),
            requestSizePx = 1600,
        )
        }
    }
}

private fun lerpRect(start: Rect, end: Rect, fraction: Float): Rect = Rect(
    lerpFloat(start.left, end.left, fraction),
    lerpFloat(start.top, end.top, fraction),
    lerpFloat(start.right, end.right, fraction),
    lerpFloat(start.bottom, end.bottom, fraction),
)

private fun lerpFloat(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

@Composable
private fun animatedLyricBlurRadius(
    listState: LazyListState,
    index: Int,
    activeIndex: Int,
    followCurrent: Boolean,
    activeProgress: Float,
): Dp {
    val distanceRadius by remember(listState, index, activeIndex, followCurrent) {
        derivedStateOf {
            if (!followCurrent || activeIndex < 0) {
                0.dp
            } else {
                val layoutInfo = listState.layoutInfo
                val activeItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == activeIndex }
                val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                val normalizedDistance = if (activeItem == null || item == null) {
                    // Lazy layout can briefly omit either item while the viewport
                    // is moving. Preserve a meaningful distance instead of flashing
                    // the row sharp for that transient measurement frame.
                    (abs(index - activeIndex) / 4f).coerceIn(0f, 1f)
                } else {
                    val activeCenter = activeItem.offset + activeItem.size / 2f
                    val itemCenter = item.offset + item.size / 2f
                    val viewportHeight =
                        (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).coerceAtLeast(1)
                    (abs(itemCenter - activeCenter) / (viewportHeight * 0.42f)).coerceIn(0f, 1f)
                }
                lerpFloat(0.75f, 4f, normalizedDistance).dp
            }
        }
    }
    val targetRadius = distanceRadius * (1f - activeProgress.coerceIn(0f, 1f))
    return animateDpAsState(
        targetValue = targetRadius,
        animationSpec = tween(
            durationMillis = if (followCurrent) 180 else 90,
            easing = FastOutSlowInEasing,
        ),
        label = "lyrics-distance-blur",
    ).value
}

private fun progressiveChromeShader(top: Boolean): String =
    """
        uniform shader content;
        uniform float2 size;
        layout(color) uniform half4 tint;
        uniform float tintIntensity;

        half4 main(float2 coord) {
            half strength = ${if (top) "half(1.0 - smoothstep(size.y * 0.58, size.y, coord.y))" else "half(smoothstep(0.0, size.y * 0.18, coord.y))"};
            half tintAmount = half(tintIntensity) * mix(half(0.72), half(1.0), strength);
            half4 glass = mix(content.eval(coord), tint, tintAmount);
            return mix(half4(0.0), glass, strength);
        }
    """.trimIndent()

private fun morphInterval(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

@Composable
private fun NowPlayingLyricsScreen(
    state: PlayerState,
    musicState: MusicUiState,
    highResolutionCoverUrl: String?,
    lyricsMode: Boolean,
    queueMode: Boolean,
    lyricsProgress: Animatable<Float, *>,
    largeCoverModifier: Modifier,
    lyricsCoverModifier: Modifier,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onOpenLyrics: () -> Unit,
    onCloseLyrics: () -> Unit,
    onCloseQueue: () -> Unit,
    onOpenQueue: () -> Unit,
    onSelectQueueItem: (Int) -> Unit,
    onSelectHistoryItem: (Int) -> Unit,
    onClearPlaybackHistory: () -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onRemoveQueueItem: (Int, Track) -> Unit,
    onRemoveQueueItemDirect: (Int) -> Unit,
) {
    val current = state.current ?: return
    val immersiveMode = lyricsMode || queueMode
    val expressiveMotion = remember { MotionScheme.expressive() }
    val controlsVisibility = remember(current.track.id) { Animatable(1f) }
    var controlsShown by remember(current.track.id) { mutableStateOf(true) }
    val defaultContentVisibility = remember {
        Animatable(if (immersiveMode) 0f else 1f)
    }
    val lyricsHeaderVisibility = remember {
        Animatable(if (immersiveMode) 1f else 0f)
    }
    val lyricsListVisibility = remember {
        Animatable(if (immersiveMode) 1f else 0f)
    }
    val lyricsChromeVisibility = remember {
        Animatable(if (immersiveMode) 1f else 0f)
    }
    var controlsActivity by remember(current.track.id) { mutableStateOf(0) }
    val progressInteraction = remember { MutableInteractionSource() }
    val volumeInteraction = remember { MutableInteractionSource() }
    val progressPressed by progressInteraction.collectIsPressedAsState()
    val progressDragged by progressInteraction.collectIsDraggedAsState()
    val volumePressed by volumeInteraction.collectIsPressedAsState()
    val volumeDragged by volumeInteraction.collectIsDraggedAsState()
    val sliderInteracting = progressPressed || progressDragged || volumePressed || volumeDragged

    val favorite = musicState.favoriteOverrides[current.track.id] ?: current.track.isFavorite
    val canGoPrevious = state.canSkipPrevious
    val canGoNext = state.canSkipNext
    val lyricsListState = rememberLazyListState()
    val lyricsDragging by lyricsListState.interactionSource.collectIsDraggedAsState()
    val density = LocalDensity.current
    val playerBackgroundBackdrop = rememberLayerBackdrop()
    val onMore = LocalTrackAction.current
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maximumVolume = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var systemVolume by remember(current.track.id, audioManager) {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
    }
    var followCurrent by remember(current.track.id) { mutableStateOf(true) }
    var lyricsPositionReady by remember(current.track.id) { mutableStateOf(false) }
    var manualScrollActivity by remember(current.track.id) { mutableStateOf(0) }
    var pendingSeekPositionMs by remember(current.track.id) { mutableStateOf<Long?>(null) }
    var lastImmersiveContentWasLyrics by remember { mutableStateOf(lyricsMode) }
    var playerRootTopPx by remember { mutableFloatStateOf(0f) }
    var measuredLyricsHeaderBottomPx by remember { mutableFloatStateOf(Float.NaN) }
    val lyricPosition = rememberLyricPosition(
        current.queueEntryId,
        pendingSeekPositionMs ?: state.positionMs,
        state.durationMs,
        state.isPlaying && pendingSeekPositionMs == null && lyricsMode,
    )
    val activeIndex by remember(musicState.lyrics, lyricPosition) {
        derivedStateOf { activeLyricIndex(musicState.lyrics, lyricPosition.value) }
    }
    val lyricTimelines = remember(musicState.lyrics, state.durationMs) {
        musicState.lyrics.indices.map { resolveLyricTimeline(musicState.lyrics, it, state.durationMs) }
    }

    fun revealControls() {
        controlsActivity += 1
    }

    fun seekAndFollow(positionMs: Long) {
        pendingSeekPositionMs = positionMs
        followCurrent = true
        revealControls()
        onSeek(positionMs)
    }

    LaunchedEffect(immersiveMode, lyricsMode) {
        if (immersiveMode) lastImmersiveContentWasLyrics = lyricsMode
    }
    LaunchedEffect(state.positionMs, pendingSeekPositionMs) {
        val pendingPosition = pendingSeekPositionMs ?: return@LaunchedEffect
        if (abs(state.positionMs - pendingPosition) <= 750L) {
            pendingSeekPositionMs = null
        }
    }
    LaunchedEffect(pendingSeekPositionMs) {
        val pendingPosition = pendingSeekPositionMs ?: return@LaunchedEffect
        delay(1_500)
        if (pendingSeekPositionMs == pendingPosition) pendingSeekPositionMs = null
    }
    LaunchedEffect(followCurrent, manualScrollActivity, lyricsMode, current.track.id) {
        if (lyricsMode && !followCurrent) {
            delay(LyricsFollowResumeDelayMillis)
            followCurrent = true
        }
    }

    DisposableEffect(context, audioManager) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemVolume = audioManager
                    .getStreamVolume(AudioManager.STREAM_MUSIC)
                    .toFloat()
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        onDispose {
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    LaunchedEffect(immersiveMode) {
        if (immersiveMode) {
            launch {
                defaultContentVisibility.animateTo(
                    0f,
                    tween(durationMillis = 140, easing = FastOutLinearInEasing),
                )
            }
            launch {
                lyricsProgress.animateTo(
                    1f,
                    expressiveMotion.defaultSpatialSpec(),
                )
            }
            launch {
                delay(120)
                lyricsHeaderVisibility.animateTo(
                    1f,
                    tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                )
            }
            launch {
                delay(120)
                lyricsChromeVisibility.animateTo(
                    1f,
                    tween(durationMillis = 180, easing = LinearOutSlowInEasing),
                )
            }
            launch {
                if (lyricsListVisibility.value > 0.01f) {
                    lyricsListVisibility.animateTo(
                        0f,
                        tween(durationMillis = 80, easing = FastOutLinearInEasing),
                    )
                }
                if (lyricsMode) snapshotFlow { lyricsPositionReady }.first { it }
                snapshotFlow { lyricsProgress.value }.first { it >= 0.995f }
                delay(60)
                lyricsListVisibility.animateTo(
                    1f,
                    tween(durationMillis = 220, easing = LinearOutSlowInEasing),
                )
            }
        } else {
            // Finish the lyric exit before moving the shared cover. This keeps the
            // compact header and lyric rows out of the large-cover frame.
            lyricsChromeVisibility.snapTo(1f)
            coroutineScope {
                launch {
                    lyricsListVisibility.animateTo(
                        0f,
                        tween(durationMillis = 150, easing = FastOutLinearInEasing),
                    )
                }
                launch {
                    delay(24)
                    lyricsHeaderVisibility.animateTo(
                        0f,
                        tween(durationMillis = 116, easing = FastOutLinearInEasing),
                    )
                }
            }

            coroutineScope {
                val coverAnimation = launch {
                    lyricsProgress.animateTo(
                        0f,
                        expressiveMotion.defaultSpatialSpec(),
                    )
                }
                launch {
                    lyricsChromeVisibility.animateTo(
                        0f,
                        tween(durationMillis = 180, easing = FastOutLinearInEasing),
                    )
                }
                // Reveal the default metadata while the returning cover is already
                // clear of the compact lyric header.
                snapshotFlow { lyricsProgress.value }.first { it <= 0.52f }
                defaultContentVisibility.animateTo(
                    1f,
                    tween(durationMillis = 140, easing = LinearOutSlowInEasing),
                )
                coverAnimation.join()
            }
        }
    }
    // Activity restarts the idle timer, never an in-flight visibility animation.
    LaunchedEffect(lyricsMode, queueMode, controlsActivity, current.track.id, sliderInteracting, lyricsDragging) {
        controlsShown = true
        if (lyricsMode && !queueMode && !sliderInteracting && !lyricsDragging) {
            snapshotFlow { lyricsListVisibility.value }.first { it >= 0.995f }
            delay(3_000)
            controlsShown = false
        }
    }
    LaunchedEffect(controlsShown, current.track.id) {
        controlsVisibility.animateTo(
            if (controlsShown) 1f else 0f,
            tween(if (controlsShown) 180 else 700, easing = FastOutSlowInEasing),
        )
    }

        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(FnBackgroundBottom)
                .onGloballyPositioned { playerRootTopPx = it.boundsInRoot().top }
                .testTag("now-playing-lyrics-container"),
    ) {
        val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
        val safeTop = safeDrawingPadding.calculateTopPadding()
        val safeBottom = safeDrawingPadding.calculateBottomPadding()
        val compactHeight = maxHeight < 760.dp
        val horizontalPadding = if (maxWidth < 600.dp) 16.dp else 48.dp
        val lyricsHorizontalPadding = if (maxWidth < 600.dp) 24.dp else 64.dp
        val lyricsPressSurfaceInset = 8.dp
        val maximumCoverSize = if (maxWidth < 600.dp) 380.dp else 440.dp
        val heightLimitedCoverSize = (maxHeight - safeTop - safeBottom - 330.dp).coerceAtLeast(240.dp)
        val largeCoverSize = minOf(maxWidth - horizontalPadding * 2, maximumCoverSize, heightLimitedCoverSize)
        val contentHorizontalPadding = ((maxWidth - largeCoverSize) / 2).coerceAtLeast(horizontalPadding)
        val largeCoverTop = safeTop + if (compactHeight) 30.dp else 38.dp
        val lyricsCoverSize = 56.dp
        val lyricsHeaderTop = safeTop + 34.dp
        val p = lyricsProgress.value.coerceIn(0f, 1f)
        val headerBottomPx = measuredLyricsHeaderBottomPx.takeIf { it.isFinite() }
            ?: with(density) { (lyricsHeaderTop + lyricsCoverSize).toPx() }
        val focusTopPx = headerBottomPx +
            (with(density) { (maxHeight - safeBottom).toPx() } - headerBottomPx) * 0.18f
        val focusTop = with(density) { focusTopPx.toDp() }
        val lyricsTrailingSpaceHeight = (maxHeight - focusTop).coerceAtLeast(0.dp)
        FollowLyricPosition(
            listState = lyricsListState,
            contentKey = current.track.id to musicState.lyrics,
            activeIndex = activeIndex,
            enabled = lyricsMode,
            following = followCurrent,
            focusTopPx = focusTopPx,
            textInsetPx = with(density) { 4.dp.toPx() },
            ready = lyricsPositionReady,
            onReady = { lyricsPositionReady = true },
        )
        val defaultContentAlpha = defaultContentVisibility.value.coerceIn(0f, 1f)
        val lyricsHeaderAlpha = lyricsHeaderVisibility.value.coerceIn(0f, 1f)
        val lyricsListAlpha = lyricsListVisibility.value.coerceIn(0f, 1f)
        val lyricsChromeAlpha = lyricsChromeVisibility.value.coerceIn(0f, 1f)
        val controlsAlpha = lerpFloat(1f, controlsVisibility.value, p)
        val defaultContentTranslation = if (!immersiveMode && !lastImmersiveContentWasLyrics) {
            0f
        } else {
            with(density) { -8.dp.toPx() } * (1f - defaultContentAlpha)
        }
        val headerTapInteractionSource = remember(current.track.id) { MutableInteractionSource() }
        val controlsTranslationPx = with(density) { 176.dp.toPx() } * (1f - controlsAlpha)
        var bottomControlsTopPx by remember { mutableFloatStateOf(Float.NaN) }
        val controlsBottomPadding = with(density) {
            lerpFloat(
                (safeBottom + 244.dp).toPx(),
                (safeBottom + 28.dp).toPx(),
                1f - controlsAlpha,
            ).toDp()
        }
        // The controls are an overlay rather than a layout sibling of the queue. Use
        // their measured top edge instead of guessing their height, so the queue can
        // always scroll its final row above the persistent playback controls.
        val queueBottomPadding = bottomControlsTopPx
            .takeIf { it.isFinite() }
            ?.let { controlsTop ->
                with(density) {
                    (maxHeight.toPx() - (controlsTop - playerRootTopPx)).coerceAtLeast(0f).toDp() + 16.dp
                }
            }
            ?: controlsBottomPadding

        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(playerBackgroundBackdrop)
                .testTag("player-artwork-background"),
        ) {
            CoverImage(
                highResolutionCoverUrl,
                null,
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.22f
                        scaleY = 1.22f
                    }
                    .blur(64.dp),
                requestSizePx = 1600,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f)),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF211923).copy(alpha = 0.34f),
                            0.55f to Color(0xFF171319).copy(alpha = 0.28f),
                            1f to FnBackgroundBottom.copy(alpha = 0.88f),
                        ),
                    ),
            )
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = largeCoverTop)
                .size(largeCoverSize)
                .then(largeCoverModifier),
        )
        if (lyricsMode || (!queueMode && lastImmersiveContentWasLyrics)) Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = lyricsHorizontalPadding,
                    y = lyricsHeaderTop,
                )
                .zIndex(3f)
                .size(lyricsCoverSize),
        ) {
            // Lyrics retain a fixed cover anchor; the queue measures its scroll item.
            Box(Modifier.fillMaxSize().then(lyricsCoverModifier))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = safeTop)
                .height(32.dp)
                .zIndex(3f)
                .then(dragModifier)
                .semantics { contentDescription = "下拉收起播放器" },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier
                    .padding(top = 5.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .background(FnTextSecondary.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                    .testTag("player-drag-handle"),
            )
        }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = largeCoverTop + largeCoverSize + if (compactHeight) 24.dp else 30.dp)
                .widthIn(max = 680.dp)
                .fillMaxWidth()
                .padding(horizontal = contentHorizontalPadding)
                .graphicsLayer {
                    alpha = defaultContentAlpha
                    translationY = defaultContentTranslation
                }
                .then(if (defaultContentAlpha < 0.1f) Modifier.clearAndSetSemantics { } else Modifier),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.testTag("player-track-metadata"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        current.track.title,
                        fontSize = 22.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        current.track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                        color = FnTextPrimary.copy(alpha = 0.68f),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .pointerInput(current.track.id, favorite) {
                            detectTapGestures {
                                onToggleFavorite(current.track.copy(isFavorite = favorite))
                            }
                        }
                        .semantics {
                            contentDescription = "收藏"
                            onClick {
                                onToggleFavorite(current.track.copy(isFavorite = favorite))
                                true
                            }
                        }
                        .testTag("player-favorite-action"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        null,
                        tint = if (favorite) FnAccent else FnTextPrimary.copy(alpha = 0.82f),
                    )
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .pointerInput(current.track.id) {
                            detectTapGestures { onMore(current.track) }
                        }
                        .semantics {
                            contentDescription = "更多操作"
                            onClick {
                                onMore(current.track)
                                true
                            }
                        }
                        .testTag("player-more-action"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreVert, null, tint = FnTextPrimary.copy(alpha = 0.82f))
                }
            }
        }

        val compactHeader: @Composable (Modifier, Boolean) -> Unit = { headerModifier, inQueue ->
            Row(
                headerModifier
                    .height(lyricsCoverSize)
                    .fillMaxWidth()
                    .zIndex(3f)
                    .clickable(
                        interactionSource = headerTapInteractionSource,
                        indication = null,
                        enabled = immersiveMode,
                    ) {
                        revealControls()
                        if (inQueue) onCloseQueue() else onCloseLyrics()
                    }
                    .onGloballyPositioned {
                        if (!inQueue) measuredLyricsHeaderBottomPx = it.boundsInRoot().bottom - playerRootTopPx
                    }
                    .testTag(if (inQueue) "player-queue-header" else "player-lyrics-header"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (inQueue) {
                    Box(Modifier.size(lyricsCoverSize).testTag("player-queue-cover")) {
                        if (queueMode) Box(Modifier.fillMaxSize().then(lyricsCoverModifier))
                        CoverImage(
                            current.coverUrl, current.track.title,
                            Modifier.fillMaxSize().graphicsLayer {
                                alpha = if (queueMode && p >= 0.999f) 1f else 0f
                            },
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                } else {
                    Spacer(Modifier.size(lyricsCoverSize + 12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(current.track.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        current.track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                        color = FnTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .pointerInput(current.track.id, favorite) {
                            detectTapGestures {
                                revealControls()
                                onToggleFavorite(current.track.copy(isFavorite = favorite))
                            }
                        }
                        .semantics {
                            contentDescription = "收藏"
                            onClick {
                                revealControls()
                                onToggleFavorite(current.track.copy(isFavorite = favorite))
                                true
                            }
                        }
                        .testTag("player-lyrics-favorite-action"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        "收藏",
                        tint = if (favorite) FnAccent else FnTextSecondary,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .pointerInput(current.track.id) {
                            detectTapGestures {
                                revealControls()
                                onMore(current.track)
                            }
                        }
                        .semantics {
                            contentDescription = "更多操作"
                            onClick {
                                revealControls()
                                onMore(current.track)
                                true
                            }
                        }
                        .testTag("player-lyrics-more-action"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreVert, null, tint = FnTextPrimary.copy(alpha = 0.82f))
                }
            }

        }
        if (lyricsMode || (!queueMode && lastImmersiveContentWasLyrics)) {
            compactHeader(
                Modifier.align(Alignment.TopStart)
                    .padding(start = lyricsHorizontalPadding, top = lyricsHeaderTop, end = lyricsHorizontalPadding)
                    .graphicsLayer { alpha = lyricsHeaderAlpha }
                    .then(if (lyricsHeaderAlpha < 0.1f) Modifier.clearAndSetSemantics { } else Modifier),
                false,
            )
        }

        // Scroll content must remain above the background's header tint.
        Box(Modifier.fillMaxSize().zIndex(2f)) {
        AnimatedVisibility(
            visible = queueMode,
            enter = fadeIn(tween(durationMillis = 180, delayMillis = 24, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(durationMillis = 130, easing = FastOutLinearInEasing)),
            modifier = Modifier.zIndex(2f),
            label = "player-queue-content",
        ) {
            QueuePlayerContent(
                state = state,
                topPadding = lyricsHeaderTop,
                bottomPadding = queueBottomPadding,
                alpha = 1f,
                onSelect = { index -> revealControls(); onSelectQueueItem(index) },
                onSelectHistoryItem = { index -> revealControls(); onSelectHistoryItem(index) },
                onClearPlaybackHistory = onClearPlaybackHistory,
                currentHeader = {
                    compactHeader(Modifier.padding(horizontal = lyricsHorizontalPadding - 16.dp, vertical = 0.dp), true)
                },
                onMove = onMoveQueueItem,
                onRemove = onRemoveQueueItemDirect,
                onMore = onRemoveQueueItem,
                onToggleShuffle = { revealControls(); onToggleShuffle() },
                onCycleRepeatMode = { revealControls(); onCycleRepeatMode() },
            )
        }
        AnimatedVisibility(
            visible = !queueMode && (lyricsMode || lastImmersiveContentWasLyrics) && p > 0.01f,
            enter = fadeIn(tween(durationMillis = 180, delayMillis = 24, easing = LinearOutSlowInEasing)),
            exit = fadeOut(tween(durationMillis = 130, easing = FastOutLinearInEasing)),
            label = "player-lyrics-content",
        ) {
        if (musicState.lyrics.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(top = lyricsHeaderTop + lyricsCoverSize + 30.dp, bottom = controlsBottomPadding)
                    .graphicsLayer {
                        alpha = lyricsListAlpha
                    }
                    .then(if (lyricsListAlpha < 0.1f) Modifier.clearAndSetSemantics { } else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Lyrics, null, tint = FnTextSecondary, modifier = Modifier.size(40.dp))
                    Text("这首歌暂无歌词", color = FnTextSecondary)
                }
            }
        } else {
            val manualScrollConnection = remember(current.track.id, lyricsMode) {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        if (source == NestedScrollSource.UserInput && lyricsMode) {
                            pendingSeekPositionMs = null
                            followCurrent = false
                            manualScrollActivity += 1
                            revealControls()
                        }
                        return Offset.Zero
                    }
                }
            }
            LazyColumn(
                state = lyricsListState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(manualScrollConnection)
                    .pointerInput(current.track.id, lyricsMode) {
                        detectTapGestures { if (lyricsMode) revealControls() }
                    }
                    .graphicsLayer {
                        alpha = lyricsListAlpha
                    }
                    .then(if (lyricsListAlpha < 0.1f) Modifier.clearAndSetSemantics { } else Modifier)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val top = headerBottomPx.coerceIn(0f, size.height)
                        val fullBottom = size.height - safeBottom.toPx()
                        val controlsTop = bottomControlsTopPx.takeIf { it.isFinite() }
                            ?.minus(playerRootTopPx) ?: (size.height - (safeBottom + 244.dp).toPx())
                        val bottom = lerpFloat(fullBottom, controlsTop, controlsAlpha)
                            .coerceIn((top + 1f).coerceAtMost(size.height), size.height)
                        val fadeTopEnd = (top + 40.dp.toPx()).coerceAtMost(bottom)
                        val fadeBottomStart = (bottom - 32.dp.toPx()).coerceAtLeast(fadeTopEnd)
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                (top / size.height) to Color.Transparent,
                                (fadeTopEnd / size.height) to Color.Black,
                                (fadeBottomStart / size.height) to Color.Black,
                                (bottom / size.height).coerceAtMost(1f) to Color.Transparent,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    }
                    .testTag("lyrics-list"),
                contentPadding = PaddingValues(
                    start = lyricsPressSurfaceInset,
                    top = (focusTop - 4.dp).coerceAtLeast(0.dp),
                    end = lyricsPressSurfaceInset,
                    bottom = safeBottom,
                ),
                // KaraokeLineText already adds 8 dp above and below each lyric.
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(musicState.lyrics, key = { index, line -> "${line.timeMs ?: -1}:$index" }) { index, line ->
                    val active = index == activeIndex
                    val activeProgress by animateFloatAsState(
                        targetValue = if (active) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (active) 600 else 400,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "lyrics-line-active",
                    )
                    val blurRadius = animatedLyricBlurRadius(
                        listState = lyricsListState,
                        index = index,
                        activeIndex = activeIndex,
                        followCurrent = followCurrent,
                        activeProgress = activeProgress,
                    )
                    val interactionSource = remember { MutableInteractionSource() }
                    val interactionPressed by interactionSource.collectIsPressedAsState()
                    var touchPressed by remember { mutableStateOf(false) }
                    val pressed = touchPressed || interactionPressed
                    val pressProgress by animateFloatAsState(
                        targetValue = if (pressed) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = if (pressed) 70 else 120,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "lyrics-line-press",
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(
                                FnTextPrimary.copy(alpha = 0.12f * pressProgress),
                                RoundedCornerShape(16.dp),
                            )
                            // Observe the down immediately; clickable delays presses inside a LazyColumn.
                            // Do not consume events: scrolling and click/keyboard semantics stay with their owners.
                            .pointerInput(line.timeMs, lyricsMode) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    touchPressed = line.timeMs != null || lyricsMode
                                    try {
                                        // Finish dispatching this down before checking later events for scroll cancellation.
                                        awaitPointerEvent(PointerEventPass.Final)
                                        waitForUpOrCancellation()
                                    } finally {
                                        touchPressed = false
                                    }
                                }
                            }
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                enabled = line.timeMs != null || lyricsMode,
                            ) {
                                revealControls()
                                line.timeMs?.let(::seekAndFollow)
                            }
                            .padding(
                                horizontal = lyricsHorizontalPadding - lyricsPressSurfaceInset,
                                vertical = 4.dp,
                            )
                            .testTag("lyrics-line-$index")
                            .semantics { lyricBlurRadius = blurRadius.value },
                    ) {
                        Column(
                            Modifier
                                .blur(
                                    radius = blurRadius,
                                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                                )
                                .graphicsLayer {
                                    val focusScale = lerpFloat(0.96f, 1f, activeProgress)
                                    val textScale = focusScale * lerpFloat(1f, 0.96f, pressProgress)
                                    scaleX = textScale
                                    scaleY = textScale
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                },
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            AccompanistLyricText(
                                text = line.text.ifBlank { "♪" },
                                timeline = lyricTimelines.getOrNull(index),
                                position = { lyricPosition.value },
                                activeProgress = activeProgress,
                                baseColor = FnTextPrimary.copy(alpha = if (!followCurrent || activeIndex < 0) 0.58f else
                                    lerpFloat(0.58f, 0.24f, (blurRadius.value / 4f).coerceIn(0f, 1f))),
                                highlightColor = FnTextPrimary,
                            )
                            line.translation?.takeIf(String::isNotBlank)?.let { translation ->
                                Text(
                                    translation,
                                    color = FnTextSecondary.copy(
                                        alpha = lerpFloat(0.65f, 1f, activeProgress),
                                    ),
                                )
                            }
                        }
                    }
                }
                item(key = "lyrics-trailing-space") {
                    Spacer(
                        Modifier
                            .height(lyricsTrailingSpaceHeight)
                            .testTag("lyrics-trailing-space"),
                    )
                }
            }
        }
        }
        }

        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                // Keep the header fully protected, then give the blur enough room
                // to dissolve before the backdrop layer reaches its clipped edge.
                .height(if (queueMode) lyricsHeaderTop else lyricsHeaderTop + lyricsCoverSize + 90.dp)
                .zIndex(1f)
                .graphicsLayer { alpha = lyricsChromeAlpha }
                .drawPlainBackdrop(
                    backdrop = playerBackgroundBackdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = {
                        blur(32.dp.toPx())
                        runtimeShaderEffect(
                            "FnPlayerLyricsHeaderTint",
                            progressiveChromeShader(top = true),
                            "content",
                        ) {
                            setFloatUniform("size", size.width, size.height)
                            setColorUniform("tint", Color(0xFF18151D))
                            setFloatUniform("tintIntensity", 0.28f)
                        }
                    },
                )
                .testTag("player-lyrics-header-blur"),
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(safeBottom + 448.dp)
                .zIndex(1f)
                .graphicsLayer {
                    alpha = lyricsChromeAlpha * controlsAlpha
                    translationY = controlsTranslationPx
                }
                .drawPlainBackdrop(
                    backdrop = playerBackgroundBackdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = {
                        blur(32.dp.toPx())
                        runtimeShaderEffect(
                            "FnPlayerBottomControlsTint",
                            progressiveChromeShader(top = false),
                            "content",
                        ) {
                            setFloatUniform("size", size.width, size.height)
                            setColorUniform("tint", Color(0xFF111016))
                            setFloatUniform("tintIntensity", 0.34f)
                        }
                    },
                )
                .testTag("player-bottom-controls-blur"),
        )

        Surface(
            color = Color.Transparent,
            contentColor = FnTextPrimary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = contentHorizontalPadding)
                .padding(bottom = safeBottom + 20.dp)
                .zIndex(2f)
                .graphicsLayer {
                    alpha = controlsAlpha
                    translationY = controlsTranslationPx
                }
                .onGloballyPositioned { coordinates ->
                    if (!lyricsMode || controlsAlpha >= 0.995f) {
                        bottomControlsTopPx = coordinates.boundsInRoot().top
                    }
                }
                .then(if (lyricsMode && controlsAlpha < 0.1f) Modifier.clearAndSetSemantics { } else Modifier)
                .testTag("player-bottom-controls"),
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compactHeight) 2.dp else 6.dp)) {
                PlaybackProgress(
                    state,
                    modifier = Modifier.testTag("player-playback-progress"),
                    interactionSource = progressInteraction,
                ) { position -> seekAndFollow(position) }
                Row(
                    Modifier.fillMaxWidth().testTag("player-transport-controls"),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { revealControls(); onPrevious() }, enabled = canGoPrevious, modifier = Modifier.size(68.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(44.dp))
                    }
                    IconButton(onClick = { revealControls(); onToggle() }, modifier = Modifier.size(84.dp)) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            "播放或暂停",
                            Modifier
                                .size(60.dp)
                                .offset(x = if (state.isPlaying) 0.dp else 2.dp),
                            tint = FnTextPrimary,
                        )
                    }
                    IconButton(onClick = { revealControls(); onNext() }, enabled = canGoNext, modifier = Modifier.size(68.dp)) {
                        Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(44.dp))
                    }
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .testTag("player-volume-control"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeDown,
                        null,
                        tint = FnTextPrimary.copy(alpha = 0.58f),
                        modifier = Modifier.size(18.dp),
                    )
                    ThinPlayerSlider(
                        value = systemVolume,
                        onValueChange = { value ->
                            revealControls()
                            systemVolume = value
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC,
                                value.roundToInt().coerceIn(0, maximumVolume),
                                0,
                            )
                        },
                        valueRange = 0f..maximumVolume.toFloat(),
                        modifier = Modifier.weight(1f).testTag("player-volume-slider"),
                        interactionSource = volumeInteraction,
                    )
                    Icon(
                        Icons.AutoMirrored.Rounded.VolumeUp,
                        "媒体音量",
                        tint = FnTextPrimary.copy(alpha = 0.58f),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(
                    Modifier.height(
                        if (compactHeight) 12.dp else 48.dp,
                    ),
                )
                Row(
                    Modifier.fillMaxWidth().testTag("player-bottom-utilities"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        IconButton(
                            onClick = {
                                revealControls()
                                if (lyricsMode) onCloseLyrics() else onOpenLyrics()
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(
                                    if (lyricsMode) FnTextPrimary.copy(alpha = .14f) else Color.Transparent,
                                    CircleShape,
                                )
                                .semantics { selected = lyricsMode }
                                .testTag("player-lyrics-entry"),
                        ) {
                            Icon(
                                Icons.Rounded.Lyrics,
                                "展开完整歌词",
                                tint = if (lyricsMode) FnTextPrimary else FnTextSecondary,
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f).height(48.dp).testTag("player-center-entry-placeholder"))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        PlayerQueueOrRoamEntry(
                            isRoaming = state.isRoaming,
                            selected = queueMode,
                            onOpenQueue = {
                                revealControls()
                                if (queueMode) onCloseQueue() else onOpenQueue()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingScreen(
    state: PlayerState,
    musicState: MusicUiState,
    coverModifier: Modifier,
    modifier: Modifier = Modifier,
    dragModifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = state.current ?: return
    val activeIndex = activeLyricIndex(musicState.lyrics, state.positionMs)
    val activeLine = musicState.lyrics.getOrNull(activeIndex)
    val favorite = musicState.favoriteOverrides[current.track.id] ?: current.track.isFavorite
    val canGoPrevious = state.canSkipPrevious
    val canGoNext = state.canSkipNext
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF302A3B), FnBackgroundBottom))),
    ) {
        val compact = maxWidth < 600.dp
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = edgeToEdgeContentPadding(
                horizontal = if (compact) 24.dp else 56.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(dragModifier),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .padding(top = 4.dp, bottom = 2.dp)
                            .size(width = 36.dp, height = 5.dp)
                            .background(FnTextSecondary.copy(alpha = 0.55f), RoundedCornerShape(3.dp)),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.KeyboardArrowDown, "收起播放器")
                        }
                        Text("正在播放", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Text("${state.currentIndex + 1} / ${state.queue.size}", color = FnTextSecondary, style = tabularLabelStyle())
                        PlayerQueueOrRoamEntry(state.isRoaming, onOpenQueue = onOpenQueue)
                    }
                }
            }
            item {
                Spacer(Modifier.height(if (compact) 14.dp else 28.dp))
                CoverImage(
                    current.coverUrl,
                    current.track.title,
                    coverModifier.size(if (compact) 320.dp else 380.dp),
                )
            }
            item {
                Row(
                    Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(top = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            current.track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            current.track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                            color = FnTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        current.track.album?.name?.let { Text(it, color = FnTextSecondary, style = MaterialTheme.typography.bodySmall) }
                    }
                    IconButton(onClick = { onToggleFavorite(current.track.copy(isFavorite = favorite)) }, modifier = Modifier.size(48.dp)) {
                        Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏", tint = if (favorite) FnAccent else FnTextSecondary)
                    }
                }
            }
            item {
                Column(Modifier.widthIn(max = 680.dp).fillMaxWidth().padding(top = 20.dp)) {
                    Card(
                        onClick = onOpenLyrics,
                        modifier = Modifier.semantics { contentDescription = "展开完整歌词" },
                        colors = CardDefaults.cardColors(containerColor = FnCard, contentColor = FnTextPrimary),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                activeLine?.text?.ifBlank { "♪" }
                                    ?: musicState.lyrics.firstOrNull()?.text?.ifBlank { "♪" }
                                    ?: "暂无歌词",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 3,
                            )
                            activeIndex.takeIf { it >= 0 }?.let { index ->
                                musicState.lyrics.getOrNull(index + 1)?.text?.takeIf(String::isNotBlank)?.let { next ->
                                    Text(next, color = FnTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                                }
                            } ?: musicState.lyrics.getOrNull(1)?.text?.takeIf(String::isNotBlank)?.let { next ->
                                Text(next, color = FnTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                            }
                            Text("点击查看完整歌词", color = FnTextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    PlaybackProgress(state, onSeek = onSeek)
                }
            }
            item {
                Row(
                    Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(vertical = 22.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onToggleShuffle, enabled = !state.isRoaming, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Rounded.Shuffle, if (state.shuffleEnabled) "关闭随机播放" else "开启随机播放", tint = if (state.shuffleEnabled) FnAccent else FnTextSecondary)
                    }
                    IconButton(onClick = onPrevious, enabled = canGoPrevious, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(34.dp)) }
                    FilledIconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                        Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "播放或暂停", Modifier.size(38.dp))
                    }
                    IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(34.dp)) }
                    IconButton(onClick = onCycleRepeatMode, enabled = !state.isRoaming, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (state.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                            state.repeatMode.accessibilityLabel(),
                            tint = if (state.repeatMode == RepeatMode.Off) FnTextSecondary else FnAccent,
                        )
                    }
                }
            }
            state.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 24.dp)) }
            }
        }
    }
}

@Composable
private fun PlayerQueueOrRoamEntry(
    isRoaming: Boolean,
    selected: Boolean = false,
    onOpenQueue: () -> Unit,
) {
    if (isRoaming) {
        Box(
            Modifier
                .size(48.dp)
                .testTag("player-roam-indicator")
                .clearAndSetSemantics { contentDescription = "漫游模式" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(FnIcons.Roam, null, tint = FnAccent, modifier = Modifier.size(24.dp))
        }
    } else {
        IconButton(
            onClick = onOpenQueue,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (selected) FnTextPrimary.copy(alpha = .14f) else Color.Transparent)
                .semantics { this.selected = selected }
                .testTag("player-queue-entry"),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.QueueMusic,
                "打开待播队列",
                tint = if (selected) FnTextPrimary else FnTextSecondary,
            )
        }
    }
}

@Composable
private fun LyricsScreen(
    state: PlayerState,
    lyrics: List<LyricLine>,
    onBack: () -> Unit,
    onToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val current = state.current ?: return
    val playbackActiveIndex = activeLyricIndex(lyrics, state.positionMs)
    val canGoPrevious = state.canSkipPrevious
    val canGoNext = state.canSkipNext
    val listState = rememberLazyListState()
    var followCurrent by remember(current.track.id) { mutableStateOf(true) }
    var manualScrollActivity by remember(current.track.id) { mutableStateOf(0) }
    var pendingSeekPositionMs by remember(current.track.id) { mutableStateOf<Long?>(null) }
    val activeIndex = activeLyricIndex(lyrics, pendingSeekPositionMs ?: state.positionMs)

    fun seekAndFollow(positionMs: Long) {
        pendingSeekPositionMs = positionMs
        followCurrent = true
        onSeek(positionMs)
    }

    LaunchedEffect(playbackActiveIndex, pendingSeekPositionMs) {
        val pendingPosition = pendingSeekPositionMs ?: return@LaunchedEffect
        if (playbackActiveIndex == activeLyricIndex(lyrics, pendingPosition)) {
            pendingSeekPositionMs = null
        }
    }
    LaunchedEffect(pendingSeekPositionMs) {
        val pendingPosition = pendingSeekPositionMs ?: return@LaunchedEffect
        delay(1_500)
        if (pendingSeekPositionMs == pendingPosition) pendingSeekPositionMs = null
    }
    LaunchedEffect(followCurrent, manualScrollActivity, current.track.id) {
        if (!followCurrent) {
            delay(LyricsFollowResumeDelayMillis)
            followCurrent = true
        }
    }
    val manualScrollConnection = remember(current.track.id) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    pendingSeekPositionMs = null
                    followCurrent = false
                    manualScrollActivity += 1
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(activeIndex, followCurrent) {
        if (followCurrent && activeIndex >= 0) {
            listState.animateScrollToItem((activeIndex - 3).coerceAtLeast(0))
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF302A3B), FnBackgroundBottom))),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回播放界面")
            }
            Column(Modifier.weight(1f)) {
                Text("歌词", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(current.track.title, color = FnTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!followCurrent && activeIndex >= 0) {
                TextButton(onClick = { followCurrent = true }) { Text("回到当前") }
            }
        }
        if (lyrics.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Lyrics, null, tint = FnTextSecondary, modifier = Modifier.size(40.dp))
                    Text("这首歌暂无歌词", color = FnTextSecondary)
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(manualScrollConnection),
                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 36.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                itemsIndexed(lyrics, key = { index, line -> "${line.timeMs ?: -1}:$index" }) { index, line ->
                    val active = index == activeIndex
                    val activeProgress by animateFloatAsState(
                        targetValue = if (active) 1f else 0f,
                        animationSpec = tween(
                            durationMillis = 260,
                            easing = FastOutSlowInEasing,
                        ),
                        label = "lyrics-page-line-active",
                    )
                    val blurRadius = animatedLyricBlurRadius(
                        listState = listState,
                        index = index,
                        activeIndex = activeIndex,
                        followCurrent = followCurrent,
                        activeProgress = activeProgress,
                    )
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = line.timeMs != null) { line.timeMs?.let(::seekAndFollow) }
                            .padding(vertical = 4.dp)
                            .semantics { lyricBlurRadius = blurRadius.value },
                    ) {
                        Column(
                            Modifier.blur(
                                radius = blurRadius,
                                edgeTreatment = BlurredEdgeTreatment.Unbounded,
                            ),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                line.text.ifBlank { "♪" },
                                color = lerp(FnTextSecondary, FnTextPrimary, activeProgress),
                                fontSize = lerpFloat(19f, 23f, activeProgress).sp,
                                lineHeight = lerpFloat(28f, 32f, activeProgress).sp,
                                fontWeight = if (activeProgress >= 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            line.translation?.takeIf(String::isNotBlank)?.let { translation ->
                                Text(
                                    translation,
                                    color = FnTextSecondary.copy(
                                        alpha = lerpFloat(0.65f, 1f, activeProgress),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        Surface(
            color = Color(0xE624202E),
            contentColor = FnTextPrimary,
            tonalElevation = 8.dp,
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
                PlaybackProgress(state, onSeek = ::seekAndFollow)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onPrevious, enabled = canGoPrevious, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipPrevious, "上一首") }
                    FilledIconButton(onClick = onToggle, modifier = Modifier.size(60.dp)) {
                        Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "播放或暂停", Modifier.size(32.dp))
                    }
                    IconButton(onClick = onNext, enabled = canGoNext, modifier = Modifier.size(52.dp)) { Icon(Icons.Rounded.SkipNext, "下一首") }
                }
            }
        }
    }
}

/**
 * The queue lives in the same immersive player shell as lyrics.  It deliberately
 * owns only the central scroll region so the playback and volume controls stay
 * stable while users browse or reorder upcoming tracks.
 */
private data class QueueDisplayItem(val key: String, val item: PlayableTrack)

private fun PlayerState.upcomingDisplayItems(): List<QueueDisplayItem> = upcomingQueueIndices.mapNotNull { index ->
    queue.getOrNull(index)?.let { QueueDisplayItem("upcoming:${it.queueEntryId}", it) }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QueuePlayerContent(
    state: PlayerState,
    topPadding: Dp,
    bottomPadding: Dp,
    alpha: Float,
    onSelect: (Int) -> Unit,
    onSelectHistoryItem: (Int) -> Unit,
    onClearPlaybackHistory: () -> Unit,
    currentHeader: @Composable () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMore: (Int, Track) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
) {
    // History is before the initial anchor, not an independently animated panel.
    // Stable keys preserve this anchor when the current song/history changes.
    val historyCount = state.playbackHistory.size
    // The spacer separates the final history row from the current-song summary.
    val currentHeaderIndex = if (historyCount == 0) 0 else historyCount + 2
    val listState = remember(state.playbackSessionId) {
        LazyListState(firstVisibleItemIndex = currentHeaderIndex)
    }
    val density = LocalDensity.current
    val edgeThresholdPx = with(density) { 64.dp.toPx() }
    val maxAutoScrollPerFramePx = with(density) { 12.dp.toPx() }
    var displayedQueue by remember(state.playbackSessionId) { mutableStateOf(state.upcomingDisplayItems()) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var draggedCenterY by remember { mutableFloatStateOf(0f) }
    var autoScrollPerFramePx by remember { mutableFloatStateOf(0f) }
    val latestState by rememberUpdatedState(state)
    val latestMove by rememberUpdatedState(onMove)
    var revealedKey by remember { mutableStateOf<String?>(null) }

    fun queueIndex(entry: QueueDisplayItem): Int = latestState.queue.indexOfFirst { it.queueEntryId == entry.item.queueEntryId }

    fun finishDrag() {
        val key = draggedKey ?: return
        val original = latestState.upcomingDisplayItems()
        val source = original.indexOfFirst { it.key == key }
        val destination = displayedQueue.indexOfFirst { it.key == key }
        if (source >= 0 && destination >= 0 && source != destination && original.size == displayedQueue.size) {
            latestMove(queueIndex(original[source]), queueIndex(original[destination]))
        }
        draggedKey = null
        autoScrollPerFramePx = 0f
    }

    // LazyListState's initial index can be clamped before conditional history
    // rows have been measured. Re-apply the current-song anchor once after the
    // queue enters composition so history starts above the viewport.
    LaunchedEffect(Unit) {
        listState.scrollToItem(currentHeaderIndex)
    }

    fun updateDrag() {
        val key = draggedKey ?: return
        val source = displayedQueue.indexOfFirst { it.key == key }
        if (source < 0) return
        val info = listState.layoutInfo
        val stickyControls = info.visibleItemsInfo.firstOrNull { it.key == "queue-modes" }
        val topEdge = maxOf(0f, stickyControls?.let { (it.offset + it.size).toFloat() } ?: 0f)
        val bottomEdge = info.viewportEndOffset.toFloat()
        val firstPending = info.visibleItemsInfo.firstOrNull { it.key == displayedQueue.firstOrNull()?.key }
        val lastPending = info.visibleItemsInfo.firstOrNull { it.key == displayedQueue.lastOrNull()?.key }
        // While dragging upward, keep scrolling until the first pending row reaches
        // the sticky controls. Do not continue into the history/current header.
        val pendingAbove = firstPending == null || firstPending.offset < topEdge
        val pendingBelow = lastPending == null || lastPending.offset + lastPending.size > bottomEdge
        autoScrollPerFramePx = when {
            pendingAbove && draggedCenterY < topEdge + edgeThresholdPx ->
                -maxAutoScrollPerFramePx * ((topEdge + edgeThresholdPx - draggedCenterY) / edgeThresholdPx).coerceIn(0f, 1f)
            pendingBelow && draggedCenterY > bottomEdge - edgeThresholdPx ->
                maxAutoScrollPerFramePx * ((draggedCenterY - bottomEdge + edgeThresholdPx) / edgeThresholdPx).coerceIn(0f, 1f)
            else -> 0f
        }
        val targetInfo = info.visibleItemsInfo
            .filter { item -> item.offset + item.size > topEdge && displayedQueue.any { it.key == item.key } }
            .minByOrNull { abs(it.offset + it.size / 2f - draggedCenterY) } ?: return
        val target = displayedQueue.indexOfFirst { it.key == targetInfo.key }
        // Wait for the preceding local move to be laid out before crossing again.
        val currentInfo = info.visibleItemsInfo.firstOrNull { it.key == key }
        val firstQueueIndex = currentHeaderIndex + 2
        if (currentInfo != null && currentInfo.index != firstQueueIndex + source) return
        if (target == source || target < 0) return
        displayedQueue = displayedQueue.toMutableList().also { it.add(target, it.removeAt(source)) }
    }

    LaunchedEffect(state.queue, state.currentIndex, state.playbackOrder, state.repeatMode, state.error) {
        // External queue changes end the gesture before an old index can affect another entry.
        draggedKey = null
        autoScrollPerFramePx = 0f
        displayedQueue = state.upcomingDisplayItems()
    }
    LaunchedEffect(state.playbackSessionId, state.current?.queueEntryId) {
        draggedKey = null
        revealedKey = null
        autoScrollPerFramePx = 0f
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { if (it) revealedKey = null }
    }
    LaunchedEffect(draggedKey) {
        while (draggedKey != null) {
            withFrameNanos { }
            if (abs(autoScrollPerFramePx) > 0.5f) {
                // The pointer stays in viewport coordinates. Only the underlying
                // list moves; translating the pointer too would double the scroll.
                listState.scrollBy(autoScrollPerFramePx)
            }
            updateDrag()
        }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize()
            .padding(top = topPadding, bottom = bottomPadding)
            .graphicsLayer { this.alpha = alpha }
            .then(if (alpha < 0.1f) Modifier.clearAndSetSemantics { } else Modifier),
    ) {
        // Even an empty pending queue must leave enough scroll range to start at
        // the current header with every history row above the viewport.
        val tailPadding = (maxHeight - 78.dp - 66.dp - 48.dp - 68.dp * displayedQueue.size)
            .coerceAtLeast(48.dp)
        LazyColumn(
            state = listState,
            userScrollEnabled = draggedKey == null,
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // Fade the songs themselves, not just the artwork behind them.
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            ((size.height - 40.dp.toPx()).coerceAtLeast(0f) / size.height.coerceAtLeast(1f)) to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                }.testTag("player-queue-list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = tailPadding),
        ) {
            if (state.playbackHistory.isNotEmpty()) {
                item(key = "history-title") {
                    Row(
                        Modifier.fillMaxWidth().height(52.dp)
                            .padding(horizontal = 8.dp)
                            .testTag("player-playback-history"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("播放记录", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "清空", color = FnTextSecondary,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onClearPlaybackHistory,
                            ).padding(12.dp).semantics { contentDescription = "清空播放记录" },
                        )
                    }
                }
                itemsIndexed(
                    state.playbackHistory,
                    key = { _, item -> "history:" + item.queueEntryId },
                ) { index, item ->
                    Row(
                        Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 8.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectHistoryItem(index) },
                            ).semantics { contentDescription = "播放记录：${item.track.title}" },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CoverImage(item.coverUrl, item.track.title, Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                item.track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                                color = FnTextSecondary, style = MaterialTheme.typography.bodySmall,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                item(key = "history-current-gap") {
                    Spacer(Modifier.height(20.dp))
                }
            }
            item(key = "queue-current") {
                Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) { currentHeader() }
            }
            stickyHeader(key = "queue-modes") {
                Column(Modifier.fillMaxWidth().testTag("player-queue-sticky-header")) {
                    Box(
                        Modifier.fillMaxWidth().padding(bottom = 18.dp)
                            .testTag("player-queue-mode-controls"),
                    ) {
                        QueueModeControls(
                            state,
                            { revealedKey = null; onToggleShuffle() },
                            { revealedKey = null; onCycleRepeatMode() },
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)
                            .testTag("player-queue-title"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("待播队列", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (state.shuffleEnabled) "${displayedQueue.size} 首 · 随机播放" else "${displayedQueue.size} 首",
                            color = FnTextSecondary, style = tabularBodyStyle(),
                        )
                    }
                }
            }
            itemsIndexed(displayedQueue, key = { _, item -> item.key }) { index, entry ->
                val dragging = draggedKey == entry.key
                val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.key }
                val translation = if (dragging && itemInfo != null) {
                    draggedCenterY - itemInfo.offset - itemInfo.size / 2f
                } else 0f
                val dragState = rememberDraggableState { delta ->
                    if (draggedKey == entry.key) {
                        draggedCenterY += delta
                        updateDrag()
                    }
                }
                QueueTrackRow(
                    item = entry.item,
                    current = false,
                    dragging = dragging,
                    dragOffsetY = translation,
                    modifier = (if (dragging) Modifier else Modifier.animateItem()).drawWithContent {
                        val header = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "queue-modes" }
                        val row = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.key }
                        val covered = if (header != null && row != null) {
                            (header.offset + header.size - row.offset).toFloat()
                        } else 0f
                        // The lifted row can travel outside its own layout bounds, but
                        // neither it nor regular rows may cover the pinned controls.
                        if (dragging && row != null) {
                            clipRect(
                                left = -8.dp.toPx(), top = covered,
                                right = size.width + 8.dp.toPx(),
                                bottom = (listState.layoutInfo.viewportEndOffset - row.offset).toFloat(),
                            ) { this@drawWithContent.drawContent() }
                        } else {
                            clipRect(top = covered.coerceIn(0f, size.height)) { this@drawWithContent.drawContent() }
                        }
                    },
                    revealed = revealedKey == entry.key,
                    onReveal = { open ->
                        if (open) revealedKey = entry.key else if (revealedKey == entry.key) revealedKey = null
                    },
                    onClick = {
                        if (revealedKey != null) revealedKey = null
                        else queueIndex(entry).takeIf { it >= 0 }?.let(onSelect)
                    },
                    onRemove = {
                        revealedKey = null
                        queueIndex(entry).takeIf { it >= 0 }?.let(onRemove)
                    },
                    onMore = {
                        revealedKey = null
                        queueIndex(entry).takeIf { it >= 0 }?.let { onMore(it, entry.item.track) }
                    },
                    onMoveUp = if (index > 0) ({ latestMove(queueIndex(entry), queueIndex(displayedQueue[index - 1])) }) else null,
                    onMoveDown = if (index < displayedQueue.lastIndex) ({ latestMove(queueIndex(entry), queueIndex(displayedQueue[index + 1])) }) else null,
                    dragModifier = Modifier
                        .semantics { contentDescription = "长按拖动排序：${entry.item.track.title}" }
                        .draggable(
                            state = dragState, orientation = Orientation.Vertical,
                            enabled = displayedQueue.size > 1, startDragImmediately = true,
                            onDragStarted = {
                                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.key }
                                if (info != null) {
                                    revealedKey = null
                                    draggedCenterY = info.offset + info.size / 2f
                                    draggedKey = entry.key
                                    autoScrollPerFramePx = 0f
                                }
                            },
                            onDragStopped = { finishDrag() },
                        ),
                )
            }
        }
    }
}

@Composable
private fun QueueModeControls(
    state: PlayerState,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QueueModePill(
            icon = Icons.Rounded.Shuffle,
            label = if (state.shuffleEnabled) "关闭随机播放" else "开启随机播放",
            selected = state.shuffleEnabled,
            modifier = Modifier.weight(1f),
            onClick = onToggleShuffle,
        )
        QueueModePill(
            icon = if (state.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            label = state.repeatMode.accessibilityLabel(),
            selected = state.repeatMode != RepeatMode.Off,
            modifier = Modifier.weight(1f),
            onClick = onCycleRepeatMode,
        )
    }
}


@Composable
private fun QueueModePill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                if (selected) FnTextPrimary.copy(alpha = .88f) else FnTextPrimary.copy(alpha = .12f),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .height(48.dp)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(22.dp),
            tint = when {
                !enabled -> FnTextSecondary.copy(alpha = .45f)
                selected -> FnBackgroundTop
                else -> FnTextPrimary
            },
        )
    }
}

private enum class QueueSwipePosition { Closed, Open }

@Composable
private fun QueueTrackRow(
    item: PlayableTrack,
    current: Boolean,
    dragging: Boolean,
    dragOffsetY: Float,
    modifier: Modifier = Modifier,
    revealed: Boolean,
    onReveal: (Boolean) -> Unit,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onMore: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    dragModifier: Modifier,
) {
    val rowShape = RoundedCornerShape(16.dp)
    val actionWidth = with(LocalDensity.current) { 72.dp.toPx() }
    val swipe = remember(item.queueEntryId, actionWidth) {
        AnchoredDraggableState(
            QueueSwipePosition.Closed,
            DraggableAnchors {
                QueueSwipePosition.Closed at 0f
                QueueSwipePosition.Open at -actionWidth
            },
        )
    }
    val latestReveal by rememberUpdatedState(onReveal)
    LaunchedEffect(swipe) {
        snapshotFlow { swipe.offset < -1f }.distinctUntilChanged().collect { latestReveal(it) }
    }
    LaunchedEffect(revealed, dragging) {
        if (!revealed || dragging) swipe.animateTo(QueueSwipePosition.Closed)
    }
    val offset = swipe.offset.takeIf { it.isFinite() } ?: 0f
    Box(
        modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 2f else 0f)
            .graphicsLayer {
                translationY = dragOffsetY
                scaleX = if (dragging) 1.025f else 1f
                scaleY = if (dragging) 1.025f else 1f
                shape = rowShape
                clip = false
                shadowElevation = if (dragging) 20.dp.toPx() else 0f
            }
            .testTag("player-queue-row-${item.track.id.value}")
            .semantics {
                customActions = buildList {
                    add(CustomAccessibilityAction("从待播队列移除") { onRemove(); true })
                    add(CustomAccessibilityAction("更多操作") { onMore(); true })
                    onMoveUp?.let { action -> add(CustomAccessibilityAction("上移") { action(); true }) }
                    onMoveDown?.let { action -> add(CustomAccessibilityAction("下移") { action(); true }) }
                }
            },
    ) {
        if (offset < -0.5f) {
            Box(
                Modifier.align(Alignment.CenterEnd).width(72.dp).height(68.dp)
                    .graphicsLayer { alpha = (-offset / actionWidth).coerceIn(0f, 1f) },
                contentAlignment = Alignment.Center,
            ) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(48.dp).background(Color(0xFFFF375F), CircleShape)
                        .testTag("player-queue-remove-${item.queueEntryId}"),
                ) {
                    Icon(Icons.Rounded.RemoveCircleOutline, "从待播队列移除：${item.track.title}", tint = Color.White)
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset }
                .background(
                    when {
                        dragging -> FnBackgroundTop.copy(alpha = .96f)
                        current -> FnTextPrimary.copy(alpha = .14f)
                        else -> Color.Transparent
                    },
                    rowShape,
                )
                .anchoredDraggable(swipe, Orientation.Horizontal, enabled = !dragging)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val trackInteraction = remember { MutableInteractionSource() }
            Row(
                Modifier.weight(1f).combinedClickable(
                    interactionSource = trackInteraction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onMore,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CoverImage(item.coverUrl, item.track.title, Modifier.size(52.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        item.track.title,
                        color = if (current) FnAccent else FnTextPrimary,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (current) "正在播放" else item.track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                        color = FnTextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(dragModifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DragHandle, "长按拖动排序", tint = FnTextSecondary)
            }
        }
    }
}

@Composable
private fun PlaybackProgress(
    state: PlayerState,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onSeek: (Long) -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(1)
    var previewPosition by remember(state.current?.track?.id) { mutableStateOf<Float?>(null) }
    val displayed = previewPosition ?: state.positionMs.toFloat().coerceIn(0f, duration.toFloat())
    ThinPlayerSlider(
        value = displayed,
        onValueChange = { previewPosition = it },
        onValueChangeFinished = {
            previewPosition?.let { onSeek(it.toLong()) }
            previewPosition = null
        },
        valueRange = 0f..duration.toFloat(),
        modifier = modifier.fillMaxWidth(),
        interactionSource = interactionSource,
    )
    Box(Modifier.fillMaxWidth()) {
        Text(
            playbackQualityLabel(state.current?.track?.audioSpec),
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 52.dp).testTag("player-quality"),
            color = FnTextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth()) {
        Text(formatDuration(displayed.toLong()), color = FnTextSecondary, style = tabularBodyStyle())
        Spacer(Modifier.weight(1f))
        Text(
            "−${formatDuration((state.durationMs - displayed.toLong()).coerceAtLeast(0L))}",
            color = FnTextSecondary,
            style = tabularBodyStyle(),
        )
        }
    }
}

@Composable
private fun ThinPlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        valueRange = valueRange,
        modifier = modifier,
        thumb = {
            Spacer(Modifier.size(10.dp).graphicsLayer { alpha = 0f })
        },
        track = { sliderState ->
            val start = valueRange.start
            val range = (valueRange.endInclusive - start).coerceAtLeast(0.0001f)
            val fraction = ((sliderState.value - start) / range).coerceIn(0f, 1f)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp),
            ) {
                val centerY = size.height / 2f
                val strokeWidth = 4.dp.toPx()
                val thumbRadius = 5.dp.toPx()
                val thumbCenterX = size.width * fraction
                drawLine(
                    color = FnTextPrimary.copy(alpha = 0.24f),
                    start = Offset(0f, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = FnTextPrimary.copy(alpha = 0.88f),
                    start = Offset(0f, centerY),
                    end = Offset(thumbCenterX, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = FnTextPrimary,
                    radius = thumbRadius,
                    center = Offset(thumbCenterX, centerY),
                )
            }
        },
    )
}

private fun activeLyricIndex(lyrics: List<LyricLine>, positionMs: Long): Int {
    if (lyrics.none { it.timeMs != null }) return -1
    return lyrics.indexOfLast { line -> line.timeMs?.let { it <= positionMs } == true }
}

private fun RepeatMode.accessibilityLabel(): String = when (this) {
    RepeatMode.Off -> "循环已关闭，点击切换为列表循环"
    RepeatMode.All -> "列表循环，点击切换为单曲循环"
    RepeatMode.One -> "单曲循环，点击关闭循环"
}

@Composable
private fun tabularBodyStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")

@Composable
private fun tabularLabelStyle(): TextStyle = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum")

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun Int?.countLabel(): String = this?.let { "$it 首歌曲" } ?: "歌曲数待载"
