package com.seasonyuu.fnmusic.feature.music

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.FnProgressiveSystemBars
import com.seasonyuu.fnmusic.core.designsystem.LocalFnBackdrop
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.designsystem.LiquidMenuHost
import com.seasonyuu.fnmusic.core.designsystem.LocalLiquidGlassEnabled
import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LibraryAdministrationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val api = LibraryStub()
    private fun content(dark: Boolean = false, fontScale: Float = 1f) {
        compose.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale), LocalLiquidGlassEnabled provides !dark) {
                FnMusicTheme(darkTheme = dark) {
                    if (dark) {
                        assertEquals(Color.White, MaterialTheme.colorScheme.onPrimary)
                        assertEquals(Color.White, MaterialTheme.colorScheme.onSecondary)
                        assertEquals(Color.White, MaterialTheme.colorScheme.onTertiary)
                    }
                    val backdrop = rememberLayerBackdrop()
                    FnProgressiveSystemBars(topBlur = { MusicAppBarBlur(it, statusBarSegmentOnly = true) }) {
                        Surface(Modifier.fillMaxSize()) {
                            LiquidMenuHost {
                                Box(Modifier.fillMaxSize()) {
                                    Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(MaterialTheme.colorScheme.background))
                                    // Mirror MusicUi: the system-bar/navigation backdrop records the
                                    // entire page. In-page glass must never sample this ancestor layer.
                                    val sceneBackdrop = LocalFnBackdrop.current!!
                                    Box(Modifier.fillMaxSize().layerBackdrop(sceneBackdrop)) {
                                        CompositionLocalProvider(LocalBottomOverlayPadding provides 120.dp, LocalAppBarBackdrop provides backdrop) {
                                            LibraryAdministrationScreen(api, {})
                                        }
                                    }
                                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(120.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainer).testTag("bottom-overlay"), contentAlignment = Alignment.Center) {
                                        Text("播放器与底部导航")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        compose.waitUntil(5_000) { compose.onAllNodesWithText("现有音乐").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val target = File(context.getExternalFilesDir(null), "library-$name.png")
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
        Thread.sleep(400) // Also let the platform Dialog window finish its opening animation.
        target.outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun addUsesDirectoryPickerAndLocalMetadataDisablesLyrics() {
        content()
        screenshot("overview-light")
        compose.onNodeWithContentDescription("添加文件夹").performClick()
        compose.waitUntil { compose.onAllNodesWithText("音乐位置").fetchSemanticsNodes().isNotEmpty() }
        screenshot("picker-light")
        compose.onAllNodesWithText("音乐位置").onLast().performClick()
        compose.waitUntil { compose.onAllNodesWithText("新音乐").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("新音乐").performClick()
        compose.waitUntil { compose.onAllNodesWithText("此文件夹没有子文件夹").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("选择此文件夹").performClick()
        compose.onNodeWithText("自动下载歌词").assertIsDisplayed()
        compose.onNodeWithText("仅使用本地数据").performClick()
        compose.onNodeWithText("自动下载歌词").assertDoesNotExist()
        compose.onNodeWithText("仅使用本地数据").performTouchInput { down(center) }
        screenshot("editor-light-ripple")
        compose.onNodeWithText("仅使用本地数据").performTouchInput { up() }
        compose.onNodeWithText("添加文件夹").performClick()
        compose.waitUntil { api.saves == 1 }
        assertEquals("/vol1/1000/Music/New", api.savedPath)
        assertEquals(false, api.savedLyrics)
        assertEquals(0, api.scans)
    }
    @Test fun editingKeepsDraftAndTasksExposeCancellationAndRetry() {
        content(dark = true, fontScale = 1.4f)
        screenshot("overview-dark-large-text")
        compose.onNodeWithText("现有音乐").performClick()
        compose.waitUntil { compose.onAllNodesWithText("保存修改").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("仅使用本地数据").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithText("保存修改").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        compose.onNodeWithText("1 项任务进行中").performClick()
        compose.onNodeWithText("取消任务").assertIsDisplayed()
        compose.onNodeWithText("取消任务").performClick()
        compose.waitUntil { api.cancels == 1 }
        compose.onNodeWithText("重试失败任务").performScrollTo().performClick()
        compose.waitUntil { api.retries == 1 }
    }
    @Test fun pollingStopsBelowResumedAndRefreshesOnReturn() {
        content()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val before = api.taskReads
        // Real time is intentional: the production poll uses the lifecycle coroutine clock.
        Thread.sleep(5_200)
        assertEquals(before, api.taskReads)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(5_000) { api.taskReads > before }
    }

    @Test fun glassEditorCanReopenAndToggleInsideRecordedScene() {
        content()
        repeat(3) {
            compose.onNodeWithText("现有音乐").performClick()
            val toggle = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            toggle.performTouchInput { click() }.assertIsOn()
            toggle.performTouchInput { click() }.assertIsOff()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("音乐库管理").assertIsDisplayed()
        }
    }

    @Test fun editorNavigationAnimatesInBothDirections() {
        content()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("现有音乐").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithText("音乐库管理").assertExists()
        compose.onNodeWithText("音乐文件夹设置").assertExists()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithText("音乐库管理").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.mainClock.advanceTimeBy(96)
        compose.onNodeWithText("音乐库管理").assertExists()
        compose.onNodeWithText("保存修改").assertExists()
        compose.mainClock.advanceTimeBy(320)
        compose.onNodeWithText("保存修改").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun darkEditorUsesLiquidToggleAndSnackbarClearsBottomOverlay() {
        content(dark = true)
        compose.onNodeWithText("现有音乐").performClick()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)).assertIsDisplayed().performClick()
        screenshot("editor-dark-toggle")
        compose.onNodeWithText("保存修改").performClick()
        compose.waitUntil { compose.onAllNodesWithText("已保存修改").fetchSemanticsNodes().isNotEmpty() }
        val message = compose.onNodeWithText("已保存修改").fetchSemanticsNode().boundsInRoot
        val overlay = compose.onNodeWithTag("bottom-overlay").fetchSemanticsNode().boundsInRoot
        assertTrue("Snackbar must stay above navigation and player", message.bottom < overlay.top)
        screenshot("snackbar-dark")
    }

    @Test fun maintenanceMenuConfirmsIndexRebuildAndSubmitsScanOnlyOnce() {
        api.includeTasks = false
        content()
        compose.onNodeWithContentDescription("更多维护操作").performClick()
        compose.onNodeWithText("扫描全部").performClick()
        compose.waitUntil { api.scans == 1 }
        compose.onNodeWithContentDescription("更多维护操作").performClick()
        compose.onNodeWithText("重建搜索索引").performClick()
        compose.onNodeWithText("重建搜索索引？").assertIsDisplayed()
        assertEquals(0, api.rebuilds)
        compose.onNodeWithText("重建").performClick()
        compose.waitUntil { api.rebuilds == 1 }
    }

    private class LibraryStub : MusicAdministration {
        @Volatile var saves = 0
        @Volatile var taskReads = 0
        var scans = 0
        var cancels = 0
        var retries = 0
        var rebuilds = 0
        var includeTasks = true
        var savedPath: String? = null
        var savedLyrics: Boolean? = null
        private val folder = MusicFolder("existing", "现有音乐", "/vol1/1000/Music/Existing/很长的音乐文件夹名称/Live Concerts & Jazz/高解析度无损收藏", accessStatus = 0, contentLastChangedAt = 1789996320)
        override suspend fun folders() = listOf(folder, MusicFolder("other", "歌单", "/vol2/1000/歌单", accessStatus = 0, contentLastChangedAt = 1789996440))
        override suspend fun folderDetail(guid: String) = folder
        override suspend fun authorizedDirectories() = listOf(AuthorizedMusicDirectory("/vol1/1000/Music", 3, name = "音乐位置"))
        override suspend fun childDirectories(parent: String) = if (parent == "/vol1/1000/Music") listOf(MusicDirectory("/vol1/1000/Music/New", "新音乐")) else emptyList()
        override suspend fun scanTasks(): List<MusicScanTask> {
            taskReads++
            if (!includeTasks) return emptyList()
            return listOf(MusicScanTask("scan", "existing", 10, 4, 0, false, false, id = "running"),
                MusicScanTask("lyrics", "existing", 10, 8, 2, true, false, id = "failed", type = "lyricDownload", retryable = true))
        }
        override suspend fun saveFolder(original: MusicFolder?, path: String, metadataPreference: String, autoDownloadLyric: Boolean) { savedPath = path; savedLyrics = autoDownloadLyric; saves++ }
        override suspend fun scanFolder(guid: String) { scans++ }
        override suspend fun scanAllFolders() { scans++ }
        override suspend fun removeFolder(guid: String) {}
        override suspend fun cancelTask(taskId: String) { cancels++ }
        override suspend fun retryTask(taskId: String) { retries++ }
        override suspend fun rebuildSearchIndex(): SearchIndexResult { rebuilds++; return SearchIndexResult(1, 2, 3) }
        override suspend fun users() = emptyList<ManagedMusicUser>()
        override suspend fun saveUser(original: ManagedMusicUser?, username: String, password: String?, access: FolderAccess) {}
        override suspend fun defaultAccess() = FolderAccess()
        override suspend fun saveDefaultAccess(access: FolderAccess) {}
        override suspend fun serverSettings() = MusicServerSettings("Test")
        override suspend fun saveServerSettings(value: MusicServerSettings) {}
    }
}
