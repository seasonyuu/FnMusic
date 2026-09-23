package com.seasonyuu.fnmusic.core.designsystem

import coil3.request.allowHardware
import coil3.toBitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.stateDescription
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.SkipNext
import coil3.compose.AsyncImage
import androidx.compose.ui.res.painterResource
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.Track

@Composable
fun FnGradientBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(FnBackgroundTop, FnBackgroundBottom))),
    ) { content() }
}

@Composable
fun CoverImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    requestSizePx: Int? = null,
    onBitmapLoaded: ((android.graphics.Bitmap) -> Unit)? = null,
) {
    Surface(modifier = modifier.clip(RoundedCornerShape(8.dp)), color = FnCard) {
        val context = LocalContext.current
        val model = remember(url, requestSizePx, onBitmapLoaded != null) {
            ImageRequest.Builder(context)
                .data(url)
                .allowHardware(onBitmapLoaded == null)
                .apply {
                    requestSizePx?.let { size(it, it) }
                }
                .crossfade(true)
                .build()
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Choose visual detail by viewport dp, independently of network resolution/density.
            val coverSize = minOf(maxWidth, maxHeight)
            val placeholder = painterResource(
                when {
                    coverSize <= 64.dp -> R.drawable.cover_placeholder_small
                    coverSize <= 180.dp -> R.drawable.cover_placeholder_medium
                    else -> R.drawable.cover_placeholder
                },
            )
            AsyncImage(
                model = model,
                onSuccess = { result -> onBitmapLoaded?.invoke(result.result.image.toBitmap()) },
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder,
            )
        }
    }
}

@Composable
fun PlaybackToggleIcon(
    state: PlayerState,
    modifier: Modifier = Modifier,
    iconSize: Dp = 32.dp,
    tint: Color = FnTextPrimary,
    contentDescription: String = "播放或暂停",
) {
    var bufferingVisible by remember(state.current?.queueEntryId) { mutableStateOf(false) }
    LaunchedEffect(state.isBuffering, state.current?.queueEntryId) {
        bufferingVisible = false
        if (state.isBuffering) {
            delay(200)
            bufferingVisible = true
        }
    }
    val target = when {
        state.isBuffering && bufferingVisible -> PlaybackGlyph.Buffering
        state.playbackIntentActive -> PlaybackGlyph.Pause
        else -> PlaybackGlyph.Play
    }
    // Keep each layer's animation alive so rapid toggles reverse from its current value.
    // The fixed viewport also prevents the smaller spinner from changing icon geometry.
    Box(
        modifier.size(iconSize).clearAndSetSemantics {
            this.contentDescription = contentDescription
            stateDescription = when {
                state.isBuffering -> "正在缓冲，点击暂停"
                state.isPlaying -> "正在播放"
                else -> "已暂停"
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        PlaybackGlyph.entries.forEach { glyph ->
            val visibility by animateFloatAsState(
                targetValue = if (target == glyph) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (glyph == PlaybackGlyph.Buffering || state.isBuffering) 120 else 160,
                    easing = FastOutSlowInEasing,
                ),
                label = "playback-glyph-$glyph",
            )
            if (visibility > 0f) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        alpha = visibility
                        scaleX = 0.9f + 0.1f * visibility
                        scaleY = scaleX
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (glyph == PlaybackGlyph.Buffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(iconSize * 0.58f),
                            color = tint,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Icon(
                            if (glyph == PlaybackGlyph.Pause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null,
                            Modifier.size(iconSize).offset(x = if (glyph == PlaybackGlyph.Play) 2.dp else 0.dp),
                            tint = tint,
                        )
                    }
                }
            }
        }
    }
}

private enum class PlaybackGlyph { Play, Pause, Buffering }

@Composable
fun TrackRow(
    track: Track,
    coverUrl: String?,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverImage(coverUrl, track.title, Modifier.size(48.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    track.artists.joinToString(" / ") { it.name }.ifBlank { "未知歌手" },
                    track.album?.name?.takeIf(String::isNotBlank),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = FnTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniPlayer(
    state: PlayerState,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpen: () -> Unit,
    leadingControls: (@Composable () -> Unit)? = null,
    trailingControls: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
    playerMorphProgress: Float = 0f,
    onPlayerBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    onCoverBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    surfaceColor: Color = FnNavigationSurface,
) {
    val current = state.current ?: return
    val backdrop = LocalFnBackdrop.current
    val glass = currentLiquidGlassMaterial()
    val interaction = rememberLiquidInteraction(consumeDrag = true)
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        Column(
            modifier
                .fillMaxWidth()
                .height(68.dp)
                .onGloballyPositioned {
                    if (interaction.isIdle) onPlayerBoundsChanged(it.boundsInRoot())
                }
                .graphicsLayer { alpha = 1f - playerMorphProgress.coerceIn(0f, 1f) }
                .then(
                    if (backdrop != null && glass.enabled) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            layerBlock = interaction.layerBlock,
                            effects = {
                                colorControls(brightness = glass.brightness, saturation = 1.5f)
                                blur(1f.dp.toPx() * glass.blurScale)
                                lens(24.dp.toPx(), 24.dp.toPx())
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = 0.32f)
                            },
                            shadow = {
                                Shadow(alpha = 0.28f)
                            },
                            innerShadow = {
                                InnerShadow(radius = 6.dp, alpha = 0.3f)
                            },
                            onDrawSurface = {
                                drawRect(surfaceColor.copy(alpha = glass.surfaceAlpha))
                            },
                        )
                    } else {
                        Modifier.graphicsLayer(interaction.layerBlock).background(surfaceColor.copy(alpha = if (glass.enabled) .90f else glass.surfaceAlpha), Capsule()).liquidSurfaceHighlight()
                    },
                )
                .clip(Capsule())
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onOpen,
                )
                .then(interaction.modifier),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                leadingControls?.invoke()
                CoverImage(
                    current.coverUrl,
                    current.track.title,
                    coverModifier
                        .size(42.dp)
                        .onGloballyPositioned {
                            if (interaction.isIdle) onCoverBoundsChanged(it.boundsInRoot())
                        },
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        current.track.title,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = FnTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        state.outputDeviceName?.let { "AirPlay · $it" } ?: current.track.artists.joinToString(" / ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = FnTextSecondary,
                        maxLines = 1,
                    )
                }
                if (trailingControls != null) trailingControls() else Row {
                    IconButton(onClick = onToggle, modifier = Modifier.size(40.dp)) {
                        PlaybackToggleIcon(state, iconSize = 24.dp)
                    }
                    IconButton(
                        onClick = onNext,
                        enabled = state.isRoaming || state.currentIndex in 0 until state.queue.lastIndex || state.repeatMode != com.seasonyuu.fnmusic.core.model.RepeatMode.Off,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Rounded.SkipNext, "下一首")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingPane(message: String = "正在加载…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LoadingIndicator(color = FnAccent)
            Spacer(Modifier.height(12.dp))
            Text(message, color = FnTextSecondary)
        }
    }
}
