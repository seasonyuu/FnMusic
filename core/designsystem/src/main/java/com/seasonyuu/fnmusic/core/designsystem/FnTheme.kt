package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.seasonyuu.fnmusic.core.designsystem.R

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

val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun FnMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FnColors,
        typography = MaterialTheme.typography.copy(
            displayLarge = MaterialTheme.typography.displayLarge.copy(fontFamily = Montserrat),
            headlineLarge = MaterialTheme.typography.headlineLarge.copy(fontFamily = Montserrat),
            headlineMedium = MaterialTheme.typography.headlineMedium.copy(fontFamily = Montserrat),
            titleLarge = MaterialTheme.typography.titleLarge.copy(fontFamily = Montserrat),
            titleMedium = MaterialTheme.typography.titleMedium.copy(fontFamily = Montserrat),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(fontFamily = Montserrat),
            bodyMedium = MaterialTheme.typography.bodyMedium.copy(fontFamily = Montserrat),
            labelLarge = MaterialTheme.typography.labelLarge.copy(fontFamily = Montserrat),
        ),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
