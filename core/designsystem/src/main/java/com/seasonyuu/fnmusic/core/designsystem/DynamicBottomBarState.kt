package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal object DynamicBottomBarScrollBehavior {
    fun updateProgress(current: Float, scrollDeltaPx: Float, collapseDistancePx: Float): Float {
        if (collapseDistancePx <= 0f) return current.coerceIn(0f, 1f)
        return (current + scrollDeltaPx / collapseDistancePx).coerceIn(0f, 1f)
    }

    fun settleTarget(progress: Float, velocityY: Float, velocityThreshold: Float = 1_200f): Float = when {
        velocityY <= -velocityThreshold -> 0f
        velocityY >= velocityThreshold -> 1f
        progress >= 0.5f -> 1f
        else -> 0f
    }
}

@Stable
class DynamicBottomBarState internal constructor(
    private val animationScope: CoroutineScope,
) {
    var expansionProgress by mutableFloatStateOf(1f)
        private set

    private var settleJob: Job? = null

    fun dispatchScroll(scrollDeltaPx: Float, collapseDistancePx: Float) {
        settleJob?.cancel()
        expansionProgress = DynamicBottomBarScrollBehavior.updateProgress(
            current = expansionProgress,
            scrollDeltaPx = scrollDeltaPx,
            collapseDistancePx = collapseDistancePx,
        )
    }

    fun settle(velocityY: Float) {
        val target = DynamicBottomBarScrollBehavior.settleTarget(expansionProgress, velocityY)
        settleJob?.cancel()
        settleJob = animationScope.launch {
            Animatable(expansionProgress).animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
            ) { expansionProgress = value }
            expansionProgress = target
        }
    }

    fun expand() {
        settleJob?.cancel()
        settleJob = animationScope.launch {
            Animatable(expansionProgress).animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.88f, stiffness = 420f),
            ) { expansionProgress = value }
            expansionProgress = 1f
        }
    }
}

@Composable
fun rememberDynamicBottomBarState(): DynamicBottomBarState {
    val scope = rememberCoroutineScope()
    return remember(scope) { DynamicBottomBarState(scope) }
}

fun dynamicBottomBarNestedScrollConnection(
    state: DynamicBottomBarState,
    collapseDistancePx: Float,
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source == NestedScrollSource.UserInput && available.y != 0f) {
            state.dispatchScroll(available.y, collapseDistancePx)
        }
        return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        state.settle(available.y)
        return Velocity.Zero
    }
}
