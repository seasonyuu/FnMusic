package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import com.seasonyuu.fnmusic.core.model.PlayerState
import org.junit.Assert.assertTrue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LiquidGlassRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun staticSelectionSurfaceDoesNotDarkenSelectedContent() {
        compose.setContent {
            FnMusicTheme {
                CompositionLocalProvider(LocalLiquidGlassEnabled provides false) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LiquidBottomTabs(0, {}, rememberLayerBackdrop(), 2, Modifier.width(240.dp)) {
                            LiquidBottomTab({}) { Box(Modifier.size(16.dp).background(FnAccent).testTag("selected-color")) }
                            LiquidBottomTab({}) { Box(Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
        val pixels = compose.onNodeWithTag("selected-color", useUnmergedTree = true).captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF62C55).red, center.red, 0.01f)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF62C55).green, center.green, 0.01f)
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF62C55).blue, center.blue, 0.01f)
    }

    @Test fun selectedTabTextUsesAccentWithGlassDisabled() {
        compose.setContent {
            FnMusicTheme {
                CompositionLocalProvider(LocalLiquidGlassEnabled provides false) {
                    DynamicMusicBottomBar(
                        state = PlayerState(), selectedDestination = MusicDestination.Profile,
                        expansionProgress = 1f, playerExpansionProgress = 0f,
                        backdrop = rememberLayerBackdrop(), onDestinationSelected = {},
                        onToggle = {}, onNext = {}, onOpenPlayer = {}, onExpand = {},
                    )
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("我的", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(androidx.compose.ui.graphics.Color(0xFFF62C55), layouts.single().layoutInput.style.color)
    }

    @Test fun enabledToggleThumbRemainsVerticallyCenteredAfterSwitching() {
        val checked = mutableStateOf(false)
        compose.setContent {
            FnMusicTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LiquidToggle(checked.value, { checked.value = it }, Modifier.testTag("toggle"))
                }
            }
        }
        compose.onNodeWithTag("toggle").performClick().assertIsOn()
        val pixels = compose.onNodeWithTag("toggle").captureToImage().toPixelMap()
        val whiteRows = (0 until pixels.height).filter { y ->
            (0 until pixels.width).any { x ->
                val color = pixels[x, y]
                color.red > .95f && color.green > .95f && color.blue > .95f
            }
        }
        assertTrue("The enabled thumb must be visible", whiteRows.isNotEmpty())
        assertEquals((pixels.height - 1) / 2f, (whiteRows.first() + whiteRows.last()) / 2f, 1f)
    }

    @Test fun staticTabIndicatorRetainsDragNavigation() {
        val selected = mutableIntStateOf(0)
        compose.setContent {
            FnMusicTheme {
                CompositionLocalProvider(LocalLiquidGlassEnabled provides false) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LiquidBottomTabs(selected.intValue, { selected.intValue = it }, rememberLayerBackdrop(), 2, Modifier.width(240.dp)) {
                            LiquidBottomTab({ selected.intValue = 0 }) { Box(Modifier.size(16.dp)) }
                            LiquidBottomTab({ selected.intValue = 1 }) { Box(Modifier.size(16.dp)) }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("liquid-bottom-tabs-indicator").performTouchInput {
            swipe(center, center.copy(x = center.x + width), 400)
        }
        compose.runOnIdle { assertEquals(1, selected.intValue) }
    }
}
