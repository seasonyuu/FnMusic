package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MusicAppBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun toolbarReservesTenDpBelowTopAlignedControls() {
        compose.setContent {
            FnMusicTheme {
                MusicAppBar("标题", modifier = Modifier.testTag("toolbar"), onBack = {})
            }
        }
        compose.onNodeWithTag("toolbar").assertHeightIsEqualTo(54.dp)
        compose.onNodeWithContentDescription("返回").assertWidthIsEqualTo(44.dp).assertHeightIsEqualTo(44.dp)
        val bar = compose.onNodeWithTag("toolbar").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithText("标题").fetchSemanticsNode().boundsInRoot
        assertEquals(bar.top, button.top, 1f)
        assertEquals(button.center.y, title.center.y, 1f)
    }

    @Test fun blurRangeIsExplicitAndIndependentOfHiddenTitle() {
        compose.setContent {
            FnMusicTheme {
                val backdrop = rememberLayerBackdrop()
                Column {
                    Box(Modifier.testTag("status-blur")) { MusicAppBarBlur(backdrop, fullAppBarBlur = true, statusBarSegmentOnly = true) }
                    Box(Modifier.testTag("full-blur")) { MusicAppBarBlur(backdrop, fullAppBarBlur = true) }
                    MusicAppBar("标题", titleAlpha = { 0f })
                }
            }
        }
        val status = compose.onNodeWithTag("status-blur").fetchSemanticsNode().boundsInRoot
        val full = compose.onNodeWithTag("full-blur").fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        assertEquals(54f * density, full.height - status.height, 1f)
    }

    @Test fun largeFontRetainsBackAndActionAndDisabledActionsCannotRun() {
        var back = 0
        var actions = 0
        compose.setContent {
            FnMusicTheme {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                    Box(Modifier.width(320.dp)) {
                        MusicAppBar("一个很长的页面标题", onBack = { back++ }, actions = {
                            AppBarButton(onClick = { actions++ }, enabled = false) { Text("保存") }
                            AppBarButton(onClick = { actions++ }) { Text("编辑") }
                        })
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("返回").assertIsDisplayed().performClick()
        compose.onNodeWithText("保存").assertIsNotEnabled().performClick()
        compose.onNodeWithText("编辑").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, back); assertEquals(1, actions) }
    }
    @Test fun titleRemainsHorizontallyCenteredWhenSidesHaveDifferentWidths() {
        compose.setContent {
            FnMusicTheme {
                Box(Modifier.width(320.dp).testTag("appbar-test-container")) {
                    MusicAppBar("标题", onBack = {}, actions = {
                        AppBarButton(onClick = {}) { Text("更多操作") }
                    })
                }
            }
        }
        val bounds = compose.onNodeWithText("标题").fetchSemanticsNode().boundsInRoot
        val container = compose.onNodeWithTag("appbar-test-container").fetchSemanticsNode().boundsInRoot
        assertEquals(container.center.x, bounds.center.x, 1f)
    }
}
