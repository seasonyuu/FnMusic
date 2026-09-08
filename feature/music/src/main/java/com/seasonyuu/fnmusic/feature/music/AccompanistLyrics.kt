package com.seasonyuu.fnmusic.feature.music

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLineText
import com.seasonyuu.fnmusic.core.model.LyricTimeline

/** Keeps FnMusic's resolved timings and text boundaries at the third-party boundary. */
internal fun LyricTimeline.toKaraokeLine(text: String): KaraokeLine.MainKaraokeLine {
    val syllables = segments.map { segment ->
        val start = segment.startMs.coerceIn(0, Int.MAX_VALUE.toLong() - 1).toInt()
        // The upstream syllable progress divides by duration, including at punctuation timestamps.
        val end = segment.endMs.coerceIn(start.toLong() + 1, Int.MAX_VALUE.toLong()).toInt()
        KaraokeSyllable(text.substring(segment.startOffset, segment.endOffset), start, end)
    }
    return KaraokeLine.MainKaraokeLine(
        syllables = syllables,
        translation = null,
        alignment = KaraokeAlignment.Start,
        start = syllables.minOf { it.start },
        end = syllables.maxOf { it.end },
    )
}

@Composable
internal fun AccompanistLyricText(
    text: String,
    timeline: LyricTimeline?,
    position: () -> Long,
    activeProgress: Float,
    baseColor: Color,
    highlightColor: Color,
    modifier: Modifier = Modifier,
) {
    if (timeline == null || timeline.segments.isEmpty()) {
        ProgressiveLyricText(text, timeline, position, activeProgress, baseColor, highlightColor, modifier)
        return
    }
    val line = remember(text, timeline) { timeline.toKaraokeLine(text) }
    val currentPosition by rememberUpdatedState(position)
    val active by rememberUpdatedState(activeProgress)
    val timeProvider = remember(line) {
        { currentPosition().coerceIn(0, Int.MAX_VALUE.toLong()).toInt() }
    }
    KaraokeLineText(
        line = line,
        currentTimeProvider = timeProvider,
        modifier = modifier
            .semantics { this.text = AnnotatedString(text) }
            .graphicsLayer {
                // Keep completed syllables filled; fade the finished line instead of resetting its clock.
                // Upcoming lines already have the renderer's unsung tint, so do not dim them twice.
                alpha = if (timeProvider() >= line.end) {
                    val restingAlpha = (baseColor.alpha / highlightColor.alpha.coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                    restingAlpha + (1f - restingAlpha) * active
                } else 1f
            },
        normalLineTextStyle = TextStyle(
            fontSize = 28.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
            // Center the line-height leading rather than distributing it by font ascent/descent.
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        activeColor = highlightColor,
        showTranslation = false,
        showPhonetic = false,
    )
}
