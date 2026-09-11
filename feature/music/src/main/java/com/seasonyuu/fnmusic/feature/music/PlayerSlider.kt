package com.seasonyuu.fnmusic.feature.music

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

internal val PlayerSliderExpansion = SemanticsPropertyKey<Float>("PlayerSliderExpansion")
internal val PlayerSliderPull = SemanticsPropertyKey<Float>("PlayerSliderPull")

internal fun Modifier.playerSliderEndpoint(
    expansion: Float,
    originX: Float,
    translationPx: Float = 0f,
    pulse: Float = 0f,
): Modifier =
    graphicsLayer {
        scaleX = 1f + (0.15f + 0.2f * pulse) * expansion
        scaleY = scaleX
        translationX = translationPx
        transformOrigin = TransformOrigin(originX, 0.5f)
    }

/** Shared geometry keeps labels and icon ink attached to the animated track edges. */
@Stable
internal class PlayerSliderMotion {
    var widthPx by mutableFloatStateOf(0f)
    var targetPullPx by mutableFloatStateOf(0f)
        private set
    var minimumHits by mutableIntStateOf(0)
        private set
    var maximumHits by mutableIntStateOf(0)
        private set
    private var lastEdge = 0
    val pull = Animatable(0f)
    val minimumPulse = Animatable(0f)
    val maximumPulse = Animatable(0f)

    fun leftShift(expansion: Float): Float =
        -widthPx * 0.025f * expansion + pull.value - abs(pull.value) * 0.4f

    fun rightShift(expansion: Float): Float =
        widthPx * 0.025f * expansion + pull.value + abs(pull.value) * 0.4f

    fun dragTo(x: Float, expansion: Float, steps: Int, rtl: Boolean, limitPx: Float) {
        if (widthPx <= 0f) return
        val overshoot = when {
            x < 0f -> x
            x > widthPx -> x - widthPx
            else -> 0f
        } * (1f + 0.05f * expansion)
        val distance = abs(overshoot) * 0.55f
        targetPullPx = sign(overshoot) * limitPx * distance / (limitPx + distance)
        var fraction = (x / widthPx).coerceIn(0f, 1f)
        if (rtl) fraction = 1f - fraction
        if (steps > 0) fraction = (fraction * (steps + 1)).roundToInt().toFloat() / (steps + 1)
        val edge = when (fraction) { 0f -> -1; 1f -> 1; else -> 0 }
        if (edge != lastEdge) {
            if (edge < 0) minimumHits++
            if (edge > 0) maximumHits++
            lastEdge = edge
        }
    }

    fun release() {
        targetPullPx = 0f
        lastEdge = 0
    }
}

@Composable
internal fun rememberPlayerSliderMotion(): PlayerSliderMotion {
    val motion = remember { PlayerSliderMotion() }
    LaunchedEffect(motion.targetPullPx) {
        motion.pull.animateTo(motion.targetPullPx, spring(dampingRatio = 0.75f, stiffness = 700f))
    }
    LaunchedEffect(motion.minimumHits) {
        if (motion.minimumHits > 0) {
            motion.minimumPulse.animateTo(1f, tween(75))
            motion.minimumPulse.animateTo(0f, tween(220, easing = LinearOutSlowInEasing))
        }
    }
    LaunchedEffect(motion.maximumHits) {
        if (motion.maximumHits > 0) {
            motion.maximumPulse.animateTo(1f, tween(75))
            motion.maximumPulse.animateTo(0f, tween(220, easing = LinearOutSlowInEasing))
        }
    }
    return motion
}

@Composable
internal fun playerSliderExpansion(source: MutableInteractionSource, enabled: Boolean = true): Float {
    val pressed by source.collectIsPressedAsState()
    val dragged by source.collectIsDraggedAsState()
    val active = enabled && (pressed || dragged)
    val expansion by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(if (active) 180 else 220, easing = LinearOutSlowInEasing),
        label = "player-slider-expansion",
    )
    return expansion
}

private fun sliderValueAfterDrag(
    value: Float,
    deltaPx: Float,
    widthPx: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    rtl: Boolean,
): Float {
    if (widthPx <= 0f) return value
    val range = valueRange.endInclusive - valueRange.start
    if (range <= 0f) return valueRange.start
    val direction = if (rtl) -1f else 1f
    val raw = (value + direction * deltaPx / widthPx * range)
        .coerceIn(valueRange.start, valueRange.endInclusive)
    if (steps <= 0) return raw
    val stepSize = range / (steps + 1)
    return (valueRange.start + ((raw - valueRange.start) / stepSize).roundToInt() * stepSize)
        .coerceIn(valueRange.start, valueRange.endInclusive)
}

/** A thumb-free track shared by playback (continuous) and system volume (stepped).
 *
 * Touch input follows the Apple Music-style interaction used by the referenced
 * interactive_slider widget: a tap is only a press, while a horizontal drag from any
 * position changes the value by the movement delta. The pointer location is never
 * converted directly into an absolute value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    onValueChangeCancelled: () -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    steps: Int = 0,
    enabled: Boolean = true,
    expansion: Float = playerSliderExpansion(interactionSource, enabled),
    motion: PlayerSliderMotion = rememberPlayerSliderMotion(),
    elastic: Boolean = false,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentExpansion by rememberUpdatedState(expansion)
    val currentSteps by rememberUpdatedState(steps)
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val currentOnValueChangeCancelled by rememberUpdatedState(onValueChangeCancelled)
    var touchInProgress by remember { mutableStateOf(false) }
    var touchCancelled by remember { mutableStateOf(false) }
    Slider(
        value = value,
        // Keep Material's semantics and keyboard behavior. Touch events are consumed by the
        // outer pointer handler below so Material's tap-to-seek path cannot run.
        onValueChange = {
            // Material's internal draggable can still observe a consumed pointer cancellation.
            // Ignore its callbacks during our custom touch gesture; keyboard and accessibility
            // updates continue through this path when no touch is active.
            if (!touchInProgress) {
                touchCancelled = false
                currentOnValueChange(it)
            }
        },
        onValueChangeFinished = {
            if (!touchInProgress && !touchCancelled) currentOnValueChangeFinished?.invoke()
        },
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .onSizeChanged { motion.widthPx = it.width.toFloat() }
            // Transform the input surface with the ink, without resizing its layout slot.
            .graphicsLayer { scaleX = 1f + 0.05f * expansion }
            .semantics {
                this[PlayerSliderExpansion] = expansion
                this[PlayerSliderPull] = motion.pull.value
            }
            .pointerInput(enabled, elastic, motion, rtl) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    // Prevent Slider's internal sliderTapModifier from converting this press into
                    // an absolute value on release. We handle only horizontal drags below.
                    down.consume()
                    touchInProgress = true
                    touchCancelled = false
                    val press = PressInteraction.Press(down.position)
                    interactionSource.tryEmit(press)
                    var dragInteraction: DragInteraction.Start? = null
                    var dragging = false
                    var released = false
                    var lastX = down.position.x
                    val startValue = currentValue
                    var accumulatedDeltaPx = 0f
                    var draggedValue = startValue
                    try {
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pointer = event.changes.firstOrNull { it.id == down.id }
                            if (pointer != null) {
                                if (pointer.pressed) {
                                    val deltaX = pointer.position.x - lastX
                                    lastX = pointer.position.x
                                    dragging = dragging || abs(pointer.position.x - down.position.x) > viewConfiguration.touchSlop
                                    if (dragging) {
                                        if (dragInteraction == null) {
                                            val start = DragInteraction.Start()
                                            dragInteraction = start
                                            interactionSource.tryEmit(start)
                                        }
                                        pointer.consume()
                                        accumulatedDeltaPx += deltaX
                                        val nextValue = sliderValueAfterDrag(
                                            value = startValue,
                                            deltaPx = accumulatedDeltaPx,
                                            widthPx = motion.widthPx,
                                            valueRange = valueRange,
                                            steps = currentSteps,
                                            rtl = rtl,
                                        )
                                        if (nextValue != draggedValue) {
                                            draggedValue = nextValue
                                            currentOnValueChange(nextValue)
                                        }
                                        if (elastic) {
                                            motion.dragTo(pointer.position.x, currentExpansion, currentSteps, rtl, 12.dp.toPx())
                                        }
                                    }
                                }
                                released = event.changes.all { !it.pressed } &&
                                    event.changes.none { it.isConsumed } &&
                                    event.changes.any { it.changedToUpIgnoreConsumed() }
                            }
                        } while (event.changes.any { it.pressed })
                    } finally {
                        if (released) {
                            dragInteraction?.let { interactionSource.tryEmit(DragInteraction.Stop(it)) }
                            if (dragging) currentOnValueChangeFinished?.invoke()
                            interactionSource.tryEmit(PressInteraction.Release(press))
                            touchCancelled = false
                        } else {
                            dragInteraction?.let { interactionSource.tryEmit(DragInteraction.Cancel(it)) }
                            interactionSource.tryEmit(PressInteraction.Cancel(press))
                            touchCancelled = true
                            currentOnValueChangeCancelled()
                        }
                        touchInProgress = false
                        motion.release()
                    }
                }
            },
        thumb = { Spacer(Modifier.size(0.dp)) },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(20.dp)) {
                val range = valueRange.endInclusive - valueRange.start
                val fraction = if (range > 0f) {
                    ((state.value - valueRange.start) / range).coerceIn(0f, 1f)
                } else 0f
                val pull = motion.pull.value
                // Rubber motion only transforms the ink; pointer coordinates stay in the normal
                // expanded track, avoiding feedback between deformation and the next drag event.
                val localPull = pull / (1f + 0.05f * expansion)
                val extraWidth = abs(localPull) * 0.8f
                val left = localPull - extraWidth / 2f
                val width = size.width + extraWidth
                val height = (4f + 5f * expansion).dp.toPx() *
                    (1f - 0.18f * (abs(pull) / 12.dp.toPx()).coerceIn(0f, 1f))
                val top = (size.height - height) / 2f
                val trackSize = Size(width, height)
                val radius = CornerRadius(height / 2f)
                val alpha = if (enabled) 1f else 0.38f
                drawRoundRect(
                    FnTextPrimary.copy(alpha = (0.24f + 0.06f * expansion) * alpha),
                    topLeft = Offset(left, top), size = trackSize, cornerRadius = radius,
                )
                // Clip a full capsule to keep the inner boundary flat, including near zero.
                if (fraction > 0f) {
                    clipRect(
                        left = left + if (rtl) width * (1f - fraction) else 0f,
                        right = left + if (rtl) width else width * fraction,
                    ) {
                        drawRoundRect(
                            FnTextPrimary.copy(alpha = (0.55f + 0.45f * expansion) * alpha),
                            topLeft = Offset(left, top), size = trackSize, cornerRadius = radius,
                        )
                    }
                }
            }
        },
    )
}
