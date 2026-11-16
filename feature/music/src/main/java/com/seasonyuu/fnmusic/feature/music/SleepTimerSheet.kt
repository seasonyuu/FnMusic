package com.seasonyuu.fnmusic.feature.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seasonyuu.fnmusic.core.designsystem.*
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LocalSleepTimerController = compositionLocalOf<SleepTimerController?> { null }
private val presets = listOf(10, 20, 30, 45, 60, 90)
private val TimerSecondary = Color.White.copy(alpha = .6f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SleepTimerSheet(
    state: SleepTimerState,
    onEnable: () -> Unit,
    onStart: (Int) -> Unit,
    onCancel: () -> Unit,
    surfaceColor: Color = FnNavigationSurface,
    onDismiss: () -> Unit,
) {
    var editing by rememberSaveable { mutableStateOf(false) }
    var hours by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(30) }
    var hoursScrolling by remember { mutableStateOf(false) }
    var minutesScrolling by remember { mutableStateOf(false) }
    val duration = sleepTimerMinutes(hours, minutes)
    val editorTransition = updateTransition(editing, label = "timer-custom-editor")
    val editorSlidePx = with(LocalDensity.current) { 8.dp.roundToPx() }
    TimerSheetSurface(surfaceColor, onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp).testTag("sleep-timer-sheet")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("定时关闭", style = MaterialTheme.typography.titleLarge)
                        if (!state.enabled && state.initialized) {
                            Text("上次 ${state.durationMinutes} 分钟", color = TimerSecondary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.CenterVertically).testTag("sleep-timer-last-duration"))
                        }
                    }
                    Text(if (state.enabled) "剩余 ${formatSleepTimer(state.remainingSeconds)}" else "计时结束后暂停播放",
                        color = TimerSecondary, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
                }
                LiquidToggle(checked = state.enabled, enabled = state.initialized,
                    onCheckedChange = { if (it) onEnable() else onCancel() },
                    modifier = Modifier.testTag("sleep-timer-switch").semantics { contentDescription = "定时关闭" })
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                presets.forEach { value ->
                    TimerPreset(value, state.enabled && state.durationMinutes == value, state.initialized) { onStart(value); editing = false }
                }
            }
            TextButton(onClick = {
                if (editing) editing = false
                else {
                    // Reverse an in-flight exit with the existing draft and wheel positions.
                    // Only a fully dismissed editor starts a fresh draft.
                    if (!editorTransition.currentState && !editorTransition.isRunning) {
                        hours = state.durationMinutes / 60
                        minutes = state.durationMinutes % 60
                        hoursScrolling = false
                        minutesScrolling = false
                    }
                    editing = true
                }
            }, enabled = state.initialized, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .testTag("sleep-timer-custom")) {
                Text("自定义", Modifier.weight(1f))
                if (state.enabled) Text("${state.durationMinutes / 60} 小时 ${state.durationMinutes % 60} 分钟", color = TimerSecondary)
            }
            editorTransition.AnimatedVisibility(
                visible = { it },
                enter = expandVertically(tween(280, easing = FastOutSlowInEasing), expandFrom = Alignment.Top) +
                    fadeIn(tween(180, delayMillis = 40)) + slideInVertically(tween(240)) { editorSlidePx },
                exit = shrinkVertically(tween(220, easing = FastOutSlowInEasing), shrinkTowards = Alignment.Top) +
                    fadeOut(tween(140)) + slideOutVertically(tween(180)) { editorSlidePx / 2 },
            ) {
                Column(Modifier.padding(horizontal = 24.dp).testTag("sleep-timer-editor")
                    .then(if (editing) Modifier else Modifier.clearAndSetSemantics {})) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TimerWheel("小时", "hours", 24, hours, Modifier.weight(1f), enabled = editing,
                            onScrolling = { hoursScrolling = it }, onSelect = {
                                if (editing) {
                                    hours = it
                                    if (it == 24) minutes = 0
                                }
                            })
                        // Recreate the minute wheel when its range changes, so 24 hours always means 24:00.
                        key(hours == 24) {
                            TimerWheel("分钟", "minutes", if (hours == 24) 0 else 59, minutes, Modifier.weight(1f), enabled = editing,
                                onScrolling = { minutesScrolling = it }, onSelect = { if (editing) minutes = it })
                        }
                    }
                    Text(if (duration == null) "请选择至少 1 分钟" else "计时 ${hours} 小时 ${minutes} 分钟",
                        color = if (duration == null) MaterialTheme.colorScheme.error else TimerSecondary,
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(enabled = editing, onClick = { editing = false }) { Text("取消") }
                        TextButton(enabled = editing && duration != null && state.initialized && !hoursScrolling && !minutesScrolling,
                            onClick = { duration?.let(onStart); editing = false },
                            modifier = Modifier.testTag("sleep-timer-confirm")) { Text("确定") }
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp)) }
        }
    }
}

/** Match the lyrics sheet's artwork surface, white foreground and ripple-free handle. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TimerSheetSurface(surfaceColor: Color, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = Color.White, onPrimary = Color.Black,
        surface = surfaceColor, onSurface = Color.White, onSurfaceVariant = TimerSecondary,
        secondaryContainer = Color.White.copy(alpha = .12f), onSecondaryContainer = Color.White)) {
        val contentRipple = LocalRippleConfiguration.current
        CompositionLocalProvider(LocalRippleConfiguration provides null) {
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden,
                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
                containerColor = surfaceColor,
                contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier.statusBarsPadding(),
            ) {
                CompositionLocalProvider(LocalRippleConfiguration provides contentRipple, content = content)
            }
        }
    }
}

@Composable
private fun TimerPreset(minutes: Int, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val progress by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "timer-preset-selection")
    val fill by animateColorAsState(if (selected) FnAccent else Color.White.copy(alpha = .05f), tween(180), label = "timer-preset-fill")
    val density = LocalDensity.current
    // The card stays fixed while the number moves and the second line fades in/out.
    val unitHeight = with(density) { 20.sp.toDp() }
    Box(Modifier.widthIn(min = 72.dp).height(64.dp + unitHeight).clip(RoundedCornerShape(22.dp))
        .background(fill).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = "$minutes 分钟" }
        .testTag("sleep-timer-preset-$minutes").padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(minutes.toString(), style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = if (enabled) 1f else .38f),
            modifier = Modifier.graphicsLayer { translationY = -unitHeight.toPx() * .5f * progress }.clearAndSetSemantics {})
        Text("min", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .85f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                .graphicsLayer { alpha = progress; translationY = (1f - progress) * 4.dp.toPx() }.clearAndSetSemantics {})
    }
}

/** Three visible rows; the center row snaps into place and can also be selected by tapping. */
@Composable
private fun TimerWheel(
    label: String,
    tag: String,
    maximum: Int,
    value: Int,
    modifier: Modifier,
    enabled: Boolean,
    onScrolling: (Boolean) -> Unit,
    onSelect: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val rowHeight = maxOf(48.dp, with(density) { 40.sp.toDp() })
    val list = rememberLazyListState(initialFirstVisibleItemIndex = value.coerceIn(0, maximum))
    val scope = rememberCoroutineScope()
    val latestSelect by rememberUpdatedState(onSelect)
    val latestScrolling by rememberUpdatedState(onScrolling)
    val centered by remember {
        derivedStateOf {
            val layout = list.layoutInfo
            val middle = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
            layout.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index ?: value
        }
    }
    LaunchedEffect(list) {
        snapshotFlow { list.isScrollInProgress to centered }.distinctUntilChanged().collect { (scrolling, index) ->
            latestScrolling(scrolling)
            if (!scrolling) latestSelect(index.coerceIn(0, maximum))
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TimerSecondary, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
        Box(Modifier.fillMaxWidth().height(rowHeight * 3).clip(RoundedCornerShape(18.dp))) {
            Box(Modifier.align(Alignment.Center).fillMaxWidth().height(rowHeight)
                .clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .1f)))
            LazyColumn(state = list, userScrollEnabled = enabled, flingBehavior = rememberSnapFlingBehavior(list),
                contentPadding = PaddingValues(vertical = rowHeight),
                modifier = Modifier.fillMaxSize().testTag("sleep-timer-$tag-wheel").semantics {
                    contentDescription = label
                    stateDescription = "$centered $label"
                    progressBarRangeInfo = ProgressBarRangeInfo(centered.toFloat(), 0f..maximum.toFloat(), (maximum - 1).coerceAtLeast(0))
                    setProgress { target ->
                        if (!enabled) return@setProgress false
                        scope.launch { list.animateScrollToItem(target.roundToInt().coerceIn(0, maximum)) }
                        true
                    }
                }) {
                items((0..maximum).toList(), key = { it }) { number ->
                    Box(Modifier.fillMaxWidth().height(rowHeight).clickable(enabled = enabled) {
                        scope.launch { list.animateScrollToItem(number) }
                    }.testTag("sleep-timer-$tag-$number")
                        .semantics { selected = number == centered; contentDescription = "$number $label" },
                        contentAlignment = Alignment.Center) {
                        Text(number.toString().padStart(2, '0'),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                            color = Color.White.copy(alpha = if (number == centered) 1f else .35f))
                    }
                }
            }
        }
    }
}
