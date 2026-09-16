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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.*

// Sample a sibling background, never the ancestor that also draws these buttons.
internal val LocalAppBarBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
internal fun MusicAppBar(
    title: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    titleAlpha: () -> Float = { 1f },
    actions: @Composable RowScope.() -> Unit = {},
) {
    Layout(modifier = modifier.fillMaxWidth().height(56.dp), content = {
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
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val left = measurables[0].measure(loose)
        val right = measurables[1].measure(loose)
        val reserve = maxOf(left.width, right.width) + 8.dp.roundToPx()
        val title = measurables[2].measure(loose.copy(maxWidth = (constraints.maxWidth - reserve * 2).coerceAtLeast(0)))
        layout(constraints.maxWidth, constraints.maxHeight) {
            left.placeRelative(0, (constraints.maxHeight - left.height) / 2)
            right.placeRelative(constraints.maxWidth - right.width, (constraints.maxHeight - right.height) / 2)
            title.placeRelative((constraints.maxWidth - title.width) / 2, (constraints.maxHeight - title.height) / 2)
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
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .then(menuAnchor?.surfaceModifier() ?: Modifier),
        foregroundModifier = menuAnchor?.foregroundModifier ?: Modifier,
        contentPadding = PaddingValues(horizontal = 12.dp), content = content)
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
