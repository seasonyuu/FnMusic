package com.seasonyuu.fnmusic.data

import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.seasonyuu.fnmusic.core.model.*
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Preserve mixed XML text before passing normalized timing tokens to the pinned parser. */
internal object AmllTtmlParser {
    private data class Word(var text: String, val start: Long?, val end: Long?)
    private fun Node.name() = localName ?: nodeName.substringAfter(':')
    private fun Element.role() = getAttributeNS("http://www.w3.org/ns/ttml#metadata", "role")
    private fun Node.children(): List<Node> = (0 until childNodes.length).map(childNodes::item)
    private val excluded = setOf("x-translation", "x-roman", "x-bg")
    private fun text(node: Node): String = when {
        node is Element && node.role() in excluded -> ""
        node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE -> node.nodeValue.orEmpty()
        else -> node.children().joinToString("") { text(it) }
    }
    private fun time(value: String): Long? {
        if (!Regex("(?:\\d+:){0,2}\\d+(?:\\.\\d+)?s?").matches(value)) return null
        return runCatching {
            val seconds = value.removeSuffix("s").split(':').fold(java.math.BigDecimal.ZERO) { acc, part ->
                acc * java.math.BigDecimal(60) + part.toBigDecimal()
            }
            (seconds * java.math.BigDecimal(1000)).toLong().takeIf { it in 0..Int.MAX_VALUE.toLong() }
        }.getOrNull()
    }

    fun parse(content: String): List<LyricLine> {
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(content)) { "不支持包含 DTD 的歌词" }
        val builder = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true; isExpandEntityReferences = false }.newDocumentBuilder()
        builder.setEntityResolver { _, _ -> throw IllegalArgumentException("不支持外部实体") }
        val root = builder.parse(InputSource(StringReader(content))).documentElement
        require(root.name() == "tt" && root.namespaceURI == "http://www.w3.org/ns/ttml") { "无效 TTML" }
        val elements = root.getElementsByTagName("*")
        val allElements = (0 until elements.length).map { elements.item(it) }.filterIsInstance<Element>()
        val translations = allElements.filter { it.name() == "translation" }.flatMap { block ->
            block.children().filterIsInstance<Element>().filter { it.name() == "text" && it.hasAttribute("for") }
                .map { it.getAttribute("for") to it.textContent }
        }.toMap()
        return allElements
            .filter { it.name() == "p" }.mapNotNull { p ->
                val start = time(p.getAttribute("begin")) ?: return@mapNotNull null
                val end = time(p.getAttribute("end"))?.takeIf { it > start }
                val translation = p.children().filterIsInstance<Element>().firstOrNull { it.role() == "x-translation" }?.textContent
                    ?: translations[(0 until p.attributes.length).map(p.attributes::item).firstOrNull { it.name() == "key" }?.nodeValue]
                val words = mutableListOf<Word>()
                var leading = ""
                fun visit(node: Node) {
                    if (node is Element && node.role() in excluded) return
                    if (node is Element && node.name() == "span" && node.hasAttribute("begin")) {
                        words += Word(leading + text(node), time(node.getAttribute("begin")), time(node.getAttribute("end")))
                        leading = ""
                    } else if (node.nodeType == Node.TEXT_NODE || node.nodeType == Node.CDATA_SECTION_NODE) {
                        if (words.isEmpty()) leading += node.nodeValue.orEmpty() else words.last().text += node.nodeValue.orEmpty()
                    } else node.children().forEach(::visit)
                }
                p.children().forEach(::visit)
                val lineText = if (words.isEmpty()) text(p) else words.joinToString("") { it.text }
                if (lineText.isBlank()) return@mapNotNull null
                val valid = end != null && words.isNotEmpty() && words.all {
                    it.text.isNotEmpty() && it.start != null && it.end != null && it.start >= start && it.end > it.start && it.end <= end
                } && words.zipWithNext().all { (a, b) -> a.end!! <= b.start!! }
                val segments = if (valid) {
                    // Opaque text tokens avoid upstream whitespace trimming/entity double decoding.
                    val spans = words.mapIndexed { i, w -> "<span begin=\"${w.start!! / 1000.0}\" end=\"${w.end!! / 1000.0}\">w$i</span>" }.joinToString("")
                    val canonical = "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><div><p begin=\"${start / 1000.0}\" end=\"${end / 1000.0}\">$spans</p></div></body></tt>"
                    val parsed = TTMLParser().parse(canonical).lines.single() as KaraokeLine
                    var offset = 0
                    parsed.syllables.mapIndexed { i, syllable ->
                        val from = offset; offset += words[i].text.length
                        LyricSegment(from, offset, syllable.start.toLong(), syllable.end.toLong())
                    }
                } else emptyList()
                val boundaries = lyricCharacterRanges(lineText).map { it.first }.toSet() + lineText.length
                val safe = valid && segments.all { it.startOffset in boundaries && it.endOffset in boundaries }
                LyricLine(start, lineText, translation, if (safe) segments else emptyList(), if (safe) LyricTimingSource.Accurate else LyricTimingSource.Line, end)
            }.sortedBy { it.timeMs }.also { require(it.isNotEmpty()) { "歌词没有可显示的时间行" } }
    }
}
