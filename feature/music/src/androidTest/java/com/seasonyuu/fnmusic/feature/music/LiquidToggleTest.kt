package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.seasonyuu.fnmusic.core.designsystem.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiquidToggleTest {
    @get:Rule val compose = createComposeRule()

    @Test fun clickKeyboardAndSemanticsEachToggleOnceWithGlassOff() {
        val checked = mutableStateOf(false)
        var changes = 0
        lateinit var inputMode: InputModeManager
        compose.setContent {
            inputMode = LocalInputModeManager.current
            FnMusicTheme {
                CompositionLocalProvider(LocalLiquidGlassEnabled provides false) {
                    LiquidToggle(checked.value, { checked.value = it; changes++ }, Modifier.testTag("toggle"))
                }
            }
        }
        val toggle = compose.onNodeWithTag("toggle")
        toggle.performTouchInput { click() }.assertIsOn()
        compose.runOnIdle { assertEquals(1, changes) }
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        toggle.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        toggle.assertIsFocused().performKeyInput { pressKey(Key.Spacebar) }.assertIsOff()
        compose.runOnIdle { assertEquals(2, changes) }
        toggle.performClick().assertIsOn()
        compose.runOnIdle { assertEquals(3, changes) }
    }

    @Test fun dragTogglesOnceInBothDirectionsAndRtl() {
        val checked = mutableStateOf(false)
        val rtl = mutableStateOf(false)
        var changes = 0
        compose.setContent {
            FnMusicTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl.value) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    LiquidToggle(checked.value, { checked.value = it; changes++ }, Modifier.testTag("toggle"))
                }
            }
        }
        val toggle = compose.onNodeWithTag("toggle")
        toggle.performTouchInput { swipe(Offset(width * .1f, centerY), Offset(width * .9f, centerY), 400) }.assertIsOn()
        compose.runOnIdle { assertEquals(1, changes) }
        toggle.performTouchInput { swipe(Offset(width * .9f, centerY), Offset(width * .1f, centerY), 400) }.assertIsOff()
        compose.runOnIdle { assertEquals(2, changes); rtl.value = true }
        toggle.performTouchInput { swipe(Offset(width * .9f, centerY), Offset(width * .1f, centerY), 400) }.assertIsOn()
        compose.runOnIdle { assertEquals(3, changes) }
    }

    @Test fun disabledToggleIgnoresTouch() {
        var changes = 0
        compose.setContent { FnMusicTheme { LiquidToggle(false, { changes++ }, Modifier.testTag("toggle"), enabled = false) } }
        compose.onNodeWithTag("toggle").assertIsNotEnabled().performTouchInput { click(); swipeLeft() }
        compose.runOnIdle { assertEquals(0, changes) }
    }
}
