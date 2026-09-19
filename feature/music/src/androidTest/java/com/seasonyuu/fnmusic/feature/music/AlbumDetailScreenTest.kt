package com.seasonyuu.fnmusic.feature.music

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.seasonyuu.fnmusic.core.designsystem.*
import androidx.compose.runtime.CompositionLocalProvider
import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AlbumDetailScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val album = Album(AlbumId("album"), "测试专辑", releaseDate = "2026-09-13")
    private val tracks = (1..30).map { Track(TrackId("$it"), "歌曲 $it", durationSeconds = 180.0, trackNo = it) }

    @Test fun scrollKeepsBackFixedAndRestoresPosition() {
        val restoration = StateRestorationTester(compose)
        var backs = 0
        restoration.setContent {
            FnMusicTheme { LiquidMenuHost {
                AlbumDetailScreen(album, MusicUiState(detailTracks = tracks), PlayerState(), { _, _ -> null },
                    { _, _ -> }, { backs++ }, {})
            } }
        }
        val before = compose.onNodeWithTag("detail-app-bar").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasTestTag("album-footer"))
        compose.onNodeWithTag("album-footer").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithTag("detail-app-bar").fetchSemanticsNode().boundsInRoot)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("album-footer").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(1, backs)
    }

    @Test fun trackClickPreservesAlbumOrderAndMoreDoesNotPlay() {
        var index = -1
        var more: Track? = null
        compose.setContent {
            FnMusicTheme { LiquidMenuHost {
                CompositionLocalProvider(LocalTrackMenuActions provides { track, _ ->
                    listOf(TrackMenuAction(LiquidMenuItem("next", "下一首播放")) { more = track })
                }) {
                    AlbumDetailScreen(album, MusicUiState(detailTracks = tracks), PlayerState(), { _, _ -> null },
                        { queue, selected -> assertEquals(tracks, queue); index = selected }, {}, {})
                }
            } }
        }
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasTestTag("album-track-12"))
        compose.onNodeWithTag("album-track-12").performClick()
        assertEquals(12, index)
        compose.onNodeWithContentDescription("歌曲 13更多操作").performClick()
        compose.onNodeWithTag("liquid-menu-overlay").assertExists()
        compose.onNodeWithText("下一首播放").performClick()
        assertEquals(tracks[12], more)
        assertEquals(12, index)
    }

    @Test fun emptyAlbumDisablesPlaybackAndErrorCanRetry() {
        var retries = 0
        compose.setContent {
            FnMusicTheme { LiquidMenuHost {
                AlbumDetailScreen(album, MusicUiState(detailError = "加载失败"), PlayerState(), { _, _ -> null },
                    { _, _ -> error("Empty album must not play") }, {}, { retries++ })
            } }
        }
        compose.onNodeWithTag("album-play").assertIsNotEnabled()
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasText("重试"))
        compose.onNodeWithText("重试").performClick()
        assertEquals(1, retries)
    }
}
