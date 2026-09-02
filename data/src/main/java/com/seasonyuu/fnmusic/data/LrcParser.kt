package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.LyricLine
object LrcParser {
    private val timestamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun parse(content: String, offsetMs: Long = 0): List<LyricLine> = buildList {
        content.lineSequence().forEach { rawLine ->
            val matches = timestamp.findAll(rawLine).toList()
            if (matches.isEmpty()) return@forEach
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            matches.forEach { match ->
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.take(3).toLong()
                }
                add(LyricLine(((minutes * 60 + seconds) * 1_000 + fractionMs + offsetMs).coerceAtLeast(0), text))
            }
        }
    }.sortedBy { it.timeMs }
}
