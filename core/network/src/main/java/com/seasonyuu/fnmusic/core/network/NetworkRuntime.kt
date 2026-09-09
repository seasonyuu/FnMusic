package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.MusicError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

class BaseUrlProvider {
    private val mutable = MutableStateFlow<HttpUrl?>(null)
    val value: StateFlow<HttpUrl?> = mutable

    fun set(url: HttpUrl) {
        mutable.value = url
    }

    fun require(): HttpUrl = mutable.value ?: throw MusicError.Protocol("音乐服务地址尚未准备好")

    fun streamUrl(trackGuid: String): String =
        require().newBuilder()
            .addPathSegments("api/v1/track/stream")
            .addQueryParameter("guid", trackGuid)
            .build()
            .toString()

    fun coverUrl(coverId: String, size: Int): String =
        require().newBuilder()
            .addPathSegments("api/v1/static/cover")
            .addQueryParameter("coverId", coverId)
            .addQueryParameter("size", size.toString())
            .build()
            .toString()
}

class MemoryCookieJar : CookieJar {
    private val cookies = CopyOnWriteArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { incoming ->
            this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
            if (incoming.expiresAt > System.currentTimeMillis()) this.cookies += incoming
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        return cookies.filter { it.matches(url) }
    }

    fun setMusicToken(url: HttpUrl, token: String) {
        val encoded = URLEncoder.encode(token, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val builder = Cookie.Builder()
            .name("music-token")
            .value(encoded)
            .domain(url.host)
            .path("/")
        if (url.isHttps) builder.secure()
        saveFromResponse(url, listOf(builder.build()))
    }

    fun clear() = cookies.clear()
}

class DynamicBaseUrlInterceptor(private val provider: BaseUrlProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.url.host != PLACEHOLDER_HOST) return chain.proceed(original)
        val base = provider.require()
        val endpointPath = original.url.encodedPath.removePrefix("/")
        val path = base.encodedPath.trimEnd('/') + "/" + endpointPath
        val target = base.newBuilder()
            .encodedPath(path)
            .encodedQuery(original.url.encodedQuery)
            .build()
        return chain.proceed(original.newBuilder().url(target).build())
    }

    private companion object {
        const val PLACEHOLDER_HOST = "music.invalid"
    }
}

/**
 * Retries a failed read once after the session coordinator has re-located and
 * authenticated the service. Writes are deliberately not replayed because the
 * server may have applied them before returning an unexpected response.
 */
class SessionRecoveryInterceptor : Interceptor {
    @Volatile private var recover: (() -> Boolean)? = null
    private val recoveryLock = Any()
    private val generation = AtomicLong(0)

    fun install(handler: () -> Boolean) {
        recover = handler
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val incoming = chain.request()
        val bypass = incoming.header(NO_SESSION_RECOVERY_HEADER) == "true"
        val request = incoming.newBuilder().removeHeader(NO_SESSION_RECOVERY_HEADER).build()
        val observedGeneration = generation.get()
        val response = chain.proceed(request)
        if (bypass || request.method !in setOf("GET", "HEAD") || !response.requiresSessionRecovery()) return response

        val recovered = synchronized(recoveryLock) {
            if (generation.get() != observedGeneration) true
            else recover?.invoke() == true && generation.incrementAndGet() >= 0
        }
        if (!recovered) return response

        response.close()
        return chain.proceed(request)
    }

    private fun Response.requiresSessionRecovery(): Boolean {
        if (code == 401) return true
        val contentType = header("Content-Type").orEmpty().lowercase()
        if (contentType.contains("text/html")) return true
        if (!contentType.contains("json")) return false
        return peekBody(1_024).string().contains(BUSINESS_AUTH_CODE)
    }

    private companion object {
        val BUSINESS_AUTH_CODE = Regex("\\\"code\\\"\\s*:\\s*401(?:\\D|$)")
    }
}

const val NO_SESSION_RECOVERY_HEADER = "X-Fn-No-Session-Recovery"

class NetworkRuntime {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
    val baseUrlProvider = BaseUrlProvider()
    val cookieJar = MemoryCookieJar()
    private val cleartextHostPolicy = CleartextHostPolicy()
    private val sessionRecovery = SessionRecoveryInterceptor()

    private val unsignedClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addNetworkInterceptor(PrivateHttpNetworkInterceptor(cleartextHostPolicy))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val httpClient: OkHttpClient = unsignedClient.newBuilder()
        .addInterceptor(DynamicBaseUrlInterceptor(baseUrlProvider))
        .addInterceptor(sessionRecovery)
        .addInterceptor(AuthxInterceptor())
        .build()

    val api: MusicApi = createApi(httpClient)

    // Recovery runs while catalog requests occupy dispatcher slots. Give its
    // bypassed auth calls their own slots so a full queue cannot deadlock login.
    internal val sessionApi: MusicApi = createApi(httpClient.newBuilder().dispatcher(okhttp3.Dispatcher()).build())

    private fun createApi(client: OkHttpClient): MusicApi = Retrofit.Builder()
        .baseUrl("https://music.invalid/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(MusicApi::class.java)

    val resolver = FnConnectResolver(unsignedClient, cookieJar, json)

    fun installSessionRecovery(handler: () -> Boolean) {
        sessionRecovery.install(handler)
    }

    fun activateBaseUrl(url: HttpUrl, allowPrivateLanHttp: Boolean) {
        cleartextHostPolicy.authorize(url, allowPrivateLanHttp)
        baseUrlProvider.set(url)
    }
}

fun <T> ApiEnvelope<T>.requireData(): T {
    requireSuccess()
    return data ?: throw MusicError.Protocol("服务端响应缺少 data")
}

fun ApiEnvelope<*>.requireSuccess() {
    if (code != 0) {
        if (code == 401) throw MusicError.Authentication(msg.ifBlank { "登录状态已失效" })
        throw MusicError.Business(code, msg.ifBlank { "服务端返回错误 $code" })
    }
}
