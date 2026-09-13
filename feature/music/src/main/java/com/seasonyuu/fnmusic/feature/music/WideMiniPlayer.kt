package com.seasonyuu.fnmusic.feature.music

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.seasonyuu.fnmusic.core.designsystem.FnNavigationSurface
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnAccent
import com.seasonyuu.fnmusic.core.designsystem.FnIcons
import com.seasonyuu.fnmusic.core.designsystem.MiniPlayer
import com.seasonyuu.fnmusic.core.designsystem.PlaybackToggleIcon
import com.seasonyuu.fnmusic.core.model.PlayerState
import com.seasonyuu.fnmusic.core.model.RepeatMode

/** Fits inside the content pane, independently of sidebar width or device orientation. */
@Composable
internal fun WideMiniPlayer(
    state: PlayerState,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onOpen: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
    playerMorphProgress: Float = 0f,
    onPlayerBoundsChanged: (Rect) -> Unit = {},
    onCoverBoundsChanged: (Rect) -> Unit = {},
    surfaceColor: Color = FnNavigationSurface,
) {
    if (state.current == null) return
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("wide-mini-player-space"),
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.widthIn(max = 1040.dp).fillMaxWidth()) {
            val fullControls = maxWidth >= 560.dp
            val leading: (@Composable () -> Unit)? =
                if (fullControls) {
                    {
                        Row(
                            Modifier.testTag("wide-mini-transport"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onToggleShuffle,
                                enabled = !state.isRoaming,
                                modifier =
                                    Modifier.size(48.dp)
                                        .semantics {
                                            selected = state.shuffleEnabled && !state.isRoaming
                                        }
                                        .testTag("wide-mini-shuffle")
                            ) {
                                Icon(
                                    Icons.Rounded.Shuffle,
                                    if (state.shuffleEnabled) "关闭随机播放" else "开启随机播放",
                                    tint =
                                        if (state.shuffleEnabled && !state.isRoaming) FnAccent
                                        else androidx.compose.material3.LocalContentColor.current
                                )
                            }
                            IconButton(
                                onClick = onPrevious,
                                enabled = state.canSkipPrevious,
                                modifier = Modifier.size(48.dp).testTag("wide-mini-previous")
                            ) { Icon(Icons.Rounded.SkipPrevious, "上一首") }
                            IconButton(
                                onClick = onToggle,
                                modifier = Modifier.size(48.dp).testTag("wide-mini-toggle")
                            ) {
                                PlaybackToggleIcon(state, iconSize = 32.dp)
                            }
                            IconButton(
                                onClick = onNext,
                                enabled = state.canSkipNext,
                                modifier = Modifier.size(48.dp).testTag("wide-mini-next")
                            ) { Icon(Icons.Rounded.SkipNext, "下一首") }
                            IconButton(
                                onClick = onCycleRepeatMode,
                                enabled = !state.isRoaming,
                                modifier =
                                    Modifier.size(48.dp)
                                        .semantics {
                                            selected =
                                                state.repeatMode != RepeatMode.Off &&
                                                    !state.isRoaming
                                        }
                                        .testTag("wide-mini-repeat")
                            ) {
                                Icon(
                                    if (state.repeatMode == RepeatMode.One) Icons.Rounded.RepeatOne
                                    else Icons.Rounded.Repeat,
                                    when (state.repeatMode) {
                                        RepeatMode.Off -> "循环已关闭，点击切换为列表循环"
                                        RepeatMode.All -> "列表循环，点击切换为单曲循环"
                                        RepeatMode.One -> "单曲循环，点击关闭循环"
                                    },
                                    tint =
                                        if (state.repeatMode != RepeatMode.Off && !state.isRoaming)
                                            FnAccent
                                        else androidx.compose.material3.LocalContentColor.current
                                )
                            }
                        }
                    }
                } else null
            val trailing: (@Composable () -> Unit)? =
                if (fullControls) {
                    {
                        Row(Modifier.testTag("wide-mini-utilities")) {
                            IconButton(
                                onClick = onOpenLyrics,
                                modifier = Modifier.size(48.dp).testTag("wide-mini-lyrics")
                            ) { Icon(Icons.Rounded.Lyrics, "展开完整歌词") }
                            IconButton(
                                onClick = onOpenQueue,
                                enabled = !state.isRoaming,
                                modifier = Modifier.size(48.dp).testTag("wide-mini-queue")
                            ) {
                                Icon(
                                    if (state.isRoaming) FnIcons.Roam
                                    else Icons.AutoMirrored.Rounded.QueueMusic,
                                    if (state.isRoaming) "漫游模式" else "打开待播队列"
                                )
                            }
                        }
                    }
                } else null
            MiniPlayer(
                state = state,
                surfaceColor = surfaceColor,
                onToggle = onToggle,
                onNext = onNext,
                onOpen = onOpen,
                leadingControls = leading,
                trailingControls = trailing,
                modifier = Modifier.testTag("wide-mini-player"),
                playerMorphProgress = playerMorphProgress,
                onPlayerBoundsChanged = onPlayerBoundsChanged,
                onCoverBoundsChanged = onCoverBoundsChanged
            )
        }
    }
}
