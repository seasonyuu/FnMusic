package com.seasonyuu.fnmusic.core.designsystem

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import com.kyant.backdrop.backdrops.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiquidMenuCompatibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun supportedRendererAndSolidFallbackAreReadableInBothThemes() {
        var dark by mutableStateOf(false)
        var glass by mutableStateOf(true)
        var forceSolid by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                FnMusicTheme(darkTheme = dark) {
                    CompositionLocalProvider(LocalLiquidGlassEnabled provides glass) {
                        val backdrop = rememberLayerBackdrop()
                        Box(Modifier.size(300.dp).testTag("scene")) {
                            Box(
                                Modifier.matchParentSize()
                                    .layerBackdrop(backdrop)
                                    .background(Color.Red)
                            )
                            LiquidMenuSurface(
                                backdrop,
                                menuBlobs(
                                    Rect(220f, 24f, 268f, 72f),
                                    Rect(68f, 24f, 268f, 260f),
                                    1f,
                                    1f,
                                ),
                                shaderSource = if (forceSolid) "invalid shader" else MenuShader,
                            )
                        }
                    }
                }
            }
        }
        for (theme in listOf(false, true)) for (enabled in listOf(false, true)) for (failed in
            listOf(false, true)) {
            compose.runOnIdle {
                dark = theme
                glass = enabled
                forceSolid = failed
            }
            val type =
                if (
                    !enabled ||
                        (failed && Build.VERSION.SDK_INT >= 33) ||
                        Build.VERSION.SDK_INT < 31
                )
                    "solid"
                else if (Build.VERSION.SDK_INT < 33) "blur" else "shader"
            compose.onNodeWithTag("liquid-menu-$type").assertExists()
            val pixels = compose.onNodeWithTag("scene").captureToImage().toPixelMap()
            val color = pixels[150, 150]
            val text = if (theme) FnDarkPalette.primary else FnLightPalette.primary
            assertTrue(
                "Menu text contrast $theme/$enabled/$failed",
                contrastRatio(text, color) >= 4.5f,
            )
            if (type == "solid") {
                val expected = if (theme) FnDarkPalette.navigation else FnLightPalette.navigation
                assertEquals(expected.red, color.red, .01f)
                assertEquals(expected.blue, color.blue, .01f)
            }
        }
    }

    @Test
    fun largeFontRtlAndCornerAnchorsStayOnScreen() {
        var expanded by mutableStateOf(false)
        var scale by mutableFloatStateOf(1f)
        var rtl by mutableStateOf(false)
        var corner by mutableIntStateOf(0)
        var attached by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, scale),
                LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                FnMusicTheme {
                    Box(Modifier.size(300.dp, 320.dp).testTag("window")) {
                        LiquidMenuHost {
                            val backdrop = rememberLayerBackdrop()
                            Box(
                                Modifier.fillMaxSize()
                                    .layerBackdrop(backdrop)
                                    .background(Color.DarkGray)
                            )
                            val align =
                                listOf(
                                    Alignment.TopStart,
                                    Alignment.TopEnd,
                                    Alignment.BottomStart,
                                    Alignment.BottomEnd,
                                )[corner]
                            Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = align) {
                                LiquidMenu(
                                    expanded,
                                    { expanded = false },
                                    backdrop,
                                    List(4) { LiquidMenuItem("$it", "较长的歌曲排序选项 $it") },
                                    {},
                                    onExpandedChange = { expanded = it },
                                    transition = if (attached) LiquidMenuTransition.Attached else LiquidMenuTransition.Detached,
                                    trigger = {
                                        LiquidButton(it, backdrop, Modifier.height(48.dp).testTag("trigger").then(surfaceModifier()),
                                            foregroundModifier = foregroundModifier) { Text("排序") }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
        for (mode in listOf(false, true)) for (font in listOf(1f, 2f)) for (right in listOf(false, true)) for (c in 0..3) {
            compose.runOnIdle {
                attached = mode
                scale = font
                rtl = right
                corner = c
            }
            compose.onNodeWithTag("trigger").performClick()
            val win = compose.onNodeWithTag("window").fetchSemanticsNode().boundsInRoot
            val menu =
                compose.onNodeWithTag("liquid-menu-content").fetchSemanticsNode().boundsInRoot
            assertTrue(
                "Menu fits $font/$right/$c: $menu in $win",
                menu.left >= win.left &&
                    menu.top >= win.top &&
                    menu.right <= win.right + .5 &&
                    menu.bottom <= win.bottom + .5,
            )
            compose.onNodeWithTag("liquid-menu-dismiss").performSemanticsAction(
                SemanticsActions.OnClick
            ) {
                it()
            }
            compose.waitForIdle()
        }
    }
}
