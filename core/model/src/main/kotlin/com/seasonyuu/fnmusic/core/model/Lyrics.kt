package com.seasonyuu.fnmusic.core.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
enum class LyricsFetchSource(val label: String, val baseUrl: String) {
    Bikonoo("Bikonoo", "https://amlldb.bikonoo.com/"),
    GitHub("GitHub", "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/"),
    Dimeta("Dimeta", "https://amll.mirror.dimeta.top/api/db/"),
    Gdba("GDBA", "https://amll-ttml-db.gbclstudio.cn/"),
}

@Serializable
enum class OnlineLyricsSource(val label: String) { Netease("网易云音乐"), QQ("QQ 音乐"), Kugou("酷狗音乐") }

@Serializable
data class OnlineLyricsPreference(
    val enabled: Boolean = true,
    val sources: Set<OnlineLyricsSource> = OnlineLyricsSource.entries.toSet(),
    val order: List<OnlineLyricsSource> = OnlineLyricsSource.entries.toList(),
) {
    val orderedSources: List<OnlineLyricsSource> get() = (order + OnlineLyricsSource.entries).distinct()
    val enabledSources: List<OnlineLyricsSource> get() = orderedSources.filter { it in sources }
}

@Serializable
data class LyricsCandidate(
    val rawFile: String,
    val titles: List<String>,
    val artists: List<String>,
    val albums: List<String> = emptyList(),
    val platformIds: Map<String, List<String>> = emptyMap(),
    val authors: List<String> = emptyList(),
    val onlineSource: OnlineLyricsSource? = null,
    val songId: String = "",
    val durationMs: Long = 0,
    val downloadMetadata: Map<String, String> = emptyMap(),
) {
    fun availableFrom(source: LyricsFetchSource): Boolean = onlineSource != null || source != LyricsFetchSource.Dimeta ||
        listOf("ncmMusicId", "qqMusicId", "appleMusicId", "spotifyId").any { key ->
            platformIds[key].orEmpty().any { it.matches(Regex("[A-Za-z0-9_-]+")) }
        }
}

@Serializable
enum class LyricsChoiceMode { Automatic, Amll, FnMusic, Online }

@Serializable
data class LyricsChoice(
    val mode: LyricsChoiceMode = LyricsChoiceMode.Automatic,
    val candidate: LyricsCandidate? = null,
    val offsetMs: Long = 0,
)

enum class LyricsOrigin { Amll, FnMusic, Netease, QQ, Kugou }
data class LyricsDocument(
    val lines: List<LyricLine>,
    val origin: LyricsOrigin,
    val candidate: LyricsCandidate? = null,
    val offsetMs: Long = 0,
)
data class LyricsState(val document: LyricsDocument? = null, val loading: Boolean = false, val error: String? = null)
enum class LyricsIndexStatus { Missing, Checking, Current, Updated, Failed }
data class LyricsIndexState(
    val source: LyricsFetchSource = LyricsFetchSource.Bikonoo,
    val version: String? = null,
    val bytes: Long = 0,
    val checkedAt: Long? = null,
    val status: LyricsIndexStatus = LyricsIndexStatus.Missing,
    val error: String? = null,
)
data class LyricsCacheUsage(val count: Int = 0, val bytes: Long = 0)

/** UI boundary; track identity is explicit so a dialog cannot modify the next playing song. */
interface LyricsActions {
    val accountKey: String get() = ""
    val onlinePreference: Flow<OnlineLyricsPreference>
    val onlineCacheUsage: StateFlow<LyricsCacheUsage>
    suspend fun setOnlinePreference(preference: OnlineLyricsPreference)
    suspend fun clearOnlineCache()
    suspend fun searchOnline(source: OnlineLyricsSource, query: String): List<LyricsCandidate>
    val amllEnabled: Flow<Boolean>
    suspend fun setAmllEnabled(enabled: Boolean)
    val index: StateFlow<LyricsIndexState>
    val cacheUsage: StateFlow<LyricsCacheUsage>
    suspend fun setSource(source: LyricsFetchSource)
    suspend fun updateIndex()
    suspend fun clearCache()
    suspend fun search(query: String): List<LyricsCandidate>
    suspend fun preview(candidate: LyricsCandidate): LyricsDocument
    fun choice(track: Track): Flow<LyricsChoice>
    suspend fun choose(track: Track, choice: LyricsChoice)
}

val LyricsDocument.hasAccurateWords: Boolean
    get() = lines.any { it.timingSource == LyricTimingSource.Accurate && it.segments.isNotEmpty() }

val LyricsOrigin.label: String get() = when (this) {
    LyricsOrigin.Amll -> "AMLL TTML DB"
    LyricsOrigin.FnMusic -> "飞牛音乐"
    LyricsOrigin.Netease -> "网易云音乐"
    LyricsOrigin.QQ -> "QQ 音乐"
    LyricsOrigin.Kugou -> "酷狗音乐"
}
