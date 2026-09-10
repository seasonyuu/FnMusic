package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.runtime.compositionLocalOf
import com.seasonyuu.fnmusic.core.model.LiquidGlassBlur

// Saved slider value, retained for preference compatibility; resolve it through currentLiquidGlassMaterial.
val LocalLiquidGlassBlur = compositionLocalOf { LiquidGlassBlur.Default }

val LocalLiquidGlassEnabled = compositionLocalOf { true }
