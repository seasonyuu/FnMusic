package com.seasonyuu.fnmusic.feature.music

import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.geometry.Offset
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.Album
import com.seasonyuu.fnmusic.core.model.AlbumId
import com.seasonyuu.fnmusic.core.model.Artist
import com.seasonyuu.fnmusic.core.model.ArtistId
import com.seasonyuu.fnmusic.core.model.AudioSpec
import com.seasonyuu.fnmusic.core.model.LyricLine
import com.seasonyuu.fnmusic.core.model.SearchSuggestions
import com.seasonyuu.fnmusic.core.model.SearchType
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import com.seasonyuu.fnmusic.core.model.TrackMetadata
import com.seasonyuu.fnmusic.core.model.TrackSort
import com.seasonyuu.fnmusic.core.model.Playlist
import com.seasonyuu.fnmusic.core.model.PlaylistId
import com.seasonyuu.fnmusic.data.SearchItem
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class MusicShellTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun homeFavoriteCardOpensPagedFavorites() {
        setContent()

        compose.onNodeWithContentDescription("收藏 快捷入口").performClick()

        compose.onNodeWithText("0 首已加载歌曲").assertIsDisplayed()
    }

    @Test
    fun homeRoamCardDispatchesTheRoamAction() {
        var roamRequests = 0
        setContent(onRoam = { roamRequests += 1 })

        compose.onNodeWithContentDescription("随机漫游").performClick()

        org.junit.Assert.assertEquals(1, roamRequests)
    }

    @Test
    fun homeRecentlyAddedFlowCardOpensTheLibraryWithRecentSort() {
        var selectedSort: TrackSort? = null
        setContent(onTrackSort = { selectedSort = it })

        compose.onNodeWithTag("首页快捷入口").performScrollToNode(hasContentDescription("最近添加 快捷入口"))
        compose.onNodeWithContentDescription("最近添加 快捷入口").performClick()

        org.junit.Assert.assertEquals(TrackSort.RecentlyAdded, selectedSort)
        compose.onNodeWithText("音乐库").assertIsDisplayed()
    }

    @Test
    fun homeUsesThreeRowRecentGridAndShowsPlaylistsWithoutAFakeRecentLink() {
        val tracks = (1..4).map { index -> Track(TrackId("track-$index"), "最近曲目 $index") }
        val playlist = Playlist(PlaylistId("playlist-placeholder"), "首页歌单", trackCount = 4)
        setContent(MusicUiState(loading = false, tracks = tracks, playlists = listOf(playlist)))

        val first = compose.onNodeWithText("最近曲目 1").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("最近曲目 2").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithText("最近曲目 3").fetchSemanticsNode().boundsInRoot
        val fourth = compose.onNodeWithText("最近曲目 4").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(first.left, second.left, 1f)
        org.junit.Assert.assertEquals(first.left, third.left, 1f)
        org.junit.Assert.assertTrue(second.top > first.top)
        org.junit.Assert.assertTrue(third.top > second.top)
        org.junit.Assert.assertTrue(fourth.left > first.left)
        compose.onNodeWithContentDescription("查看最近添加").assertDoesNotExist()
        compose.onNodeWithContentDescription("查看专辑").assertIsDisplayed()

        compose.onNodeWithText("首页歌单").performScrollTo().assertIsDisplayed().performClick()

        compose.onNodeWithText("我的歌单").assertIsDisplayed()
    }

    @Test
    fun moreRecentEntryOpensRecentTracksAndProvidesBackAction() {
        val recent = Track(TrackId("track-placeholder"), "测试曲目")
        setContent(MusicUiState(loading = false, recent = listOf(recent)))

        compose.onNodeWithText("更多").performClick()
        compose.onNodeWithText("最近播放").performClick()

        compose.onNodeWithText("1 首歌曲").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").assertIsDisplayed()
    }

    @Test
    fun albumOpensAsFullContentPageInsteadOfDialog() {
        val album = Album(AlbumId("album-placeholder"), "测试专辑", trackCount = 1)
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        setContent(
            state = MusicUiState(loading = false, detailTracks = listOf(track)),
            albums = PagingData.from(listOf(album)),
        )

        compose.onNodeWithText("更多").performClick()
        compose.onNodeWithText("专辑").performClick()
        compose.onNodeWithText("测试专辑").performClick()

        compose.onNodeWithText("详情").assertIsDisplayed()
        compose.onNodeWithText("曲目").assertIsDisplayed()
        compose.onNodeWithText("首页").assertDoesNotExist()
    }

    @Test
    fun miniPlayerOpensImmersivePlayerPage() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("测试曲目").performClick()

        compose.onNodeWithText("正在播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("收起播放器").assertDoesNotExist()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()
        compose.onNodeWithTag("top-system-bar-blur").assertDoesNotExist()
        compose.onNodeWithText("首页").assertDoesNotExist()
    }

    @Test
    fun fullPlayerPlaybackControlRemainsClickableInsideTheDragRegion() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        var toggleRequests = 0
        setContent(
            playerState = player,
            onTogglePlayback = { toggleRequests += 1 },
        )
        compose.onNodeWithText("测试曲目").performClick()

        compose.onNodeWithContentDescription("播放或暂停").performClick()

        org.junit.Assert.assertEquals(1, toggleRequests)
        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
    }

    @Test
    fun fullPlayerGlassActionsRemainClickableBesideTheDragRegions() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        var favoriteRequests = 0
        setContent(
            playerState = player,
            onToggleFavorite = { favoriteRequests += 1 },
        )
        compose.onNodeWithText("测试曲目").performClick()

        compose.onNodeWithTag("player-favorite-action").performClick()
        org.junit.Assert.assertEquals(1, favoriteRequests)

        compose.onNodeWithTag("player-more-action").performClick()
        compose.onNodeWithText("下一首播放").assertIsDisplayed()
    }

    @Test
    fun shortPlayerDragReboundsWithoutClosing() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()

        compose.onNodeWithTag("player-drag-handle").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 120f),
                durationMillis = 600,
            )
        }

        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()
    }

    @Test
    fun longPlayerDragReturnsToMiniPlayer() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()

        compose.onNodeWithTag("player-drag-handle").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("player-morph-overlay").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithText("测试曲目").assertIsDisplayed()
        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun draggingThePlayerBodyOutsideTheDedicatedRegionsDoesNotCloseIt() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("now-playing-lyrics-container").assertIsDisplayed()

        compose.onNodeWithTag("now-playing-lyrics-container").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 500,
            )
        }
        compose.onNodeWithTag("player-morph-overlay").assertIsDisplayed()
        compose.onNodeWithText("首页").assertDoesNotExist()
    }

    @Test
    fun draggingTheRenderedLargeCoverReturnsToMiniPlayer() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-morph-cover").assertIsDisplayed()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()

        compose.onNodeWithTag("player-morph-cover").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 650,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("player-morph-overlay").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun slowlyDraggingTheRenderedLargeCoverReturnsToMiniPlayer() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-morph-cover").assertIsDisplayed()

        compose.onNodeWithTag("player-morph-cover").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 2_500,
            )
        }
        compose.waitUntil(timeoutMillis = 4_000) {
            compose.onAllNodesWithTag("player-morph-overlay").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun releasedPlayerDragDoesNotApplyTheCoverOffsetTwice() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-drag-handle").assertIsDisplayed()
        val expandedCover = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("player-drag-handle").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, 700f),
                durationMillis = 500,
            )
        }
        val releasedCover = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot

        val coverTravel = releasedCover.top - expandedCover.top
        org.junit.Assert.assertTrue(
            "封面只应跟随单次 700px 拖拽，实际移动了 ${coverTravel}px",
            coverTravel in 550f..850f,
        )

        compose.mainClock.advanceTimeBy(180)
        val settlingCover = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot
        val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        org.junit.Assert.assertTrue(
            "收起动画中封面不应再次被拖拽位移推出屏幕",
            settlingCover.bottom <= rootBottom,
        )
    }

    @Test
    fun bottomBarCollapsesIntoAlignedCapsulesAndExpandsAgain() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val tracks = (1..12).map { index -> Track(TrackId("home-track-$index"), "首页曲目 $index") }
        setContent(
            state = MusicUiState(loading = false, tracks = tracks),
            playerState = player,
        )

        compose.onNodeWithTag("dynamic-primary-tabs").assertIsDisplayed()
        compose.onNodeWithTag("dynamic-primary-tab").assertDoesNotExist()
        val initialIndicatorCenter = compose.onNodeWithTag("liquid-bottom-tabs-indicator")
            .fetchSemanticsNode().boundsInRoot.center.x
        compose.onNodeWithText("曲库").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onNodeWithTag("liquid-bottom-tabs-indicator")
                .fetchSemanticsNode().boundsInRoot.center.x > initialIndicatorCenter + 100f
        }
        compose.onNodeWithText("首页").performClick()

        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(center.x, center.y * 1.3f),
                end = Offset(center.x, center.y * 0.5f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()
        }

        val primaryCenter = compose.onNodeWithTag("dynamic-primary-tab").fetchSemanticsNode().boundsInRoot.center.y
        val playerCenter = compose.onNodeWithTag("dynamic-mini-player").fetchSemanticsNode().boundsInRoot.center.y
        val searchCenter = compose.onNodeWithTag("dynamic-search").fetchSemanticsNode().boundsInRoot.center.y
        org.junit.Assert.assertEquals(primaryCenter, playerCenter, 2f)
        org.junit.Assert.assertEquals(primaryCenter, searchCenter, 2f)

        compose.onNodeWithTag("dynamic-primary-tab").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tabs").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isEmpty()
        }

        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(center.x, center.y * 1.3f),
                end = Offset(center.x, center.y * 0.5f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(center.x, center.y * 0.5f),
                end = Offset(center.x, center.y * 1.3f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tabs").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("dynamic-primary-tabs").assertIsDisplayed()
    }

    @Test
    fun expandedMiniPlayerUsesReferenceInternalSpacing() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        val playerBounds = compose.onNodeWithTag("dynamic-mini-player").fetchSemanticsNode().boundsInRoot
        val coverBounds = compose.onNodeWithTag("dynamic-cover", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val playCenter = compose.onNodeWithContentDescription("播放或暂停", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.x
        val nextCenter = compose.onNodeWithContentDescription("下一首", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.center.x

        org.junit.Assert.assertEquals(
            playerBounds.height * 16f / 50f,
            coverBounds.left - playerBounds.left,
            2f,
        )
        org.junit.Assert.assertEquals(
            playerBounds.height * 48f / 50f,
            nextCenter - playCenter,
            2f,
        )
    }

    @Test
    fun selectingSearchMovesSelectionOutOfPrimaryTabs() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithTag("liquid-bottom-tabs-indicator").assertIsDisplayed()
        compose.onNodeWithTag("dynamic-search").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("liquid-bottom-tabs-indicator").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("dynamic-search").assertIsDisplayed()
    }

    @Test
    fun selectingSearchInCompactModeClearsThePrimarySelection() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onRoot().performTouchInput {
            swipe(
                start = Offset(center.x, center.y * 1.3f),
                end = Offset(center.x, center.y * 0.5f),
                durationMillis = 500,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("dynamic-primary-tab").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithTag("dynamic-search").performClick()

        compose.onNodeWithTag("dynamic-search").assertIsSelected()
        compose.onNodeWithTag("dynamic-primary-tab").assertIsNotSelected()
    }

    @Test
    fun playerQueueUsesTheImmersivePlayerShell() {
        val first = Track(TrackId("track-one"), "第一首")
        val second = Track(TrackId("track-two"), "第二首")
        val player = PlayerState(
            queue = listOf(
                PlayableTrack(first, "https://music.invalid/one"),
                PlayableTrack(second, "https://music.invalid/two"),
            ),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("第一首").performClick()
        compose.onNodeWithContentDescription("开启随机播放").assertDoesNotExist()
        compose.onNodeWithContentDescription("循环已关闭，点击切换为列表循环").assertDoesNotExist()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithText("待播队列", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("待播队列", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("第二首", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("仅保留当前", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("player-queue-entry").assertIsSelected()
        compose.onNodeWithTag("player-bottom-controls").assertIsDisplayed()

        compose.onNodeWithTag("player-queue-header").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-header").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithTag("player-queue-entry").assertIsNotSelected()

        compose.onNodeWithTag("player-queue-entry").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-header").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("player-queue-entry").assertIsSelected()
    }

    @Test
    fun queueModeSelectsTracksAndDispatchesLocalReordering() {
        val tracks = (1..4).map { index ->
            Track(TrackId("queue-$index"), "队列曲目 $index")
        }
        val player = PlayerState(
            queue = tracks.map { PlayableTrack(it, "https://music.invalid/${it.id.value}") },
            currentIndex = 0,
            durationMs = 180_000,
        )
        var selected: Int? = null
        var moved: Pair<Int, Int>? = null
        setContent(
            playerState = player,
            onSkipToQueueItem = { selected = it },
            onMoveQueueItem = { from, to -> moved = from to to },
        )

        compose.onNodeWithText("队列曲目 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithText("待播队列", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("队列曲目 2", useUnmergedTree = true).performClick()
        org.junit.Assert.assertEquals(1, selected)

        compose.onNodeWithContentDescription("长按拖动排序：队列曲目 3").performTouchInput {
            down(center)
            advanceEventTime(600)
            moveTo(center + Offset(0f, -180f), delayMillis = 500)
            up()
        }
        org.junit.Assert.assertEquals(2 to 1, moved)
    }

    @Test
    fun shuffledQueueDisplaysAndEditsActualPlaybackOrder() {
        val tracks = (0..4).map { PlayableTrack(Track(TrackId("shuffle-$it"), "随机曲目 $it"), "https://music.invalid/$it") }
        val player = mutableStateOf(PlayerState(queue = tracks, currentIndex = 4, shuffleEnabled = true,
            playbackOrder = listOf(2, 4, 0, 3, 1), durationMs = 180_000))
        var selected: Int? = null
        val moves = mutableListOf<Pair<Int, Int>>()
        setContent(playerStateProvider = { player.value }, onSkipToQueueItem = { selected = it }, onMoveQueueItem = { from, to ->
            moves += from to to
            val order = player.value.playbackOrder.toMutableList()
            val source = order.indexOf(from)
            val destination = order.indexOf(to)
            order.add(destination, order.removeAt(source))
            player.value = player.value.copy(playbackOrder = order)
        })
        compose.onNodeWithText("随机曲目 4").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("player-queue-row-shuffle-0").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-row-shuffle-2").assertDoesNotExist()
        compose.onNodeWithText("随机曲目 0", useUnmergedTree = true).performClick()
        assertEquals(0, selected)
        val first = compose.onNodeWithContentDescription("长按拖动排序：随机曲目 0").fetchSemanticsNode().boundsInRoot
        val third = compose.onNodeWithContentDescription("长按拖动排序：随机曲目 1").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(third.center)
            moveTo(first.center, delayMillis = 700)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(1 to 0), moves)
        assertEquals(listOf(1, 0, 3), player.value.upcomingQueueIndices)
    }

    @Test
    fun queueSwipeRevealsRemovalWithoutPlayingAndClosesOnScroll() {
        val tracks = (0..15).map { PlayableTrack(Track(TrackId("swipe-$it"), "左滑曲目 $it"), "https://music.invalid/$it") }
        val player = mutableStateOf(PlayerState(queue = tracks, currentIndex = 0, durationMs = 180_000))
        var selected: Int? = null
        var removed: Int? = null
        setContent(playerStateProvider = { player.value }, onSkipToQueueItem = { selected = it }, onRemoveFromQueue = { index ->
            removed = index
            player.value = player.value.copy(queue = player.value.queue.filterIndexed { i, _ -> i != index })
        })
        compose.onNodeWithText("左滑曲目 0").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("player-queue-row-swipe-1").performTouchInput {
            swipe(start = Offset(width * .7f, center.y), end = Offset(width * .2f, center.y), durationMillis = 450)
        }
        compose.waitForIdle()
        assertEquals(null, selected)
        compose.onNodeWithContentDescription("从待播队列移除：左滑曲目 1").assertIsDisplayed()
        captureQueueScreenshot("queue-swipe")
        compose.onNodeWithContentDescription("从待播队列移除：左滑曲目 1").performClick()
        compose.waitForIdle()
        assertEquals(1, removed)
        assertEquals(tracks.first().queueEntryId, player.value.current?.queueEntryId)
        compose.onNodeWithTag("player-queue-row-swipe-1").assertDoesNotExist()
        compose.onNodeWithTag("player-queue-row-swipe-2").performTouchInput {
            swipe(start = Offset(width * .7f, center.y), end = Offset(width * .2f, center.y), durationMillis = 450)
        }
        compose.onNodeWithContentDescription("从待播队列移除：左滑曲目 2").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(start = center + Offset(0f, 80f), end = center - Offset(0f, 80f), durationMillis = 800)
        }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("从待播队列移除：左滑曲目 2").assertDoesNotExist()
    }

    @Test
    fun duplicateQueueEntriesRetainIdentityAfterDraggingAndRemoval() {
        val current = PlayableTrack(Track(TrackId("duplicate-current"), "重复队列测试"), "https://music.invalid/current")
        val first = PlayableTrack(Track(TrackId("duplicate"), "重复第一项"), "https://music.invalid/duplicate")
        val second = first.copy(track = first.track.copy(title = "重复第二项"), queueEntryId = "second-occurrence")
        val player = mutableStateOf(PlayerState(queue = listOf(current, first, second), currentIndex = 0, durationMs = 180_000))
        var removedId: String? = null
        setContent(playerStateProvider = { player.value }, onMoveQueueItem = { from, to ->
            player.value = player.value.copy(queue = player.value.queue.toMutableList().also { it.add(to, it.removeAt(from)) })
        }, onRemoveFromQueue = { index ->
            removedId = player.value.queue[index].queueEntryId
            player.value = player.value.copy(queue = player.value.queue.filterIndexed { i, _ -> i != index })
        })
        compose.onNodeWithText("重复队列测试").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        val firstHandle = compose.onNodeWithContentDescription("长按拖动排序：重复第一项").fetchSemanticsNode().boundsInRoot
        val secondHandle = compose.onNodeWithContentDescription("长按拖动排序：重复第二项").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(secondHandle.center)
            moveTo(firstHandle.center, delayMillis = 700)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf(current.queueEntryId, second.queueEntryId, first.queueEntryId), player.value.queue.map { it.queueEntryId })
        val row = compose.onNodeWithText("重复第二项", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            swipe(start = row.center, end = row.center - Offset(180f, 0f), durationMillis = 450)
        }
        compose.onNodeWithContentDescription("从待播队列移除：重复第二项").performClick()
        compose.waitForIdle()
        assertEquals(second.queueEntryId, removedId)
        assertEquals(listOf(current.queueEntryId, first.queueEntryId), player.value.queue.map { it.queueEntryId })
    }

    @Test
    fun queueModeRevealsPlaybackHistoryOnlyAfterPullingDown() {
        val historyTrack = Track(TrackId("history-track"), "已播放歌曲")
        val currentTrack = Track(TrackId("history-current"), "当前歌曲")
        val upcomingTrack = Track(TrackId("history-upcoming"), "待播歌曲")
        val player = PlayerState(
            queue = listOf(
                PlayableTrack(historyTrack, "https://music.invalid/history"),
                PlayableTrack(currentTrack, "https://music.invalid/current"),
                PlayableTrack(upcomingTrack, "https://music.invalid/upcoming"),
            ),
            playbackHistory = listOf(PlayableTrack(historyTrack, "https://music.invalid/history")),
            currentIndex = 1,
            durationMs = 180_000,
        )
        var selectedHistory: Int? = null
        var clearRequests = 0
        setContent(
            playerState = player,
            onSkipToHistoryItem = { selectedHistory = it },
            onClearPlaybackHistory = { clearRequests += 1 },
        )

        compose.onNodeWithText("当前歌曲").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.onNodeWithTag("player-playback-history").assertIsNotDisplayed()
        compose.onNodeWithTag("player-queue-row-history-current").assertDoesNotExist()
        compose.onNodeWithTag("player-queue-row-history-upcoming").assertIsDisplayed()

        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(
                start = center - Offset(0f, 160f),
                end = center + Offset(0f, 260f),
                durationMillis = 600,
            )
        }
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-playback-history").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("播放记录").assertIsDisplayed()
        compose.onNodeWithContentDescription("播放记录：已播放歌曲").performClick()
        org.junit.Assert.assertEquals(0, selectedHistory)
        compose.onNodeWithContentDescription("清空播放记录").performClick()
        org.junit.Assert.assertEquals(1, clearRequests)
    }

    @Test
    fun queueModeControlsStayPinnedWhileTheQueueScrolls() {
        val tracks = (1..24).map { index ->
            Track(TrackId("pinned-queue-$index"), "固定控制测试 $index")
        }
        val player = PlayerState(
            queue = tracks.map { PlayableTrack(it, "https://music.invalid/${it.id.value}") },
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("固定控制测试 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-mode-controls").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(start = center + Offset(0f, 200f), end = center - Offset(0f, 200f), durationMillis = 800)
        }
        compose.waitForIdle()
        val titleTop = compose.onNodeWithTag("player-queue-title").fetchSemanticsNode().boundsInRoot.top
        val controlsTop = compose.onNodeWithTag("player-queue-mode-controls").fetchSemanticsNode().boundsInRoot.top
        val viewportTop = compose.onNodeWithTag("player-queue-list").fetchSemanticsNode().boundsInRoot.top
        assertEquals(viewportTop, controlsTop, 1f)
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(start = center + Offset(0f, 120f), end = center - Offset(0f, 120f), durationMillis = 800)
        }
        compose.waitForIdle()
        val scrolledControlsTop = compose.onNodeWithTag("player-queue-mode-controls").fetchSemanticsNode().boundsInRoot.top
        assertEquals(controlsTop, scrolledControlsTop, 1f)
        assertEquals(titleTop, compose.onNodeWithTag("player-queue-title").fetchSemanticsNode().boundsInRoot.top, 1f)
        captureQueueScreenshot("queue-pinned")
    }

    @Test
    fun historyPullMovesRealCoverWithHeaderAndCanReachEveryHistoryRow() {
        val tracks = (1..12).map { PlayableTrack(Track(TrackId("continuous-$it"), "连续滚动 $it"), "https://music.invalid/$it") }
        val player = PlayerState(queue = tracks, playbackHistory = tracks.take(8), currentIndex = 8, durationMs = 180_000)
        setContent(playerState = player)
        compose.onNodeWithText("连续滚动 9").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        val headerBefore = compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top
        val coverBefore = compose.onNodeWithTag("player-queue-cover", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(start = center, end = center + Offset(0f, 90f), durationMillis = 1000)
        }
        compose.waitForIdle()
        val headerAfter = compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top
        val coverAfter = compose.onNodeWithTag("player-queue-cover", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.top
        assertTrue("下拉应连续移动歌曲栏，而不是等待展开阈值", headerAfter > headerBefore + 20f)
        assertEquals(headerAfter - headerBefore, coverAfter - coverBefore, 1f)
        compose.onNodeWithTag("player-queue-list")
            .performScrollToNode(hasContentDescription("播放记录：连续滚动 1"))
        compose.onNodeWithContentDescription("播放记录：连续滚动 1").assertIsDisplayed()
    }


    @Test
    fun draggingNearBottomScrollsBeyondInitiallyVisibleRows() {
        val tracks = (1..24).map { PlayableTrack(Track(TrackId("edge-$it"), "边缘拖动 $it"), "https://music.invalid/$it") }
        val player = mutableStateOf(PlayerState(queue = tracks, currentIndex = 0, durationMs = 180_000))
        val moves = mutableListOf<Pair<Int, Int>>()
        setContent(playerStateProvider = { player.value }, onMoveQueueItem = { from, to ->
            moves += from to to
            player.value = player.value.copy(queue = player.value.queue.toMutableList().also { it.add(to, it.removeAt(from)) })
        })
        compose.onNodeWithText("边缘拖动 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        val handle = compose.onNodeWithContentDescription("长按拖动排序：边缘拖动 2").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("player-queue-list").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        compose.onRoot().performTouchInput {
            down(handle.center)
            moveTo(Offset(handle.center.x, viewport.bottom - 12f), delayMillis = 700)
        }
        compose.mainClock.advanceTimeBy(2400)
        compose.onRoot().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        assertTrue("持续拖在可视底边应滚出屏外歌曲并继续重排", moves.any { it.second >= 8 })
        assertEquals("edge-2", player.value.queue[moves.last().second].track.id.value)
    }

    @Test
    fun queueKeepsCurrentAnchorAcrossTrackChangesAndResetsForNewSession() {
        val tracks = (1..12).map { PlayableTrack(Track(TrackId("anchor-$it"), "滚动锚点 $it"), "https://music.invalid/$it") }
        val player = mutableStateOf(PlayerState(queue = tracks, currentIndex = 0, durationMs = 180_000))
        setContent(playerStateProvider = { player.value })
        compose.onNodeWithText("滚动锚点 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        val initialTop = compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { player.value = player.value.copy(currentIndex = 1, playbackHistory = tracks.take(1)) }
        compose.waitForIdle()
        assertEquals(initialTop, compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.onNodeWithContentDescription("播放记录：滚动锚点 1").assertIsNotDisplayed()
        compose.onNodeWithTag("player-queue-row-anchor-3").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-list").performTouchInput {
            swipe(start = center, end = center + Offset(0f, 200f), durationMillis = 800)
        }
        compose.onNodeWithContentDescription("播放记录：滚动锚点 1").assertIsDisplayed()
        compose.runOnIdle {
            player.value = PlayerState(queue = tracks.reversed(), currentIndex = 0, playbackSessionId = 1, durationMs = 180_000)
        }
        compose.waitForIdle()
        compose.onNodeWithTag("player-playback-history").assertDoesNotExist()
        assertEquals(initialTop, compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top, 1f)
    }

    @Test
    fun emptyUpcomingQueueStillStartsWithHistoryOutsideViewport() {
        val tracks = (1..9).map { PlayableTrack(Track(TrackId("empty-$it"), "空待播 $it"), "https://music.invalid/$it") }
        setContent(playerState = PlayerState(queue = tracks, playbackHistory = tracks.take(8), currentIndex = 8))
        compose.onNodeWithText("空待播 9").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitForIdle()
        assertEquals(
            compose.onNodeWithTag("player-queue-list").fetchSemanticsNode().boundsInRoot.top,
            compose.onNodeWithTag("player-queue-header").fetchSemanticsNode().boundsInRoot.top,
            1f,
        )
        compose.onNodeWithContentDescription("播放记录：空待播 8").assertIsNotDisplayed()
    }

    @Test
    fun queueLastRowScrollsAbovePersistentPlaybackControls() {
        val tracks = (1..24).map { index ->
            Track(TrackId("queue-bottom-$index"), "末项可见测试 $index")
        }
        val player = PlayerState(
            queue = tracks.map { PlayableTrack(it, "https://music.invalid/${it.id.value}") },
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("末项可见测试 1").performClick()
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        val finalRowTag = "player-queue-row-queue-bottom-24"
        repeat(8) {
            compose.onNodeWithTag("player-queue-list").performTouchInput {
                swipe(
                    start = center + Offset(0f, 220f),
                    end = center - Offset(0f, 260f),
                    durationMillis = 500,
                )
            }
        }
        compose.waitForIdle()

        val finalRow = compose.onNodeWithTag(finalRowTag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("player-bottom-controls", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue(
            "队列末项不能落在常驻播放控制区域下方",
            finalRow.bottom <= controls.top,
        )
    }

    @Test
    fun closingQueueFromLyricsReturnsToDefaultPlayerWithoutShowingLyricsAgain() {
        val track = Track(TrackId("queue-return-track"), "队列返回测试")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/queue-return")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("队列返回测试").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-lyrics-header").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-header").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription("打开待播队列").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-queue-header").fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithTag("player-lyrics-header").assertDoesNotExist()
        compose.onNodeWithTag("player-track-metadata").assertIsDisplayed()
    }

    @Test
    fun nowPlayingUsesArtworkLedVerticalControlHierarchy() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        setContent(playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height > 500f
        }

        val cover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        val metadata = compose.onNodeWithTag("player-track-metadata").fetchSemanticsNode().boundsInRoot
        val progress = compose.onNodeWithTag("player-playback-progress").fetchSemanticsNode().boundsInRoot
        val transport = compose.onNodeWithTag("player-transport-controls").fetchSemanticsNode().boundsInRoot
        val volume = compose.onNodeWithTag("player-volume-control").fetchSemanticsNode().boundsInRoot
        val utilities = compose.onNodeWithTag("player-bottom-utilities").fetchSemanticsNode().boundsInRoot

        org.junit.Assert.assertTrue(metadata.top >= cover.bottom)
        org.junit.Assert.assertEquals(cover.left, metadata.left, 2f)
        org.junit.Assert.assertEquals(cover.right, metadata.right, 2f)
        org.junit.Assert.assertEquals(cover.left, progress.left, 2f)
        org.junit.Assert.assertEquals(cover.right, progress.right, 2f)
        val metadataToProgressGap = progress.top - metadata.bottom
        val minimumMetadataToProgressGap = with(compose.density) { 24.dp.toPx() }
        org.junit.Assert.assertTrue(
            "歌曲信息与进度条间距应至少为 24dp，实际为 ${metadataToProgressGap}px",
            metadataToProgressGap >= minimumMetadataToProgressGap,
        )
        org.junit.Assert.assertTrue(transport.top >= progress.bottom)
        org.junit.Assert.assertTrue(volume.top >= transport.bottom)
        org.junit.Assert.assertTrue(utilities.top >= volume.bottom)
        compose.onNodeWithTag("player-artwork-background").assertIsDisplayed()
        compose.onNodeWithTag("player-favorite-action").assertIsDisplayed()
        compose.onNodeWithTag("player-more-action").assertIsDisplayed()
    }

    @Test
    fun untimedLyricsCanExpandIntoAFullScreenPage() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句"), LyricLine(text = "第三句")),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()

        compose.onNodeWithTag("player-lyrics-entry").assertIsSelected()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodes(hasText("第一句")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        compose.onNodeWithText("第一句").assertIsDisplayed()
        compose.onNodeWithText("第三句").assertIsDisplayed()
        compose.onNodeWithText("首页").assertDoesNotExist()
    }

    @Test
    fun lyricsUsesBottomEntriesAndTogglesBackToTheLargeCover() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句")),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        val largeCover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        val defaultProgress = compose.onNodeWithTag("player-playback-progress").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("player-center-entry-placeholder").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-entry").assertIsDisplayed()

        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height < largeCover.height * 0.4f
        }
        compose.onNodeWithTag("player-lyrics-entry").assertIsSelected()
        compose.onNodeWithTag("player-lyrics-header-blur").assertIsDisplayed()
        compose.onNodeWithTag("player-bottom-controls-blur").assertIsDisplayed()
        val header = compose.onNodeWithTag("player-lyrics-header").fetchSemanticsNode().boundsInRoot
        val headerBlur = compose.onNodeWithTag("player-lyrics-header-blur").fetchSemanticsNode().boundsInRoot
        val controls = compose.onNodeWithTag("player-bottom-controls").fetchSemanticsNode().boundsInRoot
        val controlsBlur = compose.onNodeWithTag("player-bottom-controls-blur").fetchSemanticsNode().boundsInRoot
        val lyricsProgress = compose.onNodeWithTag("player-playback-progress").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(
            "默认状态与歌词状态的进度控制区应保持相同纵向位置",
            defaultProgress.top,
            lyricsProgress.top,
            2f,
        )
        org.junit.Assert.assertTrue(headerBlur.bottom > header.bottom)
        org.junit.Assert.assertTrue(controlsBlur.top < controls.top)
        org.junit.Assert.assertTrue(controlsBlur.bottom >= controls.bottom)

        compose.onNodeWithTag("player-lyrics-header").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height > largeCover.height * 0.9f
        }
        compose.onNodeWithTag("player-lyrics-entry").assertIsNotSelected()
    }

    @Test
    fun tappingTheLyricsCoverReturnsToTheLargeCover() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句")),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        val largeCover = compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height < largeCover.height * 0.4f
        }

        compose.onNodeWithTag("player-morph-cover").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-morph-cover").fetchSemanticsNode().boundsInRoot.height > largeCover.height * 0.9f
        }
        compose.onNodeWithTag("player-lyrics-entry").assertIsNotSelected()
    }

    @Test
    fun lyricsHeaderMoreActionUsesTheTrackCommandSheet() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句")),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onNodeWithTag("player-lyrics-more-action", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot.width > 0f
        }

        compose.onNodeWithTag("player-lyrics-more-action", useUnmergedTree = true).performClick()
        compose.onNodeWithText("下一首播放").assertIsDisplayed()
    }

    @Test
    fun finalTimedLyricCanRemainCenteredWithTrailingSpace() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 175_000,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(
                LyricLine(timeMs = 0, text = "开头歌词"),
                LyricLine(timeMs = 60_000, text = "中间歌词"),
                LyricLine(timeMs = 120_000, text = "末尾歌词"),
            ),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("lyrics-line-2")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("lyrics-list").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-line-2").assertIsDisplayed()
    }

    @Test
    fun autoFollowingLyricsBlurByDistanceAndManualScrollClearsBlur() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 80_000,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = (0..8).map { index ->
                LyricLine(timeMs = index * 20_000L, text = "第 ${index + 1} 句歌词")
            },
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()

        fun blurNodes() = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
            useUnmergedTree = true,
        ).fetchSemanticsNodes()

        compose.waitUntil(timeoutMillis = 3_000) {
            blurNodes().count { it.config[LyricBlurRadiusKey] > 0.1f } >= 2
        }
        val activeNode = compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals(0f, activeNode.config[LyricBlurRadiusKey], 0.05f)

        val activeCenterY = activeNode.boundsInRoot.center.y
        val blurredNodes = blurNodes()
            .filter { it.config[LyricBlurRadiusKey] > 0.1f }
            .sortedBy { kotlin.math.abs(it.boundsInRoot.center.y - activeCenterY) }
        assertTrue("自动跟随时应有至少两行非高亮歌词产生模糊", blurredNodes.size >= 2)
        assertTrue(
            "距离高亮行越远，模糊半径应越大",
            blurredNodes.last().config[LyricBlurRadiusKey] >
                blurredNodes.first().config[LyricBlurRadiusKey],
        )

        compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -360f),
                durationMillis = 400,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            val nodes = blurNodes()
            nodes.isNotEmpty() && nodes.all { it.config[LyricBlurRadiusKey] < 0.05f }
        }
    }

    @Test
    fun automaticLyricAdvanceMovesContinuouslyWithoutSnappingToTheTop() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = mutableStateOf(
            PlayerState(
                queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
                currentIndex = 0,
                positionMs = 80_000,
                durationMs = 180_000,
            ),
        )
        val state = MusicUiState(
            loading = false,
            lyrics = (0..8).map { index ->
                LyricLine(timeMs = index * 20_000L, text = "第 ${index + 1} 句歌词")
            },
        )
        setContent(state = state, playerStateProvider = { player.value })

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("lyrics-line-5", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val targetCenterY = compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        val startCenterY = compose.onNodeWithTag("lyrics-line-5", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        assertTrue("下一行在过行前应位于当前行下方", startCenterY > targetCenterY + 20f)

        val sampledCenters = mutableListOf<Float>()
        val nonTransitioningBlurSamples = mutableListOf<Float>()
        compose.mainClock.autoAdvance = false
        try {
            compose.runOnIdle { player.value = player.value.copy(positionMs = 100_000) }
            repeat(24) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("lyrics-line-5", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .firstOrNull()
                    ?.boundsInRoot
                    ?.center
                    ?.y
                    ?.let(sampledCenters::add)
                (0..8)
                    .filterNot { it == 4 || it == 5 }
                    .flatMap { index ->
                        compose.onAllNodesWithTag("lyrics-line-$index", useUnmergedTree = true)
                            .fetchSemanticsNodes()
                    }
                    .mapTo(nonTransitioningBlurSamples) { node ->
                        node.config[LyricBlurRadiusKey]
                    }
            }
            compose.mainClock.advanceTimeBy(160)
        } finally {
            compose.mainClock.autoAdvance = true
        }

        val intermediateCenters = sampledCenters.filter { centerY ->
            centerY > targetCenterY + 3f && centerY < startCenterY - 3f
        }
        assertTrue(
            "歌词过行应经历多个中间位置，而不是瞬移",
            intermediateCenters.distinctBy { (it / 2f).toInt() }.size >= 3,
        )
        assertTrue(
            "新高亮行滚动过程中不应先闪到目标位置上方",
            sampledCenters.none { it < targetCenterY - 12f },
        )
        assertTrue(
            "过行期间，与新旧高亮无关的可见歌词不应瞬间变清晰",
            nonTransitioningBlurSamples.isNotEmpty() &&
                nonTransitioningBlurSamples.all { it > 0.05f },
        )
        val finalCenterY = compose.onNodeWithTag("lyrics-line-5", useUnmergedTree = true)
            .fetchSemanticsNode()
            .boundsInRoot
            .center
            .y
        assertEquals(targetCenterY, finalCenterY, 4f)
    }

    @Test
    fun playbackProgressSeekRestoresFollowingAndMovesToTheRequestedLyric() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 40_000,
            durationMs = 200_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = (0..9).map { index ->
                LyricLine(timeMs = index * 20_000L, text = "第 ${index + 1} 句歌词")
            },
        )
        var requestedPosition: Long? = null
        setContent(
            state = state,
            playerState = player,
            onSeek = { requestedPosition = it },
        )

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("lyrics-line-2", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("lyrics-line-2", useUnmergedTree = true).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -360f),
                durationMillis = 400,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().all { it.config[LyricBlurRadiusKey] < 0.05f }
        }

        compose.onNodeWithTag("player-playback-progress", useUnmergedTree = true)
            .performTouchInput {
                click(Offset(center.x * 1.64f, center.y))
            }

        compose.waitUntil(timeoutMillis = 4_000) {
            requestedPosition != null &&
                compose.onAllNodesWithTag("lyrics-line-8", useUnmergedTree = true)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
        assertTrue("进度条应请求跳转到靠后的播放位置", requestedPosition!! >= 150_000L)
        val requestedLine = compose.onNodeWithTag("lyrics-line-8", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(
            "进度条跳转后的歌词应进入屏幕焦点区域",
            requestedLine.boundsInRoot.center.y in 700f..1_150f,
        )
        assertEquals(0f, requestedLine.config[LyricBlurRadiusKey], 0.05f)
        assertTrue(
            "进度条跳转后应立即恢复自动跟随模糊",
            compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().any { it.config[LyricBlurRadiusKey] > 0.1f },
        )
    }

    @Test
    fun manualLyricsScrollReturnsToTheCurrentLineAfterEightSeconds() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 80_000,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = (0..8).map { index ->
                LyricLine(timeMs = index * 20_000L, text = "第 ${index + 1} 句歌词")
            },
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithContentDescription("展开完整歌词").performClick()
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodesWithTag("lyrics-line-4", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true).performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, -360f),
                durationMillis = 400,
            )
        }
        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().all { it.config[LyricBlurRadiusKey] < 0.05f }
        }

        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(4_000)
            compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true).performTouchInput {
                swipe(
                    start = center,
                    end = center + Offset(0f, -120f),
                    durationMillis = 240,
                )
            }
            compose.mainClock.advanceTimeByFrame()
            compose.mainClock.advanceTimeBy(7_800)
            val beforeTimeout = compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes()
            assertTrue(
                "继续拖动歌词后应重新计算 8 秒空闲时间",
                beforeTimeout.isNotEmpty() &&
                    beforeTimeout.all { it.config[LyricBlurRadiusKey] < 0.05f },
            )
            compose.mainClock.advanceTimeBy(1_000)
        } finally {
            compose.mainClock.autoAdvance = true
        }

        compose.waitUntil(timeoutMillis = 3_000) {
            compose.onAllNodes(
                SemanticsMatcher.keyIsDefined(LyricBlurRadiusKey),
                useUnmergedTree = true,
            ).fetchSemanticsNodes().any { it.config[LyricBlurRadiusKey] > 0.1f }
        }
        val activeLine = compose.onNodeWithTag("lyrics-line-4", useUnmergedTree = true)
            .fetchSemanticsNode()
        assertTrue(
            "恢复自动跟随后，高亮歌词应回到屏幕焦点区域",
            activeLine.boundsInRoot.center.y in 700f..1_150f,
        )
        assertEquals(0f, activeLine.config[LyricBlurRadiusKey], 0.05f)
    }

    @Test
    fun lyricsTransitionKeepsTheActiveLineInPlaceWhileFadingOut() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            positionMs = 70_000,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(
                LyricLine(timeMs = 0, text = "开头歌词"),
                LyricLine(timeMs = 60_000, text = "当前歌词"),
                LyricLine(timeMs = 120_000, text = "末尾歌词"),
            ),
        )
        setContent(state = state, playerState = player)

        compose.onNodeWithText("测试曲目").performClick()
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithTag("player-lyrics-entry").fetchSemanticsNodes().isNotEmpty()
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.advanceTimeBy(340)
        val enteringLine = compose.onNodeWithTag("lyrics-line-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot

        compose.mainClock.advanceTimeBy(280)
        val settledLine = compose.onNodeWithTag("lyrics-line-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(
            "歌词应在渐显前完成居中，显现过程中不应再次跳动",
            enteringLine.top,
            settledLine.top,
            3f,
        )

        compose.onNodeWithTag("player-lyrics-header").performClick()
        val coverBeforeLyricExit = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(100)
        val coverDuringLyricExit = compose.onNodeWithTag("player-morph-cover")
            .fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertEquals(
            "歌词淡出阶段封面应保持静止",
            coverBeforeLyricExit.top,
            coverDuringLyricExit.top,
            2f,
        )
        org.junit.Assert.assertEquals(
            "歌词淡出阶段封面尺寸应保持不变",
            coverBeforeLyricExit.height,
            coverDuringLyricExit.height,
            2f,
        )
        compose.mainClock.advanceTimeBy(60)
        val exitingLine = compose.onNodeWithTag("lyrics-line-1", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val maximumExitTranslation = with(compose.density) { 3.dp.toPx() }
        org.junit.Assert.assertTrue(
            "歌词退出时只应渐隐，不应发生位移，entering=${enteringLine.top}, settled=${settledLine.top}, exiting=${exitingLine.top}, maximum=$maximumExitTranslation",
            kotlin.math.abs(exitingLine.top - settledLine.top) <= maximumExitTranslation,
        )
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun roamingNowPlayingUsesTheRoamIndicatorInsteadOfTheQueueEntry() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
            isRoaming = true,
        )
        setContent(state = MusicUiState(loading = false), playerState = player)

        compose.onNodeWithText("测试曲目").performClick()

        compose.onNodeWithTag("player-roam-indicator").assertIsDisplayed()
        compose.onNodeWithContentDescription("漫游模式").assertIsDisplayed()
        compose.onNodeWithTag("player-queue-entry").assertDoesNotExist()
        compose.onNodeWithContentDescription("打开待播队列").assertDoesNotExist()
    }

    @Test
    fun lyricsControlsHideAfterThreeSecondsAndReturnOnInteraction() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val player = PlayerState(
            queue = listOf(PlayableTrack(track, "https://music.invalid/stream")),
            currentIndex = 0,
            durationMs = 180_000,
        )
        val state = MusicUiState(
            loading = false,
            lyrics = listOf(LyricLine(text = "第一句"), LyricLine(text = "第二句")),
        )
        setContent(state = state, playerState = player)
        compose.onNodeWithText("测试曲目").performClick()
        compose.onNodeWithTag("player-lyrics-entry").assertIsDisplayed()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("player-lyrics-entry").performClick()
        compose.mainClock.advanceTimeBy(420)
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(2_900)
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()

        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("播放或暂停").assertDoesNotExist()

        compose.onNodeWithText("第一句").performClick()
        compose.mainClock.advanceTimeBy(200)
        compose.onNodeWithContentDescription("播放或暂停").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun trackMoreMenuDispatchesPlayNextAndUsesACommandSheet() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        var playedNext: Track? = null
        setContent(
            state = MusicUiState(loading = false, tracks = listOf(track)),
            onPlayNext = { playedNext = it },
        )

        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("下一首播放").assertIsDisplayed().performClick()

        org.junit.Assert.assertEquals(track.id, playedNext?.id)
    }

    @Test
    fun songInformationOpensAsAFullPageWithAudioMetadata() {
        val artist = Artist(ArtistId("artist-placeholder"), "测试歌手")
        val track = Track(TrackId("track-placeholder"), "测试曲目", artists = listOf(artist))
        val metadata = TrackMetadata(
            track = track,
            audioSpec = AudioSpec(format = "flac", codec = "FLAC", size = 12L * 1024L * 1024L, sampleRate = 48_000, bitDepth = 24, channel = 2),
        )
        setContent(state = MusicUiState(loading = false, tracks = listOf(track), detailMetadata = metadata))

        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("歌曲信息").performClick()

        compose.onNodeWithText("音频").assertIsDisplayed()
        compose.onNodeWithText("48.0 kHz").assertIsDisplayed()
        compose.onNodeWithText("24 bit").assertIsDisplayed()
        compose.onNodeWithText("首页").assertDoesNotExist()
    }

    @Test
    fun searchShowsCrossCategorySuggestionsBeforePagedResults() {
        val track = Track(TrackId("track-placeholder"), "建议歌曲")
        val album = Album(AlbumId("album-placeholder"), "建议专辑")
        setContent(
            state = MusicUiState(
                loading = false,
                searchQuery = "建议",
                searchSuggestions = SearchSuggestions(tracks = listOf(track), albums = listOf(album)),
            ),
        )

        compose.onNodeWithTag("dynamic-search").performClick()

        compose.onNodeWithText("快速匹配").assertIsDisplayed()
        compose.onNodeWithText("建议歌曲").assertIsDisplayed()
        compose.onNodeWithText("建议专辑").assertIsDisplayed()
    }

    @Test
    fun librarySortMenuDispatchesTheSelectedServerSort() {
        var selected: TrackSort? = null
        setContent(onTrackSort = { selected = it })

        compose.onNodeWithText("曲库").performClick()
        compose.onNodeWithContentDescription("排序").performClick()
        compose.onNodeWithText("歌曲名 Z–A").performClick()

        org.junit.Assert.assertEquals(TrackSort.TitleDescending, selected)
    }

    @Test
    fun libraryShowsServerReportedTotalInsteadOfLoadedItemCount() {
        setContent(state = MusicUiState(loading = false, trackTotal = 128))

        compose.onNodeWithText("曲库").performClick()

        compose.onNodeWithText("共 128 首歌曲").assertIsDisplayed()
        compose.onNodeWithText("0 首已加载歌曲").assertDoesNotExist()
    }

    @Test
    fun playlistLibraryOpensAFullPageEditorInsteadOfBeingReadOnly() {
        val playlist = Playlist(PlaylistId("playlist-placeholder"), "测试歌单", trackCount = 3)
        setContent(state = MusicUiState(loading = false, playlists = listOf(playlist)))

        compose.onNodeWithText("更多").performClick()
        compose.onNodeWithText("歌单").performClick()
        compose.onNodeWithText("只读").assertDoesNotExist()
        compose.onNodeWithContentDescription("新建歌单").performClick()

        compose.onNodeWithText("新建歌单").assertIsDisplayed()
        compose.onNodeWithText("歌单名称").assertIsDisplayed()
        compose.onNodeWithText("保存歌单").assertIsDisplayed()
        compose.onNodeWithText("首页").assertDoesNotExist()
    }

    @Test
    fun trackCanBeAddedToAnExistingPlaylistFromItsCommandSheet() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        val playlist = Playlist(PlaylistId("playlist-placeholder"), "通勤歌单", trackCount = 2)
        var target: Pair<PlaylistId, TrackId>? = null
        setContent(
            state = MusicUiState(loading = false, tracks = listOf(track), playlists = listOf(playlist)),
            onAddTrackToPlaylist = { playlistId, trackId -> target = playlistId to trackId },
        )

        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("添加到歌单").performClick()
        compose.onNodeWithContentDescription("添加到歌单：通勤歌单").performClick()

        org.junit.Assert.assertEquals(playlist.id to track.id, target)
    }

    @Test
    fun creatingAPlaylistFromTrackActionsKeepsThePendingTrack() {
        val track = Track(TrackId("track-placeholder"), "测试曲目")
        var createdWith: Triple<String, String?, TrackId?>? = null
        setContent(
            state = MusicUiState(loading = false, tracks = listOf(track)),
            onCreatePlaylist = { name, coverId, initialTrackId -> createdWith = Triple(name, coverId, initialTrackId) },
        )

        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("添加到歌单").performClick()
        compose.onNodeWithText("新建歌单").performClick()
        compose.onNodeWithText("歌单名称").performTextInput("旅途")
        compose.onNodeWithText("保存歌单").performClick()

        org.junit.Assert.assertEquals(Triple("旅途", "playlist_default_1", track.id), createdWith)
    }

    @Test
    fun playlistDetailSupportsBatchTrackRemoval() {
        val first = Track(TrackId("track-one"), "第一首")
        val second = Track(TrackId("track-two"), "第二首")
        val playlist = Playlist(PlaylistId("playlist-placeholder"), "通勤歌单", trackCount = 2)
        var removed: Pair<PlaylistId, List<TrackId>>? = null
        setContent(
            state = MusicUiState(
                loading = false,
                playlists = listOf(playlist),
                detailTracks = listOf(first, second),
            ),
            onRemoveTracksFromPlaylist = { playlistId, trackIds -> removed = playlistId to trackIds },
        )

        compose.onNodeWithText("更多").performClick()
        compose.onNodeWithText("歌单").performClick()
        compose.onNodeWithText("通勤歌单").performClick()
        compose.onNodeWithText("多选").performClick()
        compose.onNodeWithText("第一首").performClick()
        compose.onNodeWithText("移除 1 首").performClick()

        org.junit.Assert.assertEquals(playlist.id to listOf(first.id), removed)
    }

    private fun captureQueueScreenshot(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("queue-screenshots")!!
        java.io.File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun setContent(
        state: MusicUiState = MusicUiState(loading = false),
        playerState: PlayerState = PlayerState(),
        playerStateProvider: (() -> PlayerState)? = null,
        albums: PagingData<Album> = PagingData.empty(),
        onToggleShuffle: () -> Unit = {},
        onCycleRepeatMode: () -> Unit = {},
        onTogglePlayback: () -> Unit = {},
        onSeek: (Long) -> Unit = {},
        onToggleFavorite: (Track) -> Unit = {},
        onPlayNext: (Track) -> Unit = {},
        onTrackSort: (TrackSort) -> Unit = {},
        onRoam: () -> Unit = {},
        onSkipToQueueItem: (Int) -> Unit = {},
        onSkipToHistoryItem: (Int) -> Unit = {},
        onClearPlaybackHistory: () -> Unit = {},
        onMoveQueueItem: (Int, Int) -> Unit = { _, _ -> },
        onRemoveFromQueue: (Int) -> Unit = {},
        onCreatePlaylist: (String, String?, TrackId?) -> Unit = { _, _, _ -> },
        onAddTrackToPlaylist: (PlaylistId, TrackId) -> Unit = { _, _ -> },
        onRemoveTracksFromPlaylist: (PlaylistId, List<TrackId>) -> Unit = { _, _ -> },
    ) {
        compose.setContent {
            FnMusicTheme {
                MusicShell(
                    state = state,
                    playerState = playerStateProvider?.invoke() ?: playerState,
                    pagedTracks = flowOf(PagingData.empty()),
                    pagedAlbums = flowOf(albums),
                    pagedArtists = flowOf(PagingData.empty()),
                    pagedFavorites = flowOf(PagingData.empty()),
                    pagedSearch = flowOf(PagingData.empty<SearchItem>()),
                    coverUrl = { _, _ -> null },
                    onRefresh = {},
                    onSearch = {},
                    onSearchType = { _: SearchType -> },
                    onTrackSort = onTrackSort,
                    onAlbumSort = {},
                    onRoam = onRoam,
                    onPlayAllTracks = {},
                    onPlayAllFavorites = {},
                    onLoadAlbum = {},
                    onLoadArtist = {},
                    onLoadPlaylist = {},
                    onCreatePlaylist = onCreatePlaylist,
                    onUpdatePlaylist = { _, _, _ -> },
                    onDeletePlaylist = {},
                    onAddTrackToPlaylist = onAddTrackToPlaylist,
                    onRemoveTracksFromPlaylist = onRemoveTracksFromPlaylist,
                    onPurgeInvalidPlaylistTracks = {},
                    onLoadTrackMetadata = {},
                    onPlay = { _, _ -> },
                    onPlayNext = onPlayNext,
                    onAddToQueue = {},
                    onToggleFavorite = onToggleFavorite,
                    onTogglePlayback = onTogglePlayback,
                    onSeek = onSeek,
                    onPrevious = {},
                    onNext = {},
                    onSkipToQueueItem = onSkipToQueueItem,
                    onSkipToHistoryItem = onSkipToHistoryItem,
                    onClearPlaybackHistory = onClearPlaybackHistory,
                    onMoveQueueItem = onMoveQueueItem,
                    onRemoveFromQueue = onRemoveFromQueue,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeatMode = onCycleRepeatMode,
                    onCacheSizeChange = {},
                    onLogout = {},
                )
            }
        }
    }
}
