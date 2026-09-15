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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.luminance
import com.kyant.backdrop.backdrops.layerBackdrop
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

    @Test fun selectionSurfaceRemainsVisibleInBothThemesWithAndWithoutGlass() {
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(false)
        compose.setContent { FnMusicTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalLiquidGlassEnabled provides glass) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(FnNavigationSurface))
                    LiquidBottomTabs(0, {}, backdrop, 2, Modifier.width(240.dp).testTag("tabs-surface")) {
                        LiquidBottomTab({}) { Box(Modifier.size(16.dp)) }
                        LiquidBottomTab({}) { Box(Modifier.size(16.dp)) }
                    }
                }
            }
        } }
        listOf(false, true).forEach { theme ->
            listOf(false, true).forEach { enabled ->
                compose.runOnIdle { dark = theme; glass = enabled }
                val pixels = compose.onNodeWithTag("tabs-surface").captureToImage().toPixelMap()
                val selected = pixels[pixels.width / 4, pixels.height / 2].luminance()
                val unselected = pixels[pixels.width * 3 / 4, pixels.height / 2].luminance()
                assertTrue("Selection must remain visible: dark=$theme glass=$enabled", (maxOf(selected, unselected) + .05f) / (minOf(selected, unselected) + .05f) > 1.2f)
                assertTrue(if (theme) selected > unselected else selected < unselected)
            }
        }
    }

    @Test fun disabledGlassButtonRetainsEdgeHighlightAndOpaqueInterior() {
        compose.setContent { FnMusicTheme(darkTheme = true) {
            CompositionLocalProvider(LocalLiquidGlassEnabled provides false) {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Red), contentAlignment = Alignment.Center) {
                    LiquidButton({}, rememberLayerBackdrop(), Modifier.size(120.dp, 48.dp).testTag("solid-button")) {}
                }
            }
        } }
        val pixels = compose.onNodeWithTag("solid-button").captureToImage().toPixelMap()
        val center = pixels[pixels.width / 2, pixels.height / 2]
        assertEquals(FnDarkPalette.navigation.red, center.red, .01f)
        assertEquals(FnDarkPalette.navigation.green, center.green, .01f)
        val edge = (0..4).maxOf { pixels[pixels.width / 4, it].luminance() }
        assertTrue("Highlight should survive with glass disabled", edge > center.luminance() + .05f)
    }

    @Test fun draggedColorMaskMovesBeforePageSelectionIsCommitted() {
        var selected by mutableIntStateOf(0)
        var glass by mutableStateOf(false)
        var dark by mutableStateOf(false)
        compose.setContent { FnMusicTheme(darkTheme = dark, accent = androidx.compose.ui.graphics.Color.Blue) {
            CompositionLocalProvider(LocalLiquidGlassEnabled provides glass) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(FnNavigationSurface))
                    LiquidBottomTabs(selected, { selected = it }, backdrop, 2, Modifier.width(240.dp).testTag("drag-tabs")) {
                        LiquidBottomTab({ selected = 0 }) {
                            Box(Modifier.size(20.dp).background(FnTextSecondary))
                            androidx.compose.material3.Text("First", color = FnTextSecondary)
                        }
                        LiquidBottomTab({ selected = 1 }) {
                            Box(Modifier.size(20.dp).background(FnTextSecondary))
                            androidx.compose.material3.Text("Second", color = FnTextSecondary)
                        }
                    }
                }
            }
        } }
        fun bluePixels(right: Boolean, lower: Boolean): Int {
            val pixels = compose.onNodeWithTag("drag-tabs").captureToImage().toPixelMap()
            val xs = if (right) pixels.width / 2 until pixels.width else 0 until pixels.width / 2
            val ys = if (lower) pixels.height / 2 until pixels.height else 0 until pixels.height / 2
            var count = 0
            for (y in ys) for (x in xs) {
                val c = pixels[x, y]
                if (c.blue > c.red + .25f && c.blue > c.green + .25f) count++
            }
            return count
        }
        listOf(false, true).forEach { theme -> listOf(false, true).forEach { enabled ->
            compose.runOnIdle { dark = theme; glass = enabled; selected = 0 }
            compose.waitForIdle()
            assertTrue("Initially first icon is colored", bluePixels(false, false) > 20)
            val bounds = compose.onNodeWithTag("drag-tabs").fetchSemanticsNode().boundsInRoot
            compose.onRoot().performTouchInput {
                down(androidx.compose.ui.geometry.Offset(bounds.left + bounds.width / 4, bounds.center.y))
                moveTo(androidx.compose.ui.geometry.Offset(bounds.left + bounds.width * 3 / 4, bounds.center.y), 500)
            }
            compose.mainClock.advanceTimeBy(800)
            compose.runOnIdle { assertEquals("Holding a drag must not navigate", 0, selected) }
            assertTrue("Icon color follows held drag: dark=$theme glass=$enabled", bluePixels(true, false) > 20)
            assertTrue("Text color follows held drag", bluePixels(true, true) > 20)
            assertEquals("Old icon loses selected color", 0, bluePixels(false, false))
            compose.onRoot().performTouchInput { up() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(1, selected) }
        } }
    }

    @Test fun disabledGlassTintStaysAlignedWithLabelsDuringEdgeDrags() {
        var selected by mutableIntStateOf(0)
        var nextCopy = 0
        val coordinates = mutableMapOf<Pair<Int, Int>, LayoutCoordinates>()
        compose.setContent { FnMusicTheme {
            CompositionLocalProvider(LocalLiquidGlassEnabled provides false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LiquidBottomTabs(selected, { selected = it }, rememberLayerBackdrop(), 3,
                        Modifier.width(280.dp).testTag("alignment-tabs")) {
                        val copy = remember { nextCopy++ }
                        repeat(3) { tab ->
                            LiquidBottomTab({ selected = tab }) {
                                androidx.compose.material3.Text("Tab $tab", color = FnTextSecondary,
                                    modifier = Modifier.onGloballyPositioned { coordinates[copy to tab] = it })
                            }
                        }
                    }
                }
            }
        } }
        val bounds = compose.onNodeWithTag("alignment-tabs").fetchSemanticsNode().boundsInRoot
        for (target in listOf(2, 0)) {
            compose.onNodeWithTag("liquid-bottom-tabs-indicator").performTouchInput { down(center) }
            compose.onRoot().performTouchInput {
                moveTo(Offset(bounds.left + bounds.width * (target + .5f) / 3f, bounds.center.y), 500)
            }
            compose.mainClock.advanceTimeBy(800)
            compose.runOnIdle {
                assertEquals("Base and tint each contain three labels", 6, coordinates.size)
                repeat(3) { tab ->
                    val base = coordinates.getValue(0 to tab)
                    val tint = coordinates.getValue(1 to tab)
                    // Read transformed coordinates at assertion time, after the press animation.
                    for (corner in listOf(Offset.Zero, Offset(base.size.width.toFloat(), base.size.height.toFloat()))) {
                        val original = base.localToRoot(corner)
                        val colored = tint.localToRoot(corner)
                        assertEquals("Tab $tab x alignment while dragging to $target", original.x, colored.x, .5f)
                        assertEquals("Tab $tab y alignment while dragging to $target", original.y, colored.y, .5f)
                    }
                }
            }
            compose.onRoot().performTouchInput { up() }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(target, selected) }
        }
    }

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

    @Test fun baseTabTextRemainsNeutralWithGlassDisabled() {
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
        compose.onAllNodesWithText("我的").assertCountEquals(1)
        compose.onAllNodesWithText("我的", useUnmergedTree = true)[0]
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(FnDarkPalette.secondary, layouts.single().layoutInput.style.color)
    }

    @Test fun controlThumbsStayWhiteWhenGlassIsDisabledInEitherTheme() {
        var dark by mutableStateOf(true)
        var glass by mutableStateOf(false)
        var checked by mutableStateOf(false)
        compose.setContent { FnMusicTheme(darkTheme = dark) {
            CompositionLocalProvider(LocalLiquidGlassEnabled provides glass) {
                androidx.compose.foundation.layout.Column(Modifier.background(FnNavigationSurface)) {
                    LiquidToggle(checked, { checked = it }, Modifier.testTag("white-toggle"))
                    LiquidSlider(.5f, {}, {}, Modifier.width(240.dp).testTag("white-slider"))
                }
            }
        } }
        for (theme in listOf(true, false)) for (enabledGlass in listOf(false, true)) {
            compose.runOnIdle { dark = theme; glass = enabledGlass }
            for (on in listOf(false, true)) {
                compose.runOnIdle { checked = on }
                compose.waitForIdle()
                for (tag in listOf("white-toggle", "white-slider")) {
                    val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
                    val center = pixels[pixels.width / 2, pixels.height / 2]
                    val message = "$tag thumb: dark=$theme glass=$enabledGlass checked=$on"
                    assertEquals(message, 1f, center.red, .01f)
                    assertEquals(message, 1f, center.green, .01f)
                    assertEquals(message, 1f, center.blue, .01f)
                }
            }
        }
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
