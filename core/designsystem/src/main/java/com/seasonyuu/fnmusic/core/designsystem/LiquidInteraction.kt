package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.GraphicsLayerScope
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
        val width = size.width
        val height = size.height
        if (width > 0f && height > 0f) {
            val scale = lerp(1f, 1f + 4.dp.toPx() / height, highlight.pressProgress)
            val maxOffset = size.minDimension
            val offset = highlight.offset
            translationX = maxOffset * tanh(0.05f * offset.x / maxOffset)
            translationY = maxOffset * tanh(0.05f * offset.y / maxOffset)

            val maxDragScale = 4.dp.toPx() / height
            val offsetAngle = atan2(offset.y, offset.x)
            scaleX = scale + maxDragScale *
                abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                (width / height).fastCoerceAtMost(1f)
            scaleY = scale + maxDragScale *
                abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                (height / width).fastCoerceAtMost(1f)
        }
    }
}

@Composable
fun rememberLiquidInteraction(consumeDrag: Boolean = false): LiquidInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope, consumeDrag) { LiquidInteraction(scope, consumeDrag) }
}
