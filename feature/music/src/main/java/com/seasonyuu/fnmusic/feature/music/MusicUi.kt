package com.seasonyuu.fnmusic.feature.music

import androidx.compose.material3.pulltorefresh.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.style.TextAlign
import com.seasonyuu.fnmusic.core.model.TrackMetadataEdit
import com.seasonyuu.fnmusic.core.model.TrackTagOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.graphics.luminance
import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.waterfall
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.seasonyuu.fnmusic.core.designsystem.dynamicBottomBarGesture
import com.seasonyuu.fnmusic.core.designsystem.dynamicBottomBarNestedScrollConnection
import com.seasonyuu.fnmusic.core.designsystem.FnAccent
import com.seasonyuu.fnmusic.core.designsystem.FnBackgroundBottom
import com.seasonyuu.fnmusic.core.designsystem.FnBackgroundTop
import com.seasonyuu.fnmusic.core.designsystem.FnCard
import com.seasonyuu.fnmusic.core.designsystem.FlowingLightBackground
import com.seasonyuu.fnmusic.core.designsystem.FlowingLightStyle
import com.seasonyuu.fnmusic.core.designsystem.FnGradientBackground
import com.seasonyuu.fnmusic.core.designsystem.FnIcons
import com.seasonyuu.fnmusic.core.designsystem.FnNavigationSurface
import com.seasonyuu.fnmusic.core.designsystem.FnProgressiveSystemBars
import com.seasonyuu.fnmusic.core.designsystem.LiquidBottomTab
import com.seasonyuu.fnmusic.core.designsystem.LiquidButton
import com.seasonyuu.fnmusic.core.designsystem.LiquidBottomTabs
import com.seasonyuu.fnmusic.core.designsystem.LocalFnBackdrop
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary
import com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary
import com.seasonyuu.fnmusic.core.designsystem.PlaybackToggleIcon
import com.seasonyuu.fnmusic.core.designsystem.rememberDynamicBottomBarState
import com.seasonyuu.fnmusic.core.designsystem.R
import com.seasonyuu.fnmusic.core.designsystem.TrackRow
import com.seasonyuu.fnmusic.core.model.PlaybackCachePreference
import com.seasonyuu.fnmusic.core.model.AppearancePreference
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.MusicUser
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
import androidx.compose.runtime.SideEffect
import com.seasonyuu.fnmusic.core.designsystem.contrastingForeground
import com.seasonyuu.fnmusic.core.designsystem.FnAccentIcon
import androidx.compose.material3.LocalContentColor
import com.seasonyuu.fnmusic.core.model.SearchType
import com.seasonyuu.fnmusic.core.model.AlbumId
import com.seasonyuu.fnmusic.core.model.ArtistId
import com.seasonyuu.fnmusic.core.model.PlaylistId
import com.seasonyuu.fnmusic.data.SearchItem
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
    Library("音乐库", FnIcons.Library),
    Search("搜索", FnIcons.Search),
    Profile("我的", Icons.Rounded.Person),
}

internal val PlayerPageAlphaKey = SemanticsPropertyKey<Float>("PlayerPageAlpha")
internal var SemanticsPropertyReceiver.playerPageAlpha by PlayerPageAlphaKey
internal val LyricBlurRadiusKey = SemanticsPropertyKey<Float>("LyricBlurRadius")
internal var SemanticsPropertyReceiver.lyricBlurRadius by LyricBlurRadiusKey
private const val LyricsFollowResumeDelayMillis = 8_000L

enum class CatalogSection(val title: String) {
    Tracks("最近添加"), Albums("专辑"), Artists("歌手"), Favorites("收藏"), Recent("最近播放"), Playlists("歌单"), TrackTotal("曲目总数"),
}

data class MusicUiState(
    val loading: Boolean = true,
    val pendingSections: Set<CatalogSection> = CatalogSection.entries.toSet(),
    val loadedSections: Set<CatalogSection> = emptySet(),
    val sectionErrors: Map<CatalogSection, String> = emptyMap(),
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
    val favoriteOverrides: Map<TrackId, Boolean> = emptyMap(),
    val serverName: String = "飞牛音乐",
    val user: MusicUser? = null,
    val themeColor: com.seasonyuu.fnmusic.core.model.ThemeColorPreference = com.seasonyuu.fnmusic.core.model.ThemeColorPreference.Default,
    val appearance: AppearancePreference = AppearancePreference.Dark,
    val profileError: String? = null,
    val lyrics: List<LyricLine> = emptyList(),
    val liquidGlassEnabled: Boolean = true,
    val liquidGlassBlur: Float = com.seasonyuu.fnmusic.core.model.LiquidGlassBlur.Default,
    val liquidGlassSaveError: String? = null,
    val streamingQuality: com.seasonyuu.fnmusic.core.model.StreamingQualityPreference = com.seasonyuu.fnmusic.core.model.StreamingQualityPreference(),
    val cachePreference: PlaybackCachePreference = PlaybackCachePreference(),
    val cacheUsage: Pair<Long, Int> = 0L to 0,
    val cacheBytes: Long = 512L * 1024L * 1024L,
    val detailKey: DetailRequestKey? = null,
    val detailCache: Map<DetailRequestKey, MusicDetailSnapshot> = emptyMap(),
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
internal fun edgeToEdgeContentPadding(
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

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MusicShell(
    state: MusicUiState,
    playerState: PlayerState,
    pagedTracks: (TrackSort) -> Flow<PagingData<Track>>,
    pagedAlbums: (AlbumSort) -> Flow<PagingData<Album>>,
    pagedArtists: Flow<PagingData<Artist>>,
    pagedFavorites: Flow<PagingData<Track>>,
    pagedSearch: Flow<PagingData<SearchItem>>,
    coverUrl: (String?, Int) -> String?,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onSearchType: (SearchType) -> Unit,
    onRoam: () -> Unit,
    onPlayAllTracks: (TrackSort) -> Unit,
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
    onStreamingQualityChange: suspend (com.seasonyuu.fnmusic.core.model.StreamingQualityPreference) -> Unit = {},
    administration: com.seasonyuu.fnmusic.core.model.MusicAdministration? = null,
    onCachePreferenceChange: suspend (PlaybackCachePreference) -> Unit = {},
    onClearCache: suspend () -> Unit = {},
    onThemeColorChange: suspend (com.seasonyuu.fnmusic.core.model.ThemeColorPreference) -> Unit = {},
    onAppearanceChange: suspend (AppearancePreference) -> Unit = {},
    onRefreshProfile: () -> Unit = {},
    onChangePassword: (suspend (String) -> Unit)? = null,
    onLiquidGlassEnabledChange: (Boolean) -> Unit = {},
    onLiquidGlassBlurChange: (Float) -> Unit = {},
    onLiquidGlassBlurSave: () -> Unit = {},
    onSaveTrackMetadata: (suspend (Track, TrackMetadataEdit) -> TrackMetadata)? = null,
    onLoadTrackTagOptions: suspend () -> TrackTagOptions = { TrackTagOptions(emptyList(), emptyList()) },
    openPlayerRequested: Boolean = false,
    onPlayerOpenRequestConsumed: () -> Unit = {},
    playerWindowInsets: WindowInsets = WindowInsets.systemBarsIgnoringVisibility
        .union(WindowInsets.displayCutout).union(WindowInsets.waterfall).union(WindowInsets.ime),
    immersivePlayerInsets: WindowInsets = WindowInsets.displayCutout.union(WindowInsets.waterfall),
    managePlayerSystemBars: Boolean = true,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val playerInsets = playerWindowInsets.asPaddingValues()
        val playerDirection = LocalLayoutDirection.current
        val availablePlayerWidth = maxWidth - playerInsets.calculateLeftPadding(playerDirection) -
            playerInsets.calculateRightPadding(playerDirection)
        val availablePlayerHeight = maxHeight - playerInsets.calculateTopPadding() - playerInsets.calculateBottomPadding()
        val landscapePlayer = isShortLandscapePlayer(availablePlayerWidth, availablePlayerHeight)
        val widePlayer = availablePlayerWidth >= 840.dp && !landscapePlayer
        val splitPlayer = widePlayer || landscapePlayer
        val navigation = rememberSaveable(saver = MusicNavigationState.Saver) { MusicNavigationState() }
        val pageStateHolder = rememberSaveableStateHolder()
        val defaultNavigationSurface = FnNavigationSurface
        var navigationSurface by remember(defaultNavigationSurface) { mutableStateOf(defaultNavigationSurface) }
        var navigationDarkForeground by remember(defaultNavigationSurface) { mutableStateOf(contrastingForeground(defaultNavigationSurface) == Color.Black) }
        val destination = navigation.destination
        val detail = navigation.current.detail
        val keyboard = LocalSoftwareKeyboardController.current
        fun popPage() { navigation.pop()?.let { pageStateHolder.removeState(it.id) } }
        fun openPage(page: MusicPage) { navigation.push(page = page) }
        var playerOpen by rememberSaveable { mutableStateOf(false) }
        val immersivePlayer = landscapePlayer && playerOpen && playerState.current != null
        PlayerImmersiveMode(enabled = managePlayerSystemBars && immersivePlayer)
        fun pushDetail(page: LibraryDetail) {
            playerOpen = false
            navigation.push(detail = page)
        }
        var playerComposed by rememberSaveable { mutableStateOf(false) }
        if (managePlayerSystemBars) MusicSystemBarAppearance(!playerComposed && navigation.current.detail !is LibraryDetail.AlbumPage && FnBackgroundTop.luminance() > .5f)
        var playerPage by rememberSaveable { mutableStateOf(PlayerPage.NowPlaying) }
        LaunchedEffect(widePlayer, playerComposed) {
            if (widePlayer && playerComposed && playerPage == PlayerPage.NowPlaying) {
                playerPage = PlayerPage.Lyrics
            }
        }
        var miniPlayerBounds by remember { mutableStateOf<Rect?>(null) }
        var miniCoverBounds by remember { mutableStateOf<Rect?>(null) }
        val playerMorphProgress = remember { Animatable(0f) }
        val lyricsMorphProgress = remember { Animatable(0f) }
        var actionTrack by remember { mutableStateOf<Track?>(null) }
        var queueActionEntryId by remember { mutableStateOf<String?>(null) }
        var playlistPickerTrack by remember { mutableStateOf<Track?>(null) }
        var deletePlaylistCandidate by remember { mutableStateOf<Playlist?>(null) }
        var purgePlaylistCandidate by remember { mutableStateOf<Playlist?>(null) }
        var catalogEditVersion by remember { mutableStateOf(0) }
        val artistItems = pagedArtists.collectAsLazyPagingItems()
        val favoriteItems = pagedFavorites.collectAsLazyPagingItems()
        val searchItems = pagedSearch.collectAsLazyPagingItems()
        val snackbarHostState = remember { SnackbarHostState() }
        LaunchedEffect(state.error) {
            state.error?.let { snackbarHostState.showSnackbar(it) }
        }
        fun navigate(target: MusicDestination) {
            keyboard?.hide()
            navigation.select(target)
        }
        fun openPlayerPage(page: PlayerPage) {
            if (playerState.current != null && miniPlayerBounds != null && miniCoverBounds != null) {
                playerPage = page
                playerComposed = true
                playerOpen = true
            }
        }
        fun openPlayer() = openPlayerPage(PlayerPage.NowPlaying)
        fun closePlayer() {
            playerOpen = false
        }
        LaunchedEffect(openPlayerRequested, playerState.current?.queueEntryId, miniPlayerBounds, miniCoverBounds) {
            if (openPlayerRequested && playerState.current != null && miniPlayerBounds != null && miniCoverBounds != null) {
                openPlayer()
                onPlayerOpenRequestConsumed()
            }
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
        val backAnimationScope = rememberCoroutineScope()
        PredictiveBackHandler(enabled = playerComposed) { events ->
            val page = playerPage
            val motion = if (page == PlayerPage.NowPlaying || (widePlayer && page == PlayerPage.Lyrics)) playerMorphProgress else lyricsMorphProgress
            val start = motion.value
            try {
                events.collect { event -> motion.snapTo(start * (1f - event.progress)) }
                when (page) {
                    PlayerPage.Queue -> playerPage = if (widePlayer) PlayerPage.Lyrics else PlayerPage.NowPlaying
                    PlayerPage.Lyrics -> if (widePlayer) closePlayer() else { playerPage = PlayerPage.NowPlaying }
                    PlayerPage.NowPlaying -> closePlayer()
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                backAnimationScope.launch { motion.animateTo(start, spring(dampingRatio = .85f, stiffness = 450f)) }
                throw cancelled
            }
        }
        val appBarBackdrop = rememberLayerBackdrop()
        val systemBarView = androidx.compose.ui.platform.LocalView.current
        val activity = systemBarView.context as? android.app.Activity
        SideEffect {
            activity?.let {
                androidx.core.view.WindowCompat.getInsetsController(it.window, systemBarView).apply {
                    isAppearanceLightStatusBars = !playerComposed && navigationDarkForeground
                    isAppearanceLightNavigationBars = !playerComposed && navigationDarkForeground
                }
            }
        }
        CompositionLocalProvider(
            LocalAppBarBackdrop provides appBarBackdrop,
            com.seasonyuu.fnmusic.core.designsystem.LocalLiquidGlassBlur provides state.liquidGlassBlur,
            com.seasonyuu.fnmusic.core.designsystem.LocalLiquidGlassEnabled provides state.liquidGlassEnabled,
            LocalContentColor provides FnTextPrimary,
            LocalTrackAction provides { track ->
                queueActionEntryId = null
                actionTrack = track
            },
        ) {
            val collectionPage = navigation.current.page in setOf(
                MusicPage.Tracks, MusicPage.Recent, MusicPage.Favorites,
                MusicPage.Albums, MusicPage.Artists, MusicPage.Playlists,
            ) || navigation.current.detail is LibraryDetail.ArtistPage || navigation.current.detail is LibraryDetail.PlaylistPage
            FnProgressiveSystemBars(showTopBlur = !playerComposed && !collectionPage && navigation.current.detail !is LibraryDetail.AlbumPage) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.matchParentSize().layerBackdrop(appBarBackdrop).background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom))))
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
                        val dynamicBottomBarConnection = remember(dynamicBottomBarState) {
                            dynamicBottomBarNestedScrollConnection(dynamicBottomBarState)
                        }
                        Row(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (compact) Modifier
                                        .dynamicBottomBarGesture(dynamicBottomBarState)
                                        .nestedScroll(dynamicBottomBarConnection)
                                    else Modifier,
                                ),
                        ) {
                            if (!compact) {
                                FnMusicTheme(darkTheme = !navigationDarkForeground) {
                                if (expanded) PermanentSidebar(destination, { navigate(it) }, state.serverName, navigationSurface)
                                else MusicRail(destination, { navigate(it) }, navigationSurface)
                                }
                            }
                            Scaffold(
                                containerColor = Color.Transparent,
                                contentColor = FnTextPrimary,
                                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                                snackbarHost = { SnackbarHost(snackbarHostState) },
                                bottomBar = {
                                    FnMusicTheme(darkTheme = !navigationDarkForeground) {
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
                                                    surfaceColor = navigationSurface,
                                                    selectedDestination = destination,
                                                    expansionProgress = dynamicBottomBarState.navigationExpansionProgress,
                                                    playerExpansionProgress = dynamicBottomBarState.playerExpansionProgress,
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
                                        WideMiniPlayer(
                                            state = playerState,
                                            surfaceColor = navigationSurface,
                                            onPrevious = onPrevious,
                                            onToggleShuffle = onToggleShuffle,
                                            onCycleRepeatMode = onCycleRepeatMode,
                                            onOpenLyrics = { openPlayerPage(PlayerPage.Lyrics) },
                                            onOpenQueue = { openPlayerPage(PlayerPage.Queue) },
                                            onToggle = onTogglePlayback,
                                            onNext = onNext,
                                            onOpen = ::openPlayer,
                                            modifier = Modifier.navigationBarsPadding().imePadding(),
                                            playerMorphProgress = playerMorphProgress.value,
                                            onPlayerBoundsChanged = { miniPlayerBounds = it },
                                            onCoverBoundsChanged = { miniCoverBounds = it },
                                        )
                                    }
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
                                        MusicPageHost(
                                            navigation = navigation,
                                            backEnabled = !playerComposed,
                                            onPop = { popPage() },
                                            onNavigationSurfaceChanged = { navigationSurface = it },
                                            onForegroundChanged = { navigationDarkForeground = it },
                                        ) { entry ->
                                            pageStateHolder.SaveableStateProvider(entry.id) {
                                                val selected = entry.detail
                                                val detailState = state.forDetail(selected?.requestKey)
                                                if (selected != null) {
                                                    when (selected) {
                                                        is LibraryDetail.TrackPage -> TrackInfoScreen(
                                                            fallback = selected.track,
                                                            onSave = onSaveTrackMetadata?.let { save ->
                                                                { track, edit ->
                                                                    save(track, edit).also {
                                                                        catalogEditVersion++
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
                                                        is LibraryDetail.AlbumPage -> FnMusicTheme(darkTheme = true) { AlbumDetailScreen(
                                                            album = detailState.detailAlbum?.takeIf { it.id == selected.album.id } ?: selected.album,
                                                            state = detailState,
                                                            playerState = playerState,
                                                            coverUrl = coverUrl,
                                                            onPlay = onPlay,
                                                            onMore = LocalTrackAction.current,
                                                            onBack = { popPage() },
                                                            onRetry = { onLoadAlbum(selected.album.id) },
                                                        ) }
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
                                                } else if (entry.page != MusicPage.Root) {
                                                    when (entry.page) {
                                                        MusicPage.Tracks -> {
                                                            var sort by rememberSaveable { mutableStateOf(TrackSort.RecentlyAdded) }
                                                            val items = remember(entry.id, sort) { pagedTracks(sort) }.collectAsLazyPagingItems()
                                                            LaunchedEffect(catalogEditVersion) { if (catalogEditVersion > 0) items.refresh() }
                                                            PagingTrackScreen(
                                                                "全部歌曲", items, state, coverUrl, onPlay, onToggleFavorite, onRefresh,
                                                                sort = sort, totalCount = state.trackTotal, showServerTotal = true, pullRefreshEnabled = true,
                                                                onSort = { sort = it }, onPlayAll = { onPlayAllTracks(sort) }, onBack = ::popPage,
                                                            )
                                                        }
                                                        MusicPage.Favorites -> PagingTrackScreen(
                                                            "收藏", favoriteItems, state, coverUrl, onPlay, onToggleFavorite, onRefresh,
                                                            onPlayAll = onPlayAllFavorites, onBack = ::popPage,
                                                        )
                                                        MusicPage.Recent -> TrackListScreen("最近播放", state.recent, state, coverUrl, onPlay, onToggleFavorite, ::popPage)
                                                        MusicPage.Albums -> {
                                                            var sort by rememberSaveable { mutableStateOf(AlbumSort.RecentlyUpdated) }
                                                            val items = remember(entry.id, sort) { pagedAlbums(sort) }.collectAsLazyPagingItems()
                                                            LaunchedEffect(catalogEditVersion) { if (catalogEditVersion > 0) items.refresh() }
                                                            AlbumGridScreen(items, coverUrl, sort, { sort = it }, ::popPage) {
                                                                pushDetail(LibraryDetail.AlbumPage(it))
                                                            }
                                                        }
                                                        MusicPage.Artists -> ArtistGridScreen(artistItems, coverUrl, ::popPage) { pushDetail(LibraryDetail.ArtistPage(it)) }
                                                        MusicPage.Playlists -> PlaylistGridScreen(
                                                            state.playlists, state.playlistMessage, coverUrl, ::popPage,
                                                            { pushDetail(LibraryDetail.PlaylistEditorPage(null)) },
                                                        ) { pushDetail(LibraryDetail.PlaylistPage(it)) }
                                                        MusicPage.LiquidGlass -> LiquidGlassSettingsScreen(
                                                            multiplier = state.liquidGlassBlur, saveError = state.liquidGlassSaveError,
                                                            onValueChange = onLiquidGlassBlurChange, enabled = state.liquidGlassEnabled,
                                                            onEnabledChange = onLiquidGlassEnabledChange, onSave = onLiquidGlassBlurSave, onBack = ::popPage,
                                                        )
                                                        MusicPage.AdminLibraries, MusicPage.AdminUsers, MusicPage.AdminServer -> {
                                                            if (state.user?.role != "admin" || administration == null) {
                                                                LaunchedEffect(entry.id) { popPage() }
                                                            } else when (entry.page) {
                                                                MusicPage.AdminLibraries -> LibraryAdministrationScreen(administration, ::popPage)
                                                                MusicPage.AdminUsers -> UserAdministrationScreen(administration, state.user.id, ::popPage)
                                                                else -> ServerAdministrationScreen(administration, ::popPage)
                                                            }
                                                        }
                                                        MusicPage.Quality -> QualitySettingsScreen(state.streamingQuality, onStreamingQualityChange, ::popPage)
                                                        MusicPage.Cache -> CacheSettingsScreen(state.cachePreference, state.cacheUsage, onCachePreferenceChange, onClearCache, ::popPage)
                                                        MusicPage.Appearance -> AppearanceSettingsScreen(state,
                                                            onAppearanceChange, onThemeColorChange, { openPage(MusicPage.LiquidGlass) }, ::popPage)
                                                        MusicPage.ThemeColor -> { LaunchedEffect(entry.id) { popPage() } }
                                                        MusicPage.DisplayMode -> { LaunchedEffect(entry.id) { popPage() } }
                                                        MusicPage.Password -> PasswordSettingsScreen(state.user?.name.orEmpty(), onChangePassword, ::popPage)
                                                        MusicPage.Root -> Unit
                                                    }
                                                } else {
                                                    when (entry.destination) {
                                                        MusicDestination.Home -> HomeScreen(
                                                            state, coverUrl, onPlay, onToggleFavorite, onRoam,
                                                            { openPage(MusicPage.Favorites) },
                                                            { openPage(MusicPage.Recent) },
                                                            { openPage(MusicPage.Tracks) },
                                                            { openPage(MusicPage.Albums) },
                                                            { openPage(MusicPage.Playlists) },
                                                            { pushDetail(LibraryDetail.AlbumPage(it)) },
                                                            { pushDetail(LibraryDetail.PlaylistPage(it)) }, onRefresh,
                                                        )
                                                        MusicDestination.Library -> LibraryMenu(::openPage)
                                                        MusicDestination.Search -> SearchScreen(
                                                            state, searchItems, coverUrl, onSearch, onSearchType, onPlay, onToggleFavorite,
                                                            { pushDetail(LibraryDetail.AlbumPage(it)) },
                                                            { pushDetail(LibraryDetail.ArtistPage(it)) },
                                                            { pushDetail(LibraryDetail.PlaylistPage(it)) },
                                                        )
                                                        MusicDestination.Profile -> SettingsScreen(
                                                            state, onCacheSizeChange, onLogout,
                                                            onLiquidGlass = { openPage(MusicPage.LiquidGlass) },
                                                            onPassword = onChangePassword?.let { { openPage(MusicPage.Password) } },
                                                            onRefreshProfile = onRefreshProfile,
                                                            onAppearance = { openPage(MusicPage.Appearance) },
                                                            onCache = { openPage(MusicPage.Cache) },
                                                            onQuality = { openPage(MusicPage.Quality) },
                                                            onAdminLibraries = administration?.let { { openPage(MusicPage.AdminLibraries) } },
                                                            onAdminUsers = administration?.let { { openPage(MusicPage.AdminUsers) } },
                                                            onAdminServer = administration?.let { { openPage(MusicPage.AdminServer) } },
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
                    if (playerComposed) {
                        FnMusicTheme(darkTheme = true) {
                        val containerAnchor = miniPlayerBounds
                        val coverAnchor = miniCoverBounds
                        if (containerAnchor != null && coverAnchor != null) {
                            PlayerMorphOverlay(
                                state = playerState,
                                highResolutionCoverUrl = coverUrl(playerState.current?.track?.coverId, 640)
                                    ?: playerState.current?.coverUrl,
                                containerAnchor = containerAnchor,
                                coverAnchor = coverAnchor,
                                expandedTarget = playerOpen,
                                progress = playerMorphProgress,
                                contentCoverProgress = if (splitPlayer) 0f else lyricsMorphProgress.value,
                                // The stable queue paints its own scrolling artwork. The overlay
                                // owns it only while morphing between player presentations.
                                showCover = splitPlayer || playerPage != PlayerPage.Queue || lyricsMorphProgress.value < 0.999f,
                                followContentCover = !splitPlayer && playerPage != PlayerPage.NowPlaying,
                                onRequestClose = ::closePlayer,
                                onClosed = {
                                    playerComposed = false
                                    playerPage = PlayerPage.NowPlaying
                                },
                                onLargeCoverClick = onTogglePlayback,
                                onCoverClick = {
                                    when (playerPage) {
                                        PlayerPage.Lyrics -> playerPage = PlayerPage.NowPlaying
                                        PlayerPage.Queue -> playerPage = PlayerPage.NowPlaying
                                        PlayerPage.NowPlaying -> Unit
                                    }
                                },
                            ) { targetCoverModifier, lyricsCoverModifier, dragModifier, contentModifier, coverViewportModifier ->
                                when (playerPage) {
                                    PlayerPage.NowPlaying, PlayerPage.Lyrics, PlayerPage.Queue -> NowPlayingLyricsScreen(
                                        state = playerState,
                                        musicState = state,
                                        highResolutionCoverUrl = coverUrl(playerState.current?.track?.coverId, 640)
                                            ?: playerState.current?.coverUrl,
                                        wideLayout = widePlayer,
                                        landscapeLayout = landscapePlayer,
                                        windowInsets = if (immersivePlayer) immersivePlayerInsets else playerWindowInsets,
                                        lyricsMode = playerPage == PlayerPage.Lyrics || (widePlayer && playerPage == PlayerPage.NowPlaying),
                                        queueMode = playerPage == PlayerPage.Queue,
                                        lyricsProgress = lyricsMorphProgress,
                                        modifier = contentModifier,
                                        dragModifier = dragModifier,
                                        largeCoverModifier = targetCoverModifier,
                                        coverViewportModifier = coverViewportModifier,
                                        lyricsCoverModifier = lyricsCoverModifier,
                                        onToggle = onTogglePlayback,
                                        onSeek = onSeek,
                                        onPrevious = onPrevious,
                                        onNext = onNext,
                                        onToggleShuffle = onToggleShuffle,
                                        onCycleRepeatMode = onCycleRepeatMode,
                                        onToggleFavorite = onToggleFavorite,
                                        onOpenLyrics = { playerPage = PlayerPage.Lyrics },
                                        onCloseLyrics = { playerPage = if (widePlayer) PlayerPage.Lyrics else PlayerPage.NowPlaying },
                                        onCloseQueue = { playerPage = if (widePlayer) PlayerPage.Lyrics else PlayerPage.NowPlaying },
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
            }
            actionTrack?.let { track ->
                TrackActionSheet(
                    track = track,
                    coverUrl = coverUrl(track.coverId, 160),
                    showTrackHeader = !(playerComposed && queueActionEntryId == null && track.id == playerState.current?.track?.id),
                onDismiss = {
                    actionTrack = null
                    queueActionEntryId = null
                },
                    onToggleFavorite = {
                        onToggleFavorite(track.copy(isFavorite = state.favoriteOverrides[track.id] ?: track.isFavorite))
                        actionTrack = null
                    },
                    favorite = state.favoriteOverrides[track.id] ?: track.isFavorite,
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
                        TextButton(colors = readableTextButtonColors(), onClick = {
                            onDeletePlaylist(playlist.id)
                            deletePlaylistCandidate = null
                            popPage()
                        }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(colors = readableTextButtonColors(), onClick = { deletePlaylistCandidate = null }) { Text("取消") } },
                )
            }
            purgePlaylistCandidate?.let { playlist ->
                AlertDialog(
                    onDismissRequest = { purgePlaylistCandidate = null },
                    title = { Text("清理失效歌曲？") },
                    text = { Text("只会从歌单中移除已不存在或无权访问的条目，不会删除音乐文件。") },
                    confirmButton = {
                        TextButton(colors = readableTextButtonColors(), onClick = {
                            onPurgeInvalidPlaylistTracks(playlist.id)
                            purgePlaylistCandidate = null
                        }) { Text("开始清理") }
                    },
                    dismissButton = { TextButton(colors = readableTextButtonColors(), onClick = { purgePlaylistCandidate = null }) { Text("取消") } },
                )
            }
        }
    }
}

private enum class PlayerPage { NowPlaying, Lyrics, Queue }

@Composable
private fun PermanentSidebar(selected: MusicDestination, onSelect: (MusicDestination) -> Unit, title: String, surfaceColor: Color = FnNavigationSurface) {
    Column(
        Modifier
            .width(224.dp)
            .fillMaxHeight()
            .background(surfaceColor.copy(alpha = .90f))
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
private fun MusicRail(selected: MusicDestination, onSelect: (MusicDestination) -> Unit, surfaceColor: Color = FnNavigationSurface) {
    NavigationRail(containerColor = surfaceColor.copy(alpha = .90f)) {
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
    onRefresh: () -> Unit,
) {
    // Keep the vertical page rhythm inset, but let horizontal carousels own the
    // full-width viewport so cards can slide under the screen edge instead of
    // being clipped at the page's 20dp content margin.
    Column(
        // Keep the root scroll surface edge-to-edge so the system-bar blur can
        // sample content as it scrolls beneath the status bar.
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = edgeToEdgeContentPadding(top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
          item {
            LazyRow(
                modifier = Modifier.testTag("首页快捷入口"),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { RoamFeatureCard(state.roamLoading, onRoam) }
                item { FlowFeatureCard("收藏", FlowingLightStyle.Favorites, FnIcons.Favorite, onFavorites) }
                item { FlowFeatureCard("最近播放", FlowingLightStyle.Recent, FnIcons.Recent, onRecent) }
                item { FlowFeatureCard("最近添加", FlowingLightStyle.RecentlyAdded, FnIcons.Library, onRecentlyAdded) }
            }
          }
        item { Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("最近添加", onRecentlyAdded) } }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardWidth = if (maxWidth < 420.dp) maxWidth - 48.dp else 340.dp
                HomeSectionContent(state, CatalogSection.Tracks, state.tracks.isNotEmpty(), onRefresh) {
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
        }
        item { Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("专辑", onAlbums) } }
        item {
            HomeSectionContent(state, CatalogSection.Albums, state.albums.isNotEmpty(), onRefresh) {
                AlbumRow(state.albums, coverUrl, onAlbum)
            }
        }
        item { Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("歌单", onPlaylists) } }
        item {
            HomeSectionContent(state, CatalogSection.Playlists, state.playlists.isNotEmpty(), onRefresh) {
                PlaylistRow(state.playlists, coverUrl, onPlaylist)
            }
        }
        }
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
                            tint = if (favorite) FnAccentIcon else FnTextSecondary,
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
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .70f))),
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
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(70.dp)
                .clip(shape).background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .80f)))))
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 20.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(FnIcons.Roam, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(
                    "漫游", color = Color.White,
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

@Composable
private fun FlowFeatureCard(
    title: String,
    variant: FlowingLightStyle,
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
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .70f)))))
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = .42f),
                    modifier = Modifier.align(Alignment.Center).size(58.dp),
                )
                Text(
                    title, color = Color.White,
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
    val listState = rememberLazyListState()
    CollectionPage(title, onBack, { listState.firstVisibleItemIndex > 0 }) { heading, top ->
        LazyColumn(
            Modifier.fillMaxSize(), state = listState,
            contentPadding = edgeToEdgeContentPadding(top = top, bottom = 20.dp, includeTopInset = false),
        ) {
            item(key = "collection-header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    CollectionHeading(title, "${tracks.size} 首歌曲", heading)
                    Spacer(Modifier.height(20.dp))
                    CollectionPlayButton(tracks.isNotEmpty()) { onPlay(tracks, 0) }
                }
            }
            items(tracks, key = { it.id.value }) { track ->
                FavoriteTrackRow(track, state, coverUrl, { onPlay(tracks, tracks.indexOf(track)) }, onToggleFavorite)
            }
            if (tracks.isEmpty()) item { EmptyPane("这里还没有歌曲") }
        }
    }
}

@Composable
internal fun PagingTrackScreen(
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
    pullRefreshEnabled: Boolean = false,
    onSort: ((TrackSort) -> Unit)? = null,
    onPlayAll: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val pullState = rememberPullToRefreshState()
    var requestedRefresh by remember { mutableStateOf(false) }
    var sawLoading by remember { mutableStateOf(false) }
    val refresh = tracks.loadState.refresh
    LaunchedEffect(refresh, requestedRefresh) {
        if (requestedRefresh) {
            if (refresh is LoadState.Loading) sawLoading = true
            else if (sawLoading) { requestedRefresh = false; sawLoading = false }
        }
    }
    CollectionPage(title, onBack, { listState.firstVisibleItemIndex > 0 }, actions = {
        if (sort != null && onSort != null) TrackSortMenu(sort, onSort)
        if (!pullRefreshEnabled) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                AppBarButton({ expanded = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                DropdownMenu(expanded, { expanded = false }) {
                    DropdownMenuItem(text = { Text("刷新") }, onClick = {
                        expanded = false; tracks.refresh(); onRefresh()
                    })
                }
            }
        }
    }) { heading, top ->
        PullToRefreshBox(
            isRefreshing = requestedRefresh && refresh is LoadState.Loading,
            onRefresh = {
                if (!requestedRefresh && refresh !is LoadState.Loading) {
                    requestedRefresh = true; sawLoading = false; tracks.refresh()
                }
            },
            state = pullState, enabled = pullRefreshEnabled,
            indicator = {
                if (pullRefreshEnabled) PullToRefreshDefaults.Indicator(
                    state = pullState, isRefreshing = requestedRefresh && refresh is LoadState.Loading,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
                )
            },
        ) {
            // Do not clamp an entry's restored position while its paging source reconnects.
            if (tracks.itemCount == 0 && refresh is LoadState.Loading &&
                (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { EmptyPane("正在加载歌曲…") }
            } else LazyColumn(
                Modifier.fillMaxSize(), state = listState,
                contentPadding = edgeToEdgeContentPadding(top = top, bottom = 20.dp, includeTopInset = false),
            ) {
                item(key = "collection-header") {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                        CollectionHeading(title, when {
                            totalCount != null -> "共 $totalCount 首歌曲"
                            showServerTotal && state.loading -> "正在获取曲目总数…"
                            showServerTotal -> "${tracks.itemCount} 首歌曲"
                            else -> "${tracks.itemCount} 首已加载歌曲"
                        }, heading)
                        Spacer(Modifier.height(20.dp))
                        CollectionPlayButton(tracks.itemCount > 0) {
                            if (onPlayAll != null) onPlayAll()
                            else tracks.itemSnapshotList.items.takeIf(List<Track>::isNotEmpty)?.let { onPlay(it, 0) }
                        }
                    }
                }
                if (refresh is LoadState.Error && tracks.itemCount > 0) item {
                    PagingErrorPane("刷新失败，请重试") {
                        requestedRefresh = true
                        sawLoading = false
                        tracks.retry()
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
                Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏", tint = if (favorite) FnAccentIcon else FnTextSecondary)
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
        AppBarButton(onClick = { expanded = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "排序") }
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
        MusicAppBar("搜索", modifier = Modifier.padding(horizontal = 20.dp))
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            placeholder = { Text("搜索歌曲、歌手、专辑、歌单") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            singleLine = true,
            colors = readableTextFieldColors(),
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
            AlbumCard(album, coverUrl, { onAlbum(album) }, Modifier.width(142.dp))
        }
    }
}

/** The parent chooses the card width; artwork always keeps its square album format. */
@Composable
private fun AlbumCard(
    album: Album,
    coverUrl: (String?, Int) -> String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.clickable(onClick = onClick)) {
        CoverImage(
            coverUrl(album.coverId, 640), album.name,
            Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            album.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            album.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
            color = FnTextSecondary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
internal fun LibraryMenu(onNavigate: (MusicPage) -> Unit) {
    Column(
        // Keep the collection surface behind the status bar for progressive blur.
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).testTag("library-menu"),
            contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { LibraryEntry(FnIcons.Library, "全部歌曲", "浏览音乐库中的歌曲") { onNavigate(MusicPage.Tracks) } }
            item { LibraryEntry(FnIcons.Artist, "全部歌手", "按歌手浏览") { onNavigate(MusicPage.Artists) } }
            item { LibraryEntry(Icons.Rounded.Album, "全部专辑", "按专辑浏览") { onNavigate(MusicPage.Albums) } }
            item { LibraryEntry(Icons.AutoMirrored.Rounded.QueueMusic, "歌单", "浏览和管理歌单") { onNavigate(MusicPage.Playlists) } }
            item { LibraryEntry(FnIcons.Favorite, "收藏", "喜欢的歌曲") { onNavigate(MusicPage.Favorites) } }
            item { LibraryEntry(Icons.Rounded.History, "最近播放", "回顾听过的歌曲") { onNavigate(MusicPage.Recent) } }
        }
    }
}

@Composable
private fun LibraryEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
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
        MusicAppBar(title, onBack = onBack, actions = actions,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 20.dp).testTag("detail-app-bar"))
        Box(Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
internal fun PageTitle(
    title: String,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        MusicAppBar(title, onBack = onBack, actions = actions)
        if (subtitle != null) Text(subtitle, color = FnTextSecondary, style = MaterialTheme.typography.bodyMedium)
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
    val gridState = rememberLazyGridState()
    CollectionPage("专辑", onBack, { gridState.firstVisibleItemIndex > 0 }, actions = {
        AlbumSortMenu(sort, onSort)
    }) { heading, top ->
        // Capture only the scrolling content so glass controls never sample themselves.
        if (albums.itemCount == 0 && albums.loadState.refresh is LoadState.Loading &&
            (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)
        ) {
            Box(Modifier.padding(top = 80.dp)) { EmptyPane("正在加载专辑…") }
        } else LazyVerticalGrid(
            state = gridState,
            modifier = Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom)))
                .testTag("album-grid"),
            columns = GridCells.Adaptive(142.dp),
            contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = top, bottom = 20.dp, includeTopInset = false),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "collection-header", span = { GridItemSpan(maxLineSpan) }) {
                Column { CollectionHeading("专辑", "${albums.itemCount} 张已加载", heading) }
            }
            items(count = albums.itemCount, key = albums.itemKey { it.id.value }) { index ->
                albums[index]?.let { album ->
                    AlbumCard(album, coverUrl, { onAlbum(album) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun AlbumSortMenu(selected: AlbumSort, onSelect: (AlbumSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppBarButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, "专辑排序")
        }
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
    val gridState = rememberLazyGridState()
    CollectionPage("歌手", onBack, { gridState.firstVisibleItemIndex > 0 }) { heading, top ->
        if (artists.itemCount == 0 && artists.loadState.refresh is LoadState.Loading &&
            (gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0)
        ) {
            Box(Modifier.padding(top = top)) { EmptyPane("正在加载歌手…") }
        } else LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(142.dp),
            contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = top, bottom = 20.dp, includeTopInset = false),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "collection-header", span = { GridItemSpan(maxLineSpan) }) {
                Column { CollectionHeading("歌手", "${artists.itemCount} 位已加载", heading) }
            }
            items(count = artists.itemCount, key = artists.itemKey { it.id.value }) { index ->
                artists[index]?.let { artist ->
                    Column(Modifier.clickable { onArtist(artist) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        CoverImage(coverUrl(artist.coverId, 640), artist.name, Modifier.size(142.dp))
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
    val gridState = rememberLazyGridState()
    CollectionPage("歌单", onBack, { gridState.firstVisibleItemIndex > 0 }) { heading, top ->
        LazyVerticalGrid(
            modifier = Modifier.testTag("playlist-grid"),
            state = gridState,
            columns = GridCells.Adaptive(142.dp),
            contentPadding = edgeToEdgeContentPadding(horizontal = 20.dp, top = top, bottom = 20.dp, includeTopInset = false),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(key = "collection-header", span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    CollectionHeading("歌单", "${playlists.size} 个", heading)
                    OutlinedButton(colors = readableOutlinedButtonColors(), onClick = onCreate, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Rounded.Add, "新建歌单")
                        Text("新建歌单")
                    }
                    message?.let { Text(it, color = FnTextSecondary) }
                }
            }
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
        CoverImage(coverUrl(coverId, 640), title, modifier)
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
                        colors = readableTextFieldColors(),
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
                                        tint = if (selected) FnAccentIcon else FnTextSecondary,
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
    val rippleConfiguration = LocalRippleConfiguration.current
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = com.seasonyuu.fnmusic.core.designsystem.FnSurface,
            contentColor = FnTextPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
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
    showTrackHeader: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
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
    val rippleConfiguration = LocalRippleConfiguration.current
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = com.seasonyuu.fnmusic.core.designsystem.FnSurface,
            contentColor = FnTextPrimary,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        ) {
            CompositionLocalProvider(LocalRippleConfiguration provides rippleConfiguration) {
                if (showTrackHeader) {
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
                }
                ActionSheetRow(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    if (favorite) "取消收藏" else "收藏", onToggleFavorite)
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
    }
}

@Composable
private fun ActionSheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(icon, null, tint = FnAccentIcon, modifier = Modifier.size(24.dp))
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
        if (onSave != null) AppBarButton(onClick = { editing = true }, enabled = !state.detailLoading && metadata != null && state.detailError == null) { Text("编辑") }
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
                        CoverImage(coverUrl(track.coverId, 640), track.title, Modifier.size(232.dp))
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
    var selectingTracks by rememberSaveable(managedPlaylist?.id?.value) { mutableStateOf(false) }
    var selectedTrackIds by rememberSaveable(
        managedPlaylist?.id?.value,
        stateSaver = androidx.compose.runtime.saveable.listSaver<Set<TrackId>, String>(
            save = { ids -> ids.map { it.value } },
            restore = { ids -> ids.map(::TrackId).toSet() },
        ),
    ) { mutableStateOf(emptySet<TrackId>()) }
    CollectionPage(title, onBack, { listState.firstVisibleItemIndex > 0 }, actions = {
        managedPlaylist?.let { playlist ->
            var expanded by remember { mutableStateOf(false) }
            Box {
                AppBarButton({ expanded = true }) { Icon(Icons.Rounded.MoreVert, "更多") }
                DropdownMenu(expanded, { expanded = false }) {
                    DropdownMenuItem(text = { Text("编辑") }, onClick = { expanded = false; onEditPlaylist(playlist) })
                    DropdownMenuItem(text = { Text("清理失效") }, enabled = !state.playlistBusy,
                        onClick = { expanded = false; onPurgePlaylist(playlist) })
                    DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, enabled = !state.playlistBusy,
                        onClick = { expanded = false; onDeletePlaylist(playlist) })
                }
            }
        }
    }) { heading, top ->
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
                    contentPadding = edgeToEdgeContentPadding(top = top, bottom = 36.dp, includeTopInset = false),
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
                                    CoverImage(coverUrl(coverId, 640), title, Modifier.size(232.dp))
                                }
                                DetailHeading(title, subtitle, metadata, state.detailTracks, onPlay, heading)
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
                                    CoverImage(coverUrl(coverId, 640), title, Modifier.size(264.dp))
                                }
                                Box(Modifier.weight(1f)) { DetailHeading(title, subtitle, metadata, state.detailTracks, onPlay, heading) }
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
                                    OutlinedButton(colors = readableOutlinedButtonColors(),
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
    headingModifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, modifier = headingModifier, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = MaterialTheme.typography.titleMedium, color = FnTextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(metadata, color = FnTextSecondary)
        Spacer(Modifier.height(6.dp))
        CollectionPlayButton(tracks.isNotEmpty()) { onPlay(tracks, 0) }
    }
}

@Composable
private fun PagingErrorPane(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(colors = readableTextButtonColors(), onClick = onRetry) { Text("重试") }
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
    onLargeCoverClick: () -> Unit,
    content: @Composable (
        targetCoverModifier: Modifier,
        lyricsCoverModifier: Modifier,
        dragModifier: Modifier,
        contentModifier: Modifier,
        coverViewportModifier: Modifier,
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
        targetValue = if (state.playbackIntentActive) 1f else 0.73f,
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
    var coverViewportCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var coverViewportBounds by remember { mutableStateOf<Rect?>(null) }
    var lyricsCoverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    fun updateCoverBounds() {
        val root = contentCoordinates?.takeIf { it.isAttached } ?: return
        coverViewportCoordinates?.takeIf { it.isAttached }?.let {
            coverViewportBounds = root.localBoundingBoxOf(it, clipBounds = false)
        }
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
            Modifier.onGloballyPositioned {
                coverViewportCoordinates = it
                updateCoverBounds()
            },
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
                .drawWithContent {
                    val viewport = coverViewportBounds
                    if (p >= 0.999f && contentCoverProgress <= 0.001f && viewport != null) {
                        val top = (surfaceBounds.top + viewport.top * contentScale - coverBounds.top).coerceIn(0f, size.height)
                        val bottom = (surfaceBounds.top + viewport.bottom * contentScale - coverBounds.top).coerceIn(top, size.height)
                        clipRect(top = top, bottom = bottom) { this@drawWithContent.drawContent() }
                    } else drawContent()
                }
                .then(
                    if (contentCoverProgress >= 0.5f && showCover) {
                        Modifier.clickable(onClick = onCoverClick)
                    } else if (showCover && expandedTarget && p >= 0.999f &&
                        contentCoverProgress <= 0.001f && !followContentCover) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = if (state.playbackIntentActive) "暂停" else "播放",
                            onClick = onLargeCoverClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .testTag("player-morph-cover")
                .then(if (contentCoverProgress < 0.5f) dragModifier else Modifier),
            requestSizePx = 640,
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
    wideLayout: Boolean,
    landscapeLayout: Boolean,
    windowInsets: WindowInsets,
    lyricsMode: Boolean,
    queueMode: Boolean,
    lyricsProgress: Animatable<Float, *>,
    largeCoverModifier: Modifier,
    coverViewportModifier: Modifier,
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
    val requestedPage = when {
        queueMode -> PlayerPage.Queue
        lyricsMode -> PlayerPage.Lyrics
        else -> PlayerPage.NowPlaying
    }
    var displayedPage by remember { mutableStateOf(requestedPage) }
    val pageVisibility = remember { Animatable(1f) }
    LaunchedEffect(landscapeLayout, requestedPage) {
        if (!landscapeLayout) {
            displayedPage = requestedPage
            pageVisibility.snapTo(1f)
        } else {
            if (displayedPage != requestedPage) {
                // Keep the outgoing page's header and viewport intact until it is invisible.
                pageVisibility.animateTo(0f, tween(150, easing = FastOutLinearInEasing))
                displayedPage = requestedPage
            }
            // A new request cancels this sequence and continues from the current opacity.
            pageVisibility.animateTo(1f, tween(180, easing = LinearOutSlowInEasing))
        }
    }
    val displayedLyricsMode = if (landscapeLayout) displayedPage == PlayerPage.Lyrics else lyricsMode
    val displayedQueueMode = if (landscapeLayout) displayedPage == PlayerPage.Queue else queueMode
    val splitLayout = wideLayout || landscapeLayout
    val immersiveMode = !splitLayout && (displayedLyricsMode || displayedQueueMode)
    val expressiveMotion = remember { MotionScheme.expressive() }
    val controlsVisibility = remember(current.track.id) { Animatable(1f) }
    var controlsShown by remember(current.track.id) { mutableStateOf(true) }
    val defaultContentVisibility = remember { Animatable(if (immersiveMode) 0f else 1f) }
    val lyricsHeaderVisibility = remember {
        Animatable(if (splitLayout || immersiveMode) 1f else 0f)
    }
    val lyricsListVisibility = remember { Animatable(if (splitLayout || immersiveMode) 1f else 0f) }
    val lyricsChromeVisibility = remember {
        Animatable(if (splitLayout || immersiveMode) 1f else 0f)
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
    val lyricsListState = rememberLazyListState()
    val lyricsDragging by lyricsListState.interactionSource.collectIsDraggedAsState()
    val density = LocalDensity.current
    val playerBackgroundBackdrop = rememberLayerBackdrop()
    val onMore = LocalTrackAction.current
    val context = LocalContext.current
    val audioManager =
        remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maximumVolume =
        remember(audioManager) {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(0)
        }
    val minimumVolume = remember(audioManager, maximumVolume) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC).coerceIn(0, maximumVolume)
        } else 0
    }
    var systemVolume by
        remember(current.track.id, audioManager) {
            mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat())
        }
    var followCurrent by rememberSaveable(current.track.id) { mutableStateOf(true) }
    var lyricsPositionReady by remember(current.track.id) { mutableStateOf(false) }
    var manualScrollActivity by remember(current.track.id) { mutableStateOf(0) }
    var pendingSeekPositionMs by remember(current.track.id) { mutableStateOf<Long?>(null) }
    var lastImmersiveContentWasLyrics by remember { mutableStateOf(displayedLyricsMode) }
    var playerRootTopPx by remember { mutableFloatStateOf(0f) }
    var measuredLyricsHeaderBottomPx by remember { mutableFloatStateOf(Float.NaN) }
    val lyricPosition =
        rememberLyricPosition(
            current.queueEntryId,
            pendingSeekPositionMs ?: state.positionMs,
            state.durationMs,
            state.isPlaying && pendingSeekPositionMs == null && displayedLyricsMode,
        )
    val activeIndex by
        remember(musicState.lyrics, lyricPosition) {
            derivedStateOf { activeLyricIndex(musicState.lyrics, lyricPosition.value) }
        }
    val lyricTimelines =
        remember(musicState.lyrics, state.durationMs) {
            musicState.lyrics.indices.map {
                resolveLyricTimeline(musicState.lyrics, it, state.durationMs)
            }
        }

    var landscapeTouchActive by remember { mutableStateOf(false) }

    fun revealControls() {
        controlsActivity += 1
    }

    fun seekAndFollow(positionMs: Long) {
        pendingSeekPositionMs = positionMs
        followCurrent = true
        revealControls()
        onSeek(positionMs)
    }

    LaunchedEffect(immersiveMode, displayedLyricsMode) {
        if (immersiveMode) lastImmersiveContentWasLyrics = displayedLyricsMode
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
    LaunchedEffect(followCurrent, manualScrollActivity, displayedLyricsMode, current.track.id) {
        if (displayedLyricsMode && !followCurrent) {
            delay(LyricsFollowResumeDelayMillis)
            followCurrent = true
        }
    }

    DisposableEffect(context, audioManager) {
        val observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    systemVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                }
            }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    LaunchedEffect(immersiveMode, splitLayout) {
        if (splitLayout) {
            lyricsProgress.snapTo(0f)
            defaultContentVisibility.snapTo(1f)
            lyricsHeaderVisibility.snapTo(0f)
            lyricsListVisibility.snapTo(1f)
            lyricsChromeVisibility.snapTo(0f)
        } else if (immersiveMode) {
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
                if (displayedLyricsMode) snapshotFlow { lyricsPositionReady }.first { it }
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
    LaunchedEffect(
        wideLayout,
        landscapeLayout,
        landscapeTouchActive,
        displayedLyricsMode,
        displayedQueueMode,
        controlsActivity,
        current.track.id,
        sliderInteracting,
        lyricsDragging
    ) {
        controlsShown = true
        if (!wideLayout &&
                displayedLyricsMode &&
                !displayedQueueMode &&
                !sliderInteracting &&
                !lyricsDragging &&
                !landscapeTouchActive
        ) {
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
        val insets = windowInsets.asPaddingValues()
        val direction = LocalLayoutDirection.current
        val safeTop = insets.calculateTopPadding()
        val safeBottom = insets.calculateBottomPadding()
        val safeLeft = insets.calculateLeftPadding(direction)
        val safeRight = insets.calculateRightPadding(direction)
        val geometry =
            playerLayoutGeometry(maxWidth - safeLeft - safeRight, maxHeight - safeTop - safeBottom, landscapeLayout)
        val portraitPhoneLayout = !splitLayout && maxWidth - safeLeft - safeRight < 600.dp
        val compactHeight = wideLayout || geometry.short || maxHeight < 760.dp
        val playerTopPadding = if (landscapeLayout) LandscapePlayerTopPadding else 32.dp
        val playerBottomPadding = if (landscapeLayout) LandscapePlayerBottomPadding else 20.dp
        val availablePlayerHeight = (maxHeight - safeTop - safeBottom - playerTopPadding - playerBottomPadding).coerceAtLeast(0.dp)
        val p = if (splitLayout) 0f else lyricsProgress.value.coerceIn(0f, 1f)
        val defaultContentAlpha = defaultContentVisibility.value.coerceIn(0f, 1f)
        val lyricsHeaderAlpha = lyricsHeaderVisibility.value.coerceIn(0f, 1f)
        val lyricsListAlpha = lyricsListVisibility.value.coerceIn(0f, 1f)
        val lyricsChromeAlpha = lyricsChromeVisibility.value.coerceIn(0f, 1f)
        val controlsAlpha = if (splitLayout) 1f else lerpFloat(1f, controlsVisibility.value, p)
        val controlsTranslationPx = with(density) { 176.dp.toPx() } * (1f - controlsAlpha)
        var bottomControlsTopPx by remember { mutableFloatStateOf(Float.NaN) }
        var controlsHeight by remember { mutableStateOf(244.dp) }
        val metadata: @Composable () -> Unit = {
            Box(Modifier.padding(horizontal = if (landscapeLayout) 16.dp else 0.dp)) {
                PlayerTrackMetadata(
                    current.track,
                    favorite,
                    onToggleFavorite,
                    onMore,
                    titleMaxLines = if (landscapeLayout) 1 else 2
                )
            }
        }
        val controls: @Composable () -> Unit = {
            Box(Modifier.padding(horizontal = if (landscapeLayout) 16.dp else 0.dp)) {
                PlayerPlaybackControls(
                    state = state,
                    compactHeight = compactHeight,
                    compactTransport = landscapeLayout,
                    showUtilities = !landscapeLayout,
                    lyricsMode = displayedLyricsMode,
                    queueMode = displayedQueueMode,
                    systemVolume = systemVolume,
                    minimumVolume = minimumVolume,
                    maximumVolume = maximumVolume,
                    progressInteraction = progressInteraction,
                    volumeInteraction = volumeInteraction,
                    onSeek = ::seekAndFollow,
                    onPrevious = {
                        revealControls()
                        onPrevious()
                    },
                    onToggle = {
                        revealControls()
                        onToggle()
                    },
                    onNext = {
                        revealControls()
                        onNext()
                    },
                    onVolumeChange = { value ->
                        revealControls()
                        val volume = value.roundToInt().coerceIn(minimumVolume, maximumVolume)
                        systemVolume = volume.toFloat()
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            volume,
                            0
                        )
                    },
                    onLyricsClick = {
                        revealControls()
                        if (lyricsMode) onCloseLyrics() else onOpenLyrics()
                    },
                    onQueueClick = {
                        revealControls()
                        if (queueMode) onCloseQueue() else onOpenQueue()
                    },
                )
            }
        }
        Box(
            Modifier.fillMaxSize()
                .layerBackdrop(playerBackgroundBackdrop)
                .testTag("player-artwork-background"),
        ) {
            CoverImage(
                highResolutionCoverUrl,
                null,
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.22f
                        scaleY = 1.22f
                    }
                    .blur(64.dp),
                requestSizePx = 640,
            )
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.48f)),
            )
            Box(
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0xFF211923).copy(alpha = 0.34f),
                            0.55f to Color(0xFF171319).copy(alpha = 0.28f),
                            1f to FnBackgroundBottom.copy(alpha = 0.88f),
                        ),
                    ),
            )
        }

        if (landscapeLayout) {
            Box(
                Modifier.offset(x = safeLeft + geometry.contentStart, y = safeTop + playerTopPadding)
                    .width(geometry.playerWidth)
                    .height(availablePlayerHeight)
                    .then(coverViewportModifier)
                    .testTag("player-primary-pane"),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(minOf(geometry.playerWidth, availablePlayerHeight))
                        .then(largeCoverModifier)
                )
            }
        } else
            PlayerPrimaryPane(
                geometry = geometry,
                availableHeight = availablePlayerHeight,
                maximumCoverSize = if (wideLayout || maxWidth >= 600.dp) 440.dp else 380.dp,
                immersiveMode = immersiveMode,
                metadataAlpha = defaultContentAlpha,
                controlsAlpha = controlsAlpha,
                controlsTranslationPx = controlsTranslationPx,
                largeCoverModifier = largeCoverModifier,
                coverViewportModifier = coverViewportModifier,
                onControlsPositioned = {
                    controlsHeight = with(density) { it.size.height.toDp() }
                    if (!displayedLyricsMode || controlsAlpha >= 0.995f)
                        bottomControlsTopPx = it.boundsInRoot().top
                },
                modifier =
                    Modifier.offset(x = safeLeft, y = safeTop + playerTopPadding)
                        .width(maxWidth - safeLeft - safeRight)
                        .height(availablePlayerHeight)
                        .zIndex(if (wideLayout) 0f else 2f),
                metadata = metadata,
                controls = controls,
            )
        val landscapeFooterAlpha =
            if (landscapeLayout && displayedLyricsMode && !displayedQueueMode) controlsVisibility.value else 1f
        Column(
            (if (splitLayout)
                    Modifier.offset(x = safeLeft + geometry.detailStart, y = safeTop + playerTopPadding)
                        .width(geometry.detailWidth)
                        .height(
                            (maxHeight - safeTop - safeBottom - playerTopPadding - playerBottomPadding +
                                    if (landscapeLayout) playerBottomPadding * (1f - landscapeFooterAlpha)
                                    else 0.dp)
                                .coerceAtLeast(0.dp)
                        )
                else Modifier.fillMaxSize())
                .pointerInput(landscapeLayout, displayedLyricsMode) {
                    if (landscapeLayout && displayedLyricsMode) {
                        awaitEachGesture {
                            awaitFirstDown(
                                requireUnconsumed = false,
                                pass = PointerEventPass.Initial
                            )
                            landscapeTouchActive = true
                            revealControls()
                            try {
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                } while (event.changes.any { it.pressed })
                            } finally {
                                landscapeTouchActive = false
                                revealControls()
                            }
                        }
                    }
                }
        ) {
            if (landscapeLayout && displayedLyricsMode) {
                Box(Modifier.fillMaxWidth().then(dragModifier).graphicsLayer { alpha = pageVisibility.value }.testTag("player-landscape-lyrics-header")) {
                    metadata()
                }
                Spacer(Modifier.height(12.dp))
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()
                .graphicsLayer { alpha = if (landscapeLayout) pageVisibility.value else 1f }
                .semantics { playerPageAlpha = if (landscapeLayout) pageVisibility.value else 1f }
                .testTag("player-detail-pane")) {
                if (landscapeLayout && !displayedLyricsMode && !displayedQueueMode) {
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .testTag("player-landscape-controls"),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        metadata()
                        Spacer(Modifier.height(8.dp))
                        controls()
                    }
                }
                val lyricsHorizontalPadding =
                    if (splitLayout) 16.dp
                    else if (portraitPhoneLayout) safeLeft + geometry.contentStart
                    else 64.dp
                val lyricsCoverSize = if (splitLayout) 0.dp else 56.dp
                val lyricsHeaderTop = if (splitLayout) 0.dp else safeTop + 34.dp
                val panelHeight = maxHeight
                val panelBottom = if (splitLayout) 0.dp else safeBottom
                val headerBottomPx =
                    if (splitLayout) 0f
                    else
                        measuredLyricsHeaderBottomPx.takeIf { it.isFinite() }
                            ?: with(density) { (lyricsHeaderTop + lyricsCoverSize).toPx() }
                val focusTopPx =
                    headerBottomPx +
                        (with(density) { (maxHeight - panelBottom).toPx() } - headerBottomPx) *
                            0.18f
                val focusTop = with(density) { focusTopPx.toDp() }
                val lyricsTrailingSpaceHeight = (maxHeight - focusTop).coerceAtLeast(0.dp)
                val controlsBottomPadding =
                    if (splitLayout) 0.dp
                    else
                        with(density) {
                            lerpFloat(
                                    (safeBottom + controlsHeight + 20.dp).toPx(),
                                    (safeBottom + 28.dp).toPx(),
                                    1f - controlsAlpha
                                )
                                .toDp()
                        }
                val queueBottomPadding =
                    if (splitLayout) 0.dp
                    else
                        bottomControlsTopPx.takeIf { it.isFinite() }?.let {
                            with(density) {
                                (maxHeight.toPx() - (it - playerRootTopPx))
                                    .coerceAtLeast(0f)
                                    .toDp() + 16.dp
                            }
                        }
                            ?: controlsBottomPadding
                val headerTapInteractionSource =
                    remember(current.track.id) { MutableInteractionSource() }
                FollowLyricPosition(
                    listState = lyricsListState,
                    contentKey = current.track.id to musicState.lyrics,
                    activeIndex = activeIndex,
                    enabled = displayedLyricsMode,
                    following = followCurrent,
                    focusTopPx = focusTopPx,
                    textInsetPx = with(density) { 4.dp.toPx() },
                    ready = lyricsPositionReady,
                    onReady = { lyricsPositionReady = true },
                )
                if (!splitLayout && (displayedLyricsMode || (!displayedQueueMode && lastImmersiveContentWasLyrics))) {
                    Box(
                        Modifier.offset(x = lyricsHorizontalPadding, y = lyricsHeaderTop)
                            .size(lyricsCoverSize)
                            .then(lyricsCoverModifier)
                    )
                }
                val compactHeader: @Composable (Modifier, Boolean) -> Unit =
                    { headerModifier, inQueue ->
                        Row(
                            headerModifier
                                .then(dragModifier)
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
                                    if (!inQueue)
                                        measuredLyricsHeaderBottomPx =
                                            it.boundsInRoot().bottom - playerRootTopPx
                                }
                                .testTag(
                                    if (inQueue) "player-queue-header" else "player-lyrics-header"
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (inQueue) {
                                Box(Modifier.size(lyricsCoverSize).testTag("player-queue-cover")) {
                                    if (displayedQueueMode)
                                        Box(Modifier.fillMaxSize().then(lyricsCoverModifier))
                                    CoverImage(
                                        current.coverUrl,
                                        current.track.title,
                                        Modifier.fillMaxSize().graphicsLayer {
                                            alpha = if (displayedQueueMode && p >= 0.999f) 1f else 0f
                                        },
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                            } else {
                                Spacer(Modifier.size(lyricsCoverSize + 12.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(
                                    current.track.title,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    current.track.artists.joinToString(" / ") { it.name }.ifBlank {
                                        "未知歌手"
                                    },
                                    color = FnTextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Box(
                                modifier =
                                    Modifier.size(if (inQueue) 40.dp else 46.dp)
                                        .clip(CircleShape)
                                        .pointerInput(current.track.id, favorite) {
                                            detectTapGestures {
                                                revealControls()
                                                onToggleFavorite(
                                                    current.track.copy(isFavorite = favorite)
                                                )
                                            }
                                        }
                                        .semantics {
                                            contentDescription = "收藏"
                                            onClick {
                                                revealControls()
                                                onToggleFavorite(
                                                    current.track.copy(isFavorite = favorite)
                                                )
                                                true
                                            }
                                        }
                                        .testTag("player-lyrics-favorite-action"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    if (favorite) Icons.Rounded.Favorite
                                    else Icons.Rounded.FavoriteBorder,
                                    "收藏",
                                    tint = if (favorite) FnAccentIcon else FnTextSecondary,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier =
                                    Modifier.size(if (inQueue) 40.dp else 46.dp)
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
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    null,
                                    tint = FnTextPrimary.copy(alpha = 0.82f)
                                )
                            }
                        }
                    }
                if (!splitLayout && (displayedLyricsMode || (!displayedQueueMode && lastImmersiveContentWasLyrics))) {
                    compactHeader(
                        Modifier.align(Alignment.TopStart)
                            .padding(
                                start = lyricsHorizontalPadding,
                                top = lyricsHeaderTop,
                                end = lyricsHorizontalPadding
                            )
                            .graphicsLayer { alpha = lyricsHeaderAlpha }
                            .then(
                                if (lyricsHeaderAlpha < 0.1f) Modifier.clearAndSetSemantics {}
                                else Modifier
                            ),
                        false,
                    )
                }

                // Scroll content must remain above the background's header tint.
                Box(Modifier.fillMaxSize().zIndex(2f)) {
                    // The page sequencer owns landscape fades; replace content only at zero opacity.
                    if (!landscapeLayout || displayedQueueMode) androidx.compose.animation.AnimatedVisibility(
                        visible = displayedQueueMode,
                        enter =
                            fadeIn(
                                tween(
                                    durationMillis = 180,
                                    delayMillis = 24,
                                    easing = LinearOutSlowInEasing
                                )
                            ),
                        exit = fadeOut(tween(durationMillis = 130, easing = FastOutLinearInEasing)),
                        modifier = Modifier.zIndex(2f),
                        label = "player-queue-content",
                    ) {
                        QueuePlayerContent(
                            state = state,
                            topPadding = lyricsHeaderTop,
                            bottomPadding = queueBottomPadding,
                            alpha = 1f,
                            onSelect = { index ->
                                revealControls()
                                onSelectQueueItem(index)
                            },
                            onSelectHistoryItem = { index ->
                                revealControls()
                                onSelectHistoryItem(index)
                            },
                            onClearPlaybackHistory = onClearPlaybackHistory,
                            currentHeader = {
                                if (!splitLayout)
                                    compactHeader(
                                        Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 0.dp
                                        ),
                                        true
                                    )
                            },
                            onMove = onMoveQueueItem,
                            onRemove = onRemoveQueueItemDirect,
                            onMore = onRemoveQueueItem,
                            onToggleShuffle = {
                                revealControls()
                                onToggleShuffle()
                            },
                            onCycleRepeatMode = {
                                revealControls()
                                onCycleRepeatMode()
                            },
                        )
                    }
                    if (!landscapeLayout || displayedLyricsMode) androidx.compose.animation.AnimatedVisibility(
                        visible =
                            !displayedQueueMode &&
                                (wideLayout ||
                                    (landscapeLayout && displayedLyricsMode) ||
                                    ((displayedLyricsMode || lastImmersiveContentWasLyrics) && p > 0.01f)),
                        enter =
                            fadeIn(
                                tween(
                                    durationMillis = 180,
                                    delayMillis = 24,
                                    easing = LinearOutSlowInEasing
                                )
                            ),
                        exit = fadeOut(tween(durationMillis = 130, easing = FastOutLinearInEasing)),
                        label = "player-lyrics-content",
                    ) {
                        PlayerLyricsPane(
                            lyrics = musicState.lyrics,
                            trackKey = current.track.id,
                            lyricsMode = displayedLyricsMode,
                            lyricsListState = lyricsListState,
                            activeIndex = activeIndex,
                            followCurrent = followCurrent,
                            lyricTimelines = lyricTimelines,
                            lyricPosition = lyricPosition,
                            lyricsListAlpha = lyricsListAlpha,
                            viewport =
                                PlayerLyricsViewport(
                                    emptyTop =
                                        if (splitLayout) 0.dp
                                        else lyricsHeaderTop + lyricsCoverSize + 30.dp,
                                    emptyBottom = controlsBottomPadding,
                                    focusTop = focusTop,
                                    trailingSpace = lyricsTrailingSpaceHeight,
                                    horizontalPadding = lyricsHorizontalPadding,
                                    removeRendererInset = landscapeLayout || portraitPhoneLayout,
                                    bottomInset = panelBottom,
                                    fadeTopPx = headerBottomPx,
                                    fadeBottomPx =
                                        with(density) {
                                            val fullBottom = (panelHeight - panelBottom).toPx()
                                            if (splitLayout) fullBottom
                                            else
                                                lerpFloat(
                                                    fullBottom,
                                                    bottomControlsTopPx
                                                        .takeIf { it.isFinite() }
                                                        ?.minus(playerRootTopPx)
                                                        ?: (panelHeight -
                                                                safeBottom -
                                                                controlsHeight -
                                                                20.dp)
                                                            .toPx(),
                                                    controlsAlpha,
                                                )
                                        },
                                ),
                            onManualScroll = {
                                pendingSeekPositionMs = null
                                followCurrent = false
                                manualScrollActivity += 1
                                revealControls()
                            },
                            onInteraction = ::revealControls,
                            onSeek = ::seekAndFollow,
                        )
                    }
                }

                Box(
                    Modifier.align(Alignment.TopCenter)
                        .fillMaxWidth()
                        // Keep the header fully protected, then give the blur enough room
                        // to dissolve before the backdrop layer reaches its clipped edge.
                        .height(
                            if (displayedQueueMode) lyricsHeaderTop
                            else lyricsHeaderTop + lyricsCoverSize + 90.dp
                        )
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
                    Modifier.align(Alignment.BottomCenter)
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
            }
            if (landscapeLayout) {
                val footerAlpha = landscapeFooterAlpha
                // Return the footer space to the lyrics as the controls disappear.
                Box(
                    Modifier.fillMaxWidth().height(56.dp * footerAlpha).graphicsLayer {
                        alpha = footerAlpha
                        translationY = (1f - footerAlpha) * 48.dp.toPx()
                    }
                ) {
                    if (footerAlpha > 0.01f) {
                        Column(Modifier.padding(top = 8.dp)) {
                            PlayerPageControls(
                                state = state,
                                lyricsMode = displayedLyricsMode,
                                queueMode = displayedQueueMode,
                                onLyricsClick = {
                                    if (lyricsMode) onCloseLyrics() else onOpenLyrics()
                                },
                                onQueueClick = { if (queueMode) onCloseQueue() else onOpenQueue() },
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth()
                // Leave the top edge to the immersive system-bar reveal gesture.
                .padding(top = safeTop + if (landscapeLayout) 8.dp else 0.dp)
                .height(if (landscapeLayout) 24.dp else 32.dp)
                .zIndex(3f)
                .then(dragModifier)
                .semantics { contentDescription = "下拉收起播放器" },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier.padding(top = 5.dp)
                    .size(36.dp, 5.dp)
                    .background(FnTextSecondary.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                    .testTag("player-drag-handle")
            )
        }
    }
}

private data class PlayerLyricsViewport(
    val emptyTop: Dp,
    val emptyBottom: Dp,
    val focusTop: Dp,
    val trailingSpace: Dp,
    val horizontalPadding: Dp,
    val removeRendererInset: Boolean = false,
    val bottomInset: Dp,
    val fadeTopPx: Float,
    val fadeBottomPx: Float,
)

/** Lyrics render in pane-local coordinates; playback controls only supply the narrow fade edge. */
@Composable
private fun PlayerLyricsPane(
    lyrics: List<LyricLine>,
    trackKey: TrackId,
    lyricsMode: Boolean,
    lyricsListState: LazyListState,
    activeIndex: Int,
    followCurrent: Boolean,
    lyricTimelines: List<com.seasonyuu.fnmusic.core.model.LyricTimeline?>,
    lyricPosition: State<Long>,
    lyricsListAlpha: Float,
    viewport: PlayerLyricsViewport,
    onManualScroll: () -> Unit,
    onInteraction: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    val lyricsPressSurfaceInset = 8.dp
    val latestManualScroll by rememberUpdatedState(onManualScroll)
    if (lyrics.isEmpty()) {
        Box(
            Modifier.fillMaxSize()
                .padding(top = viewport.emptyTop, bottom = viewport.emptyBottom)
                .graphicsLayer { alpha = lyricsListAlpha }
                .then(if (lyricsListAlpha < 0.1f) Modifier.clearAndSetSemantics {} else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Rounded.Lyrics,
                    null,
                    tint = FnTextSecondary,
                    modifier = Modifier.size(40.dp)
                )
                Text("这首歌暂无歌词", color = FnTextSecondary)
            }
        }
    } else {
        val manualScrollConnection =
            remember(trackKey, lyricsMode) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        if (source == NestedScrollSource.UserInput && lyricsMode) {
                            latestManualScroll()
                        }
                        return Offset.Zero
                    }
                }
            }
        LazyColumn(
            state = lyricsListState,
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(manualScrollConnection)
                    .pointerInput(trackKey, lyricsMode) {
                        detectTapGestures { if (lyricsMode) onInteraction() }
                    }
                    .graphicsLayer { alpha = lyricsListAlpha }
                    .then(
                        if (lyricsListAlpha < 0.1f) Modifier.clearAndSetSemantics {} else Modifier
                    )
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val top = viewport.fadeTopPx.coerceIn(0f, size.height)
                        val bottom =
                            viewport.fadeBottomPx.coerceIn(
                                (top + 1f).coerceAtMost(size.height),
                                size.height
                            )
                        val fadeTopEnd = (top + 40.dp.toPx()).coerceAtMost(bottom)
                        val fadeBottomStart = (bottom - 32.dp.toPx()).coerceAtLeast(fadeTopEnd)
                        drawRect(
                            brush =
                                Brush.verticalGradient(
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
            contentPadding =
                PaddingValues(
                    start = lyricsPressSurfaceInset,
                    top = (viewport.focusTop - 4.dp).coerceAtLeast(0.dp),
                    end = lyricsPressSurfaceInset,
                    bottom = viewport.bottomInset,
                ),
            // KaraokeLineText already adds 8 dp above and below each lyric.
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(lyrics, key = { index, line -> "${line.timeMs ?: -1}:$index" }) {
                index,
                line ->
                val active = index == activeIndex
                val activeProgress by
                    animateFloatAsState(
                        targetValue = if (active) 1f else 0f,
                        animationSpec =
                            tween(
                                durationMillis = if (active) 600 else 400,
                                easing = FastOutSlowInEasing,
                            ),
                        label = "lyrics-line-active",
                    )
                val blurRadius =
                    animatedLyricBlurRadius(
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
                val pressProgress by
                    animateFloatAsState(
                        targetValue = if (pressed) 1f else 0f,
                        animationSpec =
                            tween(
                                durationMillis = if (pressed) 70 else 120,
                                easing = FastOutSlowInEasing,
                            ),
                        label = "lyrics-line-press",
                    )
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            FnTextPrimary.copy(alpha = 0.12f * pressProgress),
                            RoundedCornerShape(16.dp),
                        )
                        // Observe the down immediately; clickable delays presses inside a
                        // LazyColumn.
                        // Do not consume events: scrolling and click/keyboard semantics stay with
                        // their owners.
                        .pointerInput(line.timeMs, lyricsMode) {
                            awaitEachGesture {
                                awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial
                                )
                                touchPressed = line.timeMs != null || lyricsMode
                                try {
                                    // Finish dispatching this down before checking later events for
                                    // scroll cancellation.
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
                            onInteraction()
                            line.timeMs?.let(onSeek)
                        }
                        .padding(
                            horizontal = viewport.horizontalPadding - lyricsPressSurfaceInset,
                            vertical = 4.dp,
                        )
                        .testTag("lyrics-line-$index")
                        .semantics { lyricBlurRadius = blurRadius.value },
                ) {
                    Column(
                        Modifier.blur(
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
                            baseColor =
                                FnTextPrimary.copy(
                                    alpha =
                                        if (!followCurrent || activeIndex < 0) 0.58f
                                        else
                                            lerpFloat(
                                                0.58f,
                                                0.24f,
                                                (blurRadius.value / 4f).coerceIn(0f, 1f)
                                            )
                                ),
                            highlightColor = FnTextPrimary,
                            removeHorizontalInset = viewport.removeRendererInset,
                        )
                        line.translation?.takeIf(String::isNotBlank)?.let { translation ->
                            Text(
                                translation,
                                color =
                                    FnTextSecondary.copy(
                                        alpha = lerpFloat(0.65f, 1f, activeProgress),
                                    ),
                            )
                        }
                    }
                }
            }
            item(key = "lyrics-trailing-space") {
                Spacer(
                    Modifier.height(viewport.trailingSpace).testTag("lyrics-trailing-space"),
                )
            }
        }
    }
}

@Composable
private fun PlayerTrackMetadata(
    track: Track,
    favorite: Boolean,
    onToggleFavorite: (Track) -> Unit,
    onMore: (Track) -> Unit,
    titleMaxLines: Int = 2,
) {
    Row(
        Modifier.testTag("player-track-metadata"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                track.title,
                fontSize = 22.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                color = FnTextPrimary.copy(alpha = 0.68f),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier =
                Modifier.size(46.dp)
                    .clip(CircleShape)
                    .pointerInput(track.id, favorite) {
                        detectTapGestures { onToggleFavorite(track.copy(isFavorite = favorite)) }
                    }
                    .semantics {
                        contentDescription = "收藏"
                        onClick {
                            onToggleFavorite(track.copy(isFavorite = favorite))
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
            modifier =
                Modifier.size(46.dp)
                    .clip(CircleShape)
                    .pointerInput(track.id) { detectTapGestures { onMore(track) } }
                    .semantics {
                        contentDescription = "更多操作"
                        onClick {
                            onMore(track)
                            true
                        }
                    }
                    .testTag("player-more-action"),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MoreVert, null, tint = FnTextPrimary.copy(alpha = 0.82f)) }
    }
}

@Composable
private fun PlayerPlaybackControls(
    state: PlayerState,
    compactHeight: Boolean,
    compactTransport: Boolean = false,
    showUtilities: Boolean = true,
    showVolume: Boolean = true,
    lyricsMode: Boolean,
    queueMode: Boolean,
    systemVolume: Float,
    minimumVolume: Int,
    maximumVolume: Int,
    progressInteraction: MutableInteractionSource,
    volumeInteraction: MutableInteractionSource,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    val volumeExpansion = playerSliderExpansion(volumeInteraction, maximumVolume > minimumVolume)
    val volumeMotion = rememberPlayerSliderMotion()
    val startOnLeft = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Ltr
    val volumeTint = FnTextPrimary.copy(alpha = 0.58f + 0.42f * volumeExpansion)
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compactHeight) 2.dp else 6.dp)
    ) {
        PlaybackProgress(
            state,
            modifier = Modifier.testTag("player-playback-progress"),
            interactionSource = progressInteraction,
        ) { position -> onSeek(position) }
        Row(
            Modifier.fillMaxWidth()
                .padding(vertical = if (compactTransport) 8.dp else 0.dp)
                .testTag("player-transport-controls"),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onPrevious,
                enabled = state.canSkipPrevious,
                modifier = Modifier.size(if (compactTransport) 60.dp else 68.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipPrevious,
                    "上一首",
                    Modifier.size(if (compactTransport) 40.dp else 44.dp)
                )
            }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(if (compactTransport) 76.dp else 84.dp)
            ) {
                PlaybackToggleIcon(
                    state,
                    iconSize = if (compactTransport) 54.dp else 60.dp,
                    tint = FnTextPrimary,
                )
            }
            IconButton(
                onClick = onNext,
                enabled = state.canSkipNext,
                modifier = Modifier.size(if (compactTransport) 60.dp else 68.dp)
            ) {
                Icon(
                    Icons.Rounded.SkipNext,
                    "下一首",
                    Modifier.size(if (compactTransport) 40.dp else 44.dp)
                )
            }
        }
        if (showVolume)
            Row(
                Modifier.fillMaxWidth().testTag("player-volume-control"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeDown,
                    null,
                    tint = volumeTint,
                    modifier = Modifier.size(18.dp).offset(x = (-0.75).dp)
                        // Anchor at the vector's inner ink edge, not its padded 24-unit viewport.
                        .playerSliderEndpoint(
                            volumeExpansion,
                            originX = if (startOnLeft) 18.5f / 24f else 5.5f / 24f,
                            translationPx = if (startOnLeft) volumeMotion.leftShift(volumeExpansion) else volumeMotion.rightShift(volumeExpansion),
                            pulse = volumeMotion.minimumPulse.value,
                        )
                        .testTag("player-volume-low-icon"),
                )
                PlayerSlider(
                    value = systemVolume,
                    onValueChange = onVolumeChange,
                    valueRange = minimumVolume.toFloat()..maximumVolume.toFloat(),
                    steps = (maximumVolume - minimumVolume - 1).coerceAtLeast(0),
                    enabled = maximumVolume > minimumVolume,
                    modifier = Modifier.weight(1f).testTag("player-volume-slider")
                        .semantics { contentDescription = "媒体音量" },
                    interactionSource = volumeInteraction,
                    expansion = volumeExpansion,
                    motion = volumeMotion,
                    elastic = true,
                )
                Icon(
                    Icons.AutoMirrored.Rounded.VolumeUp,
                    null,
                    tint = volumeTint,
                    // VolumeUp ends at x=21: its 2.25dp end inset needs another 0.75dp.
                    modifier = Modifier.size(18.dp).offset(x = (-0.75).dp)
                        .playerSliderEndpoint(
                            volumeExpansion,
                            originX = if (startOnLeft) 3f / 24f else 21f / 24f,
                            translationPx = if (startOnLeft) volumeMotion.rightShift(volumeExpansion) else volumeMotion.leftShift(volumeExpansion),
                            pulse = volumeMotion.maximumPulse.value,
                        )
                        .testTag("player-volume-high-icon"),
                )
            }
        if (showUtilities) {
            Spacer(Modifier.height(if (compactHeight) 12.dp else 48.dp))
            PlayerPageControls(state, lyricsMode, queueMode, onLyricsClick, onQueueClick)
        }
    }
}

@Composable
private fun PlayerPageControls(
    state: PlayerState,
    lyricsMode: Boolean,
    queueMode: Boolean,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().testTag("player-bottom-utilities"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onLyricsClick,
                modifier =
                    Modifier.size(48.dp)
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
                onOpenQueue = onQueueClick,
            )
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
                        AppBarButton(onClick = onDismiss) {
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
                        Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏", tint = if (favorite) FnAccentIcon else FnTextSecondary)
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
                        Icon(Icons.Rounded.Shuffle, if (state.shuffleEnabled) "关闭随机播放" else "开启随机播放", tint = if (state.shuffleEnabled) FnAccentIcon else FnTextSecondary)
                    }
                    IconButton(onClick = onPrevious, enabled = canGoPrevious, modifier = Modifier.size(56.dp)) { Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(34.dp)) }
                    FilledIconButton(onClick = onToggle, modifier = Modifier.size(72.dp)) {
                        PlaybackToggleIcon(state, iconSize = 38.dp)
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
            Icon(FnIcons.Roam, null, tint = FnAccentIcon, modifier = Modifier.size(24.dp))
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
            AppBarButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回播放界面")
            }
            Column(Modifier.weight(1f)) {
                Text("歌词", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(current.track.title, color = FnTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!followCurrent && activeIndex >= 0) {
                AppBarButton(onClick = { followCurrent = true }) { Text("回到当前") }
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
                        PlaybackToggleIcon(state, iconSize = 32.dp)
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
    val listState = rememberSaveable(state.playbackSessionId, saver = LazyListState.Saver) {
        LazyListState(firstVisibleItemIndex = currentHeaderIndex)
    }
    var currentHeaderHeightPx by remember { mutableIntStateOf(0) }
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

    val snapBehavior = rememberQueueSnapBehavior(
        listState = listState,
        historyCount = historyCount,
        currentHeightPx = currentHeaderHeightPx,
        contentKey = listOf(state.playbackSessionId, state.current?.queueEntryId,
            state.playbackHistory.map { it.queueEntryId }, displayedQueue.map { it.key }),
        enabled = draggedKey == null && revealedKey == null,
    )

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
    var queuePositionReady by rememberSaveable(state.playbackSessionId) { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!queuePositionReady) {
            listState.scrollToItem(currentHeaderIndex)
            queuePositionReady = true
        }
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
        // A pinned header must leave room for at least one actionable queue row.
        val pinModeHeader = maxHeight >= 240.dp
        val modeHeader: @Composable () -> Unit = {
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
        LazyColumn(
            state = listState,
            userScrollEnabled = draggedKey == null,
            flingBehavior = snapBehavior,
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
                val historyHeader: @Composable () -> Unit = {
                    // Leave with the current-song boundary rather than lingering over it.
                    val currentOffset = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == "queue-current" }?.offset?.toFloat()
                    val historyVisible = !pinModeHeader || (listState.firstVisibleItemIndex < currentHeaderIndex &&
                        (currentOffset == null || currentOffset > 0f))
                    val titleHeight = with(density) { QueueHistoryTitleHeight.toPx() }
                    Row(
                        Modifier.fillMaxWidth().height(QueueHistoryTitleHeight)
                            .graphicsLayer {
                                translationY = if (pinModeHeader && currentOffset != null)
                                    (currentOffset - titleHeight).coerceAtMost(0f) else 0f
                                this.alpha = if (historyVisible) 1f else 0f
                            }
                            .then(if (historyVisible) Modifier else Modifier.clearAndSetSemantics { })
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
                if (pinModeHeader) {
                    stickyHeader(key = "history-title") { historyHeader() }
                } else {
                    item(key = "history-title") { historyHeader() }
                }
                itemsIndexed(
                    state.playbackHistory,
                    key = { _, item -> "history:" + item.queueEntryId },
                ) { index, item ->
                    Row(
                        Modifier.fillMaxWidth().height(QueueHistoryRowHeight).padding(horizontal = 8.dp)
                            .drawWithContent {
                                val row = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == "history:" + item.queueEntryId }
                                val covered = if (pinModeHeader && row != null)
                                    QueueHistoryTitleHeight.toPx() - row.offset else 0f
                                clipRect(top = covered.coerceIn(0f, size.height)) {
                                    this@drawWithContent.drawContent()
                                }
                            }
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
                    Spacer(Modifier.height(QueueHistoryGap))
                }
            }
            item(key = "queue-current") {
                Column(Modifier.fillMaxWidth()
                    .onSizeChanged { currentHeaderHeightPx = it.height }
                    .padding(bottom = 22.dp)) { currentHeader() }
            }
            if (pinModeHeader) {
                stickyHeader(key = "queue-modes") { modeHeader() }
            } else {
                item(key = "queue-modes") { modeHeader() }
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
                        color = FnTextPrimary,
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
internal fun PlaybackProgress(
    state: PlayerState,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onSeek: (Long) -> Unit,
) {
    val duration = state.durationMs.coerceAtLeast(0)
    val entryId = state.current?.queueEntryId
    val enabled = state.current != null && state.durationMs > 0
    var previewPosition by remember(entryId) { mutableStateOf<Float?>(null) }
    var pendingPosition by remember(entryId) { mutableStateOf<Long?>(null) }
    val expansion = playerSliderExpansion(interactionSource, enabled)
    val motion = rememberPlayerSliderMotion()
    val startOnLeft = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Ltr
    val timeColor = FnTextPrimary.copy(alpha = 0.58f + 0.42f * expansion)
    LaunchedEffect(pendingPosition, state.positionMs) {
        val target = pendingPosition ?: return@LaunchedEffect
        if (kotlin.math.abs(state.positionMs - target) <= 1_000L) pendingPosition = null
    }
    LaunchedEffect(pendingPosition) {
        if (pendingPosition != null) {
            delay(1_000)
            pendingPosition = null
        }
    }
    val displayed = (previewPosition ?: pendingPosition?.toFloat() ?: state.positionMs.toFloat())
        .coerceIn(0f, duration.toFloat())
    androidx.compose.runtime.key(entryId) {
        PlayerSlider(
            value = displayed,
            onValueChange = { previewPosition = it },
            onValueChangeFinished = {
                previewPosition?.let {
                    val target = it.toLong().coerceIn(0L, duration)
                    pendingPosition = target
                    previewPosition = null
                    onSeek(target)
                }
            },
            onValueChangeCancelled = { previewPosition = null },
            valueRange = 0f..duration.toFloat(),
            enabled = enabled,
            modifier = modifier.fillMaxWidth().height(32.dp).semantics { contentDescription = "播放进度" },
            interactionSource = interactionSource,
            expansion = expansion,
            motion = motion,
        )
    }
    Box(Modifier.fillMaxWidth()) {
        Text(
            playbackQualityLabel(state.playbackAudioSpec ?: state.current?.track?.audioSpec),
            modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 52.dp).testTag("player-quality"),
            color = FnTextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration(displayed.toLong()),
                modifier = Modifier.playerSliderEndpoint(
                    expansion,
                    originX = if (startOnLeft) 0f else 1f,
                    translationPx = if (startOnLeft) motion.leftShift(expansion) else motion.rightShift(expansion),
                )
                    .testTag("player-elapsed-time"),
                color = timeColor,
                style = tabularBodyStyle(),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "−${formatDuration((state.durationMs - displayed.toLong()).coerceAtLeast(0L))}",
                modifier = Modifier.playerSliderEndpoint(
                    expansion,
                    originX = if (startOnLeft) 1f else 0f,
                    translationPx = if (startOnLeft) motion.rightShift(expansion) else motion.leftShift(expansion),
                )
                    .testTag("player-remaining-time"),
                color = timeColor,
                style = tabularBodyStyle(),
            )
        }
    }
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
