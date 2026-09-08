package com.seasonyuu.fnmusic.feature.music

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MusicPageHostTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val navigation = MusicNavigationState()
    private val dispatcher = NavigationEventDispatcher()
    private val input = DirectNavigationEventInput()

    private fun setContent() {
        compose.runOnUiThread { dispatcher.addInput(input) }
        compose.setContent {
            val owner = object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = dispatcher
            }
            val savedState = rememberSaveableStateHolder()
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides owner,
            ) {
                MusicPageHost(navigation, backEnabled = true, onPop = { navigation.pop() }) { entry ->
                    savedState.SaveableStateProvider(entry.id) {
                        Box(Modifier.fillMaxSize().testTag("page-${entry.depth}"))
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun forwardEntersFromRightAndBackExitsToRight() {
        setContent()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { navigation.push(morePage = MorePage.Albums) }
        compose.mainClock.advanceTimeBy(96)
        val entering = compose.onNodeWithTag("page-1").fetchSemanticsNode().boundsInRoot
        assertTrue("Forward page must enter from the right", entering.left > 0f)
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle { navigation.pop() }
        compose.mainClock.advanceTimeBy(96)
        val exiting = compose.onNodeWithTag("page-1").fetchSemanticsNode().boundsInRoot
        assertTrue("Back must move the outgoing page right", exiting.left > 0f)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithTag("page-0").assertIsDisplayed()
    }

    @Test fun predictiveBackSeeksCancelsAndCommitsOnlyOneEntry() {
        setContent()
        compose.runOnIdle {
            navigation.push(morePage = MorePage.Albums)
            navigation.push(morePage = MorePage.Playlists)
        }
        compose.waitForIdle()
        val origin = navigation.current.id
        compose.runOnIdle { input.backStarted(event(0f)) }
        compose.runOnIdle { input.backProgressed(event(.35f)) }
        compose.waitForIdle()
        val early = compose.onNodeWithTag("page-2").fetchSemanticsNode().boundsInRoot.left
        compose.runOnIdle { input.backProgressed(event(.7f)) }
        compose.waitForIdle()
        val late = compose.onNodeWithTag("page-2").fetchSemanticsNode().boundsInRoot.left
        assertTrue("Page follows gesture progress", late > early && early > 0f)
        assertEquals(origin, navigation.current.id)
        compose.runOnIdle { input.backCancelled() }
        compose.waitForIdle()
        assertEquals(origin, navigation.current.id)
        assertEquals(0f, compose.onNodeWithTag("page-2").fetchSemanticsNode().boundsInRoot.left, 1f)

        compose.runOnIdle { input.backStarted(event(0f)) }
        compose.runOnIdle { input.backProgressed(event(.6f)) }
        compose.waitForIdle()
        compose.runOnIdle { input.backCompleted() }
        compose.waitForIdle()
        assertEquals(1, navigation.current.depth)
        compose.onNodeWithTag("page-1").assertIsDisplayed()
        compose.runOnIdle { input.backCompleted() }
        compose.waitForIdle()
        assertEquals(0, navigation.current.depth)
    }

    private fun event(progress: Float) = NavigationEvent(progress = progress, touchY = 200f, swipeEdge = NavigationEvent.EDGE_LEFT)
}
