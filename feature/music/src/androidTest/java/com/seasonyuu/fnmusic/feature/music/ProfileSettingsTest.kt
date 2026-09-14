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
            AppearanceSettingsScreen(mode, { mode = it }, {})
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
