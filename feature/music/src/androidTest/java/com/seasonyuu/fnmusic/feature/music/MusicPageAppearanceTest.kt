package com.seasonyuu.fnmusic.feature.music

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.seasonyuu.fnmusic.core.designsystem.FnNavigationSurface
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MusicPageAppearanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val navigation = MusicNavigationState()
    private val colors = mutableStateMapOf<String, Color>()
    private val reporters = mutableMapOf<String, (MusicPageAppearance) -> Unit>()
    private val input = DirectNavigationEventInput()
    private val dispatcher = NavigationEventDispatcher()
    private var renderedColor = Color.Unspecified
    private val warm = Color(0xFF604028)
    private val cool = Color(0xFF284960)

    private fun setContent() {
        compose.runOnUiThread { dispatcher.addInput(input) }
        compose.setContent {
            val owner = object : NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = dispatcher
            }
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) {
                MusicPageHost(navigation, true, { navigation.pop() }, { renderedColor = it }) { entry ->
                    val report = LocalMusicPageAppearanceReporter.current
                    SideEffect { reporters[entry.id] = report }
                    colors[entry.id]?.let { PageNavigationAppearance(it) }
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        compose.waitForIdle()
    }

    private fun assertColor(expected: Color) {
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(expected.red, renderedColor.red, .005f)
            assertEquals(expected.green, renderedColor.green, .005f)
            assertEquals(expected.blue, renderedColor.blue, .005f)
        }
    }

    @Test fun dynamicColorBelongsToEntryAndTabsRestoreTheirOwnAppearance() {
        setContent()
        assertColor(FnNavigationSurface)
        compose.runOnIdle {
            navigation.push(morePage = MorePage.Albums)
            colors[navigation.current.id] = warm
        }
        assertColor(warm)
        val albumEntry = navigation.current
        compose.runOnIdle { navigation.select(MusicDestination.Search) }
        assertColor(FnNavigationSurface)
        // A response arriving while its page is hidden updates only that page's cache.
        compose.runOnIdle { reporters.getValue(albumEntry.id)(MusicPageAppearance(cool)) }
        assertColor(FnNavigationSurface)
        compose.runOnIdle {
            colors[albumEntry.id] = cool
            navigation.select(MusicDestination.Home)
        }
        assertColor(cool)
        compose.runOnIdle { navigation.pop() }
        assertColor(FnNavigationSurface)
        compose.runOnIdle { reporters.getValue(albumEntry.id)(MusicPageAppearance(warm)) }
        assertColor(FnNavigationSurface)
        compose.runOnIdle { assertEquals(MusicPageAppearance(), navigation.appearanceFor(albumEntry)) }
    }

    @Test fun paletteCanChangeWithoutNavigating() {
        setContent()
        compose.runOnIdle { colors[navigation.current.id] = warm }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(warm, navigation.appearanceFor(navigation.current).navigationSurfaceColor) }
        assertColor(warm)
        compose.runOnIdle { colors[navigation.current.id] = cool }
        assertColor(cool)
    }

    @Test fun predictiveBackInterpolatesCancelsAndRestoresParentColor() {
        colors[navigation.current.id] = cool
        setContent()
        compose.runOnIdle {
            navigation.push(morePage = MorePage.Albums)
            colors[navigation.current.id] = warm
        }
        assertColor(warm)
        val origin = navigation.current.id
        compose.runOnIdle { input.backStarted(event(0f)) }
        compose.runOnIdle { input.backProgressed(event(.5f)) }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue(renderedColor.red > cool.red && renderedColor.red < warm.red)
            assertTrue(renderedColor.blue > warm.blue && renderedColor.blue < cool.blue)
            assertEquals(origin, navigation.current.id)
        }
        compose.runOnIdle { input.backCancelled() }
        assertColor(warm)
        compose.runOnIdle { input.backStarted(event(0f)) }
        compose.runOnIdle { input.backProgressed(event(.65f)) }
        compose.runOnIdle { input.backCompleted() }
        assertColor(cool)
        assertEquals(0, navigation.current.depth)
    }

    private fun event(progress: Float) = NavigationEvent(progress = progress, swipeEdge = NavigationEvent.EDGE_LEFT)
}
