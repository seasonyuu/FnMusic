package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.*
import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary
import com.seasonyuu.fnmusic.core.designsystem.FnLightPalette
import org.junit.Assert.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.MusicUser
import org.junit.Rule
import org.junit.Test

class ProfileSettingsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appearanceContainsGroupedEntriesAndSummaries() {
        compose.setContent { FnMusicTheme { AppearanceSettingsScreen(
            MusicUiState(appearance = AppearancePreference.System, themeColor = ThemeColorPreference.Green, liquidGlassEnabled = false),
            {}, {}, {}, {},
        ) } }
        compose.onNodeWithText("显示模式").assertDoesNotExist()
        compose.onNodeWithText("浅色").assertIsDisplayed()
        compose.onNodeWithText("深色").assertIsDisplayed()
        compose.onNodeWithText("主题色").assertIsDisplayed().assertHasNoClickAction()
        compose.onNodeWithContentDescription("绿色").assertIsSelected()
        compose.onNodeWithText("绿色").assertDoesNotExist()
        compose.onNodeWithText("Liquid Glass").assertIsDisplayed()
        compose.onNodeWithText("已关闭").assertIsDisplayed()
    }

    @Test fun officialColorsUpdateActualThemeInBothModes() {
        var choice by mutableStateOf(ThemeColorPreference.Default)
        var dark by mutableStateOf(false)
        var accent = androidx.compose.ui.graphics.Color.Unspecified
        var primary = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent { FnMusicTheme(darkTheme = dark, accent = androidx.compose.ui.graphics.Color(choice.argb)) {
            accent = com.seasonyuu.fnmusic.core.designsystem.FnAccent
            FnMusicTheme(darkTheme = true) { primary = androidx.compose.material3.MaterialTheme.colorScheme.primary }
            ThemeColorPicker(choice, { choice = it })
        } }
        listOf(false, true).forEach { mode ->
            compose.runOnIdle { dark = mode }
            ThemeColorPreference.entries.forEach { color ->
                compose.onNodeWithContentDescription(color.label).performClick()
                compose.onNodeWithContentDescription(color.label).assertIsSelected()
                compose.runOnIdle {
                    assertEquals(androidx.compose.ui.graphics.Color(color.argb), accent)
                    assertEquals(accent, primary)
                }
            }
        }
    }

    @Test fun pendingThemeSaveBlocksRepeatedSelections() {
        val completion = kotlinx.coroutines.CompletableDeferred<Unit>()
        var calls = 0
        compose.setContent { FnMusicTheme { ThemeColorPicker(ThemeColorPreference.Default, {
            calls++
            completion.await()
        }) } }
        compose.onNodeWithContentDescription("蓝色").performClick()
        compose.onNodeWithContentDescription("绿色").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls); completion.complete(Unit) }
        compose.onNodeWithContentDescription("绿色").assertIsEnabled()
    }

    @Test fun failedThemeSaveKeepsSelectionAndAllowsRetry() {
        var choice by mutableStateOf(ThemeColorPreference.Default)
        var fail = true
        compose.setContent { FnMusicTheme { ThemeColorPicker(choice, {
            if (fail) error("disk full")
            choice = it
        }) } }
        compose.onNodeWithContentDescription("蓝色").performClick()
        compose.onNodeWithText("主题色保存失败，请重新选择。").assertIsDisplayed()
        compose.onNodeWithContentDescription("红色").assertIsSelected()
        compose.runOnIdle { fail = false }
        compose.onNodeWithContentDescription("蓝色").performClick()
        compose.onNodeWithContentDescription("蓝色").assertIsSelected()
    }

    @Test fun identityDisplaysNameRoleAndServer() {
        compose.setContent {
            FnMusicTheme {
                SettingsScreen(MusicUiState(user = MusicUser("test", "Listener", "admin"), serverName = "Test NAS"), {}, {}, {})
            }
        }
        compose.onNodeWithTag("profile-identity").performScrollTo()
        compose.onNodeWithText("Listener").assertIsDisplayed()
        compose.onNodeWithText("管理员").assertIsDisplayed()
        compose.onNodeWithTag("admin-badge").assertIsDisplayed()
        compose.onNodeWithText("当前服务器 · Test NAS").assertIsDisplayed()
    }

    @Test fun passwordRequiresMatchingConfirmationAndIsNotRestored() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { FnMusicTheme { PasswordSettingsScreen("Listener", {}, {}) } }
        compose.onNodeWithText("保存修改").assertIsNotEnabled()
        compose.onNodeWithText("新密码").performTextInput("new-password")
        compose.onNodeWithText("确认密码").performTextInput("different")
        compose.onNodeWithText("保存修改").assertIsNotEnabled()
        compose.onNodeWithText("确认密码").performTextReplacement("new-password")
        compose.onNodeWithText("保存修改").assertIsEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("保存修改").assertIsNotEnabled()
    }
    @Test fun unknownRoleDoesNotExposeAdministration() {
        compose.setContent { FnMusicTheme {
            SettingsScreen(MusicUiState(user = MusicUser("test", "Listener", null)), {}, {}, {},
                onAdminLibraries = {}, onAdminUsers = {}, onAdminServer = {})
        } }
        compose.onNodeWithText("音乐库管理").assertDoesNotExist()
        compose.onNodeWithText("用户管理").assertDoesNotExist()
        compose.onNodeWithText("服务器设置").assertDoesNotExist()
        compose.onNodeWithText("普通用户").assertDoesNotExist()
    }

    @Test fun selectingLightAppearanceChangesActualPalette() {
        var mode by mutableStateOf(AppearancePreference.Dark)
        var ink = androidx.compose.ui.graphics.Color.Unspecified
        compose.setContent { FnMusicTheme(darkTheme = mode == AppearancePreference.Dark) {
            ink = FnTextPrimary
            AppearanceSettingsScreen(MusicUiState(appearance = mode), { mode = it }, {}, {}, {})
        } }
        compose.onNodeWithText("浅色").performClick()
        compose.runOnIdle { assertEquals(AppearancePreference.Light, mode); assertEquals(FnLightPalette.primary, ink) }
    }

    @Test fun cacheClearRequiresConfirmationAndCapacityUsesBytes() {
        var preference by mutableStateOf(PlaybackCachePreference())
        var cleared = false
        compose.setContent { FnMusicTheme { CacheSettingsScreen(preference, 2048L to 2, { preference = it }, { cleared = true }, {}) } }
        compose.onNodeWithText("128 MiB").performClick()
        compose.runOnIdle { assertEquals(128L * 1024 * 1024, preference.bytes) }
        compose.onNodeWithText("清除已缓存歌曲").performScrollTo().performClick()
        compose.runOnIdle { assertFalse(cleared) }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertFalse(cleared) }
        compose.onNodeWithText("清除已缓存歌曲").performClick()
        compose.onNodeWithText("清除", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertTrue(cleared) }
    }

}
