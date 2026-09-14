package com.seasonyuu.fnmusic.feature.music

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.FolderAccess
import com.seasonyuu.fnmusic.core.model.ManagedMusicUser
import com.seasonyuu.fnmusic.core.model.MusicAdministration
import com.seasonyuu.fnmusic.core.model.MusicFolder
import com.seasonyuu.fnmusic.core.model.MusicScanTask
import com.seasonyuu.fnmusic.core.model.MusicServerSettings
import org.junit.Rule
import org.junit.Test

class UserAdministrationScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun administratorUsesBadgeAndAccessIsSubtitle() {
        val users = listOf(
            ManagedMusicUser("admin", "管理员账户", "admin", access = FolderAccess("all")),
            ManagedMusicUser("member", "普通账户", "member", access = FolderAccess("partial", listOf("library"))),
        )
        compose.setContent {
            FnMusicTheme { UserAdministrationScreen(Stub(users), currentUserId = "admin", onBack = {}) }
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("管理员账户").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("管理员账户").performScrollTo()
        compose.onNodeWithTag("admin-badge", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("音乐文件夹权限 · 全部文件夹").assertIsDisplayed()
        compose.onNodeWithText("音乐文件夹权限 · 1 个文件夹").assertIsDisplayed()
        compose.onNodeWithText("普通用户").assertDoesNotExist()
        compose.onNodeWithText("刷新").assertDoesNotExist()
        compose.onNodeWithContentDescription("新增用户").assertIsDisplayed()
        compose.onNodeWithContentDescription("权限管理").assertIsDisplayed()

        compose.onNodeWithContentDescription("新增用户").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("用户名").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("用户管理").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("权限管理").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("新用户默认权限").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class Stub(private val usersValue: List<ManagedMusicUser>) : MusicAdministration {
        override suspend fun scanTasks() = emptyList<MusicScanTask>()
        override suspend fun folders() = listOf(MusicFolder("library", "音乐库"))
        override suspend fun saveFolder(original: MusicFolder?, path: String, metadataPreference: String, autoDownloadLyric: Boolean) = Unit
        override suspend fun removeFolder(guid: String) = Unit
        override suspend fun scanFolder(guid: String) = Unit
        override suspend fun users() = usersValue
        override suspend fun saveUser(original: ManagedMusicUser?, username: String, password: String?, access: FolderAccess) = Unit
        override suspend fun defaultAccess() = FolderAccess()
        override suspend fun saveDefaultAccess(access: FolderAccess) = Unit
        override suspend fun serverSettings() = MusicServerSettings("Test NAS")
        override suspend fun saveServerSettings(value: MusicServerSettings) = Unit
    }
}
