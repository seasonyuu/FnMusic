package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FnAccent = Color(0xFFF62C55)
val FnBackgroundTop = Color(0xFF2D293A)
val FnBackgroundBottom = Color(0xFF14121B)
val FnSurface = Color(0xFF24202E)
val FnCard = Color(0x14FFFFFF)
val FnCardPressed = Color(0x1FFFFFFF)
val FnBorder = Color(0x1FFFFFFF)
val FnTextPrimary = Color.White
val FnTextSecondary = Color(0xCCFFFFFF)
val FnTextTertiary = Color(0x99FFFFFF)

private val FnColors = darkColorScheme(
    primary = FnAccent,
    onPrimary = Color.White,
    background = FnSurface,
    onBackground = FnTextPrimary,
    surface = FnSurface,
    surfaceVariant = FnCard,
    onSurface = FnTextPrimary,
    onSurfaceVariant = FnTextSecondary,
    outline = FnBorder,
    error = Color(0xFFFF6B7F),
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun FnMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FnColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
