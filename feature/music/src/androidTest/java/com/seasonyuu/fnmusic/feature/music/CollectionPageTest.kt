package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.paging.compose.collectAsLazyPagingItems
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CollectionPageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun headerScrollsAwayWhileToolbarStaysFixedAndRestores() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            FnMusicTheme {
                val state = rememberLazyListState()
                CollectionPage("全部歌曲", {}, { state.firstVisibleItemIndex > 0 }) { heading, top ->
                    LazyColumn(Modifier.testTag("list"), state = state, contentPadding = PaddingValues(top = top)) {
                        item {
                            Column {
                                CollectionHeading("全部歌曲", "80 首歌曲", heading)
                                CollectionPlayButton(true) {}
                            }
                        }
                        items(80) { Text("歌曲 $it", Modifier.fillMaxWidth().height(64.dp)) }
                    }
                }
            }
        }
        val before = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("collection-play-all").assertIsDisplayed()
        compose.onNodeWithTag("list").performScrollToIndex(30)
        compose.onNodeWithTag("collection-play-all").assertDoesNotExist()
        compose.onNodeWithText("全部歌曲").assertIsDisplayed()
        assertEquals(before, compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot)
        val row = compose.onNodeWithText("歌曲 29").fetchSemanticsNode().boundsInRoot
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(row, compose.onNodeWithText("歌曲 29").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("list").performScrollToIndex(0)
        compose.onNodeWithTag("collection-play-all").assertIsDisplayed()
    }

    @Test fun narrowLargeFontPlaybackHasFullWidthAndDisabledSemantics() {
        compose.setContent {
            FnMusicTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                    Column(Modifier.width(320.dp).testTag("body")) {
                        CollectionPlayButton(false) { error("Disabled") }
                    }
                }
            }
        }
        val button = compose.onNodeWithTag("collection-play-all").assertIsNotEnabled()
        assertEquals(compose.onNodeWithTag("body").fetchSemanticsNode().boundsInRoot.width,
            button.fetchSemanticsNode().boundsInRoot.width, 1f)
        button.performClick()
    }

    @Test fun pullRefreshUsesPagingKeepsRowsOnFailureAndAllowsRetry() {
        var requests = 0
        var gate = CompletableDeferred<Unit>()
        var fail = true
        val data = (1..50).map { Track(TrackId("$it"), "歌曲 $it") }
        val pager = Pager(PagingConfig(pageSize = 50)) {
            object : PagingSource<Int, Track>() {
                override fun getRefreshKey(state: PagingState<Int, Track>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Track> {
                    requests++
                    if (requests > 1) {
                        gate.await()
                        if (fail) return LoadResult.Error(IllegalStateException("测试刷新失败"))
                    }
                    return LoadResult.Page(data, null, null)
                }
            }
        }
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost {
                    val scope = rememberCoroutineScope()
                    val flow = remember { pager.flow.cachedIn(scope) }
                    PagingTrackScreen("全部歌曲", flow.collectAsLazyPagingItems(), MusicUiState(loading = false),
                        { _, _ -> null }, { _, _ -> }, {}, { error("Must not refresh homepage") },
                        sort = TrackSort.TitleAscending, pullRefreshEnabled = true, onSort = {}, onBack = {})
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("歌曲 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("刷新").assertDoesNotExist()
        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(20)
        list.performTouchInput { swipeDown() }
        compose.runOnIdle { assertEquals(1, requests) }
        list.performScrollToIndex(0)
        val bar = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        list.performTouchInput { swipeDown(startY = height * .25f, endY = height * .85f) }
        compose.waitUntil(5_000) { requests == 2 }
        compose.onNodeWithText("歌曲 1").assertIsDisplayed()
        list.performTouchInput { swipeDown(startY = height * .25f, endY = height * .85f) }
        compose.runOnIdle { assertEquals(2, requests); gate.complete(Unit) }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("刷新失败，请重试").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("歌曲 1").assertIsDisplayed()
        assertEquals(bar, compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { fail = false; gate = CompletableDeferred(Unit) }
        compose.onNodeWithText("重试").performClick()
        compose.waitUntil(5_000) { requests == 3 }
        compose.onNodeWithText("刷新失败，请重试").assertDoesNotExist()
    }
    @Test fun emptyPagingErrorOffersRetryWithoutEnablingPlayback() {
        var fail = true
        val pager = Pager(PagingConfig(pageSize = 20)) {
            object : PagingSource<Int, Track>() {
                override fun getRefreshKey(state: PagingState<Int, Track>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Track> =
                    if (fail) LoadResult.Error(IllegalStateException("加载失败示例"))
                    else LoadResult.Page(listOf(Track(TrackId("ok"), "恢复的歌曲")), null, null)
            }
        }
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost {
                    val scope = rememberCoroutineScope()
                    val flow = remember { pager.flow.cachedIn(scope) }
                    PagingTrackScreen("全部歌曲", flow.collectAsLazyPagingItems(), MusicUiState(loading = false),
                        { _, _ -> null }, { _, _ -> }, {}, { error("Homepage refresh") },
                        sort = TrackSort.RecentlyAdded, pullRefreshEnabled = true, onSort = {})
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("加载失败示例").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("collection-play-all").assertIsNotEnabled()
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("重试").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("恢复的歌曲").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("collection-play-all").assertIsEnabled()
    }

}
