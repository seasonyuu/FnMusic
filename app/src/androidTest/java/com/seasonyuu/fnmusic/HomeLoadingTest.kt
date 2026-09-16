package com.seasonyuu.fnmusic

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.PagingData
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.feature.music.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HomeLoadingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun homeShowsSkeletonsThenCachedContentAndEmptyResults() {
        val state = mutableStateOf(MusicUiState())
        compose.setContent {
            FnMusicTheme {
                MusicShell(
                    state = state.value,
                    playerState = PlayerState(),
                    pagedTracks = { flowOf(PagingData.empty()) },
                    pagedAlbums = { flowOf(PagingData.empty()) },
                    pagedArtists = flowOf(PagingData.empty()),
                    pagedFavorites = flowOf(PagingData.empty()),
                    coverUrl = { _, _ -> null },
                    onRefresh = { },
                    onSearch = { _ -> },
                    onSearchType = { _ -> },
                    onRoam = { },
                    onPlayAllTracks = { },
                    onPlayAllFavorites = { },
                    onLoadAlbum = { _ -> },
                    onLoadArtist = { _ -> },
                    onLoadPlaylist = { _ -> },
                    onCreatePlaylist = { _, _, _ -> },
                    onUpdatePlaylist = { _, _, _ -> },
                    onDeletePlaylist = { _ -> },
                    onAddTrackToPlaylist = { _, _ -> },
                    onRemoveTracksFromPlaylist = { _, _ -> },
                    onPurgeInvalidPlaylistTracks = { _ -> },
                    onLoadTrackMetadata = { _ -> },
                    onPlay = { _, _ -> },
                    onPlayNext = { _ -> },
                    onAddToQueue = { _ -> },
                    onToggleFavorite = { _ -> },
                    onTogglePlayback = { },
                    onSeek = { _ -> },
                    onPrevious = { },
                    onNext = { },
                    onSkipToQueueItem = { _ -> },
                    onSkipToHistoryItem = { _ -> },
                    onClearPlaybackHistory = { },
                    onMoveQueueItem = { _, _ -> },
                    onRemoveFromQueue = { _ -> },
                    onToggleShuffle = { },
                    onCycleRepeatMode = { },
                    onCacheSizeChange = { _ -> },
                    onLogout = { },
                )
            }
        }
        compose.onNodeWithTag("首页快捷入口").assertIsDisplayed()
        compose.onNodeWithContentDescription("正在加载最近添加").assertIsDisplayed()
        compose.onNodeWithText("还没有最近添加的歌曲").assertDoesNotExist()
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.cacheDir, "home-loading-preview.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.runOnIdle {
            state.value = state.value.copy(tracks = listOf(Track(TrackId("cached"), "Cached song")))
        }
        compose.onNodeWithText("Cached song").assertIsDisplayed()
        compose.onNodeWithContentDescription("正在加载最近添加").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(loading = false, pendingSections = emptySet(), tracks = emptyList())
        }
        compose.onNodeWithText("还没有最近添加的歌曲").assertIsDisplayed()
        compose.onNodeWithContentDescription("正在加载最近添加").assertDoesNotExist()
    }
}
