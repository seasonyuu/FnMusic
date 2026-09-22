package com.seasonyuu.fnmusic.feature.music

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.data.PlaylistEditorController
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlaylistEditSheetTest {
    @Test fun backgroundFollowsDefaultAndPhotoSelectionInLightTheme() {
        show(dark = false)
        val context = compose.activity
        fun assertBackground(expected: androidx.compose.ui.graphics.Color) {
            compose.waitUntil(5000) {
                val pixels = compose.onNodeWithTag("playlist-editor-sheet").captureToImage().toPixelMap()
                val actual = pixels[pixels.width / 2, 4]
                kotlin.math.abs(actual.red - expected.red) < .025f &&
                    kotlin.math.abs(actual.green - expected.green) < .025f &&
                    kotlin.math.abs(actual.blue - expected.blue) < .025f
            }
        }
        assertBackground(coverTone(com.seasonyuu.fnmusic.data.DefaultPlaylistCover.preview(context, "playlist_default_2")))
        compose.onNodeWithContentDescription("默认封面 4页").performClick()
        assertBackground(coverTone(com.seasonyuu.fnmusic.data.DefaultPlaylistCover.preview(context, "playlist_default_4")))
        val file = java.io.File.createTempFile("tone-test-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            compose.runOnIdle { controller.selectPhoto(file.path) }
            assertBackground(coverTone(bitmap))
            screenshot("playlist-editor-photo-tone")
            compose.onNodeWithContentDescription("默认封面 2页").performClick()
            assertBackground(coverTone(com.seasonyuu.fnmusic.data.DefaultPlaylistCover.preview(context, "playlist_default_2")))
            compose.onNodeWithContentDescription("当前封面页").performClick()
            assertBackground(coverTone(bitmap))
        } finally { bitmap.recycle(); file.delete() }
    }

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val playlist = Playlist(PlaylistId("edit"), "通勤歌单", "playlist_default_2")
    private val songs = (1..40).map { Track(TrackId("$it"), "歌曲 $it", artists = listOf(Artist(ArtistId("artist"), "测试歌手"))) }
    private var writes = 0
    private var removals = emptyList<TrackId>()
    private var savedOrder = PlaylistOrder()
    private var failWrite = false
    private var savedCover: String? = null
    private var uploads = 0
    private var pickerLaunches = 0
    private lateinit var controller: PlaylistEditorController

    private fun show(dark: Boolean = true, stubPicker: Boolean = false) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            controller = remember {
                PlaylistEditorController(scope, { "account" }, { playlist to songs }, { _, _ -> savedOrder },
                    { _, _, order -> if (failWrite) error("本地存储不可用"); writes++; savedOrder = order },
                    { _, _, cover -> savedCover = cover }, { _, ids -> removals = ids }, {},
                    prepareCover = { it }, uploadCover = { uploads++; "uploaded-photo" },
                    uploadDefaultCover = { uploads++; "uploaded-$it" })
            }
            var showing by remember { mutableStateOf(true) }
            val registryOwner = remember {
                object : androidx.activity.result.ActivityResultRegistryOwner {
                    override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
                        override fun <I, O> onLaunch(requestCode: Int, contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I, options: androidx.core.app.ActivityOptionsCompat?) {
                            pickerLaunches++
                            dispatchResult(requestCode, android.app.Activity.RESULT_CANCELED, null)
                        }
                    }
                }
            }
            FnMusicTheme(darkTheme = dark) { LiquidMenuHost {
                if (showing) {
                    if (stubPicker) CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides registryOwner) {
                        PlaylistEditSheet(playlist.id, controller, { _, _ -> null }) { showing = false }
                    } else PlaylistEditSheet(playlist.id, controller, { _, _ -> null }) { showing = false }
                }
            } }
        }
        compose.onNodeWithTag("playlist-editor-done").assertIsEnabled()
        screenshot(if (dark) "playlist-editor-header-dark" else "playlist-editor-header-light")
    }

    @Test fun coverPagerSwipesBetweenCurrentAndDefaultsAndSavesOnlyOnDone() {
        show(dark = false)
        compose.onNodeWithContentDescription("完成").assertIsDisplayed()
        compose.onNodeWithText("当前使用", useUnmergedTree = true).assertIsDisplayed()
        assertEquals(playlist.coverId, controller.state.value.draft!!.coverId)
        compose.onNodeWithTag("playlist-cover-pager").performTouchInput { swipeLeft(startX = width * .7f, endX = width * .25f) }
        compose.waitForIdle()
        assertEquals("playlist_default_1", controller.state.value.draft!!.coverId)
        compose.onNodeWithText("默认封面 1", useUnmergedTree = true).assertIsDisplayed()
        assertNull(savedCover)
        compose.onNodeWithTag("playlist-cover-pager").performTouchInput { swipeRight(startX = width * .25f, endX = width * .7f) }
        compose.waitForIdle()
        assertEquals(playlist.coverId, controller.state.value.draft!!.coverId)
        compose.onNodeWithContentDescription("默认封面 4页").performClick()
        compose.waitForIdle()
        assertEquals("playlist_default_4", controller.state.value.draft!!.coverId)
        screenshot("playlist-editor-cover-pager")
        compose.onNodeWithTag("playlist-editor-done").performClick()
        assertEquals("uploaded-playlist_default_4", savedCover)
        assertEquals(1, uploads)
    }

    @Test fun coverChooserPageDoesNotChangeCoverAndCancelDiscardsSelection() {
        show(stubPicker = true)
        compose.onNodeWithContentDescription("选择封面页").performClick()
        compose.waitForIdle()
        assertEquals(playlist.coverId, controller.state.value.draft!!.coverId)
        compose.onNodeWithText("从相册选择").assertIsDisplayed()
        compose.onNodeWithTag("playlist-cover-page-0").performClick()
        compose.runOnIdle { assertEquals(1, pickerLaunches); assertFalse(controller.state.value.draft!!.dirty) }
        compose.onNodeWithContentDescription("默认封面 3页").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("取消").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        assertNull(savedCover)
        assertEquals(0, writes)
    }

    @Test fun selectedPhotoReplacesCurrentCoverWithoutAddingPages() {
        show()
        compose.onAllNodesWithTag("playlist-cover-indicator").assertCountEquals(6)
        compose.runOnIdle { controller.selectPhoto("/test/photo.png") }
        compose.waitForIdle()
        compose.onAllNodesWithTag("playlist-cover-indicator").assertCountEquals(6)
        compose.onNodeWithContentDescription("所选照片页").assertDoesNotExist()
        compose.onNodeWithContentDescription("当前封面页").assertIsSelected()
        compose.onNodeWithText("新照片 · 待保存", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("当前使用", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(controller.state.value.draft!!.usePhoto)
        assertEquals(0, uploads)
        compose.onNodeWithContentDescription("默认封面 3页").performClick()
        compose.waitForIdle()
        assertFalse(controller.state.value.draft!!.usePhoto)
        compose.onNodeWithContentDescription("当前封面页").performClick()
        compose.waitForIdle()
        assertTrue(controller.state.value.draft!!.usePhoto)
        compose.runOnIdle { controller.selectPhoto("/test/replacement.png") }
        compose.waitForIdle()
        compose.onAllNodesWithTag("playlist-cover-indicator").assertCountEquals(6)
        compose.onNodeWithContentDescription("当前封面页").assertIsSelected()
        assertEquals("/test/replacement.png", controller.state.value.draft!!.photoPath)
        compose.onNodeWithTag("playlist-editor-done").performClick()
        assertEquals(1, uploads)
        assertEquals("uploaded-photo", savedCover)
    }

    @Test fun removalIsDraftUntilDoneAndCancelDiscards() {
        show()
        compose.onNodeWithTag("playlist-editor-list").performScrollToNode(hasTestTag("playlist-edit-track-0"))
        compose.onNodeWithTag("playlist-edit-track-0").performClick()
        compose.onNodeWithTag("playlist-editor-remove").performClick()
        assertTrue(removals.isEmpty())
        compose.onNodeWithContentDescription("取消").performClick()
        compose.onNodeWithText("放弃未保存的修改？").assertIsDisplayed()
        compose.onNodeWithText("继续编辑").performClick()
        compose.onNodeWithTag("playlist-editor-done").performClick()
        compose.onNodeWithTag("playlist-editor-sheet").assertDoesNotExist()
        assertEquals(listOf(songs.first().id), removals)
    }

    @Test fun toggleGatesHandlesAndAccessibilityReorderChangesPersistedOrder() {
        show(dark = false)
        compose.onNodeWithTag("playlist-custom-order").assertIsOff()
        compose.onNodeWithTag("playlist-editor-list").performScrollToNode(hasTestTag("playlist-edit-track-0"))
        compose.onNodeWithTag("playlist-drag-0").assertDoesNotExist()
        compose.onNodeWithTag("playlist-custom-order").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-editor-list").performScrollToNode(hasTestTag("playlist-edit-track-0"))
        val moveDown = compose.onNodeWithTag("playlist-drag-0", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.CustomActions].single { it.label == "下移" }
        compose.runOnIdle { moveDown.action() }
        compose.onNodeWithTag("playlist-edit-track-0").assertTextContains("歌曲 2")
        compose.onNodeWithTag("playlist-custom-order").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-editor-list").performScrollToNode(hasTestTag("playlist-edit-track-0"))
        compose.onNodeWithTag("playlist-edit-track-0").assertTextContains("歌曲 1")
        compose.onNodeWithTag("playlist-custom-order").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-editor-list").performScrollToNode(hasTestTag("playlist-edit-track-0"))
        compose.onNodeWithTag("playlist-edit-track-0").assertTextContains("歌曲 2")
        screenshot("playlist-edit-light")
        compose.onNodeWithTag("playlist-editor-done").performClick()
        assertEquals(songs[1].id, savedOrder.keys.first().id)
        assertEquals(1, writes)
    }

    @Test fun dragScrollsLongListAndKeepsControlsFixed() {
        show()
        compose.onNodeWithTag("playlist-custom-order").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-editor-list").performScrollToNode(hasTestTag("playlist-edit-track-10"))
        val before = compose.onNodeWithTag("playlist-editor-done").fetchSemanticsNode().boundsInRoot
        val listBottom = compose.onNodeWithTag("playlist-editor-list").fetchSemanticsNode().boundsInRoot.bottom
        val handle = compose.onNodeWithTag("playlist-drag-10", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val original = controller.state.value.draft!!.order.keys
        compose.onNodeWithTag("playlist-drag-10", useUnmergedTree = true).performTouchInput {
            down(center)
            moveTo(center.copy(y = listBottom - handle.top - 8f), delayMillis = 1000)
            advanceEventTime(1000)
            up()
        }
        compose.waitForIdle()
        assertNotEquals(original, controller.state.value.draft!!.order.keys)
        assertEquals(before, compose.onNodeWithTag("playlist-editor-done").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("playlist-editor-remove").assertIsDisplayed()
        screenshot("playlist-edit-dark")
    }

    @Test fun failedSaveStaysOpenAndRetryCompletes() {
        failWrite = true
        show()
        compose.onNodeWithTag("playlist-custom-order").performScrollTo().performClick()
        compose.onNodeWithTag("playlist-editor-done").performClick()
        compose.onNodeWithTag("playlist-editor-error").assertIsDisplayed()
        compose.onNodeWithTag("playlist-editor-sheet").assertExists()
        failWrite = false
        compose.onNodeWithTag("playlist-editor-done").performClick()
        compose.onNodeWithTag("playlist-editor-sheet").assertDoesNotExist()
        assertTrue(savedOrder.enabled)
    }

    @Test fun keyboardKeepsSaveAndRemovalControlsReachable() {
        fun shell(command: String): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
        val previous = shell("settings get secure show_ime_with_hard_keyboard")
        shell("settings put secure show_ime_with_hard_keyboard 1")
        try {
        show(dark = false)
        compose.onNodeWithTag("playlist-editor-name").performClick().performTextReplacement("编辑后的名称")
        compose.waitUntil(5_000) {
            androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
        }
        // Floating IMEs reserve no bottom inset; docked IMEs must leave the controls above it.
        var lastInset = -1
        var stableSince = android.os.SystemClock.uptimeMillis()
        compose.waitUntil(5_000) {
            val inset = androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0
            if (inset != lastInset) { lastInset = inset; stableSince = android.os.SystemClock.uptimeMillis() }
            android.os.SystemClock.uptimeMillis() - stableSince > 300
        }
        val bottom = compose.onNodeWithTag("playlist-editor-remove").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(bottom <= compose.activity.window.decorView.height - lastInset + 2)
        compose.onNodeWithTag("playlist-editor-done").assertIsDisplayed()
        compose.onNodeWithTag("playlist-editor-remove").assertIsDisplayed()
        screenshot("playlist-editor-keyboard")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("playlist-screenshots")!!
        java.io.File(directory, "playlist-keyboard-screen.png").outputStream().use { automation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithContentDescription("取消").performClick()
        compose.onNodeWithText("放弃修改").performClick()
        assertEquals(0, writes)
        } finally {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val directory = instrumentation.targetContext.getExternalFilesDir("playlist-screenshots")!!
            java.io.File(directory, "keyboard-test-exit.png").outputStream().use {
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            shell(if (previous == "null") "settings delete secure show_ime_with_hard_keyboard" else "settings put secure show_ime_with_hard_keyboard $previous")
        }
    }

    @Test fun unavailablePlaylistTracksStayVisibleAndCanBeRemovedButNeverPlayed() {
        val tracks = listOf(songs[0].copy(accessStatus = 1), songs[1], songs[2].copy(accessStatus = 2), songs[1])
        var plays = 0
        var selectedIndex = -1
        var removed = 0
        compose.setContent {
            FnMusicTheme { LiquidMenuHost {
                CompositionLocalProvider(LocalTrackMenuActions provides { _, _ -> listOf(
                    TrackMenuAction(LiquidMenuItem("next", "下一首播放"), invoke = { error("Unavailable track played") }),
                    TrackMenuAction(LiquidMenuItem("remove-playlist", "从歌单移除"), invoke = { removed++ }),
                ) }) {
                    PlaylistDetailScreen(playlist, MusicUiState(detailTracks = tracks), PlayerState(), { _, _ -> null },
                        { queue, selected -> assertEquals(listOf(songs[1], songs[1]), queue); selectedIndex = selected; plays++ }, {}, {}, {}, {})
                }
            } }
        }
        compose.onNodeWithTag("playlist-play").performClick()
        assertEquals(0, selectedIndex)
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasTestTag("playlist-track-0"))
        compose.onNodeWithTag("playlist-track-0").assertIsNotEnabled().assertTextContains("已失效 · 文件不存在").performClick()
        assertEquals(1, plays)
        compose.onNodeWithTag("playlist-track-0").onChildren().filter(hasClickAction()).onLast().performClick()
        compose.onNodeWithText("下一首播放").assertIsNotEnabled()
        compose.onNodeWithText("从歌单移除").assertIsEnabled().performClick()
        assertEquals(1, removed)
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasTestTag("playlist-track-2"))
        compose.onNodeWithTag("playlist-track-2").assertTextContains("不可用 · 无访问权限").assertIsNotEnabled()
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasTestTag("playlist-track-3"))
        compose.onNodeWithTag("playlist-track-3").performClick()
        assertEquals(1, selectedIndex)
        assertEquals(2, plays)
    }

    @Test fun playlistWithOnlyUnavailableTracksDisablesPlay() {
        compose.setContent {
            FnMusicTheme { LiquidMenuHost {
                PlaylistDetailScreen(playlist, MusicUiState(detailTracks = listOf(songs[0].copy(accessStatus = 99))),
                    PlayerState(), { _, _ -> null }, { _, _ -> error("No playable tracks") }, {}, {}, {}, {})
            } }
        }
        compose.onNodeWithTag("playlist-play").assertIsNotEnabled().performClick()
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasTestTag("playlist-track-0"))
        compose.onNodeWithTag("playlist-track-0").assertTextContains("歌曲暂不可用").assertIsNotEnabled()
    }

    @Test fun playlistPlaybackUsesDisplayedLocalOrder() {
        val ordered = PlaylistOrder(true, songs.playlistKeys().reversed()).apply(songs)
        var index = -1
        compose.setContent {
            FnMusicTheme { LiquidMenuHost {
                PlaylistDetailScreen(playlist, MusicUiState(detailTracks = ordered), PlayerState(), { _, _ -> null },
                    { queue, selected -> assertEquals(ordered, queue); index = selected }, {}, {}, {}, {})
            } }
        }
        compose.onNodeWithTag("playlist-play").performClick()
        assertEquals(0, index)
        compose.onNodeWithTag("library-detail-list").performScrollToNode(hasTestTag("playlist-track-5"))
        compose.onNodeWithTag("playlist-track-5").assertTextContains("歌曲 35").performClick()
        assertEquals(5, index)
        compose.onNodeWithText("多选").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("playlist-screenshots")!!
        java.io.File(directory, "$name.png").outputStream().use {
            compose.onNodeWithTag("playlist-editor-sheet").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
