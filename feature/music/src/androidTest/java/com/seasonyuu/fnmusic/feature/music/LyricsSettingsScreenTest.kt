package com.seasonyuu.fnmusic.feature.music

import androidx.compose.ui.test.*
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
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { LyricsSettingsScreen(actions, {}, { opened++ }) } } }
        compose.onNodeWithTag("lyrics-amll-toggle").assertDoesNotExist()
        compose.onNodeWithTag("lyrics-amll-entry").performTouchInput { click(androidx.compose.ui.geometry.Offset(width - 2f, height / 2f)) }
        compose.runOnIdle { assertEquals(1, opened) }
    }
    @Test fun disabledAmllDisablesDetailActionsAndCanBeReenabled() {
        val actions = Stub()
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost { AmllSettingsScreen(actions, {}) } } } }
        compose.onNodeWithTag("lyrics-amll-toggle").performClick()
        compose.runOnIdle { assertFalse(actions.amllEnabled.value) }
        listOf("lyrics-source-menu", "lyrics-index-update", "lyrics-cache-clear").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsNotEnabled()
        }
        compose.onNodeWithTag("lyrics-amll-toggle").performScrollTo().performClick()
        listOf("lyrics-source-menu", "lyrics-index-update", "lyrics-cache-clear").forEach {
            compose.onNodeWithTag(it).performScrollTo().assertIsEnabled()
        }
    }
    @Test fun showsIndexAndUpdatesAndClearsOnlyLyrics() {
        val actions = Stub()
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost { AmllSettingsScreen(actions, {}) } } } }
        compose.onNodeWithTag("lyrics-index-version").performScrollTo().assertTextEquals("abcdef123456")
        compose.onNodeWithTag("lyrics-index-size").assertTextEquals("2.0 KiB")
        capture("amll-settings-index")
        compose.onNodeWithTag("lyrics-index-update").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, actions.updates) }
        compose.onNodeWithTag("lyrics-cache-clear").performScrollTo()
        capture("amll-settings-cache")
        compose.onNodeWithTag("lyrics-cache-clear").performClick()
        compose.onNodeWithText("清除").performClick()
        compose.onNodeWithTag("lyrics-cache-usage").performScrollTo().assertTextEquals("暂无缓存")
        compose.onNodeWithTag("lyrics-cache-clear").assertIsNotEnabled()
        compose.runOnIdle { assertEquals("abcdef123456", actions.index.value.version) }
    }
    @Test fun sourceSwitchAndCheckingStateAreVisible() {
        val actions = Stub()
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost { AmllSettingsScreen(actions, {}) } } } }
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
        compose.setContent { FnMusicTheme { androidx.compose.material3.Surface { LyricsPickerDialog(track, actions, null, {}) } } }
        compose.onNodeWithText("使用飞牛歌词").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LyricsChoiceMode.FnMusic, actions.chosen.value.mode) }
        compose.onNodeWithText("自动匹配", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(LyricsChoiceMode.Automatic, actions.chosen.value.mode) }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val directory = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?.let { java.io.File(it).apply { mkdirs() } }
            ?: InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("lyrics-screenshots")!!
        java.io.File(directory, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private class Stub : LyricsActions {
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
        override suspend fun preview(candidate: LyricsCandidate) = LyricsDocument(emptyList(), LyricsOrigin.Amll, candidate)
        override fun choice(track: Track): Flow<LyricsChoice> = chosen
        override suspend fun choose(track: Track, choice: LyricsChoice) { chosen.value = choice }
    }
}
