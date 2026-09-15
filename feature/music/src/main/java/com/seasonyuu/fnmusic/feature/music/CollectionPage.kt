package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.kyant.backdrop.backdrops.*
import com.seasonyuu.fnmusic.core.designsystem.*

/** Blur samples content; buttons sample content plus blur, excluding themselves. */
@Composable
internal fun CollectionPage(
    title: String,
    onBack: (() -> Unit)?,
    headingGone: () -> Boolean,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (Modifier, Dp) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val appBarBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding() + 64.dp
    var origin by remember { mutableFloatStateOf(0f) }
    var headingBottom by remember { mutableStateOf<Float?>(null) }
    // Read at draw time as well, so layout/scroll updates need no timed transition.
    val alpha = { if (headingGone()) 1f else headingBottom?.let {
        ((with(density) { (top + 24.dp).toPx() } - (it - origin)) /
            with(density) { 24.dp.toPx() }).coerceIn(0f, 1f)
    } ?: 0f }
    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot().y }) {
        Box(Modifier.fillMaxSize().layerBackdrop(appBarBackdrop)) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)
                .background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom)))) {
                content(Modifier.testTag("collection-heading").onGloballyPositioned {
                    headingBottom = it.positionInRoot().y + it.size.height
                }, top + 12.dp)
            }
            ProgressiveBarBlur(backdrop, true, Modifier, extraHeight = 64.dp)
        }
        CompositionLocalProvider(LocalAppBarBackdrop provides appBarBackdrop) {
            MusicAppBar(title, onBack = onBack, titleAlpha = alpha, actions = actions,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 20.dp, vertical = 8.dp).testTag("collection-app-bar"))
        }
    }
}

@Composable
internal fun CollectionHeading(title: String, count: String, headingModifier: Modifier) {
    Text(title, headingModifier, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    Text(count, color = FnTextSecondary)
}

@Composable
internal fun CollectionPlayButton(enabled: Boolean, onClick: () -> Unit) {
    FilledTonalButton(onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("collection-play-all"),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .06f),
            contentColor = FnTextPrimary)) {
        Icon(Icons.Rounded.PlayArrow, null)
        Spacer(Modifier.width(6.dp))
        Text("播放全部")
    }
}
