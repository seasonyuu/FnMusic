package com.seasonyuu.fnmusic.feature.music

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.sp
import com.seasonyuu.fnmusic.core.model.LyricTimeline
import com.seasonyuu.fnmusic.core.model.lyricCharacterRanges
import com.seasonyuu.fnmusic.core.model.progressAt
import kotlinx.coroutines.flow.first

/** Extrapolates player samples for drawing and derived state without recomposing the player each frame. */
@Composable
internal fun rememberLyricPosition(
    trackKey: Any,
    positionMs: Long,
    durationMs: Long,
    playing: Boolean,
): State<Long> {
    val position = remember(trackKey, positionMs, playing) { mutableLongStateOf(positionMs) }
    LaunchedEffect(position, durationMs) {
        if (playing) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    val estimate = positionMs + (now - start) / 1_000_000
                    position.longValue = if (durationMs > 0) estimate.coerceAtMost(durationMs) else estimate
                }
            }
        }
    }
    return position
}

/** All seek/follow movement has one owner. Controls never participate in this calculation. */
@Composable
internal fun FollowLyricPosition(
    listState: LazyListState,
    contentKey: Any,
    activeIndex: Int,
    enabled: Boolean,
    following: Boolean,
    focusTopPx: Float,
    textInsetPx: Float,
    ready: Boolean,
    onReady: () -> Unit,
) {
    LaunchedEffect(contentKey, activeIndex, enabled, following, focusTopPx) {
        if (!enabled) return@LaunchedEffect
        if (!following || activeIndex < 0) {
            onReady()
            return@LaunchedEffect
        }
        fun offset(): Float? = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == activeIndex }
            ?.let { it.offset + listState.layoutInfo.beforeContentPadding + textInsetPx - focusTopPx }
        suspend fun awaitLayout() {
            snapshotFlow { offset() }.first { it != null }
        }
        if (!ready) {
            listState.scrollToItem(activeIndex)
            awaitLayout()
            offset()?.let { listState.scrollBy(it) }
        } else {
            if (offset() == null) {
                listState.animateScrollToItem(activeIndex)
                awaitLayout()
            }
            offset()?.let {
                listState.animateScrollBy(it, tween(420, easing = FastOutSlowInEasing))
            }
        }
        onReady()
    }
}

/** One Text layout supplies both passes, including wrapping, bidi and emoji cluster bounds. */
@Composable
internal fun ProgressiveLyricText(
    text: String,
    timeline: LyricTimeline?,
    position: () -> Long,
    activeProgress: Float,
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    var layout by remember(text) { mutableStateOf<TextLayoutResult?>(null) }
    val ranges = remember(text) { lyricCharacterRanges(text) }
    // Cache the layout-dependent geometry, rather than querying glyph boxes on every frame.
    val glyphs = remember(layout, ranges) {
        layout?.let { measured ->
            ranges.map { range ->
                var box = measured.getBoundingBox(range.first)
                for (offset in range.drop(1)) {
                    val next = measured.getBoundingBox(offset)
                    box = Rect(minOf(box.left, next.left), minOf(box.top, next.top),
                        maxOf(box.right, next.right), maxOf(box.bottom, next.bottom))
                }
                Triple(range, box, measured.getBidiRunDirection(range.first) == ResolvedTextDirection.Rtl)
            }
        }.orEmpty()
    }
    val segmentGlyphs = remember(glyphs, timeline) {
        timeline?.segments?.map { segment ->
            segment to glyphs.filter { it.first.first >= segment.startOffset && it.first.first < segment.endOffset }
        }.orEmpty()
    }
    Text(
        text = text,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold,
        color = baseColor,
        onTextLayout = { layout = it },
        modifier = modifier.drawWithContent {
            drawContent()
            val measured = layout ?: return@drawWithContent
            if (activeProgress <= 0f) return@drawWithContent
            if (timeline == null) {
                drawText(measured, color = highlightColor, alpha = activeProgress)
            } else {
                val now = position()
                val path = Path()
                for ((segment, characters) in segmentGlyphs) {
                    val progress = segment.progressAt(now)
                    if (progress <= 0f) continue
                    // Progress through a supplied multi-character segment in reading order.
                    val filled = progress * characters.size
                    characters.forEachIndexed { index, (_, box, rtl) ->
                        val amount = (filled - index).coerceIn(0f, 1f)
                        if (amount > 0f) path.addRect(if (rtl) {
                            Rect(box.right - box.width * amount, box.top, box.right, box.bottom)
                        } else {
                            Rect(box.left, box.top, box.left + box.width * amount, box.bottom)
                        })
                    }
                }
                clipPath(path) { drawText(measured, color = highlightColor, alpha = activeProgress) }
            }
        },
    )
}
