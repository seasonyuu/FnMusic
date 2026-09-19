package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiquidMenuInteractionTest {
    @get:Rule val compose = createComposeRule()
    private var expanded by mutableStateOf(false)
    private var mounted by mutableStateOf(true)
    private val selected = mutableListOf<String>()
    private var dismissals = 0

    private fun fixture(long: Boolean = false, transition: LiquidMenuTransition = LiquidMenuTransition.Attached, hostInset: Int = 0) {
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost(Modifier.padding(top = hostInset.dp)) {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.DarkGray))
                    Box(
                        Modifier.fillMaxSize().padding(top = 40.dp, end = 20.dp),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        if (mounted)
                            LiquidMenu(
                                expanded,
                                {
                                    dismissals++
                                    expanded = false
                                },
                                backdrop,
                                if (long) List(40) { LiquidMenuItem("$it", "Option $it") }
                                else
                                    listOf(
                                        LiquidMenuItem("first", "First", selected = true),
                                        LiquidMenuItem("disabled", "Disabled", enabled = false),
                                        LiquidMenuDivider("divider"),
                                        LiquidMenuItem("last", "Last"),
                                    ),
                                { selected += it },
                                onExpandedChange = { expanded = it },
                                transition = transition,
                                trigger = { toggle ->
                                    LiquidButton(toggle, backdrop,
                                        Modifier.testTag("trigger").height(48.dp).then(surfaceModifier()),
                                        foregroundModifier = foregroundModifier) { Text("Open") }
                                },
                            )
                    }
                }
            }
        }
    }

    private fun open() {
        compose.onNodeWithTag("trigger").performClick()
        compose.waitForIdle()
    }

    @Test
    fun highlightAppearsAtPressedRowSlidesOnlyWhileHeldAndFadesInPlace() {
        var target by mutableStateOf<Offset?>(null)
        lateinit var highlight: LiquidMenuHighlightState
        compose.setContent {
            highlight = remember { LiquidMenuHighlightState() }
            LaunchedEffect(target) { highlight.update(target) }
        }
        compose.mainClock.autoAdvance = false
        val bottom = Offset(240f, 64f)
        compose.runOnIdle { target = bottom }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(bottom, highlight.geometry.value) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(.12f, highlight.alpha.value, .001f) }

        val top = Offset(12f, 48f)
        compose.runOnIdle { target = top }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertTrue(highlight.geometry.value.x > top.x)
            assertTrue(highlight.geometry.value.x < bottom.x)
        }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(top, highlight.geometry.value) }

        compose.runOnIdle { target = null }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle {
            assertEquals(top, highlight.geometry.value)
            assertTrue(highlight.alpha.value > 0f)
        }
        // A new press during fade-out must also jump directly to the new row.
        compose.runOnIdle { target = bottom }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(bottom, highlight.geometry.value) }
        compose.runOnIdle { target = null }
        compose.mainClock.advanceTimeBy(2_000)
        compose.runOnIdle {
            assertEquals(bottom, highlight.geometry.value)
            assertEquals(0f, highlight.alpha.value, .001f)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test
    fun selectionIsDeliveredOnceAndReopeningPreservesState() {
        fixture()
        open()
        compose.onNodeWithTag("liquid-menu-item-first").assertIsSelected()
        compose.onNodeWithTag("liquid-menu-item-disabled").assertIsNotEnabled()
        compose.onNodeWithTag("liquid-menu-item-last").performClick()
        compose.waitForIdle()
        assertEquals(listOf("last"), selected)
        assertEquals(1, dismissals)
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        open()
        compose.onNodeWithTag("liquid-menu-item-first").assertIsSelected()
        compose.onNodeWithTag("liquid-menu-dismiss").performClick()
        compose.waitForIdle()
        assertEquals(2, dismissals)
    }

    @Test
    fun slidingSelectsOnlyReleaseTargetAndOutsideStretchCancels() {
        fixture()
        open()
        val first =
            compose.onNodeWithTag("liquid-menu-item-first").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("liquid-menu-item-last").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(first.center)
            moveTo(last.center, 300)
            up()
        }
        compose.waitForIdle()
        assertEquals(listOf("last"), selected)
        open()
        val content = compose.onNodeWithTag("liquid-menu-content").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(first.center)
            moveTo(Offset(content.left - 25, first.center.y), 200)
            moveTo(first.center, 200)
            up()
        }
        compose.waitForIdle()
        assertEquals(1, selected.size)
        compose.onNodeWithTag("liquid-menu-overlay").assertExists()
    }

    @Test
    fun rapidReversalAndUnmountLeaveNoOverlay() {
        fixture()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("trigger").performClick()
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle { expanded = false }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { expanded = true }
        compose.mainClock.advanceTimeBy(2000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("liquid-menu-item-last").assertIsEnabled()
        compose.runOnIdle { mounted = false }
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        assertTrue(selected.isEmpty())
    }

    @Test
    fun scrollDoesNotChooseAnItem() {
        fixture(long = true)
        open()
        compose.onNodeWithTag("liquid-menu-content").performTouchInput {
            swipeUp(durationMillis = 500)
        }
        compose.waitForIdle()
        assertTrue(selected.isEmpty())
        compose.onNodeWithTag("liquid-menu-overlay").assertExists()
    }

    @Test
    fun keyboardSelectionAndEscape() {
        fixture()
        open()
        compose.onNodeWithTag("liquid-menu-content").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        compose.waitForIdle()
        assertEquals(listOf("last"), selected)
        open()
        compose.onNodeWithTag("liquid-menu-content").performKeyInput { pressKey(Key.Escape) }
        compose.waitForIdle()
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
    }

    @Test
    fun physicalOutsideTapAndSystemBackDismissWithoutSelection() {
        fixture()
        open()
        compose.onRoot().performTouchInput { click(Offset(4f, 4f)) }
        compose.waitForIdle()
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        open()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        assertTrue(selected.isEmpty())
    }

    @Test
    fun cancelingPointerDoesNotSelectOrLeaveStretch() {
        fixture()
        open()
        val first =
            compose.onNodeWithTag("liquid-menu-item-first").fetchSemanticsNode().boundsInRoot
        val before = compose.onNodeWithTag("liquid-menu-content").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(first.center)
            moveTo(first.center, 200)
            cancel()
        }
        compose.waitForIdle()
        assertTrue(selected.isEmpty())
        val after = compose.onNodeWithTag("liquid-menu-content").fetchSemanticsNode().boundsInRoot
        assertEquals(before.left, after.left, .5f)
        assertEquals(before.width, after.width, .5f)
    }

    @Test
    fun keyboardScrollsSelectedOptionIntoView() {
        fixture(long = true)
        open()
        compose.onNodeWithTag("liquid-menu-content").performKeyInput {
            repeat(40) { pressKey(Key.DirectionDown) }
        }
        compose.onNodeWithTag("liquid-menu-item-39").assertIsDisplayed()
        compose.onNodeWithTag("liquid-menu-content").performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf("39"), selected)
    }

    @Test
    fun zeroSizedExternalAnchorDoesNotLeaveAnInputBarrier() {
        expanded = true
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost {
                    LiquidMenu(
                        expanded,
                        { expanded = false },
                        rememberLayerBackdrop(),
                        listOf(LiquidMenuItem("one", "One")),
                        {},
                        onExpandedChange = { expanded = it },
                        trigger = { Box(Modifier.size(0.dp)) },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
    }

    @Test
    fun heldDragDeformsAlongItsAxisAndCancelSpringsBackWithoutSelecting() {
        fixture()
        open()
        val menu = compose.onNodeWithTag("liquid-menu-content")
        val resting = menu.fetchSemanticsNode().boundsInRoot
        for (horizontal in listOf(true, false)) {
            menu.performTouchInput {
                down(center)
                moveBy(if (horizontal) Offset(100f, 0f) else Offset(0f, 100f), 160)
            }
            compose.waitForIdle()
            val stretched = menu.fetchSemanticsNode().boundsInRoot
            val sx = stretched.width / resting.width
            val sy = stretched.height / resting.height
            assertTrue("Press must expand both axes", sx > 1f && sy > 1f)
            assertTrue("Drag must stretch its own axis", if (horizontal) sx > sy + .001f else sy > sx + .001f)
            val center = stretched.center
            // A held pointer must not keep pulling the menu through coordinate feedback.
            compose.mainClock.advanceTimeBy(500)
            val steady = menu.fetchSemanticsNode().boundsInRoot
            assertEquals(center.x, steady.center.x, .5f)
            assertEquals(center.y, steady.center.y, .5f)
            menu.performTouchInput { cancel() }
            compose.waitForIdle()
            val returned = menu.fetchSemanticsNode().boundsInRoot
            assertEquals(resting.left, returned.left, .5f)
            assertEquals(resting.top, returned.top, .5f)
            assertEquals(resting.width, returned.width, .5f)
            assertEquals(resting.height, returned.height, .5f)
            assertTrue(selected.isEmpty())
        }
    }

    @Test
    fun hundredOpenCloseCyclesReleaseHost() {
        fixture()
        repeat(100) {
            open()
            compose.onNodeWithTag("liquid-menu-dismiss").performClick()
            compose.waitForIdle()
        }
        assertEquals(100, dismissals)
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        compose.onNodeWithTag("trigger").assertExists()
    }
    private fun reopenDuringClosing(transition: LiquidMenuTransition, waitForFinish: Boolean = true, hostInset: Int = 0) {
        fixture(transition = transition, hostInset = hostInset)
        open()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("liquid-menu-item-first").performClick()
        compose.mainClock.advanceTimeBy(120)
        val reopen = compose.onNodeWithTag("liquid-menu-reopen")
        reopen.performTouchInput { down(center) }
        // Finish the spring while the new tap is held; its release must still reopen.
        if (waitForFinish) compose.mainClock.advanceTimeBy(2_000)
        reopen.assertExists().performTouchInput { up() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("liquid-menu-content").assertIsDisplayed()
        compose.onNodeWithTag("liquid-menu-reopen").assertDoesNotExist()
        assertEquals(listOf("first"), selected)
        assertEquals(1, dismissals)
        compose.onNodeWithTag("liquid-menu-item-last").performClick()
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        assertEquals(listOf("first", "last"), selected)
    }

    @Test fun transientAnchorReversesBeforeClosingFinishes() = reopenDuringClosing(LiquidMenuTransition.Transient, waitForFinish = false)

    @Test fun attachedAnchorReopensOnANewTapDuringClosing() = reopenDuringClosing(LiquidMenuTransition.Attached)
    @Test fun transientAnchorReopensEvenAfterTheGlassDisappears() = reopenDuringClosing(LiquidMenuTransition.Transient)
    @Test fun insetHostReopensUsingWindowToHostCoordinates() = reopenDuringClosing(LiquidMenuTransition.Transient, hostInset = 32)

    @Test fun detachedAnchorReopensDuringClosing() = reopenDuringClosing(LiquidMenuTransition.Detached)

    @Test fun canceledReopenTapDoesNotLeaveAnOverlay() {
        fixture()
        open()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("liquid-menu-dismiss").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("liquid-menu-reopen").performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("liquid-menu-reopen").performTouchInput { cancel() }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
        compose.onNodeWithTag("liquid-menu-overlay").assertDoesNotExist()
        assertTrue(selected.isEmpty())
    }
}
