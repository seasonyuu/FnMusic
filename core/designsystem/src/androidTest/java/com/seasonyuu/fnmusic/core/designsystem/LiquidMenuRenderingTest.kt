package com.seasonyuu.fnmusic.core.designsystem

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.backdrop.backdrops.*
import java.io.File
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LiquidMenuRenderingTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun shaderContourMatchesIndependentDistanceOracleAtKeyFrames() {
        assumeTrue("Full shader requires API 33", Build.VERSION.SDK_INT >= 33)
        var progress by mutableFloatStateOf(0f)
        val anchor = Rect(264f, 32f, 312f, 80f)
        val target = Rect(112f, 32f, 312f, 256f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                FnMusicTheme(darkTheme = true) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.size(360.dp, 420.dp).testTag("scene")) {
                        Box(
                            Modifier.matchParentSize()
                                .layerBackdrop(backdrop)
                                .background(Color.White)
                        )
                        LiquidMenuSurface(backdrop, menuBlobs(anchor, target, progress, 1f))
                    }
                }
            }
        }
        for (p in listOf(0f, .08f, .2f, .4f, .7f, 1f, 1.03f, -.02f, 0f)) {
            compose.runOnIdle { progress = p }
            compose.waitForIdle()
            compose.onNodeWithTag("liquid-menu-shader").assertExists()
            val image = compose.onNodeWithTag("scene").captureToImage()
            save("contour-$p", image)
            val pixels = image.toPixelMap()
            val blobs = menuBlobs(anchor, target, p, 1f)
            var inside = 0
            // CPU oracle intentionally uses Double math and polynomial smooth union independently.
            fun sdf(x: Double, y: Double, r: Rect, radius: Float): Double {
                val qx = abs(x - r.center.x) - r.width / 2 + radius
                val qy = abs(y - r.center.y) - r.height / 2 + radius
                return hypot(max(0.0, qx), max(0.0, qy)) + min(0.0, max(qx, qy)) - radius
            }
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val a = sdf(x + .5, y + .5, blobs.anchor, blobs.anchorRadius)
                val b = sdf(x + .5, y + .5, blobs.body, blobs.radius)
                val k = blobs.blend.toDouble()
                val h = if (k > 0) max(0.0, k - abs(a - b)) / k else 0.0
                val d =
                    if (blobs.anchor.width < 1 || blobs.anchor.height < 1) b
                    else min(a, b) - k * h * h / 4
                val drawn = pixels[x, y].red < .85f
                if (d < -2) {
                    inside++
                    assertTrue("Missing interior p=$p at $x,$y d=$d", drawn)
                }
                if (d > 2) assertFalse("Rectangular leak p=$p at $x,$y d=$d", drawn)
            }
            assertTrue("Nonempty liquid shape", inside > 100)
        }
    }

    @Test
    fun backdropMovesWithoutSamplingMenuOrTintingOutside() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        var color by mutableStateOf(Color.Red)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                FnMusicTheme(darkTheme = true) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.size(320.dp).testTag("scene")) {
                        Box(Modifier.matchParentSize().layerBackdrop(backdrop).background(color))
                        LiquidMenuSurface(
                            backdrop,
                            menuBlobs(
                                Rect(240f, 20f, 288f, 68f),
                                Rect(88f, 20f, 288f, 240f),
                                1f,
                                1f,
                            ),
                        )
                    }
                }
            }
        }
        val red = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[180, 150]
        compose.runOnIdle { color = Color.Blue }
        val blue = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[180, 150]
        assertTrue(red.red > blue.red + .2f)
        assertTrue(blue.blue > red.blue + .2f)
        val outside = compose.onNodeWithTag("scene").captureToImage().toPixelMap()[10, 150]
        assertEquals(1f, outside.blue, .01f)
        assertEquals(0f, outside.red, .01f)
    }

    private fun save(name: String, image: ImageBitmap) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.getExternalFilesDir(null), "liquid-menu").apply { mkdirs() }
        val actual = image.asAndroidBitmap()
        File(dir, "$name.png").outputStream().use {
            actual.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        val path = "liquid-menu/api-${Build.VERSION.SDK_INT}/$name.png"
        val reference =
            try {
                assets.open(path).use { BitmapFactory.decodeStream(it) }
            } catch (_: java.io.FileNotFoundException) {
                null
            }
        if (reference == null) {
            assertFalse(
                "Missing screenshot baseline: $path",
                InstrumentationRegistry.getArguments().getString("requireLiquidMenuBaselines") ==
                    "true",
            )
            return
        }
        assertEquals(reference.width, actual.width)
        assertEquals(reference.height, actual.height)
        val diff = Bitmap.createBitmap(actual.width, actual.height, Bitmap.Config.ARGB_8888)
        var mismatches = 0
        for (y in 0 until actual.height) for (x in 0 until actual.width) {
            val a = actual.getPixel(x, y)
            val b = reference.getPixel(x, y)
            val changed =
                listOf(0, 8, 16, 24).any { shift ->
                    abs(((a ushr shift) and 255) - ((b ushr shift) and 255)) > 8
                }
            if (changed) mismatches++
            diff.setPixel(
                x,
                y,
                if (changed) android.graphics.Color.MAGENTA else android.graphics.Color.TRANSPARENT,
            )
        }
        File(dir, "$name-diff.png").outputStream().use {
            diff.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        assertTrue(
            "Screenshot drift: $mismatches pixels ($path)",
            mismatches <= actual.width * actual.height * .005,
        )
    }
}
