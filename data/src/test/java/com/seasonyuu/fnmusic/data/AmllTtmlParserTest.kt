package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Test

class AmllTtmlParserTest {
    @Test fun wordsPreserveSpacesTranslationAndRealTiming() {
        val line = AmllTtmlParser.parse(lyricFixture).single()
        assertEquals("Hello world!", line.text)
        assertEquals("你好 世界！", line.translation)
        assertEquals(4000L, line.endTimeMs)
        assertEquals(listOf("Hello ", "world!"), line.segments.map { line.text.substring(it.startOffset, it.endOffset) })
        assertEquals(2500L, line.segments[1].startMs)
        assertEquals(LyricTimingSource.Accurate, resolveLyricTimeline(listOf(line), 0, 10000)!!.source)
    }
    @Test fun entitiesMixedTextAndLegacyNamespaceRemainIntact() {
        val xml = lyricFixture.replace("<div>", "<div xmlns=\"\">").replace("Hello", "A &amp; &#x4F60;  B").replace("world!", "&amp;lt;!")
        val line = AmllTtmlParser.parse(xml).single()
        assertEquals("A & 你  B &lt;!", line.text)
        assertNotNull(resolveLyricTimeline(listOf(line), 0, 10000))
    }
    @Test fun invalidWordTimesFallBackToLineNotEstimatedKaraoke() {
        val line = AmllTtmlParser.parse(lyricFixture.replace("end=\"2.000\"", "end=\"0.500\"")).single()
        assertEquals("Hello world!", line.text)
        assertEquals(LyricTimingSource.Line, line.timingSource)
        assertNull(resolveLyricTimeline(listOf(line), 0, 10000))
    }
    @Test fun lineLyricsAndExcludedTracksDoNotLeakMetadataIntoText() {
        val xml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div><p begin="00:01.000" end="00:02.000">A B<span ttm:role="x-roman">a b</span><span ttm:role="x-bg">background</span></p></div></body></tt>"""
        val line = AmllTtmlParser.parse(xml).single()
        assertEquals("A B", line.text)
        assertNull(resolveLyricTimeline(listOf(line), 0, 10000))
    }
    @Test fun rejectsHtmlDtdAndMalformedXml() {
        assertThrows(Exception::class.java) { AmllTtmlParser.parse("<html>no</html>") }
        assertThrows(Exception::class.java) { AmllTtmlParser.parse("<!DOCTYPE tt SYSTEM 'file:///x'>" + lyricFixture) }
        assertThrows(Exception::class.java) { AmllTtmlParser.parse(lyricFixture.dropLast(5)) }
    }
}
