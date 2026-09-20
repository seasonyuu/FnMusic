package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.LyricSegment
import com.seasonyuu.fnmusic.core.model.LyricTimeline
import com.seasonyuu.fnmusic.core.model.LyricTimingSource
import org.junit.Assert.*
import org.junit.Test

class AccompanistLyricsTest {
    @Test fun preservesUnicodeAndAbsoluteTimings() {
        val text = "你👩‍💻好"
        val timeline = LyricTimeline(LyricTimingSource.Accurate, listOf(
            LyricSegment(0, 1, 1000, 1300),
            LyricSegment(1, 6, 1300, 1700),
            LyricSegment(6, 7, 1700, 2000),
        ))
        val line = timeline.toKaraokeLine(text)
        assertEquals(text, line.syllables.joinToString("") { it.content })
        assertEquals("👩‍💻", line.syllables[1].content)
        assertEquals(1300, line.syllables[1].start)
        assertEquals(1000, line.start)
        assertEquals(2000, line.end)
    }

    @Test fun zeroDurationPunctuationHasFiniteProgress() {
        val timeline = LyricTimeline(LyricTimingSource.Estimated, listOf(LyricSegment(0, 1, 1000, 1000)))
        val syllable = timeline.toKaraokeLine("！").syllables.single()
        assertEquals(1000, syllable.start)
        assertEquals(1001, syllable.end)
        assertFalse(syllable.progress(1000).isNaN())
    }

    @Test fun negativeOffsetKeepsCompletedSyllablesFilledAtStart() {
        val timeline = LyricTimeline(LyricTimingSource.Accurate, listOf(LyricSegment(0, 1, -500, -100)))
        val syllable = timeline.toKaraokeLine("字").syllables.single()
        assertEquals(-500, syllable.start)
        assertEquals(1f, syllable.progress(0), 0f)
    }

    @Test fun clampsLongMediaTimesWithoutOverflow() {
        val timeline = LyricTimeline(LyricTimingSource.Accurate, listOf(LyricSegment(0, 1, Long.MAX_VALUE - 1, Long.MAX_VALUE)))
        val line = timeline.toKaraokeLine("字")
        assertEquals(Int.MAX_VALUE - 1, line.start)
        assertEquals(Int.MAX_VALUE, line.end)
    }
}
