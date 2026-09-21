package com.seasonyuu.fnmusic.feature.music

import androidx.compose.ui.test.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.junit4.createComposeRule
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricsSettingsScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun lyricsEntryIsClickableAcrossTheWholeItem() {
        val actions = Stub()
        var opened = 0
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { LyricsSettingsScreen({}, { opened++ }) } } }
        compose.onNodeWithTag("lyrics-amll-toggle").assertDoesNotExist()
        compose.onNodeWithText("AMLL 设置").assertDoesNotExist()
        compose.onNodeWithText("利用在线资源搜索逐字歌词，达到更优秀的体验").assertExists()
        compose.onNodeWithTag("lyrics-online-entry").performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 2f, height / 2f)) }
        compose.runOnIdle { assertEquals(1, opened) }
    }
    @Test fun disabledAmllHidesDetailsAndCanBeReenabled() {
        val actions = Stub()
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost { OnlineLyricsSettingsScreen(actions, {}) } } } }
        compose.onNodeWithTag("lyrics-amll-toggle").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(actions.amllEnabled.value); assertTrue(actions.onlinePreference.value.enabled) }
        compose.onNodeWithTag("lyrics-cache-clear").performScrollTo().assertIsEnabled()
        listOf("lyrics-source-menu", "lyrics-index-update").forEach {
            compose.onNodeWithTag(it).assertDoesNotExist()
        }
        compose.onNodeWithTag("lyrics-amll-toggle").performScrollTo().performClick()
        listOf("lyrics-source-menu", "lyrics-index-update").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsEnabled()
        }
    }
    @Test fun showsIndexAndUpdatesAndClearsOnlyLyrics() {
        val actions = Stub()
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost { OnlineLyricsSettingsScreen(actions, {}) } } } }
        compose.onNodeWithTag("lyrics-index-version").performScrollTo().assertTextEquals("abcdef123456")
        compose.onNodeWithTag("lyrics-index-size").assertTextEquals("2.0 KiB")
        capture("amll-settings-index")
        compose.onNodeWithTag("lyrics-index-update").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, actions.updates) }
        compose.onNodeWithTag("lyrics-cache-clear").performScrollTo()
        capture("amll-settings-cache")
        compose.onNodeWithTag("lyrics-cache-usage").assertTextEquals("5.0 KiB")
        compose.onNodeWithText("已缓存 3 份歌词").assertExists()
        compose.onNodeWithTag("lyrics-cache-clear").performClick()
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithTag("lyrics-cache-usage").performScrollTo().assertTextEquals("暂无缓存")
        compose.onNodeWithTag("lyrics-cache-clear").assertIsNotEnabled()
        compose.runOnIdle { assertEquals("abcdef123456", actions.index.value.version) }
    }
    @Test fun combinedClearStillClearsAmllWhenOnlineClearFails() {
        val actions = Stub().apply { failOnlineClear = true }
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost { OnlineLyricsSettingsScreen(actions, {}) } } } }
        compose.onNodeWithTag("lyrics-cache-clear").performScrollTo().performClick()
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithTag("lyrics-cache-usage").performScrollTo().assertTextEquals("1.0 KiB")
        compose.runOnIdle {
            assertEquals(0, actions.cacheUsage.value.count)
            assertEquals(1, actions.onlineCacheUsage.value.count)
            assertEquals("abcdef123456", actions.index.value.version)
        }
        compose.onNodeWithText("部分缓存未能清除，请重试").performScrollTo().assertIsDisplayed()
    }
    @Test fun sourceSwitchAndCheckingStateAreVisible() {
        val actions = Stub()
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost { OnlineLyricsSettingsScreen(actions, {}) } } } }
        compose.onNodeWithTag("lyrics-source-menu").performScrollTo().performClick()
        compose.onNodeWithText("Dimeta").assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("lyrics-source-menu").assertTextContains("Dimeta")
        compose.runOnIdle { assertEquals(LyricsFetchSource.Dimeta, actions.index.value.source) }
        compose.onNodeWithTag("lyrics-source-menu").performClick()
        compose.onNode(hasText("Dimeta") and isSelected()).assertIsDisplayed()
        compose.onNodeWithText("GitHub").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(LyricsFetchSource.GitHub, actions.index.value.source) }
        compose.runOnIdle { actions.index.value = actions.index.value.copy(status = LyricsIndexStatus.Checking) }
        compose.onNodeWithTag("lyrics-index-update").performScrollTo().assertIsNotEnabled()
    }
    @Test fun pickerCanPinNasAndRestoreAutomatic() {
        val actions = Stub()
        val track = Track(TrackId("song"), "Song")
        val visible = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface {
            if (visible.value) LyricsPickerDialog(track, actions, null, { visible.value = false })
        } } }
        compose.onNodeWithText("使用飞牛歌词").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LyricsChoiceMode.FnMusic, actions.chosen.value.mode); assertFalse(visible.value); visible.value = true }
        compose.onNodeWithText("自动匹配", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LyricsChoiceMode.Automatic, actions.chosen.value.mode) }
    }
    @Test fun onlineSettingsProtectLastSourceAndHideDetails() {
        val actions = Stub()
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost { OnlineLyricsSettingsScreen(actions, {}) } } } }
        capture("online-lyrics-settings")
        compose.onNodeWithTag("online-source-QQ").performScrollTo().performClick()
        compose.onNodeWithTag("online-source-Kugou").performScrollTo().performClick()
        compose.onNodeWithTag("online-source-priority").assertTextEquals("仅从 网易云音乐 搜索，优先采用逐词歌词。")
        compose.onNodeWithText("拖动调整优先级，越靠上越优先。").assertDoesNotExist()
        compose.onNodeWithTag("online-source-Netease").assertIsEnabled().performClick().assertIsOn()
        compose.runOnIdle { assertEquals(setOf(OnlineLyricsSource.Netease), actions.onlinePreference.value.sources) }
        compose.onNodeWithTag("online-lyrics-toggle").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(actions.onlinePreference.value.enabled); assertTrue(actions.amllEnabled.value) }
        compose.onNodeWithTag("online-source-QQ").assertDoesNotExist()
        compose.onNodeWithTag("online-lyrics-toggle").performScrollTo().performClick()
        compose.onNodeWithTag("online-source-Netease").performScrollTo().assertIsOn()
        compose.onNodeWithTag("online-source-QQ").assertIsEnabled()
    }
    @Test fun dragOrderUpdatesPreferenceAndExplanation() {
        val actions = Stub()
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface {
            LyricsSourceOrder(actions.onlinePreference.collectAsState().value, false) { actions.onlinePreference.value = it }
        } } }
        val first = compose.onNodeWithTag("online-source-drag-Netease", useUnmergedTree = true)
        val last = compose.onNodeWithTag("online-source-drag-Kugou", useUnmergedTree = true)
        val distance = last.fetchSemanticsNode().boundsInRoot.center.y - first.fetchSemanticsNode().boundsInRoot.center.y
        first.performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(0f, distance), 600) }
        compose.runOnIdle { assertEquals(listOf(OnlineLyricsSource.QQ, OnlineLyricsSource.Kugou, OnlineLyricsSource.Netease), actions.onlinePreference.value.order) }
        compose.onNodeWithTag("online-source-priority").assertTextEquals("优先采用逐词歌词，同等质量按 QQ 音乐 → 酷狗音乐 → 网易云音乐 的顺序选择。")
        compose.onNodeWithTag("online-source-Kugou").performClick()
        compose.onNodeWithTag("online-source-priority").assertTextEquals("优先采用逐词歌词，同等质量按 QQ 音乐 → 网易云音乐 的顺序选择。")
        capture("lyrics-source-order")
        compose.onNodeWithTag("online-source-reset").performClick()
        compose.runOnIdle {
            assertEquals(OnlineLyricsSource.entries.toList(), actions.onlinePreference.value.order)
            assertEquals(OnlineLyricsSource.entries.toSet(), actions.onlinePreference.value.sources)
        }
        OnlineLyricsSource.entries.forEach { compose.onNodeWithTag("online-source-${it.name}").assertIsOn() }
        compose.onNodeWithTag("online-source-priority").assertTextEquals("优先采用逐词歌词，同等质量按 网易云音乐 → QQ 音乐 → 酷狗音乐 的顺序选择。")
    }
    @Test fun manualSearchUsesSeparateSheetAndAppliesPreview() {
        val actions = Stub()
        var dismissed = false
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { LyricsPickerDialog(Track(TrackId("song"), "Song"), actions, null, { dismissed = true }) } } }
        compose.onNodeWithTag("lyrics-manual-search").performScrollTo()
        compose.mainClock.advanceTimeBy(500)
        capture("lyrics-choice-sheet", "lyrics-picker")
        compose.onNodeWithTag("lyrics-manual-search").performScrollTo().performClick()
        compose.onNodeWithTag("lyrics-picker").assertDoesNotExist()
        compose.onNodeWithTag("lyrics-search-sheet").assertExists()
        capture("lyrics-search-sheet", "lyrics-search-sheet")
        val initialSearches = actions.searches
        compose.onNodeWithTag("lyrics-search-query").performTextReplacement("Other")
        compose.runOnIdle { assertEquals(initialSearches, actions.searches) }
        compose.onNodeWithTag("lyrics-search-submit").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertTrue(actions.searches > initialSearches) }
        compose.onNodeWithText("Candidate").performScrollTo().performClick()
        compose.onNodeWithContentDescription("返回结果").performClick()
        compose.onNodeWithTag("lyrics-search-query").assertTextContains("Other")
        compose.onNodeWithText("Candidate").performScrollTo().performClick()
        compose.onNodeWithTag("lyrics-preview-apply").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertEquals(LyricsChoiceMode.Online, actions.chosen.value.mode) }
    }
    private fun capture(name: String, tag: String? = null) {
        compose.waitForIdle()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { java.io.File(it).apply { mkdirs() } }
            ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("lyrics-screenshots")!!
        java.io.File(directory, "$name.png").outputStream().use {
            (if (tag == null) compose.onRoot() else compose.onNodeWithTag(tag)).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private class Stub : LyricsActions {
        override val onlinePreference = MutableStateFlow(OnlineLyricsPreference())
        override val onlineCacheUsage = MutableStateFlow(LyricsCacheUsage(1, 1024))
        override suspend fun setOnlinePreference(preference: OnlineLyricsPreference) { onlinePreference.value = preference }
        var failOnlineClear = false
        override suspend fun clearOnlineCache() { if (failOnlineClear) error("Cannot clear"); onlineCacheUsage.value = LyricsCacheUsage() }
        var searches = 0
        override suspend fun searchOnline(source: OnlineLyricsSource, query: String): List<LyricsCandidate> { searches++; return if (source == OnlineLyricsSource.Netease) listOf(LyricsCandidate("", listOf("Candidate"), listOf("Singer"), onlineSource = source, songId = "1")) else emptyList() }
        override val amllEnabled = MutableStateFlow(true)
        override suspend fun setAmllEnabled(enabled: Boolean) { amllEnabled.value = enabled }
        override val index = MutableStateFlow(LyricsIndexState(version = "abcdef123456", bytes = 2048, status = LyricsIndexStatus.Current))
        override val cacheUsage = MutableStateFlow(LyricsCacheUsage(2, 4096))
        val chosen = MutableStateFlow(LyricsChoice())
        var updates = 0
        override suspend fun setSource(source: LyricsFetchSource) { index.value = index.value.copy(source = source) }
        override suspend fun updateIndex() { updates++ }
        override suspend fun clearCache() { cacheUsage.value = LyricsCacheUsage() }
        override suspend fun search(query: String) = emptyList<LyricsCandidate>()
        override suspend fun preview(candidate: LyricsCandidate) = LyricsDocument(listOf(LyricLine(0, "Preview")), if (candidate.onlineSource == null) LyricsOrigin.Amll else LyricsOrigin.Netease, candidate)
        override fun choice(track: Track): Flow<LyricsChoice> = chosen
        override suspend fun choose(track: Track, choice: LyricsChoice) { chosen.value = choice }
    }
}
