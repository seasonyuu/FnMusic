package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlayerSliderTest {
    @get:Rule val compose = createComposeRule()
    private fun slider() = compose.onNodeWithTag("slider")
    private fun current() = slider().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current
    private fun expansion() = slider().fetchSemanticsNode().config[PlayerSliderExpansion]

    @Test fun pressExpandsInkWithoutMovingLabelsAndReleaseRestoresIt() {
        val value = mutableFloatStateOf(0.5f)
        compose.setContent {
            FnMusicTheme {
                Column(Modifier.background(Color.Black).padding(24.dp).width(300.dp)) {
                    PlayerSlider(value.floatValue, { value.floatValue = it }, 0f..1f, Modifier.testTag("slider"))
                    androidx.compose.material3.Text("Time", Modifier.testTag("label"))
                }
            }
        }
        val label = compose.onNodeWithTag("label").fetchSemanticsNode().boundsInRoot
        fun inkHeight(): Int {
            val pixels = slider().captureToImage().toPixelMap()
            return (0 until pixels.height).count { pixels[pixels.width / 4, it].red > 0.1f }
        }
        val restingHeight = inkHeight()
        val restingWidth = slider().fetchSemanticsNode().boundsInRoot.width
        compose.mainClock.autoAdvance = false
        slider().performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(240)
        assertEquals(1f, expansion(), 0.001f)
        assertTrue(inkHeight() >= restingHeight * 2)
        assertEquals(restingWidth * 1.05f, slider().fetchSemanticsNode().boundsInRoot.width, 2f)
        assertEquals(label, compose.onNodeWithTag("label").fetchSemanticsNode().boundsInRoot)
        slider().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(300)
        assertEquals(0f, expansion(), 0.001f)
        assertEquals(restingHeight, inkHeight())
    }

    @Test fun dragPreviewsAndCommitsOnceWithoutFlashingOldPosition() {
        val state = mutableStateOf(player())
        val seeks = mutableListOf<Long>()
        content(state, seeks)
        compose.mainClock.autoAdvance = false
        slider().performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.75f, centerY), delayMillis = 100)
        }
        compose.mainClock.advanceTimeBy(240)
        val preview = current()
        assertTrue(preview > 100_000)
        assertTrue(seeks.isEmpty())
        compose.runOnIdle { state.value = state.value.copy(positionMs = 25_000) }
        assertEquals(preview, current(), 1f)
        slider().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
        assertEquals(1, seeks.size)
        assertEquals(preview, current(), 1f)
        compose.runOnIdle { state.value = state.value.copy(positionMs = seeks.single() + 100) }
        compose.mainClock.advanceTimeBy(32)
        assertEquals(state.value.positionMs.toFloat(), current(), 1f)
    }

    @Test fun trackTapDoesNotJumpButDragFromAnyPositionAccumulatesDelta() {
        val value = mutableFloatStateOf(0.5f)
        var finishes = 0
        compose.setContent {
            FnMusicTheme {
                PlayerSlider(
                    value = value.floatValue,
                    onValueChange = { value.floatValue = it },
                    valueRange = 0f..1f,
                    modifier = Modifier.width(300.dp).testTag("slider"),
                    onValueChangeFinished = { finishes++ },
                )
            }
        }
        val initial = current()
        slider().performTouchInput { click(Offset(width * 0.9f, centerY)) }
        assertEquals("A track tap must not jump to its absolute position", initial, current(), 0f)
        assertEquals("A track tap is not a completed drag", 0, finishes)

        slider().performTouchInput {
            down(Offset(width * 0.9f, centerY))
            moveTo(Offset(width * 1.1f, centerY), delayMillis = 100)
            up()
        }
        assertEquals("A drag accumulates movement from its starting position", 0.7f, current(), 0.03f)
        assertEquals(1, finishes)
    }

    @Test fun slowSteppedDragAccumulatesSubStepDistance() {
        val value = mutableFloatStateOf(5f)
        compose.setContent {
            FnMusicTheme {
                PlayerSlider(
                    value = value.floatValue,
                    onValueChange = { value.floatValue = it },
                    valueRange = 0f..10f,
                    modifier = Modifier.width(300.dp).testTag("slider"),
                    steps = 9,
                )
            }
        }

        slider().performTouchInput {
            down(Offset(width * 0.3f, centerY))
            repeat(16) { index ->
                moveTo(Offset(width * 0.3f + (index + 1) * 6f, centerY), delayMillis = 100)
            }
        }
        assertEquals("Small drag deltas must accumulate across stepped values", 6f, current(), 0f)
        slider().performTouchInput { up() }
    }

    @Test fun cancelledDragDoesNotSeekAndNextGestureStillWorks() {
        val state = mutableStateOf(player())
        val seeks = mutableListOf<Long>()
        content(state, seeks)
        slider().performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveTo(Offset(width * 0.8f, centerY), delayMillis = 100)
        }
        assertTrue(current() > 100_000)
        assertTrue("A drag that is still held must not seek", seeks.isEmpty())
        slider().performTouchInput { cancel() }
        compose.waitForIdle()
        assertTrue("A cancelled gesture must not seek", seeks.isEmpty())
        assertEquals(20_000f, current(), 1f)
        assertEquals(0f, expansion(), 0.001f)
        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(120_000f) }
        assertEquals(listOf(120_000L), seeks)
        slider().performTouchInput { click(center) }
        assertEquals("A playback track tap must not seek", 1, seeks.size)
    }

    @Test fun seekTimeoutAndDuplicateQueueEntryResetPreview() {
        val state = mutableStateOf(player())
        val seeks = mutableListOf<Long>()
        content(state, seeks)
        compose.mainClock.autoAdvance = false
        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(150_000f) }
        compose.mainClock.advanceTimeBy(32)
        assertEquals(150_000f, current(), 1f)
        compose.mainClock.advanceTimeBy(1_100)
        assertEquals(20_000f, current(), 1f)
        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(140_000f) }
        compose.runOnIdle {
            state.value = state.value.copy(
                queue = listOf(state.value.current!!.copy(queueEntryId = "another-entry")),
                positionMs = 10_000,
            )
        }
        compose.mainClock.advanceTimeBy(32)
        assertEquals(10_000f, current(), 1f)
    }

    @Test fun unknownDurationIsDisabled() {
        content(mutableStateOf(player().copy(durationMs = 0)), mutableListOf())
        slider().assertIsNotEnabled()
        slider().performTouchInput { click(center) }
        assertEquals(0f, expansion(), 0f)
    }

    @Test fun switchingQueueEntryDuringDragDiscardsTheOldGesture() {
        val state = mutableStateOf(player())
        val seeks = mutableListOf<Long>()
        content(state, seeks)
        slider().performTouchInput {
            down(center)
            moveTo(Offset(width * 0.8f, centerY), delayMillis = 100)
        }
        compose.runOnIdle {
            state.value = state.value.copy(
                queue = listOf(state.value.current!!.copy(queueEntryId = "next")),
                positionMs = 5_000,
            )
        }
        slider().performTouchInput { up() }
        assertTrue(seeks.isEmpty())
        assertEquals(5_000f, current(), 1f)
        assertEquals(0f, expansion(), 0.001f)
    }

    @Test fun endpointsAccessibilityKeyboardAndDiscreteVolume() {
        val value = mutableFloatStateOf(3f)
        var finishes = 0
        compose.setContent {
            FnMusicTheme {
                PlayerSlider(value.floatValue, { value.floatValue = it }, 0f..10f,
                    Modifier.width(300.dp).testTag("slider"), steps = 9,
                    onValueChangeFinished = { finishes++ })
            }
        }
        slider().performSemanticsAction(SemanticsActions.SetProgress) { it(6.3f) }
        assertEquals(6f, current(), 0f)
        slider().performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        slider().performKeyInput { pressKey(Key.DirectionRight) }
        assertEquals(7f, current(), 0f)
        slider().performTouchInput { swipe(Offset(width * 0.5f, centerY), Offset(width * 1.2f, centerY), 200) }
        assertEquals(10f, current(), 0f)
        slider().performTouchInput { swipe(Offset(width * 0.1f, centerY), Offset(-width * 1.2f, centerY), 200) }
        assertEquals(0f, current(), 0f)
        assertTrue(finishes >= 3)
    }

    private fun player() = PlayerState(
        queue = listOf(PlayableTrack(Track(TrackId("slider"), "Slider test"), "https://music.invalid/test")),
        currentIndex = 0, positionMs = 20_000, durationMs = 200_000,
    )

    private fun content(state: androidx.compose.runtime.MutableState<PlayerState>, seeks: MutableList<Long>) {
        compose.setContent {
            FnMusicTheme {
                Column(Modifier.padding(24.dp).width(300.dp)) {
                    PlaybackProgress(state.value, Modifier.testTag("slider"), onSeek = { seeks.add(it) })
                }
            }
        }
    }
}
