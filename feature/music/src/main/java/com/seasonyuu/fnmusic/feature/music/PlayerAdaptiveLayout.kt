package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
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

internal data class PlayerContentInsets(val start: Dp, val end: Dp)

internal fun portraitPlayerContentInsets(width: Dp, safeStart: Dp, safeEnd: Dp): PlayerContentInsets {
    val safeWidth = (width - safeStart - safeEnd).coerceAtLeast(0.dp)
    val contentWidth = minOf((safeWidth - PortraitPlayerHorizontalPadding * 2).coerceAtLeast(0.dp), 380.dp)
    val gutter = (safeWidth - contentWidth) / 2
    return PlayerContentInsets(safeStart + gutter, safeEnd + gutter)
}

/** The budget excludes the elastic gap itself, so measuring it cannot feed back into the budget. */
internal fun playerUtilitiesGap(availableHeight: Dp, fixedHeight: Dp, desiredCover: Dp): Dp =
    (availableHeight - fixedHeight - desiredCover).coerceIn(12.dp, 48.dp)

internal const val PlayerUtilitiesGapLayoutId = "player-utilities-gap"

/** Measures fixed controls once before allocating the explicitly identified elastic spacer. */
@Composable
internal fun PlayerControlsLayout(
    desiredHeight: Dp?,
    spacing: Dp,
    content: @Composable () -> Unit,
) {
    if (desiredHeight == null) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) { content() }
    } else {
        androidx.compose.ui.layout.Layout(content = content, modifier = Modifier.fillMaxWidth()) { measurables, constraints ->
            val gapIndex = measurables.indexOfFirst { it.layoutId == PlayerUtilitiesGapLayoutId }
            check(gapIndex >= 0) { "Adaptive controls require an elastic utilities spacer" }
            val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = androidx.compose.ui.unit.Constraints.Infinity)
            val fixed = measurables.mapIndexed { index, measurable ->
                if (index == gapIndex) null else measurable.measure(childConstraints)
            }
            val spacingPx = spacing.roundToPx()
            val fixedHeight = fixed.sumOf { it?.height ?: 0 } + spacingPx * (measurables.size - 1)
            val gap = playerUtilitiesGap(desiredHeight, fixedHeight.toDp(), 0.dp).roundToPx()
            val spacer = measurables[gapIndex].measure(childConstraints.copy(minHeight = gap, maxHeight = gap))
            layout(constraints.maxWidth, fixedHeight + gap) {
                var y = 0
                fixed.forEach { child ->
                    val placeable = child ?: spacer
                    placeable.placeRelative(0, y)
                    y += placeable.height + spacingPx
                }
            }
        }
    }
}

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
        val horizontalPadding = if (width < 600.dp) portraitPlayerContentInsets(width, 0.dp, 0.dp).start else 16.dp
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
    controls: @Composable (Dp?) -> Unit,
    adaptiveUtilitiesGap: Boolean = false,
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
                Box(Modifier.size(coverSize).testTag("player-artwork-slot").then(largeCoverModifier))
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
            // A transparent Surface still clips its children. Keep the slider's horizontal
            // expansion visible outside this slot while preserving the scrollable height limit.
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        // Even with large fonts, leave a scrollable viewport for track information.
                        .heightIn(
                            max =
                                if (geometry.short && immersiveMode) availableHeight * .55f
                                else availableHeight - minOf(72.dp, availableHeight * .2f)
                        )
                        // Keep measuring the controls for a stable height budget, but do not
                        // leave invisible interactive children over the expanded list.
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            layout(placeable.width, placeable.height) {
                                if (controlsAlpha > 0.01f) placeable.placeRelative(0, 0)
                            }
                        }
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
            ) {
                CompositionLocalProvider(LocalContentColor provides FnTextPrimary) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        controls(if (adaptiveUtilitiesGap)
                            availableHeight - geometry.playerWidth - metadataHeight - sectionGap * 2 - 6.dp
                        else null)
                    }
                }
            }
        }
    }
}
