package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Rule
import org.junit.Test

class LiquidMenuReducedMotionTest {
    @get:Rule
    val compose =
        createComposeRule(
            effectContext =
                object : MotionDurationScale {
                    override val scaleFactor = 0f
                }
        )

    @Test
    fun disablingAnimationsShowsAndDismissesWithoutSpringDelay() {
        var expanded by mutableStateOf(false)
        var attached by mutableStateOf(false)
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost {
                    val backdrop = rememberLayerBackdrop()
                    LiquidMenu(
                        expanded,
                        { expanded = false },
                        backdrop,
                        listOf(LiquidMenuItem("one", "One")),
                        {},
                        onExpandedChange = { expanded = it },
                        transition = if (attached) LiquidMenuTransition.Attached else LiquidMenuTransition.Detached,
                        trigger = {
                            LiquidButton(it, backdrop, Modifier.height(48.dp).testTag("trigger").then(surfaceModifier()),
                                foregroundModifier = foregroundModifier) { Text("Open") }
                        },
                    )
                }
            }
        }
        for (mode in listOf(false, true)) {
            compose.runOnIdle { attached = mode }
            compose.mainClock.autoAdvance = false
            compose.onNodeWithTag("trigger").performClick()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("liquid-menu-item-one").assertIsEnabled()
            compose.runOnIdle { expanded = false }
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
            compose.mainClock.autoAdvance = true
        }
    }
}
