package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.PlaylistId
import com.seasonyuu.fnmusic.core.model.Track

internal data class TrackMenuAction(val item: LiquidMenuItem, val retireSession: Boolean = false, val invoke: () -> Unit)
internal val LocalTrackMenuActions = compositionLocalOf<(Track, PlaylistId?) -> List<TrackMenuAction>> { { _, _ -> emptyList() } }

internal val LocalTrackMenuBackdrop = compositionLocalOf<Backdrop?> { null }

@Composable
internal fun PlayerMoreMenu(
    track: Track,
    backdrop: Backdrop,
    modifier: Modifier = Modifier.size(46.dp),
    onOpenChange: (Boolean) -> Unit = {},
    surfaceColor: Color,
) = TrackMoreMenu(track, backdrop = backdrop, modifier = modifier,
    preferAboveAnchor = true, tint = FnTextPrimary.copy(alpha = .82f), onOpenChange = onOpenChange, surfaceColor = surfaceColor,
    actionIds = listOf("artist", "album", "playlist", "info"))

@Composable
internal fun TrackMoreMenu(
    track: Track,
    sourcePlaylist: PlaylistId? = null,
    actionIds: List<String>? = null,
    surfaceColor: Color = LocalLiquidMenuSurfaceColor.current,
    backdrop: Backdrop? = LocalTrackMenuBackdrop.current,
    modifier: Modifier = Modifier.size(48.dp),
    icon: ImageVector = Icons.Rounded.MoreVert,
    tint: Color = FnTextSecondary,
    description: String = "更多操作",
    enabled: Boolean = true,
    preferAboveAnchor: Boolean = false,
    onOpenChange: (Boolean) -> Unit = {},
) {
    var generation by remember(track.id, sourcePlaylist) { mutableStateOf(0) }
    androidx.compose.runtime.key(track.id, sourcePlaylist, generation) {
        var expanded by remember { mutableStateOf(false) }
        val availableActions = LocalTrackMenuActions.current(track, sourcePlaylist)
        val actions = actionIds?.mapNotNull { id -> availableActions.firstOrNull { it.item.id == id } }
            ?: availableActions
        val notify = rememberUpdatedState(onOpenChange)
        LaunchedEffect(enabled) {
            if (!enabled && expanded) {
                expanded = false
                notify.value(false)
            }
        }
        DisposableEffect(Unit) { onDispose { notify.value(false) } }
        CompositionLocalProvider(LocalLiquidMenuSurfaceColor provides surfaceColor) {
            LiquidMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false; onOpenChange(false) },
                onExpandedChange = { expanded = it; onOpenChange(it) },
                backdrop = backdrop ?: LocalAppBarBackdrop.current ?: rememberLayerBackdrop(),
                transition = LiquidMenuTransition.Transient,
                preferAboveAnchor = preferAboveAnchor,
                items = actions.map { it.item },
                onSelect = { id ->
                    val action = actions.firstOrNull { it.item.id == id && it.item.enabled }
                    if (action != null) {
                        // Retire the selected session before navigation or a modal sheet takes focus.
                        expanded = false
                        if (action.retireSession) generation++
                        onOpenChange(false)
                        action.invoke()
                    }
                },
                trigger = { toggle ->
                    Box(modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).then(surfaceModifier())
                        .clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = toggle)
                        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                        Box(Modifier.matchParentSize().then(foregroundModifier), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = tint)
                        }
                    }
                },
            )
        }
    }
}
