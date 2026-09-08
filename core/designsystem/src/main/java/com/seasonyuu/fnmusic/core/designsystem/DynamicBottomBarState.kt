package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal object DynamicBottomBarScrollBehavior {
    fun targetForScroll(currentTarget: Float, scrollDeltaPx: Float): Float = when {
        scrollDeltaPx < 0f -> 0f
        scrollDeltaPx > 0f -> 1f
        else -> currentTarget
    }
}

@Stable
class DynamicBottomBarState internal constructor(
    private val animationScope: CoroutineScope,
    private val navigationAnimationSpec: FiniteAnimationSpec<Float>,
    private val playerAnimationSpec: FiniteAnimationSpec<Float>,
) {
    private val navigationAnimation = Animatable(1f)
    private val playerAnimation = Animatable(1f)

    val navigationExpansionProgress: Float get() = navigationAnimation.value
    val playerExpansionProgress: Float get() = playerAnimation.value

    private var targetProgress = 1f
    private var animationJob: Job? = null
    private var gestureActive = false
    private var gestureTriggered = false

    internal fun beginGesture() {
        gestureActive = true
        gestureTriggered = false
    }

    internal fun endGesture() {
        gestureActive = false
        gestureTriggered = false
    }

    fun dispatchScroll(scrollDeltaPx: Float) {
        if (!gestureActive || gestureTriggered || scrollDeltaPx == 0f) return
        gestureTriggered = true
        animateTo(DynamicBottomBarScrollBehavior.targetForScroll(targetProgress, scrollDeltaPx))
    }

    fun expand() {
        animateTo(1f)
    }

    private fun animateTo(target: Float) {
        // Repeated input in the same direction must not restart the transition.
        if (target == targetProgress) return
        targetProgress = target
        val navigationVelocity = navigationAnimation.velocity
        val playerVelocity = playerAnimation.velocity
        animationJob?.cancel()
        animationJob = animationScope.launch {
            launch {
                navigationAnimation.animateTo(
                    targetValue = target,
                    animationSpec = navigationAnimationSpec,
                    initialVelocity = navigationVelocity,
                )
            }
            launch {
                playerAnimation.animateTo(
                    targetValue = target,
                    animationSpec = playerAnimationSpec,
                    initialVelocity = playerVelocity,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun rememberDynamicBottomBarState(): DynamicBottomBarState {
    val scope = rememberCoroutineScope()
    val navigationSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val playerSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    return remember(scope, navigationSpec, playerSpec) {
        DynamicBottomBarState(scope, navigationSpec, playerSpec)
    }
}

fun dynamicBottomBarNestedScrollConnection(
    state: DynamicBottomBarState,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) {
            state.dispatchScroll(available.y)
        }
        return Offset.Zero
    }
}

fun Modifier.dynamicBottomBarGesture(state: DynamicBottomBarState): Modifier = pointerInput(state) {
    try {
        awaitEachGesture {
            // Observe before children consume input, without consuming their events.
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.beginGesture()
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
            } finally {
                state.endGesture()
            }
        }
    } finally {
        state.endGesture()
    }
}
