/*
 * Adapted from Kyant0's AndroidLiquidGlass catalog LiquidSlider (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidSlider.kt
 * Changes: Material input/semantics, application colors, blur preference, and track-only capture.
 * License: res/raw/android_liquid_glass_license.txt
 */
package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/** Catalog glass rendering with standard slider touch, keyboard, RTL, and accessibility behavior. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val scope = rememberCoroutineScope()
    val motion = remember(scope) {
        DampedDragAnimation(
            animationScope = scope,
            initialValue = value,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStopped = {},
            onDrag = { _, _ -> },
        )
    }
    LaunchedEffect(value) {
        if (motion.targetValue != value) motion.updateValue(value)
    }
    LaunchedEffect(pressed, dragged) {
        if (pressed || dragged) motion.press() else motion.release()
    }
    val glass = currentLiquidGlassMaterial()
    val trackBackdrop = rememberLayerBackdrop()
    // This control sits on a uniform card. Capture only its track, never its own thumb.
    val thumbBackdrop = rememberBackdrop(trackBackdrop) { drawBackdrop ->
        val progress = motion.pressProgress
        scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) { drawBackdrop() }
    }
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier.fillMaxWidth().height(56.dp),
        track = { state ->
            Box(
                Modifier.fillMaxWidth().height(6.dp).layerBackdrop(trackBackdrop)
                    .clip(Capsule()).background(Color(0xFF787880).copy(alpha = 0.36f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.fillMaxWidth(state.value.coerceIn(0f, 1f)).height(6.dp).background(FnAccent, Capsule()))
            }
        },
        thumb = {
            Box(
                Modifier.drawBackdrop(
                    backdrop = thumbBackdrop,
                    shape = { Capsule() },
                    effects = {
                        val progress = motion.pressProgress
                        blur(4.dp.toPx() * (1f - progress) * glass.blurScale)
                        lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = motion.pressProgress,
                        )
                    },
                    shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = { InnerShadow(radius = 4.dp * motion.pressProgress, alpha = motion.pressProgress) },
                    layerBlock = {
                        scaleX = motion.scaleX
                        scaleY = motion.scaleY
                        val velocity = if (pressed || dragged) motion.velocity / 10f else 0f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - motion.pressProgress)) },
                ).size(40.dp, 24.dp),
            )
        },
    )
}
