/*
 * Adapted from Kyant0's AndroidLiquidGlass catalog LiquidToggle (Apache-2.0):
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/kmp/app/src/commonMain/kotlin/com/kyant/backdrop/catalog/components/LiquidToggle.kt
 * Changes: standard toggle/drag semantics, app accent, global glass fallback, and minimum touch target.
 * License: res/raw/android_liquid_glass_license.txt
 */
package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule

/** Controlled switch: input remains available when the global glass material is off. */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val glass = currentLiquidGlassMaterial()
    val backdrop = LocalFnBackdrop.current
    val trackBackdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()
    val currentChecked by rememberUpdatedState(checked)
    val onChange by rememberUpdatedState(onCheckedChange)
    val motion = remember(scope) {
        DampedDragAnimation(scope, if (checked) 1f else 0f, 0f..1f, 0.001f, 1f, 1.5f, {}, { _, _ -> })
    }
    val interactions = remember { MutableInteractionSource() }
    val pressed by interactions.collectIsPressedAsState()
    var dragging by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val dragWidth = with(LocalDensity.current) { 20.dp.toPx() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    LaunchedEffect(checked, dragging) {
        if (!dragging) {
            fraction = if (checked) 1f else 0f
            motion.updateValue(fraction)
        }
    }
    LaunchedEffect(pressed, dragging) {
        if (pressed || dragging) motion.press() else motion.release()
    }
    val dragState = rememberDraggableState { delta ->
        fraction = (fraction + delta / dragWidth).coerceIn(0f, 1f)
        motion.updateValue(fraction)
    }
    Box(
        modifier.size(64.dp, 48.dp).graphicsLayer { alpha = if (enabled) 1f else 0.38f }
            .toggleable(checked, interactions, null, enabled, Role.Switch) { onChange(it) }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                reverseDirection = rtl,
                onDragStarted = {
                    fraction = if (currentChecked) 1f else 0f
                    dragging = true
                },
                onDragStopped = {
                    val next = fraction >= 0.5f
                    dragging = false
                    if (next != currentChecked) onChange(next)
                    motion.updateValue(if (next) 1f else 0f)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Keep effect layers inside a fixed visual frame, centered in the larger hit area.
        Box(Modifier.requiredSize(64.dp, 28.dp), contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier.matchParentSize()
                    .then(if (glass.enabled) Modifier.layerBackdrop(trackBackdrop) else Modifier)
                    .clip(Capsule()).drawBehind {
                        drawRect(lerp(Color(0xFF787880).copy(alpha = 0.36f), FnAccent, motion.value))
                    },
            )
            val transform: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
                scaleX = motion.scaleX
                scaleY = motion.scaleY
                val velocity = motion.velocity / 50f
                scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
            }
            Box(
                Modifier.align(Alignment.CenterStart).requiredSize(40.dp, 24.dp).graphicsLayer {
                    translationX = (if (rtl) -1 else 1) * (2.dp.toPx() + dragWidth * motion.value)
                }.then(
                    if (glass.enabled) {
                        val thumbBackdrop = rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = motion.pressProgress
                            scale(
                                androidx.compose.ui.util.lerp(2f / 3f, 0.75f, progress),
                                androidx.compose.ui.util.lerp(0f, 0.75f, progress),
                            ) { drawBackdrop() }
                        }
                        Modifier.drawBackdrop(
                            backdrop = if (backdrop != null) rememberCombinedBackdrop(backdrop, thumbBackdrop) else thumbBackdrop,
                            shape = { Capsule() },
                            effects = {
                                val progress = motion.pressProgress
                                blur(8.dp.toPx() * (1f - progress) * glass.blurScale)
                                lens(5.dp.toPx() * progress, 10.dp.toPx() * progress, chromaticAberration = true)
                            },
                            highlight = { Highlight.Ambient.copy(alpha = motion.pressProgress) },
                            shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.05f)) },
                            innerShadow = { InnerShadow(radius = 4.dp * motion.pressProgress, alpha = motion.pressProgress) },
                            layerBlock = transform,
                            onDrawSurface = { drawRect(Color.White.copy(alpha = 1f - motion.pressProgress)) },
                        )
                    } else {
                        Modifier.graphicsLayer(transform).background(glass.surfaceColor, Capsule())
                    },
                ),
            )
        }
    }
}
