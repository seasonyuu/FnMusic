package com.seasonyuu.fnmusic.core.model

/** A resolved timeline uses absolute media times and UTF-16 text offsets. */
data class LyricTimeline(val source: LyricTimingSource, val segments: List<LyricSegment>)

/** Unicode clusters used for estimation and clipping; never split a surrogate or joined emoji. */
fun lyricCharacterRanges(text: String): List<IntRange> = buildList {
    var offset = 0
    while (offset < text.length) {
        val start = offset
        val first = text.codePointAt(offset)
        offset += Character.charCount(first)
        var regionalCount = if (first in 0x1F1E6..0x1F1FF) 1 else 0
        while (offset < text.length) {
            val cp = text.codePointAt(offset)
            val type = Character.getType(cp)
            when {
                type == Character.NON_SPACING_MARK.toInt() ||
                    type == Character.COMBINING_SPACING_MARK.toInt() ||
                    type == Character.ENCLOSING_MARK.toInt() ||
                    cp in 0xFE00..0xFE0F || cp in 0x1F3FB..0x1F3FF ||
                    cp in 0xE0020..0xE007F -> offset += Character.charCount(cp)
                cp == 0x200D && offset + 1 < text.length -> {
                    offset++
                    offset += Character.charCount(text.codePointAt(offset))
                }
                regionalCount == 1 && cp in 0x1F1E6..0x1F1FF -> {
                    regionalCount++
                    offset += Character.charCount(cp)
                }
                else -> break
            }
        }
        add(start until offset)
    }
}

private fun String.isTimedCharacter(range: IntRange): Boolean {
    val cp = codePointAt(range.first)
    return !Character.isWhitespace(cp) && Character.getType(cp) !in setOf(
        Character.SPACE_SEPARATOR.toInt(), Character.LINE_SEPARATOR.toInt(),
        Character.PARAGRAPH_SEPARATOR.toInt(), Character.CONNECTOR_PUNCTUATION.toInt(),
        Character.DASH_PUNCTUATION.toInt(), Character.START_PUNCTUATION.toInt(),
        Character.END_PUNCTUATION.toInt(), Character.INITIAL_QUOTE_PUNCTUATION.toInt(),
        Character.FINAL_QUOTE_PUNCTUATION.toInt(), Character.OTHER_PUNCTUATION.toInt(),
    )
}

fun resolveLyricTimeline(lines: List<LyricLine>, index: Int, durationMs: Long): LyricTimeline? {
    val line = lines.getOrNull(index) ?: return null
    val start = line.timeMs ?: return null
    if (line.text.isBlank()) return null
    val ranges = lyricCharacterRanges(line.text)
    val boundaries = ranges.map { it.first }.toSet() + line.text.length
    val supplied = line.segments
    if (line.timingSource == LyricTimingSource.Accurate && supplied.isNotEmpty() &&
        supplied.first().startOffset == 0 && supplied.last().endOffset == line.text.length &&
        supplied.all {
            it.startOffset in boundaries && it.endOffset in boundaries && it.endOffset > it.startOffset &&
                it.startMs >= start && it.endMs > it.startMs
        } && supplied.zipWithNext().all { (a, b) -> a.endOffset == b.startOffset && a.endMs <= b.startMs }
    ) return LyricTimeline(LyricTimingSource.Accurate, supplied)

    val end = lines.drop(index + 1).mapNotNull { it.timeMs }.firstOrNull { it > start }
        ?: durationMs.takeIf { it > start } ?: return null
    val timed = ranges.filter { line.text.isTimedCharacter(it) }
    if (timed.isEmpty()) return null
    var completed = 0
    val segments = ranges.map { range ->
        val from = start + ((end - start).toDouble() * completed / timed.size).toLong()
        if (line.text.isTimedCharacter(range)) completed++
        LyricSegment(
            startOffset = range.first,
            endOffset = range.last + 1,
            startMs = from,
            endMs = start + ((end - start).toDouble() * completed / timed.size).toLong(),
        )
    }
    return LyricTimeline(LyricTimingSource.Estimated, segments)
}

fun LyricSegment.progressAt(positionMs: Long): Float = when {
    positionMs >= endMs -> 1f
    positionMs <= startMs -> 0f
    else -> ((positionMs - startMs).toDouble() / (endMs - startMs)).toFloat()
}
