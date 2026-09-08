package com.seasonyuu.fnmusic.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

private val LocalLiquidTabScale = staticCompositionLocalOf { 1f }

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    showSelectionIndicator: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    if (tabsCount == 0) return
    val glass = currentLiquidGlassMaterial()
    val scope = rememberCoroutineScope()
    val tabsBackdrop = rememberLayerBackdrop()
    val panelOffset = remember { Animatable(0f) }
    val useBackdrop = true
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val tabWidth = (maxWidth - 8.dp) / tabsCount
        val density = androidx.compose.ui.platform.LocalDensity.current
        val tabWidthPx = with(density) { tabWidth.toPx() }
        val fraction = (panelOffset.value / maxWidth.value).coerceIn(-1f, 1f)
        val panelPx = with(density) { 4.dp.toPx() * fraction.sign * (abs(fraction) * (2f - abs(fraction))) }
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex.coerceIn(0, tabsCount - 1)) }
        val dragAnimation = remember(scope, tabsCount, tabWidthPx) {
            DampedDragAnimation(
                animationScope = scope,
                initialValue = selectedTabIndex.coerceIn(0, tabsCount - 1).toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStopped = {
                    currentIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                    animateToValue(currentIndex.toFloat())
                    scope.launch {
                        panelOffset.animateTo(0f, spring(stiffness = 300f, dampingRatio = 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue((targetValue + dragAmount.x / tabWidthPx).coerceIn(0f, (tabsCount - 1).toFloat()))
                    scope.launch { panelOffset.snapTo(panelOffset.value + dragAmount.x) }
                },
            )
        }
        LaunchedEffect(selectedTabIndex, tabsCount) {
            currentIndex = selectedTabIndex.coerceIn(0, tabsCount - 1)
        }
        LaunchedEffect(dragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }
        CompositionLocalProvider(LocalLiquidTabScale provides lerp(1f, 1.2f, dragAnimation.pressProgress)) {
            Row(
                Modifier
                    .graphicsLayer { translationX = panelPx }
                    .then(
                        if (useBackdrop) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { Capsule() },
                                effects = {
                                    colorControls(brightness = glass.brightness, saturation = 1.5f)
                                    blur(4.dp.toPx() * glass.blurScale)
                                    lens(24.dp.toPx(), 24.dp.toPx())
                                },
                                layerBlock = {
                                    val scale = lerp(1f, 1f + 16.dp.toPx() / size.width, dragAnimation.pressProgress)
                                    scaleX = scale
                                    scaleY = scale
                                },
                                onDrawSurface = { drawRect(glass.surfaceColor) },
                            )
                        } else {
                            Modifier.background(glass.surfaceColor, Capsule())
                        },
                    )
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
            if (useBackdrop) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)
                        .graphicsLayer { translationX = panelPx }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { Capsule() },
                            effects = {
                                colorControls(brightness = glass.brightness, saturation = 1.5f)
                                blur(4.dp.toPx() * glass.blurScale)
                                lens(
                                    24.dp.toPx() * dragAnimation.pressProgress,
                                    24.dp.toPx() * dragAnimation.pressProgress,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                            },
                            onDrawSurface = {
                                drawRect(glass.surfaceColor)
                            },
                        )
                        .height(56.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .graphicsLayer(colorFilter = ColorFilter.tint(FnAccent)),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
            if (showSelectionIndicator) Box(Modifier.padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = dragAnimation.value * tabWidthPx + panelPx
                }
                .then(dragAnimation.modifier)
                .then(
                    if (useBackdrop) {
                        Modifier.drawBackdrop(
                            backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                            shape = { Capsule() },
                            effects = {
                                lens(
                                    10.dp.toPx() * dragAnimation.pressProgress,
                                    14.dp.toPx() * dragAnimation.pressProgress,
                                    chromaticAberration = true,
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                            },
                            shadow = {
                                Shadow(alpha = dragAnimation.pressProgress)
                            },
                            innerShadow = {
                                InnerShadow(
                                    radius = 8.dp * dragAnimation.pressProgress,
                                    alpha = dragAnimation.pressProgress,
                                )
                            },
                            layerBlock = {
                                scaleX = dragAnimation.scaleX
                                scaleY = dragAnimation.scaleY
                                val velocity = dragAnimation.velocity / 10f
                                scaleX /= 1f - (velocity * .75f).coerceIn(-.2f, .2f)
                                scaleY *= 1f - (velocity * .25f).coerceIn(-.2f, .2f)
                            },
                            onDrawSurface = {
                                drawRect(Color.White.copy(alpha = .12f * (1f - dragAnimation.pressProgress)))
                                drawRect(FnAccent.copy(alpha = .08f * dragAnimation.pressProgress))
                            },
                        )
                    } else {
                        Modifier.background(Color.White.copy(alpha = .12f), Capsule())
                    },
                )
                .height(56.dp).fillMaxWidth(1f / tabsCount)
                .testTag("liquid-bottom-tabs-indicator")
            )
        }
    }
}

@Composable
fun RowScope.LiquidBottomTab(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalLiquidTabScale.current
    Column(modifier.clip(Capsule()).clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick).fillMaxHeight().weight(1f).graphicsLayer { scaleX = scale; scaleY = scale }, verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally, content = content)
}
