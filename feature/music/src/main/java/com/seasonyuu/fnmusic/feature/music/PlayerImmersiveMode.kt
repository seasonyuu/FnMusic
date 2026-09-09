package com.seasonyuu.fnmusic.feature.music

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Own system bars only for the lifetime of the immersive player. */
@Composable
internal fun PlayerImmersiveMode(enabled: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(enabled, context, view) {
        val window = context.playerActivity()?.window
        if (!enabled || window == null) return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        val previousBehavior = controller.systemBarsBehavior
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller.show(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = previousBehavior
        }
    }
}

private tailrec fun Context.playerActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.playerActivity()
        else -> null
    }
