package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.*
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TrackMoreLiquidMenuTest {
    @get:Rule val compose = createComposeRule()
    private var track by mutableStateOf(Track(TrackId("one"), "第一首"))
    private var mounted by mutableStateOf(true)
    private var enabled by mutableStateOf(true)
    private var favorite by mutableStateOf(false)
    private var pageSurface by mutableStateOf(Color.Unspecified)
    private val calls = mutableListOf<String>()

    private fun fixture(playerSurface: Color? = null, glass: Boolean = true) {
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost {
                    val backdrop = rememberLayerBackdrop()
                    CompositionLocalProvider(LocalLiquidMenuSurfaceColor provides pageSurface,
                        LocalLiquidGlassEnabled provides glass, LocalTrackMenuActions provides { current, _ ->
                        listOf(TrackMenuAction(LiquidMenuItem("favorite", if (favorite) "取消收藏" else "收藏",
                            icon = { Icon(Icons.Rounded.Favorite, null) })) { calls += current.id.value })
                    }) {
                        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.DarkGray))
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopEnd) {
                            if (mounted) TrackMoreMenu(track, surfaceColor = playerSurface ?: LocalLiquidMenuSurfaceColor.current, backdrop = backdrop, enabled = enabled,
                                modifier = Modifier.size(48.dp).testTag("anchor"))
                        }
                    }
                }
            }
        }
    }

    @Test fun changingTrackRetiresOldSessionAndUsesNewCallback() {
        fixture()
        compose.onNodeWithTag("anchor").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.Role, androidx.compose.ui.semantics.Role.Button))
        compose.onNodeWithTag("anchor").performClick()
        compose.onNodeWithTag("liquid-menu-overlay").assertExists()
        compose.runOnIdle { track = Track(TrackId("two"), "第二首") }
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        compose.onNodeWithTag("anchor").performClick()
        compose.onNodeWithTag("liquid-menu-item-favorite").performClick()
        assertEquals(listOf("two"), calls)
    }

    @Test fun disablingAndReenablingDoesNotReopenOldSession() {
        fixture()
        compose.onNodeWithTag("anchor").performClick()
        compose.runOnIdle { enabled = false }
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        compose.runOnIdle { enabled = true }
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        compose.onNodeWithTag("anchor").performClick()
        compose.onNodeWithTag("liquid-menu-overlay").assertExists()
    }

    @Test fun stateUpdatesAndUnmountLeavesNoModalOverlay() {
        fixture()
        compose.onNodeWithTag("anchor").performClick()
        compose.onNodeWithText("收藏").assertExists()
        compose.runOnIdle { favorite = true }
        compose.onNodeWithText("取消收藏").assertExists()
        compose.onAllNodesWithTag("liquid-menu-item-favorite").assertCountEquals(1)
        compose.runOnIdle { mounted = false }
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)
    }
    private fun assertSurface(expected: Color) {
        val pixels = compose.onNodeWithTag("liquid-menu-solid").captureToImage().toPixelMap()
        val actual = pixels[pixels.width / 2, 8]
        assertEquals(expected.red, actual.red, .01f)
        assertEquals(expected.green, actual.green, .01f)
        assertEquals(expected.blue, actual.blue, .01f)
    }

    @Test fun openMenuTracksPageTintChanges() {
        pageSurface = Color(0xFF243F62)
        fixture(glass = false)
        compose.onNodeWithTag("anchor").performClick()
        assertSurface(pageSurface)
        compose.runOnIdle { pageSurface = Color(0xFF63352A) }
        assertSurface(pageSurface)
    }

    @Test fun artworkTintOverridesTheBackgroundPageTint() {
        pageSurface = Color.Red
        val artworkTint = Color(0xFF23452B)
        fixture(playerSurface = artworkTint, glass = false)
        compose.onNodeWithTag("anchor").performClick()
        assertSurface(artworkTint)
        compose.runOnIdle { pageSurface = Color.Blue }
        assertSurface(artworkTint)
    }

    @Test fun artworkToneRetainsHueAndKeepsWhiteTextReadable() {
        for (argb in listOf(android.graphics.Color.RED, android.graphics.Color.BLUE, android.graphics.Color.WHITE)) {
            val bitmap = android.graphics.Bitmap.createBitmap(32, 32, android.graphics.Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(argb)
            val tone = coverTone(bitmap)
            org.junit.Assert.assertTrue(contrastRatio(Color.White, tone) >= 4.5f)
            if (argb == android.graphics.Color.RED) org.junit.Assert.assertTrue(tone.red > tone.blue)
            if (argb == android.graphics.Color.BLUE) org.junit.Assert.assertTrue(tone.blue > tone.red)
            bitmap.recycle()
        }
    }

}
