package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.seasonyuu.fnmusic.core.designsystem.FnNavigationSurface

/** Opaque base color only. Navigation keeps glass opacity and interaction styling consistent. */
@Immutable
internal data class MusicPageAppearance(
    val navigationSurfaceColor: Color = FnNavigationSurface,
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
internal fun PageNavigationAppearance(surfaceColor: Color) {
    val report = LocalMusicPageAppearanceReporter.current
    SideEffect { report(MusicPageAppearance(surfaceColor)) }
}
