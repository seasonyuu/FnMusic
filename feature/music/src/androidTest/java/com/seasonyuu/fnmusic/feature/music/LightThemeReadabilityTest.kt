package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.ThemeColorPreference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LightThemeReadabilityTest {
    @get:Rule val compose = createComposeRule()

    private fun textColor(label: String): Color {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single().layoutInput.style.color
    }

    private fun textLayout(label: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        return layouts.single()
    }

    @Test fun nestedDarkSurfaceOverridesInheritedLightText() {
        compose.setContent { FnMusicTheme(darkTheme = false) {
            CompositionLocalProvider(LocalContentColor provides FnTextPrimary) {
                FnMusicTheme(darkTheme = true) { Text("专辑中的歌曲名") }
            }
        } }
        assertEquals(FnDarkPalette.primary, textColor("专辑中的歌曲名"))
    }

    @Test fun badgeAndPlayLabelsStayReadableForEveryAccent() {
        var accent by mutableStateOf(ThemeColorPreference.Default)
        compose.setContent { FnMusicTheme(darkTheme = false, accent = Color(accent.argb)) {
            Column { UserRoleBadge(); CollectionPlayButton(true, {}) }
        } }
        ThemeColorPreference.entries.forEach { choice ->
            compose.runOnIdle { accent = choice }
            assertEquals(FnLightPalette.primary, textColor("管理员"))
            assertEquals(FnLightPalette.primary, textColor("播放全部"))
        }
    }


    @Test fun realMiniPlayerUsesLocalForegroundForTitle() {
        val track = com.seasonyuu.fnmusic.core.model.Track(
            com.seasonyuu.fnmusic.core.model.TrackId("contrast"),
            "迷你播放器标题",
            artists = listOf(com.seasonyuu.fnmusic.core.model.Artist(
                com.seasonyuu.fnmusic.core.model.ArtistId("contrast-artist"), "歌手",
            )),
        )
        val player = com.seasonyuu.fnmusic.core.model.PlayerState(
            queue = listOf(com.seasonyuu.fnmusic.core.model.PlayableTrack(track, "https://music.invalid/stream")), currentIndex = 0)
        compose.setContent { FnMusicTheme(darkTheme = false) {
            CompositionLocalProvider(LocalContentColor provides FnTextPrimary) {
                FnMusicTheme(darkTheme = true) { MiniPlayer(player, {}, {}, {}) }
            }
        } }
        assertEquals(FnDarkPalette.primary, textColor("迷你播放器标题"))
        val title = textLayout("迷你播放器标题")
        val artist = textLayout("歌手")
        assertEquals("Mini player title and artist use the same text size", artist.layoutInput.style.fontSize, title.layoutInput.style.fontSize)
        assertEquals(FontWeight.SemiBold, title.layoutInput.style.fontWeight)
        assertEquals(FnDarkPalette.primary, title.layoutInput.style.color)
        assertEquals(FnDarkPalette.secondary, artist.layoutInput.style.color)
    }

    @Test fun focusedInputAndTextActionUseNeutralText() {
        compose.setContent { FnMusicTheme(darkTheme = false, accent = Color(0xFF6BAB45)) {
            Column {
                OutlinedTextField("", {}, label = { Text("名称") }, colors = readableTextFieldColors())
                TextButton({}, colors = readableTextButtonColors()) { Text("重试") }
            }
        } }
        compose.onNodeWithText("名称").performClick()
        assertEquals(FnLightPalette.primary, textColor("名称"))
        assertEquals(FnLightPalette.primary, textColor("重试"))
    }

    @Test fun lightSkeletonHasVisiblePlaceholderPixels() {
        compose.setContent { FnMusicTheme(darkTheme = false) {
            Box(Modifier.width(320.dp).background(FnSurface).testTag("skeleton")) {
                HomeSectionContent(MusicUiState(loading = true), CatalogSection.Tracks, false, {}) {}
            }
        } }
        val image = compose.onNodeWithTag("skeleton").captureToImage()
        val pixels = image.toPixelMap()
        val base = FnLightPalette.surface
        var changed = 0
        for (y in 0 until pixels.height step 4) for (x in 0 until pixels.width step 4) {
            if (contrastRatio(pixels[x, y], base) > 1.1f) changed++
        }
        assertTrue("Skeleton must remain visible on a light surface", changed > 50)

    }

    @Test fun glassFallbackUsesLocalSurface() {
        var surface = Color.Unspecified
        compose.setContent { FnMusicTheme(darkTheme = false) {
            CompositionLocalProvider(LocalLiquidGlassEnabled provides false) {
                surface = currentLiquidGlassMaterial().baseSurface
            }
        } }
        compose.runOnIdle { assertEquals(FnLightPalette.navigation, surface) }
    }
}
