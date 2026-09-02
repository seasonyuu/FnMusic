package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.MusicError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom

private const val FN_CONNECT_API_KEY = "zIGtkc3dqZnJpd29qZXJqa2w7c"
private const val FN_CONNECT_ENDPOINT = "/api/v1/fn/con"

@Serializable
private data class LocatorRequest(val fnId: String)

@Serializable
private data class LocatorData(val fn: List<String> = emptyList())

@Serializable
private data class LocatorEnvelope(val code: Int = -1, val msg: String = "", val data: LocatorData? = null)

class FnConnectResolver(
    private val client: OkHttpClient,
    private val cookieJar: MemoryCookieJar,
    private val json: Json,
) {
    suspend fun resolveAndActivate(fnId: String): HttpUrl = withContext(Dispatchers.IO) {
        val normalized = fnId.trim()
        require(normalized.length >= 6) { "FN ID 至少需要 6 个字符" }
        val bodyText = json.encodeToString(LocatorRequest(normalized))
        val timestamp = System.currentTimeMillis().toString()
        val nonce = SecureRandom().nextInt(900_000).plus(100_000).toString()
        val signingText = listOf(
            AUTHX_PREFIX,
            FN_CONNECT_ENDPOINT,
            nonce,
            timestamp,
            bodyText.md5(),
            FN_CONNECT_API_KEY,
        ).joinToString("_")
        val fnSign = "trim_connect`$normalized`$timestamp`anna".sha256()
        val request = Request.Builder()
            .url("https://fnos.net$FN_CONNECT_ENDPOINT")
            .post(bodyText.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json, text/plain, */*")
            .header("authx", "nonce=$nonce&timestamp=$timestamp&sign=${signingText.md5()}")
            .header("fn-sign", fnSign)
            .build()
        val response = client.newCall(request).execute()
        response.use {
            if (!it.isSuccessful) throw MusicError.Network(IllegalStateException("FN Connect HTTP ${it.code}"))
            val envelope = json.decodeFromString<LocatorEnvelope>(it.body?.string().orEmpty())
            if (envelope.code != 0) throw MusicError.Business(envelope.code, envelope.msg)
            val relay = envelope.data?.fn.orEmpty().firstNotNullOfOrNull(::permittedRelay)
                ?: throw MusicError.Protocol("FN Connect 没有返回受支持的 HTTPS relay")
            activate(relay, normalized)
        }
    }

    private fun permittedRelay(candidate: String): HttpUrl? {
        val parsed = runCatching { "https://$candidate/music/".toHttpUrl() }.getOrNull() ?: return null
        if (parsed.port != 443) return null
        val host = parsed.host.lowercase()
        return parsed.takeIf { host.endsWith(".fnos.net") || host.endsWith(".5ddd.com") }
    }

    private fun activate(baseUrl: HttpUrl, fnId: String): HttpUrl {
        val request = Request.Builder()
            .url(baseUrl)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Origin", "https://fnos.net")
            .header("Referer", "https://fnos.net/$fnId/music/")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw MusicError.Protocol("relay 激活失败：HTTP ${response.code}")
            val relayCookie = cookieJar.loadForRequest(baseUrl).any { it.name == "mode" && it.value == "relay" }
            if (!relayCookie) throw MusicError.Protocol("relay 未返回 mode=relay Cookie")
        }
        return baseUrl
    }
}
