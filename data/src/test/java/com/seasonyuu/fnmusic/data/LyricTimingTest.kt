package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LyricTimingTest {
    @Test fun `estimation skips punctuation and whitespace without splitting emoji`() {
        val text = "你， 👩🏽‍💻好!"
        val timeline = resolveLyricTimeline(listOf(LyricLine(1_000, text)), 0, 4_000)!!
        assertEquals(LyricTimingSource.Estimated, timeline.source)
        val moving = timeline.segments.filter { it.endMs > it.startMs }
        assertEquals(listOf("你", "👩🏽‍💻", "好"), moving.map { text.substring(it.startOffset, it.endOffset) })
        assertEquals(listOf(1_000L, 2_000L, 3_000L), moving.map { it.startMs })
        assertEquals(4_000L, timeline.segments.last().endMs)
        assertEquals(0.5f, moving[1].progressAt(2_500), 0.001f)
    }

    @Test fun `duplicate timestamps use next strictly later line`() {
        val lines = LrcParser.parse("[00:01]甲\n[00:01]乙\n[00:04]丙", offsetMs = 500)
        assertEquals(4_500L, resolveLyricTimeline(lines, 0, 8_000)!!.segments.last().endMs)
        assertEquals(1_500L, resolveLyricTimeline(lines, 1, 8_000)!!.segments.first().startMs)
    }

    @Test fun `final line uses valid duration or falls back to whole line`() {
        val lines = listOf(LyricLine(3_000, "末句"))
        assertNull(resolveLyricTimeline(lines, 0, 0))
        assertNull(resolveLyricTimeline(lines, 0, 2_000))
        assertEquals(5_000L, resolveLyricTimeline(lines, 0, 5_000)!!.segments.last().endMs)
        assertNull(resolveLyricTimeline(listOf(LyricLine(text = "无时间")), 0, 5_000))
        assertNull(resolveLyricTimeline(listOf(LyricLine(0, "")), 0, 5_000))
    }

    @Test fun `accurate data takes priority and malformed data falls back`() {
        val exact = listOf(LyricSegment(0, 1, 1_000, 1_300), LyricSegment(1, 2, 2_000, 2_500))
        val line = LyricLine(1_000, "你好", segments = exact, timingSource = LyricTimingSource.Accurate)
        assertEquals(exact, resolveLyricTimeline(listOf(line), 0, 6_000)!!.segments)
        assertEquals(LyricTimingSource.Accurate, resolveLyricTimeline(listOf(line), 0, 6_000)!!.source)
        val invalid = line.copy(segments = listOf(LyricSegment(0, 8, 1_000, 2_000)))
        assertEquals(LyricTimingSource.Estimated, resolveLyricTimeline(listOf(invalid), 0, 6_000)!!.source)
    }

    @Test fun `unicode clusters include combining accents flags and family emoji`() {
        val text = "e\u0301🇨🇳👨‍👩‍👧‍👦"
        assertEquals(listOf("e\u0301", "🇨🇳", "👨‍👩‍👧‍👦"), lyricCharacterRanges(text).map { text.substring(it) })
        val line = LyricLine(0, "😀", segments = listOf(LyricSegment(0, 1, 0, 1_000), LyricSegment(1, 2, 1_000, 2_000)), timingSource = LyricTimingSource.Accurate)
        assertEquals(LyricTimingSource.Estimated, resolveLyricTimeline(listOf(line), 0, 3_000)!!.source)
    }

    @Test fun `progress clamps before start and after end`() {
        val segment = LyricSegment(0, 1, 1_000, 2_000)
        assertEquals(0f, segment.progressAt(500), 0f)
        assertEquals(1f, segment.progressAt(3_000), 0f)
    }
}
