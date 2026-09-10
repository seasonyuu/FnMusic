package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary

internal val PortraitPlayerHorizontalPadding = 40.dp
internal val LandscapePlayerTopPadding = 44.dp
internal val LandscapePlayerBottomPadding = 32.dp

/** Geometry uses the actual safe window, including when resized in split screen. */
internal data class PlayerLayoutGeometry(
    val wide: Boolean,
    val short: Boolean,
    val contentStart: Dp,
    val playerWidth: Dp,
    val detailStart: Dp,
    val detailWidth: Dp,
)

internal fun isShortLandscapePlayer(width: Dp, height: Dp): Boolean =
    width >= 600.dp && height < 500.dp && width > height

internal fun playerLayoutGeometry(width: Dp, height: Dp, shortLandscape: Boolean = isShortLandscapePlayer(width, height)): PlayerLayoutGeometry {
    if (shortLandscape) {
        val contentWidth = minOf(width, 1440.dp) - 64.dp
        val start = (width - contentWidth) / 2
        // Derive the unscaled square from the shared vertical margins.
        val coverWidth = (height - LandscapePlayerTopPadding - LandscapePlayerBottomPadding).coerceAtLeast(0.dp)
        return PlayerLayoutGeometry(
            true,
            true,
            start,
            coverWidth,
            start + coverWidth + 32.dp,
            contentWidth - coverWidth - 32.dp,
        )
    }
    val wide = width >= 840.dp
    if (!wide) {
        val horizontalPadding = if (width < 600.dp) PortraitPlayerHorizontalPadding else 16.dp
        val playerWidth =
            minOf((width - horizontalPadding * 2).coerceAtLeast(0.dp), if (width < 600.dp) 380.dp else 440.dp)
        return PlayerLayoutGeometry(
            false,
            height < 600.dp,
            (width - playerWidth) / 2,
            playerWidth,
            0.dp,
            width
        )
    }
    val contentWidth = minOf(width, 1440.dp) - 64.dp
    val start = (width - contentWidth) / 2
    val playerWidth = minOf((contentWidth - 48.dp) * .42f, 480.dp)
    return PlayerLayoutGeometry(
        true,
        height < 600.dp,
        start,
        playerWidth,
        start + playerWidth + 48.dp,
        contentWidth - playerWidth - 48.dp
    )
}

/**
 * Leave room for measured text and controls; scroll the artwork section when space is exhausted.
 */
internal fun playerCoverSize(
    width: Dp,
    height: Dp,
    metadata: Dp,
    controls: Dp,
    gap: Dp,
    maximum: Dp
): Dp =
    minOf(width, maximum, (height - metadata - controls - gap * 2 - 6.dp).coerceAtLeast(48.dp))
        .coerceAtLeast(0.dp)

/** Shared artwork/metadata/controls column; only the surrounding pane geometry changes. */
@Composable
internal fun PlayerPrimaryPane(
    geometry: PlayerLayoutGeometry,
    availableHeight: Dp,
    maximumCoverSize: Dp,
    immersiveMode: Boolean,
    metadataAlpha: Float,
    controlsAlpha: Float,
    controlsTranslationPx: Float,
    largeCoverModifier: Modifier,
    coverViewportModifier: Modifier,
    onControlsPositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
    metadata: @Composable () -> Unit,
    controls: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    var metadataHeight by remember { mutableStateOf(74.dp) }
    var controlsHeight by remember { mutableStateOf(244.dp) }
    val sectionGap = if (geometry.short) 12.dp else 24.dp
    val coverSize =
        playerCoverSize(
            geometry.playerWidth,
            availableHeight,
            metadataHeight,
            controlsHeight,
            sectionGap,
            maximumCoverSize
        )
    // Metadata and controls share a stable gutter with lyrics even if height limits the artwork.
    val primaryWidth = geometry.playerWidth
    val primaryStart = geometry.contentStart + (geometry.playerWidth - primaryWidth) / 2
    val artworkScrollState = rememberScrollState()
    Box(modifier) {
        Column(
            Modifier.offset(x = primaryStart)
                .width(primaryWidth)
                .fillMaxHeight()
                .testTag("player-primary-pane"),
        ) {
            Column(
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .then(coverViewportModifier)
                    .testTag("player-artwork-viewport")
                    // Disabled scroll containers still participate in hit testing. Remove the
                    // modifier in immersive mode so lyrics and queue gestures reach their pane.
                    .then(
                        if (immersiveMode) Modifier else Modifier.verticalScroll(artworkScrollState)
                    )
                    .padding(top = if (geometry.short) 0.dp else 6.dp, bottom = sectionGap),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(sectionGap),
            ) {
                Box(Modifier.size(coverSize).then(largeCoverModifier))
                Box(
                    Modifier.fillMaxWidth()
                        .onSizeChanged { metadataHeight = with(density) { it.height.toDp() } }
                        .graphicsLayer { alpha = metadataAlpha }
                        .then(
                            if (metadataAlpha < 0.1f) Modifier.clearAndSetSemantics {} else Modifier
                        ),
                ) {
                    if (metadataAlpha >= 0.1f) metadata()
                    else Spacer(Modifier.height(metadataHeight))
                }
            }
            Surface(
                color = Color.Transparent,
                contentColor = FnTextPrimary,
                modifier =
                    Modifier.fillMaxWidth()
                        // Even with large fonts, leave a scrollable viewport for track information.
                        .heightIn(
                            max =
                                if (geometry.short && immersiveMode) availableHeight * .55f
                                else availableHeight - minOf(72.dp, availableHeight * .2f)
                        )
                        .graphicsLayer {
                            alpha = controlsAlpha
                            translationY = controlsTranslationPx
                        }
                        .onGloballyPositioned {
                            controlsHeight = with(density) { it.size.height.toDp() }
                            onControlsPositioned(it)
                        }
                        .then(
                            if (controlsAlpha < 0.1f) Modifier.clearAndSetSemantics {} else Modifier
                        )
                        .testTag("player-bottom-controls"),
            ) { Column(Modifier.verticalScroll(rememberScrollState())) { controls() } }
        }
    }
}
