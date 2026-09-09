package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LyricsPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingFontScaleMatchesFreshLyricsMeasurement() {
        val scale = mutableFloatStateOf(1f)
        val text = "歌词会在字号变化后正确换行"
        val timeline = resolveLyricTimeline(listOf(LyricLine(0, text)), 0, 10_000)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.floatValue)) {
                androidx.compose.foundation.layout.Column {
                    AccompanistLyricText(text, timeline, { 5_000L }, 1f, Color.Gray, Color.White,
                        Modifier.width(220.dp).testTag("retained-line"))
                    key(scale.floatValue) {
                        AccompanistLyricText(text, timeline, { 5_000L }, 1f, Color.Gray, Color.White,
                            Modifier.width(220.dp).testTag("fresh-line"))
                    }
                }
            }
        }
        for (fontScale in listOf(1.3f, 2f, 1f)) {
            compose.runOnIdle { scale.floatValue = fontScale }
            val retained = compose.onNodeWithTag("retained-line").fetchSemanticsNode().boundsInRoot.height
            val fresh = compose.onNodeWithTag("fresh-line").fetchSemanticsNode().boundsInRoot.height
            assertEquals("Changing font scale must refresh cached line heights", fresh, retained, 1f)
        }
    }

    @Test fun highlightingNeverChangesMultilineLayout() {
        val active = mutableFloatStateOf(0f)
        val text = "你好，世界 👩🏽‍💻 Hello music\n让每一个音符留下来"
        val timeline = resolveLyricTimeline(listOf(LyricLine(0, text)), 0, 10_000)
        compose.setContent {
            AccompanistLyricText(text, timeline, { 5_000L }, active.floatValue,
                Color.Gray, Color.White, Modifier.width(220.dp).testTag("line"))
        }
        val before = compose.onNodeWithTag("line").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { active.floatValue = 1f }
        val after = compose.onNodeWithTag("line").fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        compose.onNodeWithText(text).assertExists()
    }

    @Test fun highlightAdvancesAcrossWrappedLinesAtLargeFontScale() {
        val position = mutableLongStateOf(0)
        val text = "你好，世界 👩🏽‍💻 Hello music"
        val timeline = resolveLyricTimeline(listOf(LyricLine(0, text)), 0, 10_000)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                AccompanistLyricText(text, timeline, { position.longValue }, 1f,
                    Color.Gray, Color.White, Modifier.width(220.dp).background(Color.Black).testTag("line"))
            }
        }
        fun brightPixels(): Int {
            val pixels = compose.onNodeWithTag("line").captureToImage().toPixelMap()
            var count = 0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val color = pixels[x, y]
                if (color.red > 0.9f && color.green > 0.9f && color.blue > 0.9f) count++
            }
            return count
        }
        val initial = brightPixels()
        compose.runOnIdle { position.longValue = 5_000 }
        val partial = brightPixels()
        compose.runOnIdle { position.longValue = 10_000 }
        val complete = brightPixels()
        assertTrue("前半句应出现高亮", partial > initial + 50)
        assertTrue("换行后的文字应继续高亮", complete > partial + 50)
    }

    @Test fun completedLineFadesContinuouslyInsteadOfResettingItsHighlight() {
        val active = mutableFloatStateOf(1f)
        val text = "唱完这一句"
        val timeline = resolveLyricTimeline(listOf(LyricLine(0, text)), 0, 10_000)
        compose.setContent {
            AccompanistLyricText(text, timeline, { 10_000L }, active.floatValue,
                Color.White.copy(alpha = 0.3f), Color.White,
                Modifier.width(240.dp).background(Color.Black).testTag("fading-line"))
        }
        fun luminance(): Double {
            val pixels = compose.onNodeWithTag("fading-line").captureToImage().toPixelMap()
            var total = 0.0
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) total += pixels[x, y].red
            return total
        }
        val focused = luminance()
        compose.runOnIdle { active.floatValue = 0.5f }
        val halfway = luminance()
        compose.runOnIdle { active.floatValue = 0f }
        val resting = luminance()
        assertTrue("退出过程中应逐渐变暗", halfway < focused * 0.85)
        assertTrue("退出动画结束前不应提前清空高亮", halfway > resting * 1.25)
        assertTrue("退出后仍应保留可读的歌词", resting > focused * 0.2)
    }

    @Test fun interpolatedClockFreezesAndResetsOnPauseSeekAndTrackChange() {
        val sample = mutableLongStateOf(1_000)
        val playing = mutableStateOf(true)
        val track = mutableStateOf("a")
        var position: State<Long>? = null
        compose.mainClock.autoAdvance = false
        compose.setContent { position = rememberLyricPosition(track.value, sample.longValue, 10_000, playing.value) }
        compose.mainClock.advanceTimeBy(250)
        compose.runOnIdle { assertTrue(position!!.value > 1_100) }
        compose.runOnIdle { playing.value = false; sample.longValue = 1_250 }
        compose.mainClock.advanceTimeBy(1_000)
        compose.runOnIdle { assertEquals(1_250L, position!!.value); sample.longValue = 7_000 }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(7_000L, position!!.value); track.value = "b"; sample.longValue = 0 }
        compose.mainClock.advanceTimeByFrame()
        compose.runOnIdle { assertEquals(0L, position!!.value) }
    }
}
