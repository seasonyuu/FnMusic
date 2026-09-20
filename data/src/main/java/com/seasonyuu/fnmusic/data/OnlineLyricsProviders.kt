package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface LyricsProvider {
    val source: OnlineLyricsSource
    suspend fun search(query: String): List<LyricsCandidate>
    suspend fun load(candidate: LyricsCandidate): List<LyricLine>
}

internal class LyricsHttp(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS).build(),
    private val rewriteUrl: (String) -> String = { it },
) {
    private val permits = Semaphore(3)
    suspend fun request(url: String, body: String? = null, form: Boolean = false, headers: Map<String, String> = emptyMap()): ByteArray = permits.withPermit {
        val request = Request.Builder().url(rewriteUrl(url)).header("User-Agent", "Mozilla/5.0")
        headers.forEach { (key, value) -> request.header(key, value) }
        body?.let { request.post(it.toRequestBody((if (form) "application/x-www-form-urlencoded" else "application/json").toMediaType())) }
        val call = client.newCall(request.build())
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val bytes = response.use {
                            check(it.isSuccessful) { "歌词服务返回 HTTP ${it.code}" }
                            val input = it.body?.byteStream() ?: error("歌词服务返回空内容")
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val size = input.read(buffer)
                                if (size < 0) break
                                require(output.size() + size <= 4 * 1024 * 1024) { "歌词响应过大" }
                                output.write(buffer, 0, size)
                            }
                            output.toByteArray()
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
                }
            })
        }
    }
}

internal fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonElement?.items(): List<JsonElement> = (this as? JsonArray)?.toList().orEmpty()
internal fun JsonElement?.str(): String = (this as? JsonPrimitive)?.contentOrNull.orEmpty()
internal fun JsonElement?.number(): Long = str().toLongOrNull() ?: 0
internal fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
private fun json(bytes: ByteArray) = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
private fun candidate(source: OnlineLyricsSource, id: String, title: String, artists: List<String>, album: String, duration: Long, metadata: Map<String, String> = emptyMap()) =
    LyricsCandidate("", listOf(title), artists, listOf(album).filter { it.isNotBlank() }, onlineSource = source, songId = id, durationMs = duration, downloadMetadata = metadata)

internal class NeteaseLyricsProvider(private val http: LyricsHttp) : LyricsProvider {
    override val source = OnlineLyricsSource.Netease
    private suspend fun api(path: String, params: JsonObject): JsonObject {
        val payload = JsonObject(params + mapOf("e_r" to JsonPrimitive(true), "header" to JsonPrimitive(buildJsonObject {
            put("os", "pc"); put("appver", "3.1.3.203419"); put("requestId", System.currentTimeMillis().toString())
        }.toString()))).toString()
        val apiPath = path.replace("/eapi/", "/api/")
        val digest = md5("nobody${apiPath}use${payload}md5forencrypt")
        val plain = "$apiPath-36cd479b6b5-$payload-36cd479b6b5-$digest"
        val key = SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain.toByteArray()).joinToString("") { "%02x".format(it) }
        val response = http.request("https://interface.music.163.com$path", "params=$encrypted", true, mapOf("Referer" to "https://music.163.com/"))
        val result = if (response.firstOrNull()?.toInt() == '{'.code) json(response) else {
            cipher.init(Cipher.DECRYPT_MODE, key); json(cipher.doFinal(response))
        }
        check(result["code"].number() == 200L) { "网易云请求失败：${result["code"].str()}" }
        return result
    }
    override suspend fun search(query: String): List<LyricsCandidate> {
        val root = api("/eapi/search/song/list/page", buildJsonObject {
            put("limit", "10"); put("offset", "0"); put("keyword", query); put("scene", "NORMAL"); put("needCorrect", "true")
        })
        check(root["data"].obj()["resources"] is JsonArray) { "网易云返回无效搜索结果" }
        return root["data"].obj()["resources"].items().mapNotNull {
            val song = it.obj()["baseInfo"].obj()["simpleSongData"].obj()
            val id = song["id"].str(); val title = song["name"].str()
            if (id.isBlank() || title.isBlank()) null else candidate(source, id, title,
                (song["ar"] ?: song["artists"]).items().map { artist -> artist.obj()["name"].str() },
                (song["al"] ?: song["album"]).obj()["name"].str(), (song["dt"] ?: song["duration"]).number())
        }.take(10)
    }
    override suspend fun load(candidate: LyricsCandidate): List<LyricLine> {
        val root = api("/eapi/song/lyric/v1", buildJsonObject {
            put("id", candidate.songId.toLong()); listOf("lv", "tv", "rv", "yv", "ytv").forEach { put(it, -1) }
        })
        val timed = OnlineLyricsParser.timed(root["yrc"].obj()["lyric"].str(), source)
        val lines = timed.ifEmpty { OnlineLyricsParser.lrc(root["lrc"].obj()["lyric"].str()) }
        return OnlineLyricsParser.translate(lines, (root["ytlrc"] ?: root["tlyric"]).obj()["lyric"].str())
    }
}

internal class QQLyricsProvider(private val http: LyricsHttp) : LyricsProvider {
    override val source = OnlineLyricsSource.QQ
    private suspend fun api(module: String, method: String, params: JsonObject): JsonObject {
        val body = buildJsonObject {
            put("comm", buildJsonObject { put("ct", "11"); put("cv", "1003006"); put("v", "1003006"); put("tmeAppID", "qqmusiclight") })
            put("req_0", buildJsonObject { put("module", module); put("method", method); put("param", params) })
        }
        val root = json(http.request("https://u.y.qq.com/cgi-bin/musicu.fcg", body.toString()))
        val envelope = root["req_0"].obj()
        check(root["code"].number() == 0L && envelope["code"].number() == 0L && envelope.containsKey("data")) { "QQ 音乐请求失败" }
        return envelope["data"].obj()
    }
    override suspend fun search(query: String): List<LyricsCandidate> {
        val data = api("music.search.SearchCgiService", "DoSearchForQQMusicLite", buildJsonObject {
            put("query", query); put("search_type", 0); put("num_per_page", 10); put("page_num", 1)
            put("search_id", System.currentTimeMillis().toString()); put("remoteplace", "search.android.keyboard")
            put("highlight", 0); put("nqc_flag", 0); put("page_id", 1); put("grp", 1)
        })
        check(data["body"].obj()["item_song"] is JsonArray) { "QQ 返回无效搜索结果" }
        return data["body"].obj()["item_song"].items().mapNotNull {
            val song = it.obj(); val id = song["id"].str(); val title = (song["title"] ?: song["name"]).str()
            if (id.isBlank() || title.isBlank()) null else candidate(source, id, title, song["singer"].items().map { a -> a.obj()["name"].str() },
                song["album"].obj()["name"].str(), song["interval"].number() * 1000)
        }.take(10)
    }
    override suspend fun load(candidate: LyricsCandidate): List<LyricLine> {
        fun base64(value: String) = Base64.getEncoder().encodeToString(value.toByteArray())
        val data = api("music.musichallSong.PlayLyricInfo", "GetPlayLyricInfo", buildJsonObject {
            put("songID", candidate.songId.toLong()); put("songName", base64(candidate.titles.firstOrNull().orEmpty()))
            put("albumName", base64(candidate.albums.firstOrNull().orEmpty())); put("singerName", base64(candidate.artists.joinToString("/")))
            put("crypt", 1); put("qrc", 1); put("trans", 1); put("roma", 0); put("cv", 2111); put("ct", 19)
            put("lrc_t", 0); put("qrc_t", 0); put("trans_t", 0); put("type", 0); put("interval", candidate.durationMs / 1000)
        })
        val raw = data["lyric"].str().takeIf { it.isNotBlank() }?.let(OnlineLyricsParser::qrc).orEmpty()
        val lines = OnlineLyricsParser.timed(raw, source).ifEmpty { OnlineLyricsParser.lrc(raw) }
        val translated = data["trans"].str().takeIf { it.isNotBlank() }?.let(OnlineLyricsParser::qrc).orEmpty()
        return OnlineLyricsParser.translate(lines, translated)
    }
}

internal class KugouLyricsProvider(private val http: LyricsHttp) : LyricsProvider {
    override val source = OnlineLyricsSource.Kugou
    private val device = md5(java.util.UUID.randomUUID().toString())
    private suspend fun api(url: String, custom: Map<String, String>, search: Boolean = false): JsonObject {
        val params = mutableMapOf("appid" to "3116", "clientver" to "11070")
        if (search) params.putAll(mapOf("userid" to "0", "token" to "", "clienttime" to (System.currentTimeMillis() / 1000).toString(),
            "iscorrection" to "1", "uuid" to "-", "mid" to device, "dfid" to "-", "platform" to "AndroidFilter"))
        params.putAll(custom)
        val salt = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        params["signature"] = md5(salt + params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" } + salt)
        val target = url.toHttpUrl().newBuilder().apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val data = json(http.request(target.toString(), headers = mapOf("User-Agent" to "Android14-1070-11070-201-0-SearchSong-wifi")))
        check(data["error_code"].number() == 0L) { "酷狗请求失败" }
        return data
    }
    override suspend fun search(query: String): List<LyricsCandidate> {
        val data = api("https://complexsearch.kugou.com/v2/search/song", mapOf("keyword" to query, "page" to "1", "pagesize" to "10"), true)
        check(data["data"].obj()["lists"] is JsonArray) { "酷狗返回无效搜索结果" }
        return data["data"].obj()["lists"].items().mapNotNull {
            val song = it.obj(); val id = song["ID"].str(); val title = song["SongName"].str()
            if (id.isBlank() || title.isBlank()) null else candidate(source, id, title, song["Singers"].items().map { a -> (a.obj()["name"] ?: a.obj()["Name"]).str() },
                song["AlbumName"].str(), song["Duration"].number() * 1000, mapOf("hash" to song["FileHash"].str()))
        }.take(10)
    }
    override suspend fun load(candidate: LyricsCandidate): List<LyricLine> {
        val hash = candidate.downloadMetadata["hash"].orEmpty(); require(hash.isNotBlank())
        val result = api("https://lyrics.kugou.com/v1/search", mapOf("album_audio_id" to candidate.songId, "hash" to hash,
            "duration" to candidate.durationMs.toString(), "keyword" to (candidate.artists.joinToString("/") + " - " + candidate.titles.first()), "lrctxt" to "1", "man" to "no"))
        val lyric = result["candidates"].items().firstOrNull()?.obj() ?: return emptyList()
        val data = api("https://lyrics.kugou.com/download", mapOf("accesskey" to lyric["accesskey"].str(), "id" to lyric["id"].str(),
            "charset" to "utf8", "client" to "mobi", "fmt" to "krc", "ver" to "1"))
        val encoded = data["content"].str(); if (encoded.isBlank()) return emptyList()
        val raw = if (data["contenttype"].number() == 2L) String(Base64.getDecoder().decode(encoded), Charsets.UTF_8) else OnlineLyricsParser.krc(encoded)
        val lines = OnlineLyricsParser.timed(raw, source).ifEmpty { OnlineLyricsParser.lrc(raw) }
        return addTranslation(raw, lines)
    }
    private fun addTranslation(raw: String, lines: List<LyricLine>): List<LyricLine> {
        val encoded = Regex("\\[language:([^]]+)]").find(raw)?.groupValues?.get(1) ?: return lines
        val translations = runCatching {
            json(Base64.getDecoder().decode(encoded))["content"].items().firstOrNull { it.obj()["type"].number() == 1L }
                .obj()["lyricContent"].items().map { it.items().joinToString("") { part -> part.str() } }
        }.getOrDefault(emptyList())
        return lines.mapIndexed { index, line -> line.copy(translation = translations.getOrNull(index)?.takeIf { it.isNotBlank() }) }
    }
}

internal fun defaultLyricsProviders(): List<LyricsProvider> {
    val http = LyricsHttp()
    return listOf(NeteaseLyricsProvider(http), QQLyricsProvider(http), KugouLyricsProvider(http))
}
