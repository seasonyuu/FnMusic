package com.seasonyuu.fnmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.SessionState
import com.seasonyuu.fnmusic.feature.music.MusicShell
import com.seasonyuu.fnmusic.feature.music.MusicLoadingScreen
import com.seasonyuu.fnmusic.feature.session.ConnectionScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            FnMusicTheme {
                val viewModel: MainViewModel = hiltViewModel()
                val session by viewModel.session.collectAsState()
                val music by viewModel.music.collectAsState()
                val player by viewModel.player.collectAsState()
                if (session is SessionState.Ready) {
                    if (player.current == null && music.loading && music.tracks.isEmpty() && music.albums.isEmpty() && music.playlists.isEmpty()) {
                        MusicLoadingScreen()
                    } else {
                        MusicShell(
                            state = music,
                            playerState = player,
                            pagedTracks = viewModel.pagedTracks,
                            pagedAlbums = viewModel.pagedAlbums,
                            pagedArtists = viewModel.pagedArtists,
                            pagedFavorites = viewModel.pagedFavorites,
                            pagedSearch = viewModel.pagedSearch,
                            coverUrl = viewModel::coverUrl,
                            onRefresh = viewModel::refresh,
                            onSearch = viewModel::search,
                            onSearchType = viewModel::selectSearchType,
                            onTrackSort = viewModel::selectTrackSort,
                            onAlbumSort = viewModel::selectAlbumSort,
                            onRoam = viewModel::roam,
                            onPlayAllTracks = viewModel::playAllTracks,
                            onPlayAllFavorites = viewModel::playAllFavorites,
                            onLoadAlbum = viewModel::loadAlbum,
                            onLoadArtist = viewModel::loadArtist,
                            onLoadPlaylist = viewModel::loadPlaylist,
                            onCreatePlaylist = viewModel::createPlaylist,
                            onUpdatePlaylist = viewModel::updatePlaylist,
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                            onRemoveTracksFromPlaylist = viewModel::removeTracksFromPlaylist,
                            onPurgeInvalidPlaylistTracks = viewModel::purgeInvalidPlaylistTracks,
                            onLoadTrackMetadata = viewModel::loadTrackMetadata,
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
                            onLogout = viewModel::logout,
                        )
                    }
                } else {
                    ConnectionScreen(session, viewModel::connect)
                }
            }
        }
    }
}
