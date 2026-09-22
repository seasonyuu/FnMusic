package com.seasonyuu.fnmusic

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.SessionState
import com.seasonyuu.fnmusic.core.player.PlaybackService
import com.seasonyuu.fnmusic.feature.music.MusicShell
import com.seasonyuu.fnmusic.feature.session.ConnectionScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var openPlayerRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openPlayerRequested = savedInstanceState?.getBoolean("openPlayerRequested")
            ?: (intent.action == PlaybackService.ACTION_OPEN_PLAYER)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val viewModel: MainViewModel = hiltViewModel()
            val aboutViewModel: AboutViewModel = hiltViewModel()
            val music by viewModel.music.collectAsState()
            val session by viewModel.session.collectAsState()
            val dark = when (music.appearance) {
                com.seasonyuu.fnmusic.core.model.AppearancePreference.Dark -> true
                com.seasonyuu.fnmusic.core.model.AppearancePreference.Light -> false
                com.seasonyuu.fnmusic.core.model.AppearancePreference.System -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            androidx.compose.runtime.SideEffect {
                if (session !is SessionState.Ready) androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            var permissionResult by androidx.compose.runtime.remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
            val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
                permissionResult?.invoke(granted); permissionResult = null
            }
            val requestNetwork: ((Boolean) -> Unit) -> Unit = { callback ->
                if (android.os.Build.VERSION.SDK_INT < 37 || checkSelfPermission(android.Manifest.permission.ACCESS_LOCAL_NETWORK) == android.content.pm.PackageManager.PERMISSION_GRANTED) callback(true)
                else { permissionResult = callback; permissionLauncher.launch(android.Manifest.permission.ACCESS_LOCAL_NETWORK) }
            }
            androidx.compose.runtime.CompositionLocalProvider(
                com.seasonyuu.fnmusic.feature.music.LocalPlaybackOutput provides viewModel.outputController,
                com.seasonyuu.fnmusic.feature.music.LocalNetworkPermissionRequest provides requestNetwork,
            ) {
            FnMusicTheme(darkTheme = dark, accent = androidx.compose.ui.graphics.Color(music.themeColor.argb)) {
                val player by viewModel.player.collectAsState()
                if (session is SessionState.Ready) {
                    MusicShell(
                        state = music,
                        playerState = player,
                        pagedTracks = viewModel::pagedTracks,
                        pagedAlbums = viewModel::pagedAlbums,
                        pagedArtists = viewModel.pagedArtists,
                        pagedFavorites = viewModel.pagedFavorites,
                        coverUrl = viewModel::coverUrl,
                        onRefresh = viewModel::refresh,
                        onSearch = viewModel::search,
                        onSearchType = viewModel::selectSearchType,
                        onSubmitSearch = viewModel::submitSearch,
                        onRetrySearch = viewModel::retrySearch,
                        onRoam = viewModel::roam,
                        onPlayAllTracks = viewModel::playAllTracks,
                        onPlayAllFavorites = viewModel::playAllFavorites,
                        onLoadAlbum = viewModel::loadAlbum,
                        onLoadArtist = viewModel::loadArtist,
                        onLoadPlaylist = viewModel::loadPlaylist,
                        onCreatePlaylist = viewModel::createPlaylist,
                        onUpdatePlaylist = viewModel::updatePlaylist,
                        playlistEditing = viewModel.playlistEditing,
                        onDeletePlaylist = viewModel::deletePlaylist,
                        onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                        onRemoveTracksFromPlaylist = viewModel::removeTracksFromPlaylist,
                        onPurgeInvalidPlaylistTracks = viewModel::purgeInvalidPlaylistTracks,
                        onLoadTrackMetadata = viewModel::loadTrackMetadata,
                        onSaveTrackMetadata = viewModel::saveTrackMetadata,
                        onLoadTrackTagOptions = viewModel::loadTrackTagOptions,
                        onPlay = viewModel::play,
                        onPlayNext = viewModel::playNext,
                        onAddToQueue = viewModel::addToQueue,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onTogglePlayback = viewModel::togglePlayback,
                        onSeek = viewModel::seekTo,
                        onPrevious = viewModel::skipPrevious,
                        onNext = viewModel::skipNext,
                        onSkipToQueueItem = viewModel::skipToQueueItem,
                        onSkipToHistoryItem = viewModel::skipToHistoryItem,
                        onClearPlaybackHistory = viewModel::clearPlaybackHistory,
                        onMoveQueueItem = viewModel::moveQueueItem,
                        onRemoveFromQueue = viewModel::removeFromQueue,
                        onToggleShuffle = viewModel::toggleShuffle,
                        onCycleRepeatMode = viewModel::cycleRepeatMode,
                        onCacheSizeChange = viewModel::setCacheSize,
                        onLiquidGlassBlurChange = viewModel::previewLiquidGlassBlur,
                        onLiquidGlassEnabledChange = viewModel::setLiquidGlassEnabled,
                        onLiquidGlassBlurSave = viewModel::saveLiquidGlassBlur,
                        onLogout = viewModel::logout,
                        onChangePassword = viewModel::changePassword,
                        onRefreshProfile = viewModel::refreshProfile,
                        administration = viewModel.administration,
                        onStreamingQualityChange = viewModel::setStreamingQuality,
                        onAppearanceChange = viewModel::setAppearance,
                        onThemeColorChange = viewModel::setThemeColor,
                        onCachePreferenceChange = viewModel::setCachePreference,
                        onClearCache = viewModel::clearCache,
                        lyricsActions = viewModel.lyricsActions,
                        aboutActions = aboutViewModel,
                        openPlayerRequested = openPlayerRequested,
                        onPlayerOpenRequestConsumed = { openPlayerRequested = false },
                    )
                } else {
                    val loginForm by viewModel.loginForm.collectAsState()
                    ConnectionScreen(session, { profile, password ->
                        requestNetwork { granted -> if (granted) viewModel.connect(profile, password) else {
                            password.fill('\u0000')
                            android.widget.Toast.makeText(this@MainActivity, "需要本地网络权限才能连接局域网 NAS，请重新连接并授权", android.widget.Toast.LENGTH_LONG).show()
                        } }
                    }, loginForm, viewModel::updateLoginForm)
                }
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == PlaybackService.ACTION_OPEN_PLAYER) openPlayerRequested = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("openPlayerRequested", openPlayerRequested)
        super.onSaveInstanceState(outState)
    }
}
