package com.seasonyuu.fnmusic.core.designsystem

import com.seasonyuu.fnmusic.core.model.LiquidGlassBlur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiquidGlassMaterialTest {
    @Test fun clearThroughDefaultKeepsBlurZeroWhileTintIncreases() {
        var previousAlpha = 0f
        for (step in 0..50) {
            val material = resolveLiquidGlassMaterial(LiquidGlassBlur.fromSlider(step / 100f))
            assertEquals(0f, material.blurScale, 0f)
            assertTrue(material.surfaceAlpha >= previousAlpha)
            previousAlpha = material.surfaceAlpha
        }
        assertEquals(0.25f, resolveLiquidGlassMaterial(0.1f).surfaceAlpha, 0f)
        assertEquals(0.55f, resolveLiquidGlassMaterial(1f).surfaceAlpha, 0f)
    }

    @Test fun tintedHalfIncreasesBothBlurAndOpacityContinuously() {
        val middle = resolveLiquidGlassMaterial(LiquidGlassBlur.fromSlider(0.75f))
        assertEquals(1f, middle.blurScale, 0f)
        assertEquals(0.575f, middle.surfaceAlpha, 0.00001f)
        val end = resolveLiquidGlassMaterial(2f)
        assertEquals(2f, end.blurScale, 0f)
        assertEquals(0.6f, end.surfaceAlpha, 0.00001f)
        val below = resolveLiquidGlassMaterial(LiquidGlassBlur.fromSlider(0.49999f))
        val above = resolveLiquidGlassMaterial(LiquidGlassBlur.fromSlider(0.50001f))
        assertEquals(below.surfaceAlpha, above.surfaceAlpha, 0.0001f)
        assertEquals(below.blurScale, above.blurScale, 0.0001f)
    }

    @Test fun brightnessStaysZeroAcrossSlider() {
        for (position in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val material = resolveLiquidGlassMaterial(LiquidGlassBlur.fromSlider(position))
            assertEquals(0f, material.brightness, 0f)
        }
    }

    @Test fun existingPreferencesAndInvalidValuesResolveSafely() {
        assertEquals(0f, resolveLiquidGlassMaterial(0.25f).blurScale, 0f)
        assertEquals(resolveLiquidGlassMaterial(1f), resolveLiquidGlassMaterial(Float.NaN))
        assertEquals(resolveLiquidGlassMaterial(0.1f), resolveLiquidGlassMaterial(-1f))
        assertEquals(resolveLiquidGlassMaterial(2f), resolveLiquidGlassMaterial(100f))
    }
}
