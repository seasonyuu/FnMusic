package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.serialization.json.*
import java.text.Normalizer
import java.util.Locale

internal object AmllIndex {
    private val json = Json { ignoreUnknownKeys = true }
    // Historical collaborative submissions separate author IDs with commas.
    private val filePattern = Regex("[A-Za-z0-9_,-]+\\.ttml")
    private val platforms = listOf("ncmMusicId" to "ncm-lyrics", "qqMusicId" to "qq-lyrics", "appleMusicId" to "am-lyrics", "spotifyId" to "spotify-lyrics")

    fun parse(content: String): List<LyricsCandidate> {
        val rows = content.lineSequence().filter { it.isNotBlank() }.map { line ->
            val value = json.parseToJsonElement(line).jsonObject
            val raw = value.getValue("rawLyricFile").jsonPrimitive.content
            require(filePattern.matches(raw)) { "索引包含无效文件名" }
            val metadata = value.getValue("metadata").jsonArray.associate { item ->
                val pair = item.jsonArray
                require(pair.size == 2)
                pair[0].jsonPrimitive.content to pair[1].jsonArray.map { it.jsonPrimitive.content }
            }
            LyricsCandidate(raw, metadata["musicName"].orEmpty(), metadata["artists"].orEmpty(),
                metadata["album"].orEmpty(), platforms.mapNotNull { (key, _) -> metadata[key]?.let { key to it } }.toMap(),
                metadata["ttmlAuthorGithubLogin"] ?: metadata["ttmlAuthorGithub"].orEmpty())
        }.toList()
        require(rows.isNotEmpty()) { "索引为空" }
        // Resolve each platform independently: a newer submission for one ID must not erase other IDs.
        val latest = mutableMapOf<Pair<String, String>, String>()
        rows.forEach { row -> row.platformIds.forEach { (key, ids) -> ids.forEach { latest[key to it] = row.rawFile } } }
        return rows.distinctBy { it.rawFile }.mapNotNull { row ->
            val ids = row.platformIds.mapValues { (key, ids) -> ids.filter { latest[key to it] == row.rawFile } }.filterValues { it.isNotEmpty() }
            if (row.platformIds.isNotEmpty() && ids.isEmpty()) null else row.copy(platformIds = ids)
        }
    }

    fun path(source: LyricsFetchSource, candidate: LyricsCandidate): String? {
        if (source != LyricsFetchSource.Dimeta) return candidate.rawFile.takeIf(filePattern::matches)?.let { "raw-lyrics/$it" }
        return platforms.firstNotNullOfOrNull { (key, folder) ->
            candidate.platformIds[key]?.firstOrNull { it.matches(Regex("[A-Za-z0-9_-]+")) }?.let { "$folder/$it.ttml" }
        }
    }

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()

    private val versionMarkers = Regex("\\b(live|remix|instrumental|karaoke|acoustic|remaster(?:ed)?)\\b|现场|伴奏|重制|重製|演唱会|演唱會")
    private fun versions(titles: List<String>) = titles.flatMap { versionMarkers.findAll(normalize(it)).map { match -> match.value }.toList() }.toSet()

    fun match(rows: List<LyricsCandidate>, track: Track): LyricsCandidate? {
        val title = normalize(track.title)
        val artists = track.artists.map { normalize(it.name) }.filter { it.isNotEmpty() }.toSet()
        if (title.isEmpty() || artists.isEmpty()) return null
        val matches = rows.filter { row -> row.titles.any { normalize(it) == title } &&
            versions(row.titles) == versions(listOf(track.title)) &&
            // AMLL includes translated names and aliases alongside performer names.
            // Require every NAS artist to match, while allowing extra index names.
            row.artists.map(::normalize).toSet().containsAll(artists) }
        if (matches.size == 1) return matches.single()
        val album = track.album?.name?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: return null
        return matches.filter { it.albums.any { album == normalize(it) } }.singleOrNull()
    }

    fun search(rows: List<LyricsCandidate>, query: String): List<LyricsCandidate> {
        val terms = normalize(query).split(' ').filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return rows.filter { row ->
            val text = normalize((row.titles + row.artists + row.albums).joinToString(" "))
            terms.all(text::contains)
        }.sortedByDescending { row -> row.titles.any { normalize(it) == normalize(query) } }.take(100)
    }
}
