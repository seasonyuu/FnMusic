package com.seasonyuu.fnmusic.feature.music

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.seasonyuu.fnmusic.core.designsystem.FnTextSecondary
import com.seasonyuu.fnmusic.core.model.*
import kotlin.math.roundToInt

@Composable
internal fun LyricsSourceOrder(preference: OnlineLyricsPreference, saving: Boolean, onChange: (OnlineLyricsPreference) -> Unit) {
    var order by remember { mutableStateOf(preference.orderedSources) }
    var dragged by remember { mutableStateOf<OnlineLyricsSource?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val rowHeight = 56.dp * density.fontScale.coerceAtLeast(1f)
    val rowPixels = with(density) { rowHeight.toPx() }
    val haptic = LocalHapticFeedback.current
    val enabled = preference.enabled && !saving
    LaunchedEffect(preference.orderedSources, saving, dragged) {
        if (!saving && dragged == null) order = preference.orderedSources
    }
    fun move(source: OnlineLyricsSource, target: Int) {
        val from = order.indexOf(source)
        if (from != target) {
            order = order.toMutableList().apply { add(target, removeAt(from)) }
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("歌词来源", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = {
                    message = null
                    order = OnlineLyricsSource.entries.toList()
                    onChange(preference.copy(sources = OnlineLyricsSource.entries.toSet(), order = order))
                },
                enabled = enabled && dragged == null,
                colors = readableOutlinedButtonColors(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("online-source-reset"),
            ) { Text("恢复默认", style = MaterialTheme.typography.labelMedium) }
        }
        Box(Modifier.fillMaxWidth().height(rowHeight * order.size)) {
            OnlineLyricsSource.entries.forEach { source -> key(source) {
                val position = order.indexOf(source)
                val lifted = dragged == source
                val y by animateFloatAsState(position * rowPixels, tween(180), label = "lyrics-source-position")
                Row(Modifier.fillMaxWidth().height(rowHeight).offset { IntOffset(0, (if (lifted) dragY else y).roundToInt()) }
                    .zIndex(if (lifted) 1f else 0f)
                    .shadow(if (lifted) 6.dp else 0.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (lifted) MaterialTheme.colorScheme.surfaceContainerHigh else androidx.compose.ui.graphics.Color.Transparent)
                    .toggleable(value = source in preference.sources, enabled = enabled && dragged == null, role = Role.Checkbox) {
                        if (source in preference.sources && preference.sources.size == 1) {
                            message = "至少保留一个来源，如需停用请关闭聚合歌词搜索"
                        } else {
                            message = null
                            onChange(preference.copy(sources = if (source in preference.sources) preference.sources - source else preference.sources + source, order = order))
                        }
                    }.testTag("online-source-${source.name}"), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).testTag("online-source-drag-${source.name}")
                        .semantics {
                            contentDescription = "拖动调整${source.label}优先级"
                            if (enabled) customActions = buildList {
                                if (position > 0) add(CustomAccessibilityAction("上移") { move(source, position - 1); onChange(preference.copy(order = order)); true })
                                if (position < order.lastIndex) add(CustomAccessibilityAction("下移") { move(source, position + 1); onChange(preference.copy(order = order)); true })
                            }
                        }
                        .pointerInput(source, enabled, rowPixels, preference) {
                            if (enabled) detectDragGestures(
                                onDragStart = { dragged = source; dragY = order.indexOf(source) * rowPixels; haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                                onDragCancel = { dragged = null; order = preference.orderedSources },
                                onDragEnd = { onChange(preference.copy(order = order)); dragged = null },
                            ) { change, amount ->
                                change.consume()
                                dragY = (dragY + amount.y).coerceIn(0f, rowPixels * order.lastIndex)
                                move(source, (dragY / rowPixels).roundToInt().coerceIn(0, order.lastIndex))
                            }
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DragHandle, null, tint = FnTextSecondary) }
                    Text(source.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Checkbox(checked = source in preference.sources, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(horizontal = 12.dp))
                }
            } }
        }
        val active = order.filter { it in preference.sources }
        if (active.size > 1) {
            Text("拖动调整优先级，越靠上越优先。", style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
        }
        val priorityDescription = if (active.size == 1) {
            "仅从 ${active.single().label} 搜索，优先采用逐词歌词。"
        } else {
            "优先采用逐词歌词，同等质量按 ${active.joinToString(" → ") { it.label }} 的顺序选择。"
        }
        Text(priorityDescription, modifier = Modifier.testTag("online-source-priority"), style = MaterialTheme.typography.bodySmall, color = FnTextSecondary)
        message?.let { Text(it, color = FnTextSecondary, style = MaterialTheme.typography.bodySmall) }
    }
}
