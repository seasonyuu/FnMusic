package com.seasonyuu.fnmusic.feature.music

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.*
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.data.SearchItem
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SearchScreenTest {
    @get:Rule val compose = createComposeRule()
    private val tracks = (0..79).map { Track(TrackId("$it"), "歌曲 $it") }
    private fun best() = SearchUiState(query = "音乐", status = SearchStatus.Ready,
        bestResults = tracks.map { SearchItem.TrackItem(it) })

    @Composable
    private fun Page(
        state: SearchUiState, onSearch: (String) -> Unit = {}, onFilter: (SearchFilter) -> Unit = {},
        onSubmit: () -> Unit = {}, onRetry: () -> Unit = {}, onPlay: (List<Track>, Int) -> Unit = { _, _ -> },
        onMore: (Track) -> Unit = {}, onAlbum: (Album) -> Unit = {}, onArtist: (Artist) -> Unit = {}, onPlaylist: (Playlist) -> Unit = {},
    ) = LiquidMenuHost {
        CompositionLocalProvider(LocalTrackMenuActions provides { track, _ ->
            listOf(TrackMenuAction(LiquidMenuItem("next", "下一首播放")) { onMore(track) })
        }) {
            SearchScreen(state, { _, _ -> null }, onSearch, onFilter, onSubmit, onRetry, onPlay, onAlbum, onArtist, onPlaylist)
        }
    }

    @Test fun clearKeepsFocusAndImeSubmitsWithoutChangingQuery() {
        var state by mutableStateOf(SearchUiState())
        var submissions = 0
        compose.setContent { FnMusicTheme { Page(state, onSearch = { state = state.copy(query = it) }, onSubmit = { submissions++ }) } }
        compose.onNodeWithText("搜索你的音乐").assertIsDisplayed()
        compose.onNodeWithTag("search-input").performClick().performTextInput("周杰伦")
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.onNodeWithTag("search-input").assertIsFocused().assertTextContains("")
        compose.runOnIdle { assertEquals("", state.query) }
        compose.onNodeWithTag("search-input").performTextInput("音乐")
        compose.onNodeWithTag("search-input").performImeAction()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, submissions); assertEquals("音乐", state.query) }
    }

    @Test fun initialSearchShowsTitleAndResultsRevealFiltersAndCloseButton() {
        var state by mutableStateOf(SearchUiState())
        compose.setContent { FnMusicTheme { Page(state) } }
        compose.onNodeWithText("搜索").assertIsDisplayed()
        compose.onNodeWithTag("search-filters").assertDoesNotExist()
        compose.onNodeWithTag("search-input").performClick()
        compose.onNodeWithText("搜索").assertDoesNotExist()
        compose.onNodeWithTag("search-filters").assertDoesNotExist()
        compose.runOnIdle {
            state = SearchUiState(
                query = "音乐",
                status = SearchStatus.Ready,
                bestResults = listOf(SearchItem.TrackItem(tracks.first())),
            )
        }
        compose.onNodeWithTag("search-filters").assertIsDisplayed()
        compose.onNodeWithTag("search-close").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭搜索").performClick()
        compose.onNodeWithText("搜索").assertIsDisplayed()
        compose.onNodeWithTag("search-filters").assertDoesNotExist()
    }

    @Test fun returningToSearchRestoresFiltersWithoutRestoringInputFocus() {
        var visible by mutableStateOf(true)
        var state by mutableStateOf(SearchUiState())
        compose.setContent {
            val holder = rememberSaveableStateHolder()
            FnMusicTheme {
                if (visible) holder.SaveableStateProvider("search") {
                    Page(state, onSearch = { state = SearchUiState(query = it) })
                }
            }
        }
        compose.onNodeWithTag("search-input").performClick()
        compose.runOnIdle { state = best() }
        compose.onNodeWithTag("search-filters").assertIsDisplayed()
        val originalTop = compose.onNodeWithTag("search-row-track:0").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { visible = false }
        compose.onNodeWithTag("search-page").assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { visible = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        assertEquals(originalTop, compose.onNodeWithTag("search-row-track:0").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(originalTop, compose.onNodeWithTag("search-row-track:0").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("search-filter-Best").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithTag("search-input").assertTextContains("音乐").assertIsNotFocused()
        compose.onNodeWithText("搜索").assertDoesNotExist()
        compose.onNodeWithTag("search-close").performClick()
        compose.onNodeWithText("搜索").assertIsDisplayed()
        compose.onNodeWithTag("search-filters").assertDoesNotExist()
    }

    @Test fun mixedRowsNavigateAndSongMoreDoesNotPlay() {
        val artist = Artist(ArtistId("artist"), "周杰伦")
        val album = Album(AlbumId("album"), "专辑示例")
        val playlist = Playlist(PlaylistId("playlist"), "歌单示例")
        val rows = SearchSuggestions(artists = listOf(artist), albums = listOf(album), playlists = listOf(playlist), tracks = tracks.take(2)).bestResults()
        var plays = 0
        var selectedIndex = -1
        var queue = emptyList<Track>()
        var more: Track? = null
        val opened = mutableListOf<String>()
        compose.setContent { FnMusicTheme { Page(best().copy(bestResults = rows),
            onPlay = { songs, index -> plays++; queue = songs; selectedIndex = index }, onMore = { more = it },
            onArtist = { opened += it.id.value }, onAlbum = { opened += it.id.value }, onPlaylist = { opened += it.id.value }) } }
        compose.waitForIdle()
        compose.onNodeWithText("快速匹配").assertDoesNotExist()
        compose.onNodeWithTag("search-row-artist:artist")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("专辑示例").performClick()
        compose.onNodeWithText("歌单示例").performClick()
        compose.onNodeWithTag("search-results").performScrollToNode(hasTestTag("search-row-track:1"))
        compose.onNodeWithContentDescription("更多操作：歌曲 1").performClick()
        compose.onNodeWithTag("liquid-menu-overlay").assertExists()
        compose.onNodeWithText("下一首播放").performClick()
        compose.runOnIdle { assertEquals(0, plays); assertEquals(tracks[1], more) }
        compose.onNodeWithTag("search-row-track:1").performClick()
        compose.runOnIdle { assertEquals(listOf("artist", "album", "playlist"), opened); assertEquals(1, plays); assertEquals(1, selectedIndex); assertEquals(tracks.take(2), queue) }
    }

    @Test fun filtersScrollOnNarrowScreensAndDispatchSelection() {
        var state by mutableStateOf(SearchUiState())
        compose.setContent { FnMusicTheme { Box(Modifier.width(320.dp)) {
            Page(state, onFilter = { state = state.copy(filter = it) })
        } } }
        compose.onNodeWithTag("search-input").performClick()
        compose.runOnIdle {
            state = SearchUiState(
                query = "音乐",
                status = SearchStatus.Ready,
                bestResults = listOf(SearchItem.TrackItem(tracks.first())),
                categoryCounts = SearchFilter.entries
                    .filterNot { it == SearchFilter.Best }
                    .associateWith { 1 },
            )
        }
        compose.onNodeWithTag("search-filter-Best").assertIsSelected()
        compose.onNodeWithTag("search-filters").performScrollToNode(hasTestTag("search-filter-Playlist"))
        compose.onNodeWithTag("search-filter-Playlist").performClick().assertIsSelected()
        compose.runOnIdle { assertEquals(SearchFilter.Playlist, state.filter) }
        compose.onNodeWithTag("search-close").performClick()
    }

    @Test fun categoryFiltersReturnAfterDeletingAndReenteringQuery() {
        val result = SearchItem.TrackItem(tracks.first())
        var state by mutableStateOf(SearchUiState())
        compose.setContent { FnMusicTheme { Page(state) } }
        compose.onNodeWithTag("search-input").performClick()

        compose.runOnIdle {
            state = SearchUiState(
                query = "旧关键词",
                filter = SearchFilter.Track,
                status = SearchStatus.Ready,
                pages = flowOf(PagingData.from(listOf(result))),
            )
        }
        compose.onNodeWithTag("search-filters").assertIsDisplayed()

        compose.runOnIdle { state = SearchUiState(filter = SearchFilter.Track) }
        compose.onNodeWithTag("search-filters").assertDoesNotExist()

        compose.runOnIdle {
            state = SearchUiState(
                query = "新关键词",
                filter = SearchFilter.Track,
                status = SearchStatus.Ready,
                pages = flowOf(PagingData.from(listOf(result))),
            )
        }
        compose.onNodeWithTag("search-filters").assertIsDisplayed()
    }

    @Test fun scrollRestoresAndNewQueryResetsToTop() {
        val restoration = StateRestorationTester(compose)
        var state by mutableStateOf(best())
        restoration.setContent { FnMusicTheme { Page(state) } }
        compose.onNodeWithTag("search-results").performScrollToIndex(40)
        val before = compose.onNodeWithTag("search-row-track:40").fetchSemanticsNode().boundsInRoot
        restoration.emulateSavedInstanceStateRestore()
        assertEquals(before, compose.onNodeWithTag("search-row-track:40").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { state = state.copy(query = "新关键词", generation = 1) }
        compose.onNodeWithTag("search-row-track:0").assertIsDisplayed()
        compose.onNodeWithTag("search-results").performScrollToIndex(40)
        compose.runOnIdle { state = state.copy(filter = SearchFilter.Track, generation = 2,
            pages = flowOf(PagingData.from(tracks.map { SearchItem.TrackItem(it) }))) }
        compose.onNodeWithTag("search-row-track:0").assertIsDisplayed()
    }

    @Test fun loadingEmptyAndErrorStatesAreDistinctAndRetryIsActionable() {
        var state by mutableStateOf(best().copy(status = SearchStatus.Loading, bestResults = emptyList()))
        var retries = 0
        compose.setContent { FnMusicTheme { Page(state, onRetry = { retries++ }) } }
        compose.onNodeWithTag("search-loading").assertIsDisplayed()
        compose.runOnIdle { state = state.copy(status = SearchStatus.Error, error = "网络不可用") }
        compose.onNodeWithText("网络不可用").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.runOnIdle { assertEquals(1, retries); state = state.copy(status = SearchStatus.Ready) }
        compose.onNodeWithText("没有找到“音乐”").assertIsDisplayed()
        compose.onNodeWithText("重试").assertDoesNotExist()
    }

    @Test fun bestResultsDoNotShowViewAllSongsAction() {
        compose.setContent { FnMusicTheme { Page(best().copy(bestResults = listOf(SearchItem.TrackItem(tracks[0])))) } }
        compose.onNodeWithText("查看全部歌曲").assertDoesNotExist()
    }

    @Test fun appendFailureKeepsRowsAndRetryLoadsNextPage() {
        var fail = true
        val pager = Pager(PagingConfig(pageSize = 10, initialLoadSize = 10, prefetchDistance = 1)) {
            object : PagingSource<Int, SearchItem>() {
                override fun getRefreshKey(state: PagingState<Int, SearchItem>): Int? = null
                override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchItem> {
                    val page = params.key ?: 0
                    if (page == 1 && fail) return LoadResult.Error(IllegalStateException("离线"))
                    return LoadResult.Page(tracks.drop(page * 10).take(10).map { SearchItem.TrackItem(it) }, null, if (page == 0) 1 else null)
                }
            }
        }
        compose.setContent { FnMusicTheme { Page(best().copy(filter = SearchFilter.Track, pages = pager.flow)) } }
        compose.onNodeWithTag("search-results").performScrollToIndex(9)
        compose.onNodeWithTag("search-results").performScrollToNode(hasText("重试"))
        compose.onNodeWithText("加载更多失败").assertIsDisplayed()
        compose.onNodeWithTag("search-row-track:9").assertExists()
        compose.runOnIdle { fail = false }
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithTag("search-results").performScrollToIndex(19)
        compose.onNodeWithTag("search-row-track:19").assertIsDisplayed()
    }

    @Test fun themesLargeTypeAndBottomInsetRemainReadable() {
        var dark by mutableStateOf(false)
        var accent by mutableStateOf(Color(0xFFF62C55))
        var scale by mutableFloatStateOf(1f)
        compose.setContent { FnMusicTheme(darkTheme = dark, accent = accent) {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale), LocalBottomOverlayPadding provides 140.dp) {
                Box(Modifier.width(360.dp).fillMaxHeight().background(FnSurface)) {
                    val artist = Artist(ArtistId("jay"), "周杰伦")
                    val album = Album(AlbumId("album"), "太阳之子", artists = listOf(artist))
                    val songs = tracks.map { it.copy(artists = listOf(artist)) }
                    Page(best().copy(query = "周杰伦", bestResults = SearchSuggestions(
                        artists = listOf(artist), albums = listOf(album), tracks = songs,
                    ).bestResults()))
                }
            }
        } }
        compose.onNodeWithTag("search-input").performClick()
        for ((name, isDark, color, fontScale) in listOf(
            ThemeCase("light", false, Color(0xFFF62C55), 1f),
            ThemeCase("dark", true, Color(0xFFF62C55), 1f),
            ThemeCase("large-green", false, Color(0xFF6BAB45), 1.5f),
        )) {
            compose.runOnIdle { dark = isDark; accent = color; scale = fontScale }
            compose.onNodeWithTag("search-results").performScrollToIndex(0)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("最佳结果", useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(FnDarkPalette.primary, layouts.single().layoutInput.style.color)
            capture(name)
            compose.onNodeWithTag("search-results").performScrollToIndex(tracks.lastIndex)
            val footer = compose.onNodeWithTag("search-row-track:79").fetchSemanticsNode().boundsInRoot
            val body = compose.onNodeWithTag("search-results").fetchSemanticsNode().boundsInRoot
            assertTrue("Last result must clear the bottom overlay", body.bottom - footer.bottom >= 140 * compose.density.density)
        }
        compose.onNodeWithTag("search-close").performClick()
    }

    private data class ThemeCase(val name: String, val dark: Boolean, val color: Color, val fontScale: Float)

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?.let { File(it).apply { mkdirs() } } ?: context.getExternalFilesDir(null)!!
        val output = File(directory, "search-$name.png")
        compose.onNodeWithTag("search-page").captureToImage().asAndroidBitmap().let { bitmap ->
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
