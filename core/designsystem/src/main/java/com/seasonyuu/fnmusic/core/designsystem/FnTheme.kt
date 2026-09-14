package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

val FnAccent = Color(0xFFF62C55)

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
fun FnMusicTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (darkTheme) FnDarkPalette else FnLightPalette
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    CompositionLocalProvider(LocalFnPalette provides palette) {
        MaterialTheme(
            colorScheme = base.copy(
                primary = FnAccent, onPrimary = Color.White,
                background = palette.surface, onBackground = palette.primary,
                surface = palette.surface, onSurface = palette.primary,
                surfaceVariant = palette.card, onSurfaceVariant = palette.secondary,
                outline = palette.border,
                error = if (darkTheme) Color(0xFFFF6B7F) else Color(0xFFB51E3B),
            ),
            motionScheme = MotionScheme.expressive(), content = content,
        )
    }
}
