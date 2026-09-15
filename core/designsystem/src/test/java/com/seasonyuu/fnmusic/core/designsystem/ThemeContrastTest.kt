package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.seasonyuu.fnmusic.core.model.ThemeColorPreference
import org.junit.Assert.*
import org.junit.Test

class ThemeContrastTest {
    @Test fun officialFillsUseReadableForegroundWithoutChangingAccent() {
        ThemeColorPreference.entries.forEach {
            val accent = Color(it.argb)
            assertTrue(it.id, contrastRatio(contrastingForeground(accent), accent) >= 4.5f)
        }
    }

    @Test fun neutralTextRemainsReadableOnActualBadgeAndPlaySurfaces() {
        listOf(FnLightPalette, FnDarkPalette).forEach { palette ->
            ThemeColorPreference.entries.forEach {
                val badge = Color(it.argb).copy(alpha = .14f).compositeOver(palette.surface)
                val play = palette.primary.copy(alpha = .06f).compositeOver(palette.surface)
                assertTrue(it.id, contrastRatio(palette.primary, badge) >= 4.5f)
                assertTrue(contrastRatio(palette.primary, play) >= 4.5f)
            }
        }
    }
}
