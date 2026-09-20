package com.seasonyuu.fnmusic.core.designsystem

import android.os.Build
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LiquidMenuAnchorTest {
    @get:Rule val compose = createComposeRule()
    private var expanded by mutableStateOf(false)
    private var offset by mutableStateOf(0)
    private var mounted by mutableStateOf(true)
    private var label by mutableStateOf("Sort")
    private var dark by mutableStateOf(true)
    private var glass by mutableStateOf(true)
    private var width by mutableStateOf(48)
    private var roundRect by mutableStateOf(false)
    private var custom by mutableStateOf(false)
    private var dismissals = 0
    private var compositions = 0
    private var lastForegroundBounds = Rect.Zero

    private fun fixture(attached: Boolean) {
        compose.setContent {
            FnMusicTheme(darkTheme = dark) {
                CompositionLocalProvider(LocalLiquidGlassEnabled provides glass) {
                    LiquidMenuHost(Modifier.testTag("host")) {
                        val backdrop = rememberLayerBackdrop()
                        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.DarkGray))
                        Box(Modifier.fillMaxSize().padding(top = 60.dp, start = offset.dp), contentAlignment = Alignment.TopCenter) {
                            if (mounted) LiquidMenu(
                                expanded, { expanded = false; dismissals++ }, backdrop,
                                listOf(LiquidMenuItem("one", "One"), LiquidMenuItem("two", "Two")), {},
                                transition = if (attached) LiquidMenuTransition.Attached else LiquidMenuTransition.Detached,
                                onExpandedChange = { expanded = it },
                                trigger = { toggle ->
                                    if (attached) {
                                        val shape = if (roundRect) LiquidMenuAnchorShape.RoundedRectangle(8.dp) else LiquidMenuAnchorShape.Capsule
                                        // The larger touch target must not become the visible morph surface.
                                        Box(Modifier.size(width.dp + 16.dp, 64.dp).clickable(interactionSource = null, indication = null, onClick = toggle).testTag("trigger"), contentAlignment = Alignment.Center) {
                                            val content: @Composable RowScope.() -> Unit = {
                                                DisposableEffect(Unit) { compositions++; onDispose { compositions-- } }
                                                Box(Modifier.size(12.dp).background(Color.Magenta))
                                                if (width > 48) Text(label, color = Color.Magenta)
                                            }
                                            if (roundRect) {
                                                Row(Modifier.size(width.dp, 48.dp).then(surfaceModifier(shape))
                                                    .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                                    Row(foregroundModifier, verticalAlignment = Alignment.CenterVertically, content = content)
                                                }
                                            } else {
                                                LiquidButton(toggle, backdrop, Modifier.size(width.dp, 48.dp).then(surfaceModifier()),
                                                    foregroundModifier = foregroundModifier, content = content)
                                            }
                                        }
                                    } else {
                                        Text(label, Modifier.testTag("trigger").clickable(interactionSource = null, indication = null, onClick = toggle)
                                            .then(if (custom) Modifier.background(Color.Blue, CutCornerShape(8.dp)).padding(12.dp) else Modifier), color = Color.Magenta)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun foregroundPixels(artifact: String? = null): Int {
        val image = compose.onNodeWithTag("host").captureToImage()
        if (artifact != null) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val dir = File(context.getExternalFilesDir(null), "liquid-menu").apply { mkdirs() }
            File(dir, "$artifact.png").outputStream().use {
                image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        val pixels = image.toPixelMap()
        var count = 0
        var left = pixels.width; var right = 0; var top = pixels.height; var bottom = 0
        for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
            val c = pixels[x, y]
            if (c.red > .8f && c.blue > .8f && c.green < .2f) {
                count++
                left = minOf(left, x); right = maxOf(right, x)
                top = minOf(top, y); bottom = maxOf(bottom, y)
            }
        }
        lastForegroundBounds = if (count == 0) Rect.Zero else Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        return count
    }

    @Test fun closingForegroundReturnsBeforeSpringFinishesForAllSupportedAnchors() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        fixture(attached = true)
        for (theme in listOf(true, false)) for (variant in 0..2) {
            compose.runOnIdle { dark = theme; width = if (variant == 0) 48 else 144; roundRect = variant == 2 }
            val original = foregroundPixels()
            val originalCenter = lastForegroundBounds.center
            assertTrue(original > 0)
            assertEquals(1, compositions)
            compose.onNodeWithTag("trigger").performClick()
            compose.waitForIdle()
            assertEquals(0, foregroundPixels())
            compose.mainClock.autoAdvance = false
            compose.runOnIdle { expanded = false }
            var returnedWhileActive = false
            var recovered = false
            var framesAfterRecovery = 0
            repeat(75) {
                compose.mainClock.advanceTimeByFrame()
                val count = foregroundPixels(if (it in listOf(0, 16, 24, 32, 48, 64)) "anchor-$theme-$variant-$it" else null)
                val active = compose.onAllNodesWithTag("liquid-menu-overlay").fetchSemanticsNodes().isNotEmpty()
                if (count >= original * .8f && active) { returnedWhileActive = true; recovered = true }
                if (it >= 48 && count > 0) {
                    assertEquals("Foreground horizontal alignment", originalCenter.x, lastForegroundBounds.center.x, 2f)
                    assertEquals("Foreground vertical alignment", originalCenter.y, lastForegroundBounds.center.y, 2f)
                }
                if (recovered) {
                    assertTrue("Foreground vanished during handoff: variant=$variant frame=$it", count >= original * .6f)
                    assertTrue("Duplicate foreground: variant=$variant frame=$it", count <= original * 1.25f)
                    framesAfterRecovery++
                }
            }
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
            assertTrue("Must restore before animation completion", returnedWhileActive)
            assertTrue(framesAfterRecovery > 2)
            assertEquals(original, foregroundPixels())
            assertEquals(1, compositions)
        }
    }

    @Test fun detachedAndFallbackKeepTriggerVisibleAndDismissOnMovement() {
        fixture(attached = false)
        for (enabled in listOf(true, false)) for (shaped in listOf(false, true)) {
            compose.runOnIdle { glass = enabled; custom = shaped }
            val original = foregroundPixels("detached-$enabled-$shaped-before")
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("trigger").performClick()
            compose.mainClock.advanceTimeBy(80)
            assertEquals("enabled=$enabled shaped=$shaped", original, foregroundPixels("detached-$enabled-$shaped-during"))
            compose.onNodeWithTag("liquid-menu-foreground").assertDoesNotExist()
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            // Move the anchor and verify immediate cleanup, without a phantom return animation.
            compose.runOnIdle { offset += 20 }
            compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
            assertTrue(foregroundPixels() > 0)
        }
        assertEquals(4, dismissals)
    }

    @Test fun attachedFallbackMorphsAndRestoresItsForeground() {
        glass = false
        fixture(attached = true)
        for (enabled in if (Build.VERSION.SDK_INT < 33) listOf(false, true) else listOf(false)) {
            compose.runOnIdle { glass = enabled }
            val original = foregroundPixels()
            val surfaceTag = if (enabled && Build.VERSION.SDK_INT >= 31) "liquid-menu-blur" else "liquid-menu-solid"
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("trigger").performClick()
            compose.mainClock.advanceTimeBy(80)
            compose.onNodeWithTag("liquid-menu-foreground").assertExists()
            val opening = compose.onNodeWithTag(surfaceTag).fetchSemanticsNode().boundsInRoot
            compose.mainClock.advanceTimeBy(2_000)
            val opened = compose.onNodeWithTag(surfaceTag).fetchSemanticsNode().boundsInRoot
            assertTrue("Fallback expands from the anchor", opening.width < opened.width)
            assertEquals(0, foregroundPixels())
            compose.runOnIdle { expanded = false }
            compose.mainClock.advanceTimeBy(160)
            val closing = compose.onNodeWithTag(surfaceTag).fetchSemanticsNode().boundsInRoot
            assertTrue("Fallback contracts toward the anchor", closing.width < opened.width)
            compose.onNodeWithTag("liquid-menu-foreground").assertExists()
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
            assertEquals(original, foregroundPixels())
        }
    }

    @Test fun recordedContentUpdatesWhileHiddenAndRapidReversalCanUnmount() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        width = 144
        fixture(attached = true)
        compose.runOnIdle { label = "MMMM" }
        val updated = foregroundPixels()
        compose.runOnIdle { label = "I" }
        val old = foregroundPixels()
        assertTrue(updated > old * 1.3f)
        compose.onNodeWithTag("trigger").performClick()
        compose.runOnIdle { label = "MMMM" }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("liquid-menu-overlay").assertExists()
        val returning = foregroundPixels("updated-foreground-return")
        assertTrue("Replay must contain the updated label", returning > updated * .85f && returning < updated * 1.15f)
        compose.runOnIdle { expanded = true }
        compose.mainClock.advanceTimeBy(1500)
        assertEquals(1, compositions)
        compose.runOnIdle { mounted = false }
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        assertEquals(0, compositions)
    }
}
