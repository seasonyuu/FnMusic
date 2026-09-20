package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/** Platform wire formats, translated into the app's existing timeline without estimating word times. */
internal object OnlineLyricsParser {
    private val lineHeader = Regex("^\\[(\\d+),(\\d+)](.*)$")
    private val yrcWord = Regex("\\((\\d+),(\\d+),\\d+\\)")
    private val qrcWord = Regex("\\((\\d+),(\\d+)\\)")
    private val krcWord = Regex("<(\\d+),(\\d+),\\d+>")
    private val stamp = Regex("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]")

    fun lrc(raw: String): List<LyricLine> = raw.lineSequence().flatMap { line ->
        val marks = stamp.findAll(line).toList()
        val text = marks.lastOrNull()?.let { line.substring(it.range.last + 1) }.orEmpty()
        marks.map { m -> LyricLine(m.groupValues[1].toLong() * 60000 + m.groupValues[2].toLong() * 1000 +
            m.groupValues[3].padEnd(3, '0').toLong(), text, timingSource = LyricTimingSource.Line) }
    }.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }.toList()

    fun timed(raw: String, source: OnlineLyricsSource): List<LyricLine> {
        val content = if (source == OnlineLyricsSource.QQ && raw.contains("LyricContent=")) {
            val value = Regex("LyricContent=\"([\\s\\S]*?)\"").find(raw)?.groupValues?.get(1) ?: error("无效 QRC")
            entities(value)
        } else raw
        return content.lineSequence().mapNotNull { line ->
            val header = lineHeader.matchEntire(line.trimEnd('\r')) ?: return@mapNotNull null
            val start = header.groupValues[1].toLong()
            val end = start + header.groupValues[2].toLong()
            val body = header.groupValues[3]
            val pattern = when (source) { OnlineLyricsSource.Netease -> yrcWord; OnlineLyricsSource.QQ -> qrcWord; OnlineLyricsSource.Kugou -> krcWord }
            val matches = pattern.findAll(body).toList()
            val text = StringBuilder()
            val segments = mutableListOf<LyricSegment>()
            matches.forEachIndexed { index, match ->
                val word = if (source == OnlineLyricsSource.QQ) body.substring(if (index == 0) 0 else matches[index - 1].range.last + 1, match.range.first)
                    else body.substring(match.range.last + 1, matches.getOrNull(index + 1)?.range?.first ?: body.length)
                val from = text.length
                text.append(word)
                val wordStart = match.groupValues[1].toLong() + if (source == OnlineLyricsSource.Kugou) start else 0
                segments += LyricSegment(from, text.length, wordStart, wordStart + match.groupValues[2].toLong())
            }
            // Un-timed prefixes/suffixes are still visible, but make the line unsuitable for accurate karaoke.
            val prefix = if (source != OnlineLyricsSource.QQ && matches.isNotEmpty()) body.substring(0, matches.first().range.first) else ""
            val suffix = if (source == OnlineLyricsSource.QQ && matches.isNotEmpty()) body.substring(matches.last().range.last + 1) else ""
            val value = if (matches.isEmpty()) body else prefix + text + suffix
            if (value.isBlank()) return@mapNotNull null
            val boundaries = lyricCharacterRanges(value).map { it.first }.toSet() + value.length
            val valid = prefix.isEmpty() && suffix.isEmpty() && end > start && segments.isNotEmpty() && segments.all {
                it.endOffset > it.startOffset && it.startOffset in boundaries && it.endOffset in boundaries && it.startMs >= start && it.endMs > it.startMs && it.endMs <= end
            } && segments.zipWithNext().all { (a, b) -> a.endMs <= b.startMs }
            LyricLine(start, value, segments = if (valid) segments else emptyList(), timingSource = if (valid) LyricTimingSource.Accurate else LyricTimingSource.Line, endTimeMs = end.takeIf { it > start })
        }.sortedBy { it.timeMs }.toList()
    }

    fun translate(lines: List<LyricLine>, translation: String): List<LyricLine> {
        val translated = lrc(translation).associateBy { it.timeMs }
        return lines.map { it.copy(translation = translated[it.timeMs]?.text?.takeIf { text -> text.isNotBlank() && text.trim() != "//" }) }
    }

    fun inflate(bytes: ByteArray): String = InflaterInputStream(ByteArrayInputStream(bytes)).use { input ->
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(out.size() + size <= 2 * 1024 * 1024) { "歌词解压后过大" }
            out.write(buffer, 0, size)
        }
        out.toString("UTF-8")
    }

    fun krc(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size > 4 && bytes.copyOfRange(0, 4).contentEquals(byteArrayOf(107, 114, 99, 49))) { "无效 KRC" }
        val key = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        return inflate(ByteArray(bytes.size - 4) { (bytes[it + 4].toInt() xor key[it % key.size]).toByte() })
    }

    fun qrc(encoded: String): String {
        if (encoded.startsWith("[") || encoded.startsWith("<?xml") || encoded.startsWith("<Qrc")) return encoded
        if (!encoded.matches(Regex("[a-fA-F0-9]+"))) return String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
        val bytes = encoded.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        require(bytes.size % 8 == 0) { "无效 QRC 密文" }
        return inflate(QrcCipher.decrypt(bytes))
    }

    private fun entities(value: String): String = Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|quot|apos|lt|gt);").replace(value) {
        when (val token = it.groupValues[1]) {
            "amp" -> "&"; "quot" -> "\""; "apos" -> "'"; "lt" -> "<"; "gt" -> ">"
            else -> String(Character.toChars(if (token.startsWith("#x")) token.drop(2).toInt(16) else token.drop(1).toInt()))
        }
    }
}
