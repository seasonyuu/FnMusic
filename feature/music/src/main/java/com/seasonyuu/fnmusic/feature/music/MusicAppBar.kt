package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.*

// Sample a sibling background, never the ancestor that also draws these buttons.
internal val LocalAppBarBackdrop = staticCompositionLocalOf<Backdrop?> { null }

internal val MusicAppBarHeight = 54.dp
private val MusicAppBarContentHeight = 44.dp

/** Range is chosen by the caller, independently of title visibility or scroll position. */
@Composable
internal fun MusicAppBarBlur(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    fullAppBarBlur: Boolean = true,
    tint: Color = FnBackgroundTop,
    includeSystemInset: Boolean = true,
    statusBarSegmentOnly: Boolean = false,
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // Tune full-range blur by physical position, independently of status-bar height.
    val radiusStops = if (fullAppBarBlur) listOf(
        BlurRadiusStop(0.dp, 32.dp),
        BlurRadiusStop(statusBarHeight, 24.dp),
        BlurRadiusStop(statusBarHeight + 22.dp, 12.dp),
        BlurRadiusStop(statusBarHeight + MusicAppBarContentHeight, 3.dp),
        BlurRadiusStop(statusBarHeight + MusicAppBarHeight, 0.dp),
    ).distinctBy { it.y } else null
    ProgressiveBarBlur(
        backdrop = backdrop, top = true, modifier = modifier, tint = tint,
        extraHeight = if (fullAppBarBlur && !statusBarSegmentOnly) MusicAppBarHeight else 0.dp,
        transitionHeight = 0.dp, fadeStartFraction = 0f, radiusStops = radiusStops,
        includeSystemInset = includeSystemInset,
        maskTopOffset = if (includeSystemInset) 0.dp else statusBarHeight,
        maskBottomExtension = if (fullAppBarBlur && statusBarSegmentOnly) MusicAppBarHeight else 0.dp,
    )
}

@Composable
internal fun MusicAppBar(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleAlpha: () -> Float = { 1f },
    fullAppBarBlur: Boolean = true,
    drawBackgroundBlur: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Box(modifier.fillMaxWidth().height(MusicAppBarHeight)) {
        // Page-owned overlays already draw below the controls. Other bars draw their
        // own background here so the shell blur cannot wash out titles or buttons.
        val backdrop = LocalAppBarBackdrop.current
        if (drawBackgroundBlur && fullAppBarBlur && backdrop != null) {
            MusicAppBarBlur(backdrop, fullAppBarBlur = fullAppBarBlur, includeSystemInset = false)
        }
        Layout(modifier = Modifier.fillMaxWidth().height(MusicAppBarHeight), content = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onBack != null) AppBarButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
            Box {
                if (title != null) Text(title,
                    modifier = Modifier.graphicsLayer { alpha = titleAlpha() }
                        .then(if (titleAlpha() == 0f) Modifier.clearAndSetSemantics {} else Modifier),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }) { measurables, constraints ->
            val contentHeight = MusicAppBarContentHeight.roundToPx().coerceAtMost(constraints.maxHeight)
            val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = contentHeight)
            val left = measurables[0].measure(loose)
            val right = measurables[1].measure(loose)
            val reserve = maxOf(left.width, right.width) + 8.dp.roundToPx()
            val title = measurables[2].measure(loose.copy(maxWidth = (constraints.maxWidth - reserve * 2).coerceAtLeast(0)))
            layout(constraints.maxWidth, constraints.maxHeight) {
                left.placeRelative(0, (contentHeight - left.height) / 2)
                right.placeRelative(constraints.maxWidth - right.width, (contentHeight - right.height) / 2)
                title.placeRelative((constraints.maxWidth - title.width) / 2, (contentHeight - title.height) / 2)
            }
        }
    }
}

@Composable
internal fun AppBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    menuAnchor: LiquidMenuAnchorScope? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalAppBarBackdrop.current ?: rememberLayerBackdrop()
    LiquidButton(onClick = onClick, backdrop = backdrop, enabled = enabled,
        modifier = modifier.height(MusicAppBarContentHeight).widthIn(min = 44.dp)
            .then(menuAnchor?.surfaceModifier() ?: Modifier),
        foregroundModifier = menuAnchor?.foregroundModifier ?: Modifier,
        contentPadding = PaddingValues(horizontal = 10.dp), content = content)
}

/** Standard app-bar menu adapter: business callers only supply entries and actions. */
@Composable
internal fun AppBarMenu(
    items: List<LiquidMenuEntry>,
    onSelect: (String) -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val backdrop = LocalAppBarBackdrop.current ?: rememberLayerBackdrop()
    LiquidMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        onExpandedChange = { expanded = it },
        backdrop = backdrop,
        items = items,
        onSelect = onSelect,
        transition = LiquidMenuTransition.Attached,
        trigger = { toggle -> AppBarButton(toggle, menuAnchor = this, content = content) },
    )
}
