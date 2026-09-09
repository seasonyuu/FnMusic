package com.seasonyuu.fnmusic.feature.music

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerImmersiveModeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun hidesBarsAndRestoresThemOnExitAndDisposal() {
        val enabled = mutableStateOf(false)
        val mounted = mutableStateOf(true)
        lateinit var view: View
        lateinit var activity: Activity
        compose.setContent {
            view = LocalView.current
            var context: Context = LocalContext.current
            while (context is ContextWrapper && context !is Activity) context = context.baseContext
            activity = context as Activity
            Box(Modifier.fillMaxSize())
            if (mounted.value) PlayerImmersiveMode(enabled.value)
        }
        fun awaitBars(visible: Boolean) {
            compose.waitUntil(5_000) {
                ViewCompat.getRootWindowInsets(view)?.let {
                    it.isVisible(WindowInsetsCompat.Type.statusBars()) == visible &&
                        it.isVisible(WindowInsetsCompat.Type.navigationBars()) == visible
                } == true
            }
        }
        compose.runOnIdle {
            WindowCompat.getInsetsController(activity.window, view)
                .show(WindowInsetsCompat.Type.systemBars())
        }
        awaitBars(true)
        compose.runOnIdle { enabled.value = true }
        awaitBars(false)
        compose.runOnIdle {
            assertEquals(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE,
                WindowCompat.getInsetsController(activity.window, view).systemBarsBehavior,
            )
            enabled.value = false
        }
        awaitBars(true)
        compose.runOnIdle { enabled.value = true }
        awaitBars(false)
        compose.runOnIdle { mounted.value = false }
        awaitBars(true)
    }
}
