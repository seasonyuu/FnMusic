package com.seasonyuu.fnmusic.feature.music

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AboutScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun manualCheckDisablesRepeatedRequestsAndShowsFailureRetry() {
        val actions = Stub()
        compose.setContent { FnMusicTheme { Surface { AboutScreen(actions, {}, {}) } } }
        compose.onNodeWithText("开发版").assertExists()
        compose.onNodeWithText("检查更新").performScrollTo().performClick()
        compose.onNodeWithText("正在检查更新…").performScrollTo().assertHasNoClickAction()
        compose.runOnIdle {
            assertEquals(1, actions.checks)
            actions.update.value = AppUpdateState.Failed("无法连接 GitHub")
        }
        compose.onNodeWithText("无法连接 GitHub").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("重试").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(2, actions.checks) }
    }

    @Test fun missingBrowserDoesNotCrashAboutPage() {
        val actions = Stub()
        var attempted = false
        compose.setContent {
            val base = androidx.compose.ui.platform.LocalContext.current
            val context = object : android.content.ContextWrapper(base) {
                override fun startActivity(intent: android.content.Intent) {
                    attempted = true
                    throw android.content.ActivityNotFoundException()
                }
            }
            CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides context) {
                FnMusicTheme { Surface { AboutScreen(actions, {}, {}) } }
            }
        }
        compose.onNodeWithText("源代码仓库").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(attempted) }
        compose.onNodeWithTag("about-page").assertExists()
    }

    @Test fun librariesHaveNoSearchAndScrollBehindTheAppBar() {
        val actions = Stub().apply {
            openSource.value = OpenSourceState.Ready(List(40) { index ->
                OpenSourceLibrary("library-$index", "Library $index", "1.0", "", null,
                    listOf(OpenSourceLicense("MIT", "Permission notice")))
            })
        }
        var selected = ""
        compose.setContent { FnMusicTheme { Surface { OpenSourceLibrariesScreen(actions, { selected = it }, {}) } } }
        compose.onNodeWithTag("open-source-search").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        assertScrollsBehindAppBar("open-source-page", "open-source-list", "Library 20", 21)
        compose.onNodeWithTag("open-source-list").performScrollToIndex(5)
        compose.onNodeWithText("Library 4").performClick()
        compose.runOnIdle { assertEquals("library-4", selected) }
    }

    private fun assertScrollsBehindAppBar(pageTag: String, listTag: String, text: String, index: Int) {
        val page = compose.onNodeWithTag(pageTag).fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag(listTag)
        assertEquals(page.top, list.fetchSemanticsNode().boundsInRoot.top, 0.5f)
        val bar = compose.onNodeWithTag("collection-app-bar").fetchSemanticsNode().boundsInRoot
        list.performScrollToIndex(index)
        val original = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { scroll ->
            scroll(0f, original.top - bar.center.y)
        }
        val scrolled = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        assertTrue("Content must enter the app-bar blur region", scrolled.top < bar.bottom && scrolled.top >= page.top)
        assertEquals(bar, compose.onNodeWithTag("collection-app-bar").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun longOfflineNoticeRemainsScrollableInDarkThemeAndLargeFont() {
        val actions = Stub()
        compose.setContent {
            FnMusicTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                    Surface { OpenSourceDetailScreen(actions, "sender", {}) }
                }
            }
        }
        compose.onNodeWithText("AirPlay Sender").assertExists()
        assertScrollsBehindAppBar("open-source-detail-page", "open-source-detail-list", "MIT / NOTICE", 1)
        compose.onNodeWithText("Copyright test author", substring = true).assertExists()
    }

    @Test fun developmentVersionShowsStableReferenceWithoutUpgradePrompt() {
        val actions = Stub().apply {
            update.value = AppUpdateState.Available(AppRelease("1.2.3", "2026-09-20T12:00:00Z",
                "首个公开版本，支持 AirPlay 输出与外部在线歌词搜索。\n\n本次亮点：\n• AirPlay 输出：连接接收设备。",
                "$RELEASES_URL/tag/v1.2.3", false))
        }
        compose.setContent { FnMusicTheme { Surface { AboutScreen(actions, {}, {}) } } }
        compose.onNodeWithText("最新正式版 1.2.3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("更新摘要").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("首个公开版本，支持 AirPlay 输出与外部在线歌词搜索。").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("查看发布页").performScrollTo().assertIsDisplayed()
        val image = compose.onRoot().captureToImage()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.getExternalFilesDir(null), "about-update-dark.png")
        file.outputStream().use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun capturesAboutAndLicensesForVisualReview() {
        val actions = Stub()
        val dark = androidx.compose.runtime.mutableStateOf(false)
        val page = androidx.compose.runtime.mutableStateOf(0)
        compose.setContent {
            FnMusicTheme(darkTheme = dark.value) {
                Surface {
                    when (page.value) {
                        0 -> AboutScreen(actions, {}, {})
                        1 -> OpenSourceLibrariesScreen(actions, {}, {})
                        else -> OpenSourceDetailScreen(actions, "sender", {})
                    }
                }
            }
        }
        fun capture(name: String) {
            compose.waitForIdle()
            val image = compose.onRoot().captureToImage()
            val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val file = java.io.File(context.getExternalFilesDir(null), "$name.png")
            file.outputStream().use { image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
        capture("about-light")
        compose.runOnIdle { dark.value = true }
        capture("about-dark")
        compose.runOnIdle { page.value = 1 }
        capture("licenses-dark")
        compose.runOnIdle {
            val entries = (actions.openSource.value as OpenSourceState.Ready).libraries
            actions.openSource.value = OpenSourceState.Ready(entries + List(40) { index ->
                entries.first().copy(id = "sample-$index", name = "Open source library $index")
            })
        }
        assertScrollsBehindAppBar("open-source-page", "open-source-list", "Open source library 10", 13)
        capture("licenses-scrolled-dark")
        compose.runOnIdle { page.value = 2 }
        capture("license-detail-dark")
        assertScrollsBehindAppBar("open-source-detail-page", "open-source-detail-list", "MIT / NOTICE", 1)
        capture("license-detail-scrolled-dark")
    }

    private class Stub : AboutActions {
        override val info = AppAboutInfo("1.2.3-dev.2+abcdef0", 1002005, true)
        override val update = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)
        override val openSource = MutableStateFlow<OpenSourceState>(OpenSourceState.Ready(listOf(
            OpenSourceLibrary("sender", "AirPlay Sender", "abc1234", "Test author", null,
                listOf(OpenSourceLicense("MIT / NOTICE", "Copyright test author\n" + "Permission notice.\n".repeat(100)))),
            OpenSourceLibrary("coil", "Coil", "3.2.0", "Coil contributors", null,
                listOf(OpenSourceLicense("Apache-2.0", "Apache license"))),
        )))
        var checks = 0
        override fun checkUpdate() { checks++; update.value = AppUpdateState.Checking }
        override fun loadLibraries() = Unit
    }
}
