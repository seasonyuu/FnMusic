package com.seasonyuu.fnmusic.feature.music

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One transition owns both button navigation and the seekable system back preview. */
@Composable
internal fun MusicPageHost(
    navigation: MusicNavigationState,
    backEnabled: Boolean,
    onPop: () -> Unit,
    content: @Composable (MusicPageEntry) -> Unit,
) {
    val current = navigation.current
    val transitionState = remember { SeekableTransitionState(current) }
    val transition = rememberTransition(transitionState, label = "music-page")
    var preview by remember { mutableStateOf<MusicPageEntry?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var predicting by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = backEnabled && navigation.canPop) { events ->
        val origin = navigation.current
        val previous = navigation.previous
        try {
            events.collect { event ->
                if (backEnabled && previous != null && navigation.current.id == origin.id) {
                    preview = previous
                    progress = event.progress
                    predicting = true
                }
            }
            if (backEnabled && previous != null && navigation.current.id == origin.id) onPop()
        } catch (cancelled: CancellationException) {
            // Keep the stack intact. The settling effect reverses the preview to its origin.
            throw cancelled
        } finally {
            predicting = false
        }
    }

    if (predicting && preview != null) {
        LaunchedEffect(progress, preview) {
            transitionState.seekTo(progress, requireNotNull(preview))
        }
    } else {
        LaunchedEffect(current) {
            if (transitionState.currentState != current) {
                transitionState.animateTo(current)
            } else if (transitionState.targetState != current) {
                // animateTo(current) would start a new transition. Rewind this one on cancel.
                animate(transitionState.fraction, 0f, animationSpec = tween(180)) { value, _ ->
                    launch { transitionState.seekTo(value) }
                }
                transitionState.snapTo(current)
            }
        }
    }

    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize().testTag("music-page-host"),
        contentKey = { it.id },
        transitionSpec = {
            if (initialState.destination != targetState.destination) {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            } else if (targetState.depth < initialState.depth) {
                ((fadeIn(tween(240)) + slideInHorizontally(tween(280)) { -it / 4 }) togetherWith
                    (fadeOut(tween(280)) + slideOutHorizontally(tween(280)) { it }))
                    .apply { targetContentZIndex = targetState.depth.toFloat() }
            } else {
                ((fadeIn(tween(280)) + slideInHorizontally(tween(280)) { it }) togetherWith
                    (fadeOut(tween(240)) + slideOutHorizontally(tween(280)) { -it / 4 }))
                    .apply { targetContentZIndex = targetState.depth.toFloat() }
            }
        },
    ) { entry -> content(entry) }
}
