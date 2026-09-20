package com.seasonyuu.fnmusic.feature.music

import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricsSheetTest {
    @get:Rule val compose = createComposeRule()
    private val track = Track(TrackId("sheet-song"), "Song")
    private fun show(actions: Fake, external: Boolean = false) {
        compose.setContent {
            FnMusicTheme { Surface {
                LyricsPickerDialog(track, actions, LyricsState(LyricsDocument(listOf(LyricLine(0, "Line")), if (external) LyricsOrigin.Netease else LyricsOrigin.FnMusic)), {}, LyricsSheetVisual(Color(0xFF202B30)))
            } }
        }
    }
    private fun openSearch() { compose.onNodeWithTag("lyrics-manual-search").performScrollTo().performClick(); compose.waitForIdle() }
    @Test fun tabsLoadOnDemandAndUseSubmittedQuery() {
        val actions = Fake(); show(actions); openSearch()
        compose.runOnIdle { assertEquals(listOf(OnlineLyricsSource.Netease to "Song"), actions.requests) }
        compose.onNodeWithTag("lyrics-search-query").performTextReplacement("Edited")
        compose.onNodeWithTag("lyrics-tab-QQ").performClick()
        compose.runOnIdle { assertEquals(OnlineLyricsSource.QQ to "Song", actions.requests.last()) }
        compose.onNodeWithTag("lyrics-tab-Netease").performClick()
        compose.runOnIdle { assertEquals(2, actions.requests.size) }
        compose.onNodeWithTag("lyrics-search-query").assertTextContains("Edited")
        compose.onNodeWithTag("lyrics-search-submit").performClick()
        compose.runOnIdle { assertEquals(OnlineLyricsSource.Netease to "Edited", actions.requests.last()) }
        compose.onNodeWithTag("lyrics-tab-QQ").performClick()
        compose.runOnIdle { assertEquals(OnlineLyricsSource.QQ to "Edited", actions.requests.last()); assertEquals(4, actions.requests.size) }
    }
    @Test fun eachTabAndPreviewPreserveScrollPosition() {
        val actions = Fake().apply { rowCount = 24 }; show(actions); openSearch()
        compose.onNodeWithTag("lyrics-search-results").performScrollToNode(hasText("Netease result 18"))
        compose.onNodeWithTag("lyrics-tab-QQ").performClick()
        compose.onNodeWithText("QQ result 0").assertIsDisplayed()
        compose.onNodeWithTag("lyrics-tab-Netease").performClick()
        compose.onNodeWithText("Netease result 18").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("返回结果").performClick()
        compose.onNodeWithText("Netease result 18").assertIsDisplayed()
    }
    @Test fun switchingCancelsPendingSourceAndReloadsItOnReturn() {
        val actions = Fake().apply { blocked = OnlineLyricsSource.QQ }; show(actions); openSearch()
        compose.onNodeWithTag("lyrics-tab-QQ").performClick()
        compose.onNodeWithTag("lyrics-tab-Kugou").performClick()
        compose.runOnIdle { assertEquals(1, actions.cancellations) }
        compose.onNodeWithTag("lyrics-tab-QQ").performClick()
        compose.runOnIdle { assertEquals(2, actions.requests.count { it.first == OnlineLyricsSource.QQ }) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("lyrics-picker").assertExists()
        compose.onNodeWithTag("lyrics-search-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, actions.cancellations) }
    }
    @Test fun manualBindingSelectsTabDespiteDisabledAutomaticSources() {
        val actions = Fake().apply {
            onlinePreference.value = OnlineLyricsPreference(false, setOf(OnlineLyricsSource.Netease))
            chosen.value = LyricsChoice(LyricsChoiceMode.Online, candidate(OnlineLyricsSource.QQ))
        }
        show(actions); openSearch()
        compose.onNodeWithTag("lyrics-tab-QQ").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(OnlineLyricsSource.QQ to "Song"), actions.requests) }
    }
    @Test fun missingAmllIndexRequiresExplicitDownloadAndMirrorOnlyInvalidatesAmll() {
        val actions = Fake(); show(actions); openSearch()
        compose.onNodeWithTag("lyrics-tab-Amll").performClick()
        compose.runOnIdle { assertEquals(0, actions.updates); assertEquals(0, actions.amllSearches) }
        compose.onNodeWithText("获取索引并搜索").performClick()
        compose.runOnIdle { assertEquals(1, actions.updates); assertEquals(1, actions.amllSearches) }
        compose.onNodeWithTag("lyrics-tab-Netease").performClick()
        compose.runOnIdle { actions.index.value = actions.index.value.copy(source = LyricsFetchSource.GitHub) }
        compose.onNodeWithText("Netease result").assertExists()
        compose.runOnIdle { assertEquals(1, actions.requests.size) }
        compose.onNodeWithTag("lyrics-tab-Amll").performClick()
        compose.runOnIdle { assertEquals(2, actions.amllSearches) }
    }
    @Test fun previewFailureRequiresRetryAndReturningPreservesQueryAndTab() {
        val actions = Fake().apply { failPreview = true }; show(actions); openSearch()
        compose.onNodeWithTag("lyrics-tab-QQ").performClick()
        compose.onNodeWithText("QQ result").performClick()
        compose.onNodeWithTag("lyrics-preview-apply").assertIsNotEnabled()
        compose.runOnIdle { actions.failPreview = false }
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithTag("lyrics-preview-apply").assertIsEnabled()
        compose.onNodeWithContentDescription("返回结果").performClick()
        compose.onNodeWithTag("lyrics-tab-QQ").assertIsSelected()
        compose.onNodeWithTag("lyrics-search-query").assertTextContains("Song")
    }
    @Test fun offsetIsCollapsedAndPreservesStepAndReset() {
        val actions = Fake(); show(actions, external = true)
        compose.onNodeWithText("提前").assertDoesNotExist()
        compose.onNodeWithTag("lyrics-offset-toggle").performScrollTo().performClick()
        compose.onNodeWithText("延后").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(100L, actions.chosen.value.offsetMs) }
        compose.onNodeWithText("重置").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0L, actions.chosen.value.offsetMs) }
        compose.onNodeWithTag("lyrics-offset-toggle").performScrollTo().performClick()
        compose.onNodeWithText("提前").assertDoesNotExist()
    }
    @Test fun nasHidesOffsetAndAmllTabRemainsAccessible() {
        val actions = Fake(); show(actions)
        compose.onNodeWithTag("lyrics-offset-toggle").assertDoesNotExist()
        openSearch()
        compose.onNodeWithTag("lyrics-tab-Amll").assertIsDisplayed().performClick()
        compose.onNodeWithText("获取索引并搜索").assertIsDisplayed()
    }
    @Test fun failedSearchStaysLocalAndRetryLoadsResults() {
        val actions = Fake().apply { failSearch = true }; show(actions); openSearch()
        compose.onNodeWithText("Search failed").assertIsDisplayed()
        compose.runOnIdle { actions.failSearch = false }
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("Netease result").assertIsDisplayed()
    }
    @Test fun captureColorAndFallbackSheets() {
        val actions = Fake()
        var visual by mutableStateOf(LyricsSheetVisual(Color(0xFF203D38), android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.rgb(69, 155, 131)) }))
        compose.setContent { FnMusicTheme { Surface { LyricsPickerDialog(track, actions, null, {}, visual) } } }
        capture("lyrics-color-selection")
        openSearch()
        capture("lyrics-color-search")
        compose.onNodeWithText("Netease result").performClick()
        capture("lyrics-color-preview")
        compose.onNodeWithContentDescription("返回结果").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { visual = LyricsSheetVisual(Color(0xFF272727)) }
        capture("lyrics-no-cover-selection")
    }
    private fun capture(name: String) {
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(250) // Allow the native window to present the completed Compose frame.
        val directory = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { java.io.File(it).apply { mkdirs() } }
            ?: instrumentation.targetContext.getExternalFilesDir("lyrics-screenshots")!!
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        java.io.File(directory, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private class Fake : LyricsActions {
        override val onlinePreference = MutableStateFlow(OnlineLyricsPreference())
        override val onlineCacheUsage = MutableStateFlow(LyricsCacheUsage())
        override val amllEnabled = MutableStateFlow(false)
        override val index = MutableStateFlow(LyricsIndexState())
        override val cacheUsage = MutableStateFlow(LyricsCacheUsage())
        val chosen = MutableStateFlow(LyricsChoice())
        val requests = mutableListOf<Pair<OnlineLyricsSource, String>>()
        var blocked: OnlineLyricsSource? = null
        var cancellations = 0
        var updates = 0
        var amllSearches = 0
        var failSearch = false
        var failPreview = false
        var rowCount = 1
        override suspend fun setOnlinePreference(preference: OnlineLyricsPreference) { onlinePreference.value = preference }
        override suspend fun clearOnlineCache() = Unit
        override suspend fun setAmllEnabled(enabled: Boolean) { amllEnabled.value = enabled }
        override suspend fun setSource(source: LyricsFetchSource) { index.value = index.value.copy(source = source) }
        override suspend fun updateIndex() { updates++; index.value = index.value.copy(version = "index") }
        override suspend fun clearCache() = Unit
        override suspend fun searchOnline(source: OnlineLyricsSource, query: String): List<LyricsCandidate> {
            requests += source to query
            if (failSearch) error("Search failed")
            if (source == blocked) try { CompletableDeferred<Unit>().await() } finally { cancellations++ }
            return if (rowCount == 1) listOf(candidate(source)) else List(rowCount) { candidate(source).copy(titles = listOf("${source.name} result $it"), songId = "$it") }
        }
        override suspend fun search(query: String): List<LyricsCandidate> { amllSearches++; return emptyList() }
        override suspend fun preview(candidate: LyricsCandidate): LyricsDocument {
            if (failPreview) error("Preview failed")
            return LyricsDocument(listOf(LyricLine(0, "Preview")), LyricsOrigin.QQ, candidate)
        }
        override fun choice(track: Track): Flow<LyricsChoice> = chosen
        override suspend fun choose(track: Track, choice: LyricsChoice) { chosen.value = choice }
    }
    companion object {
        private fun candidate(source: OnlineLyricsSource) = LyricsCandidate("", listOf("${source.name} result"), listOf("Singer"), onlineSource = source, songId = source.name)
    }
}
