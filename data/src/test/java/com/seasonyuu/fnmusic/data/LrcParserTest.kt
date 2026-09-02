package com.seasonyuu.fnmusic.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LrcParserTest {
    @Test
    fun `parses centisecond and millisecond timestamps`() {
        val result = LrcParser.parse("[00:01.25]第一句\n[01:02.345]第二句")

        assertEquals(1_250L, result[0].timeMs)
        assertEquals("第一句", result[0].text)
        assertEquals(62_345L, result[1].timeMs)
    }

    @Test
    fun `expands multiple timestamps and applies offset`() {
        val result = LrcParser.parse("[00:02.00][00:04.00]重复", offsetMs = -500)

        assertEquals(listOf(1_500L, 3_500L), result.map { it.timeMs })
        assertEquals(listOf("重复", "重复"), result.map { it.text })
    }

    @Test
    fun `ignores metadata and keeps empty timed line`() {
        val result = LrcParser.parse("[ar:artist]\n[00:00.00]\nplain text")

        assertEquals(1, result.size)
        assertEquals(0L, result.single().timeMs)
        assertEquals("", result.single().text)
    }
}
