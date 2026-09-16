package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope

/** Shared LiquidButton press, touch highlight, elastic drag and spring return. */
class LiquidInteraction internal constructor(scope: CoroutineScope, consumeDrag: Boolean) {
    private val highlight = InteractiveHighlight(scope, consumeDrag = consumeDrag)

    // Morph anchors must be recorded at rest, not while the glass is being deformed.
    val isIdle: Boolean get() = highlight.pressProgress == 0f && highlight.offset.getDistance() < 0.1f
    val modifier: Modifier = highlight.modifier.then(highlight.gestureModifier)
    val layerBlock: GraphicsLayerScope.() -> Unit = {
        val deformation = liquidDragDeformation(size, highlight.offset, highlight.pressProgress, 4.dp.toPx())
        translationX = deformation.translation.x
        translationY = deformation.translation.y
        scaleX = deformation.scale.x
        scaleY = deformation.scale.y
    }
}

@Composable
fun rememberLiquidInteraction(consumeDrag: Boolean = false): LiquidInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope, consumeDrag) { LiquidInteraction(scope, consumeDrag) }
}

internal data class LiquidDragDeformation(val translation: Offset, val scale: Offset)

/** Shared geometry keeps button and menu elasticity consistent; callers own their gestures. */
internal fun liquidDragDeformation(size: Size, offset: Offset, press: Float, expansion: Float): LiquidDragDeformation {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return LiquidDragDeformation(Offset.Zero, Offset(1f, 1f))
    val scale = lerp(1f, 1f + expansion / height, press)
    val maxOffset = size.minDimension
    val translation = Offset(
        maxOffset * tanh(0.05f * offset.x / maxOffset),
        maxOffset * tanh(0.05f * offset.y / maxOffset),
    )
    val maxDragScale = expansion / height
    val angle = atan2(offset.y, offset.x)
    return LiquidDragDeformation(translation, Offset(
        scale + maxDragScale * abs(cos(angle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f),
        scale + maxDragScale * abs(sin(angle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f),
    ))
}
