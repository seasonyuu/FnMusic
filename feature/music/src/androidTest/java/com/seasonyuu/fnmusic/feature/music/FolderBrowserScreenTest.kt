package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost
import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Fixture-only. Run on a dedicated test emulator; the Gradle runner may uninstall packages. */
class FolderBrowserScreenTest {
    @get:Rule val compose = createComposeRule()
    private val api = Stub()
    private val wide = mutableStateOf(false)

    private fun show(admin: Boolean = true) {
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost {
                    FolderBrowserScreen(api, admin, wide.value, { _, _ -> null }, {}, {}, {}, initialPath = "/music")
                }
            }
        }
        compose.onNodeWithTag("folder-content").assertExists()
    }

    @Test fun layoutChangePreservesDirectoryAndSelectionAndBackExitsSelection() {
        show()
        compose.onNodeWithText("Album").performTouchInput { longClick() }
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
        compose.runOnIdle { wide.value = true }
        compose.onNodeWithTag("folder-tree").assertExists()
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("已选 1 项").assertDoesNotExist()
        compose.onNodeWithText("共 2 项 · 目录下 3 首歌曲").assertIsDisplayed()
    }

    @Test fun permanentDeletionRequiresSecondConfirmationAndCancelDoesNotWrite() {
        show()
        compose.onNodeWithText("Album").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("删除", useUnmergedTree = true).performClick()
        compose.onNodeWithText("移除并删除文件").performClick()
        compose.onNodeWithText("永久删除文件？").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, api.deletes) }
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("已选 1 项").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, api.deletes) }
    }

    @Test fun ordinaryUsersDoNotGetDeleteOrRecoveryActions() {
        show(admin = false)
        compose.onNodeWithText("Album").performTouchInput { longClick() }
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText("播放", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("删除", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("重新加入音乐库", useUnmergedTree = true).assertDoesNotExist()
    }

    private class Stub : FolderBrowserActions {
        override val accountKey = "fixture"
        var deletes = 0
        override suspend fun roots() = listOf(MusicFolder("root", "Music", "/music"))
        override suspend fun content(path: String, sort: FolderSort) = MusicDirectoryContent(MusicDirectoryNode(path, "Music", 3),
            if (path == "/music") listOf(MusicDirectoryNode("/music/album", "Album", 2)) else emptyList(),
            listOf(Track(TrackId("song"), "Song")), sort)
        override suspend fun playableTracks(selection: FolderSelection) = emptyList<Track>()
        override suspend fun play(selection: FolderSelection, mode: FolderPlaybackMode) = FolderPlaybackResult(0, false)
        override suspend fun playlists() = emptyList<Playlist>()
        override suspend fun createPlaylist(name: String) = Playlist(PlaylistId("new"), name)
        override suspend fun addToPlaylist(selection: FolderSelection, playlistId: PlaylistId) = Unit
        override suspend fun delete(selection: FolderSelection, permanently: Boolean) { deletes++ }
        override suspend fun recover(selection: FolderSelection) = Unit
    }
}
