package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver

fun contrastRatio(foreground: Color, background: Color): Float {
    val a = foreground.luminance()
    val b = background.luminance()
    return (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)
}

fun contrastingForeground(background: Color): Color =
    if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) Color.Black else Color.White

val FnAccentIcon: Color @Composable @ReadOnlyComposable get() =
    if (contrastRatio(FnAccent, FnSurface) >= 3f) FnAccent else FnTextPrimary

val LocalFnAccent = staticCompositionLocalOf { Color(0xFFF62C55) }
val FnAccent: Color @Composable @ReadOnlyComposable get() = LocalFnAccent.current

data class FnPalette(
    val navigation: Color, val top: Color, val bottom: Color, val surface: Color,
    val card: Color, val pressed: Color, val border: Color,
    val primary: Color, val secondary: Color, val tertiary: Color,
)
val FnDarkPalette = FnPalette(
    Color(0xFF14121B), Color(0xFF2D293A), Color(0xFF14121B), Color(0xFF24202E),
    Color(0x14FFFFFF), Color(0x1FFFFFFF), Color(0x1FFFFFFF),
    Color.White, Color(0xCCFFFFFF), Color(0x99FFFFFF),
)
val FnLightPalette = FnPalette(
    Color(0xFFF5F3F8), Color(0xFFF5F1FA), Color(0xFFFAF9FC), Color(0xFFF5F3F8),
    Color.White, Color(0xFFEAE4F0), Color(0xFFD7D0DF),
    Color(0xFF211D29), Color(0xFF615B6D), Color(0xFF787181),
)
val LocalFnPalette = staticCompositionLocalOf { FnDarkPalette }
val FnNavigationSurface: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.navigation
val FnBackgroundTop: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.top
val FnBackgroundBottom: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.bottom
val FnSurface: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.surface
val FnCard: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.card
val FnCardPressed: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.pressed
val FnBorder: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.border
val FnTextPrimary: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.primary
val FnTextSecondary: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.secondary
val FnTextTertiary: Color @Composable @ReadOnlyComposable get() = LocalFnPalette.current.tertiary

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun FnMusicTheme(darkTheme: Boolean = true, accent: Color = FnAccent, content: @Composable () -> Unit) {
    val palette = if (darkTheme) FnDarkPalette else FnLightPalette
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalFnPalette provides palette, LocalFnAccent provides accent, LocalContentColor provides palette.primary) {
        MaterialTheme(
            colorScheme = base.copy(
                primary = accent, onPrimary = contrastingForeground(accent),
                primaryContainer = accent.copy(alpha = .14f).compositeOver(palette.surface), onPrimaryContainer = palette.primary,
                secondary = accent, onSecondary = contrastingForeground(accent),
                tertiary = accent, onTertiary = contrastingForeground(accent),
                secondaryContainer = accent.copy(alpha = .14f).compositeOver(palette.surface), onSecondaryContainer = palette.primary,
                tertiaryContainer = accent.copy(alpha = .14f).compositeOver(palette.surface), onTertiaryContainer = palette.primary,
                background = palette.surface, onBackground = palette.primary,
                surface = palette.surface, onSurface = palette.primary,
                surfaceVariant = palette.card, onSurfaceVariant = palette.secondary,
                surfaceDim = palette.surface, surfaceBright = palette.top,
                surfaceContainerLowest = palette.bottom, surfaceContainerLow = palette.surface,
                surfaceContainer = palette.surface, surfaceContainerHigh = palette.pressed.compositeOver(palette.surface),
                surfaceContainerHighest = palette.pressed.compositeOver(palette.surface), surfaceTint = Color.Transparent,
                inverseSurface = if (darkTheme) FnLightPalette.surface else FnDarkPalette.surface,
                inverseOnSurface = if (darkTheme) FnLightPalette.primary else FnDarkPalette.primary,
                inversePrimary = if (darkTheme) FnLightPalette.primary else FnDarkPalette.primary,
                outline = palette.secondary, outlineVariant = palette.border,
                error = if (darkTheme) Color(0xFFFF6B7F) else Color(0xFFB51E3B),
            ),
            motionScheme = MotionScheme.expressive(), content = content,
        )
    }
}
