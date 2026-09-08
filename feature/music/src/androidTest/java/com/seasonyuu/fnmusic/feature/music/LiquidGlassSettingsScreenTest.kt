package com.seasonyuu.fnmusic.feature.music

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.designsystem.FnGradientBackground
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiquidGlassSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun capturePreviewAtThreeBlurLevels() {
        val value = mutableFloatStateOf(1f)
        compose.setContent {
            FnMusicTheme {
                FnGradientBackground {
                    LiquidGlassSettingsScreen(value.floatValue, null, { value.floatValue = it }, {}, {})
                }
            }
        }
        for (level in listOf(0.1f, 1f, 2f)) {
            compose.runOnIdle { value.floatValue = level }
            compose.waitForIdle()
            val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("glass-screenshots")!!
            java.io.File(directory, "glass-${(level * 100).toInt()}.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test fun sliderUpdatesPreviewSavesAndResets() {
        val value = mutableFloatStateOf(1f)
        var saves = 0
        compose.setContent {
            FnMusicTheme {
                LiquidGlassSettingsScreen(value.floatValue, null, { value.floatValue = it }, { saves++ }, {})
            }
        }
        compose.onNodeWithTag("liquid-glass-slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        compose.runOnIdle { assertEquals(2f, value.floatValue, 0f); assertEquals(1, saves) }
        compose.onNodeWithTag("liquid-glass-slider")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "色调"))
        compose.onNodeWithTag("liquid-glass-slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        compose.runOnIdle { assertEquals(0.1f, value.floatValue, 0f); assertEquals(2, saves) }
        compose.onNodeWithTag("liquid-glass-slider")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "透明"))
        compose.onNodeWithText("恢复默认").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1f, value.floatValue, 0f); assertEquals(3, saves) }
    }

    @Test fun liquidThumbDragPreviewsBeforeReleaseAndSavesOnce() {
        val value = mutableFloatStateOf(1f)
        var saves = 0
        compose.setContent {
            FnMusicTheme {
                FnGradientBackground {
                    LiquidGlassSettingsScreen(value.floatValue, null, { value.floatValue = it }, { saves++ }, {})
                }
            }
        }
        val slider = compose.onNodeWithTag("liquid-glass-slider").performScrollTo()
        slider.performTouchInput {
            down(center)
            moveBy(Offset(width * 0.3f, 0f))
        }
        compose.runOnIdle { assertTrue(value.floatValue > 1f); assertEquals(0, saves) }
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("glass-screenshots")!!
        java.io.File(directory, "liquid-slider-pressed.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        slider.performTouchInput { up() }
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun liquidSliderSupportsKeyboardAndRtlTouch() {
        val value = mutableFloatStateOf(1f)
        var saves = 0
        compose.setContent {
            FnMusicTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    LiquidGlassSettingsScreen(value.floatValue, null, { value.floatValue = it }, { saves++ }, {})
                }
            }
        }
        val slider = compose.onNodeWithTag("liquid-glass-slider").performScrollTo()
        slider.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        slider.performKeyInput { pressKey(Key.DirectionLeft) }
        compose.runOnIdle { assertTrue(value.floatValue > 1f); assertEquals(1, saves) }
        compose.runOnIdle { value.floatValue = 1f; saves = 0 }
        slider.performTouchInput { swipe(center, Offset(width * 0.15f, centerY)) }
        compose.runOnIdle { assertTrue(value.floatValue > 1f); assertEquals(1, saves) }
    }

    @Test fun introductionScrollsBehindFixedGlassControlsWithoutScrollingSettings() {
        compose.setContent {
            FnMusicTheme {
                FnGradientBackground { LiquidGlassSettingsScreen(1f, null, {}, {}, {}) }
            }
        }
        compose.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("较清晰").assertDoesNotExist()
        compose.onNodeWithText("较模糊").assertDoesNotExist()
        compose.onNodeWithText("仅调整玻璃控件", substring = true).assertDoesNotExist()
        compose.onNodeWithText("透明").assertIsDisplayed()
        compose.onNodeWithText("色调").assertIsDisplayed()
        compose.onNodeWithText("飞牛音乐").assertIsDisplayed()
        compose.onNodeWithText("歌曲名").assertIsDisplayed()
        compose.onNodeWithText("歌手").assertIsDisplayed()
        compose.onNodeWithContentDescription("应用图标", useUnmergedTree = true).assertIsDisplayed()
        val control = compose.onNodeWithTag("liquid-glass-preview-player")
        val top = control.fetchSemanticsNode().boundsInRoot.top
        val outer = compose.onNodeWithTag("liquid-glass-page")
        val outerScroll = outer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        val preview = compose.onNodeWithTag("liquid-glass-preview-scroll")
        preview.performTouchInput { swipe(Offset(centerX, height * 0.6f), Offset(centerX, height * 0.2f), 400) }
        compose.waitForIdle()
        assertTrue(preview.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
        assertEquals(outerScroll, outer.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value(), 0f)
        assertEquals(top, control.fetchSemanticsNode().boundsInRoot.top, 1f)
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir("glass-screenshots")!!
        java.io.File(directory, "preview-scrolled.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun saveErrorCanBeRetried() {
        var saves = 0
        compose.setContent {
            FnMusicTheme { LiquidGlassSettingsScreen(1.5f, "保存失败", {}, { saves++ }, {}) }
        }
        compose.onNodeWithText("重试保存").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, saves) }
    }

    @Test fun narrowLargeFontSettingsRetainAllActions() {
        var cache = 0L
        var opened = false
        var loggedOut = false
        compose.setContent {
            FnMusicTheme {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                    Box(Modifier.width(320.dp)) {
                        SettingsScreen(MusicUiState(), { cache = it }, { loggedOut = true }, { opened = true }, {})
                    }
                }
            }
        }
        compose.onAllNodes(hasText("%", substring = true)).assertCountEquals(0)
        compose.onNodeWithText("模糊程度", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Liquid Glass").performScrollTo().performClick()
        compose.onNodeWithText("2 GiB").performScrollTo().performClick()
        compose.onNodeWithText("退出并清除凭据").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(opened)
            assertTrue(loggedOut)
            assertEquals(2_048L * 1024 * 1024, cache)
        }
    }
}
