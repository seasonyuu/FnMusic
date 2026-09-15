package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.seasonyuu.fnmusic.core.designsystem.FnNavigationSurface

/** Page-owned surface and foreground intent; foreground does not follow intermediate animation frames. */
@Immutable
internal data class MusicPageAppearance(
    val navigationSurfaceColor: Color = Color.Unspecified,
    val darkForeground: Boolean? = null,
)

internal val LocalMusicPageAppearanceReporter =
    staticCompositionLocalOf<(MusicPageAppearance) -> Unit> { {} }

/**
 * Declare at page scope when artwork or page content changes the surrounding surface.
 * Report the target color; MusicPageHost animates it, including predictive back previews.
 * The value belongs to this navigation entry, so outgoing asynchronous results cannot
 * change another page's navigation. Undeclared pages use the default appearance.
 */
@Composable
internal fun PageNavigationAppearance(surfaceColor: Color, darkForeground: Boolean = false) {
    val report = LocalMusicPageAppearanceReporter.current
    SideEffect { report(MusicPageAppearance(surfaceColor, darkForeground)) }
}
