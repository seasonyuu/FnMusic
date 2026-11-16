package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SleepTimerSheetTest {
    @get:Rule val compose = createComposeRule()
    private class Timer : SleepTimerController {
        override val state = MutableStateFlow(SleepTimerState(initialized = true))
        val starts = mutableListOf<Int>()
        override fun enable() = start(state.value.durationMinutes)
        override fun start(minutes: Int) {
            starts += minutes
            state.value = SleepTimerState(minutes, true, minutes * 60L, true)
        }
        override fun cancel() { state.value = state.value.copy(enabled = false, remainingSeconds = 0) }
    }
    private val timer = Timer()
    private fun sheet() {
        compose.setContent { FnMusicTheme {
            val state by timer.state.collectAsState()
            SleepTimerSheet(state, timer::enable, timer::start, timer::cancel, onDismiss = {})
        } }
    }
    @Test fun switchAndPresetRestartAndRememberLastSelection() {
        sheet()
        compose.onNodeWithTag("sleep-timer-preset-30").assertIsNotSelected()
        compose.onNodeWithTag("sleep-timer-last-duration").assertTextEquals("上次 30 分钟")
        compose.onNodeWithTag("sleep-timer-switch").performClick().assertIsOn()
        compose.onNodeWithTag("sleep-timer-preset-30").assertIsSelected()
        compose.onNodeWithTag("sleep-timer-last-duration").assertDoesNotExist()
        compose.onNodeWithTag("sleep-timer-preset-20").performClick().assertIsSelected()
        compose.onNodeWithTag("sleep-timer-preset-20").performClick()
        compose.onNodeWithTag("sleep-timer-switch").performClick().assertIsOff()
        compose.onNodeWithTag("sleep-timer-preset-20").assertIsNotSelected()
        compose.onNodeWithTag("sleep-timer-last-duration").assertTextEquals("上次 20 分钟")
        compose.onNodeWithTag("sleep-timer-switch").performClick()
        compose.onNodeWithTag("sleep-timer-preset-20").assertIsSelected()
        compose.runOnIdle { assertEquals(listOf(30, 20, 20, 20), timer.starts) }
    }
    @Test fun customValidationAndCancellationDoNotAlterRunningTimer() {
        timer.start(30)
        sheet()
        compose.onNodeWithTag("sleep-timer-custom").performClick()
        compose.onNodeWithTag("sleep-timer-minutes-wheel").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithTag("sleep-timer-confirm").assertIsNotEnabled()
        compose.onNodeWithText("取消", useUnmergedTree = true).performClick()
        compose.runOnIdle { assertEquals(listOf(30), timer.starts) }
        compose.onNodeWithTag("sleep-timer-custom").performClick()
        compose.onNodeWithTag("sleep-timer-hours-wheel").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("sleep-timer-minutes-wheel").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithTag("sleep-timer-confirm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(30, 60), timer.starts) }
    }
    @Test fun twentyFourHoursClampsMinutesAndZeroDurationCannotStart() {
        timer.start(90)
        sheet()
        compose.onNodeWithTag("sleep-timer-custom").performClick()
        compose.onNodeWithTag("sleep-timer-hours-wheel")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(24f) }
        compose.onNodeWithTag("sleep-timer-confirm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1440, timer.starts.last()) }
        compose.onNodeWithTag("sleep-timer-custom").performScrollTo().performClick()
        compose.onNodeWithTag("sleep-timer-hours-wheel")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0f) }
        compose.onNodeWithTag("sleep-timer-confirm").assertIsNotEnabled()
    }
    @Test fun reversingEditorExitPreservesDraftAndConfirmationCollapsesIt() {
        sheet()
        compose.onNodeWithTag("sleep-timer-custom").performClick()
        compose.onNodeWithTag("sleep-timer-hours-wheel")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("sleep-timer-custom").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("sleep-timer-confirm").assertDoesNotExist()
        compose.onNodeWithTag("sleep-timer-custom").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("sleep-timer-hours-1").assertIsSelected()
        compose.onNodeWithTag("sleep-timer-confirm").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(90), timer.starts) }
        compose.onNodeWithTag("sleep-timer-editor").assertDoesNotExist()
    }
    @Test fun playerMenuUpdatesCountdownAndRetiresOverlayBeforeSheet() {
        compose.setContent { FnMusicTheme { LiquidMenuHost {
            CompositionLocalProvider(LocalSleepTimerController provides timer) {
                PlayerMoreMenu(Track(TrackId("fixture"), "Fixture"), rememberLayerBackdrop(),
                    modifier = Modifier.padding(24.dp).size(48.dp).testTag("player-more"), surfaceColor = FnSurface)
            }
        } } }
        compose.onNodeWithTag("player-more").performClick()
        compose.onNodeWithTag("liquid-menu-item-sleep-timer").assertExists()
        compose.runOnIdle { timer.start(90) }
        compose.onNodeWithText("90:00", useUnmergedTree = true).assertExists()
        compose.runOnIdle { timer.state.value = timer.state.value.copy(remainingSeconds = 5399) }
        compose.onNodeWithText("89:59", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("liquid-menu-item-sleep-timer").performClick()
        compose.onNodeWithTag("sleep-timer-sheet").assertExists()
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
    }
}
