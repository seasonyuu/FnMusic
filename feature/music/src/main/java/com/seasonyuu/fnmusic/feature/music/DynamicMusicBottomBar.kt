package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.seasonyuu.fnmusic.core.designsystem.CoverImage
import com.seasonyuu.fnmusic.core.designsystem.DynamicBottomBarGeometryCalculator
import com.seasonyuu.fnmusic.core.designsystem.FloatRect
import com.seasonyuu.fnmusic.core.designsystem.FnAccent
import com.seasonyuu.fnmusic.core.designsystem.FnTextPrimary
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary
import com.seasonyuu.fnmusic.core.designsystem.LiquidBottomTab
import com.seasonyuu.fnmusic.core.designsystem.LiquidBottomTabs
import com.seasonyuu.fnmusic.core.designsystem.LiquidButton
import com.seasonyuu.fnmusic.core.designsystem.rememberLiquidInteraction
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.RepeatMode
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DynamicMusicBottomBar(
    state: PlayerState,
    selectedDestination: MusicDestination,
    expansionProgress: Float,
    playerExpansionProgress: Float,
    backdrop: Backdrop,
    onDestinationSelected: (MusicDestination) -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    onExpand: () -> Unit,
    playerMorphProgress: Float = 0f,
    onPlayerBoundsChanged: (Rect) -> Unit = {},
    onCoverBoundsChanged: (Rect) -> Unit = {},
    coverModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val current = state.current
    val density = LocalDensity.current
    val primaryDestinations = remember { MusicDestination.entries.filterNot { it == MusicDestination.Search } }
    var lastPrimaryDestination by remember { mutableStateOf(MusicDestination.Home) }
    LaunchedEffect(selectedDestination) {
        if (selectedDestination != MusicDestination.Search) lastPrimaryDestination = selectedDestination
    }
    val compactDestination = if (selectedDestination == MusicDestination.Search) lastPrimaryDestination else selectedDestination

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(DynamicBottomBarGeometryCalculator.ContainerHeightDp.dp)
            .testTag("dynamic-bottom-bar"),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val geometry = DynamicBottomBarGeometryCalculator.calculate(
            widthPx = widthPx,
            density = density.density,
            expansionProgress = expansionProgress,
            playerExpansionProgress = playerExpansionProgress,
        )

        val expandedTabsAlpha = interval(expansionProgress, 0.18f, 0.62f)
        if (expandedTabsAlpha > 0.001f) {
            LiquidBottomTabs(
                selectedTabIndex = primaryDestinations.indexOf(selectedDestination).takeIf { it >= 0 }
                    ?: primaryDestinations.indexOf(lastPrimaryDestination),
                onTabSelected = { onDestinationSelected(primaryDestinations[it]) },
                backdrop = backdrop,
                tabsCount = primaryDestinations.size,
                showSelectionIndicator = selectedDestination != MusicDestination.Search,
                modifier = Modifier
                    .place(geometry.primaryTabs, density)
                    .graphicsLayer { alpha = expandedTabsAlpha }
                    .testTag("dynamic-primary-tabs"),
            ) {
                primaryDestinations.forEach { destination ->
                    LiquidBottomTab(onClick = { onDestinationSelected(destination) }) {
                        Icon(
                            destination.icon,
                            destination.label,
                            tint = if (selectedDestination == destination) FnAccent else FnTextSecondary,
                        )
                        Text(
                            destination.label,
                            color = if (selectedDestination == destination) FnTextPrimary else FnTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
            }
        }

        val compactTabAlpha = 1f - interval(expansionProgress, 0.16f, 0.58f)
        if (compactTabAlpha > 0.001f) {
            Box(
                Modifier
                    .place(geometry.primaryTabs, density)
                    .graphicsLayer {
                        alpha = compactTabAlpha
                        val scale = 0.96f + compactTabAlpha * 0.04f
                        scaleX = scale
                        scaleY = scale
                    }
                    .glassCapsule(backdrop)
                    .clip(Capsule())
                    .clickable(onClick = onExpand)
                    .semantics { selected = selectedDestination != MusicDestination.Search }
                    .testTag("dynamic-primary-tab"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    compactDestination.icon,
                    compactDestination.label,
                    tint = if (selectedDestination == MusicDestination.Search) FnTextSecondary else FnAccent,
                )
            }
        }

        LiquidButton(
            onClick = { onDestinationSelected(MusicDestination.Search) },
            backdrop = backdrop,
            modifier = Modifier
                .place(geometry.search, density)
                .semantics { selected = selectedDestination == MusicDestination.Search }
                .testTag("dynamic-search"),
        ) {
            Icon(
                MusicDestination.Search.icon,
                MusicDestination.Search.label,
                tint = if (selectedDestination == MusicDestination.Search) FnAccent else FnTextSecondary,
            )
        }

        if (current != null) {
            LiquidMiniPlayer(
                state = state, backdrop = backdrop,
                onToggle = onToggle, onNext = onNext, onOpenPlayer = onOpenPlayer,
                modifier = Modifier.place(geometry.player, density),
                coverSize = with(density) { geometry.cover.width.toDp() },
                coverModifier = coverModifier, playerMorphProgress = playerMorphProgress,
                onPlayerBoundsChanged = onPlayerBoundsChanged, onCoverBoundsChanged = onCoverBoundsChanged,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiquidMiniPlayer(
    state: PlayerState,
    backdrop: Backdrop,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    coverSize: androidx.compose.ui.unit.Dp = 32.dp,
    coverModifier: Modifier = Modifier,
    playerMorphProgress: Float = 0f,
    onPlayerBoundsChanged: (Rect) -> Unit = {},
    onCoverBoundsChanged: (Rect) -> Unit = {},
    coverContent: (@Composable (Modifier) -> Unit)? = null,
) {
    val current = state.current ?: return
    val interaction = rememberLiquidInteraction(consumeDrag = true)
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Row(
            modifier
                .onGloballyPositioned {
                    if (interaction.isIdle) onPlayerBoundsChanged(it.boundsInRoot())
                }
                .graphicsLayer { alpha = 1f - playerMorphProgress.coerceIn(0f, 1f) }
                .glassCapsule(backdrop, interaction.layerBlock, refractionHeight = 24.dp, refractionAmount = 24.dp)
                .clip(Capsule())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onOpenPlayer,
                )
                .then(interaction.modifier)
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .testTag("dynamic-mini-player"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val artworkModifier = coverModifier.size(coverSize)
                .onGloballyPositioned {
                    if (interaction.isIdle) onCoverBoundsChanged(it.boundsInRoot())
                }.testTag("dynamic-cover")
            if (coverContent != null) {
                coverContent(artworkModifier)
            } else {
                CoverImage(current.coverUrl, current.track.title, artworkModifier)
            }
            Column(Modifier.weight(1f)) {
                Text(current.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    current.track.artists.joinToString(" / ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = FnTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "播放或暂停")
            }
            IconButton(
                onClick = onNext,
                enabled = state.isRoaming || state.currentIndex in 0 until state.queue.lastIndex || state.repeatMode != RepeatMode.Off,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Rounded.SkipNext, "下一首")
            }
        }
    }
}

private fun Modifier.place(rect: FloatRect, density: Density): Modifier = this
    .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
    .size(
        width = with(density) { rect.width.toDp() },
        height = with(density) { rect.height.toDp() },
    )

@Composable
private fun Modifier.glassCapsule(
    backdrop: Backdrop,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
    refractionHeight: androidx.compose.ui.unit.Dp = 6.dp,
    refractionAmount: androidx.compose.ui.unit.Dp = 8.dp,
): Modifier {
    val glass = com.seasonyuu.fnmusic.core.designsystem.currentLiquidGlassMaterial()
    return drawBackdrop(
        backdrop = backdrop,
        shape = { Capsule() },
        layerBlock = layerBlock,
        effects = {
            colorControls(brightness = glass.brightness, saturation = 1.5f)
            blur(1f.dp.toPx() * glass.blurScale)
            lens(refractionHeight.toPx(), refractionAmount.toPx())
        },
        highlight = { Highlight.Default.copy(alpha = 0.3f) },
        shadow = { Shadow(alpha = 0.24f) },
        innerShadow = { InnerShadow(radius = 6.dp, alpha = 0.26f) },
        onDrawSurface = { drawRect(glass.surfaceColor) },
    )
}

private fun interval(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)
