package com.seasonyuu.fnmusic.core.network

import com.seasonyuu.fnmusic.core.model.MusicError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response
import java.net.Inet6Address
import java.net.InetAddress
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

class ConnectionPolicy {
    suspend fun validateAndNormalize(value: String, allowPrivateLanHttp: Boolean): HttpUrl = withContext(Dispatchers.IO) {
        val url = runCatching { value.trim().toHttpUrl() }
            .getOrElse { throw MusicError.UnsafeConnection("请输入完整的 HTTP 或 HTTPS 地址") }
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw MusicError.UnsafeConnection("地址中不能包含用户名或密码")
        }
        if (url.scheme == "http") {
            if (!allowPrivateLanHttp) throw MusicError.UnsafeConnection("HTTP 仅允许用于明确确认的局域网地址")
            val addresses = runCatching { InetAddress.getAllByName(url.host).toList() }
                .getOrElse { throw MusicError.UnsafeConnection("无法解析局域网地址") }
            if (addresses.isEmpty() || addresses.any { !it.isPrivateAddress() }) {
                throw MusicError.UnsafeConnection("HTTP 地址必须只解析到回环、私网、链路本地或 IPv6 ULA")
            }
        }
        val path = url.encodedPath.trimEnd('/').let { current ->
            when {
                current.endsWith("/music") -> "$current/"
                current.isBlank() || current == "/" -> "/music/"
                else -> "$current/music/"
            }
        }
        url.newBuilder().encodedPath(path).query(null).fragment(null).build()
    }

    internal fun InetAddress.isPrivateAddress(): Boolean {
        if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress) return true
        if (this is Inet6Address) {
            val first = address.first().toInt() and 0xff
            return first and 0xfe == 0xfc
        }
        return false
    }
}

class CleartextHostPolicy {
    private val allowedHost = AtomicReference<String?>(null)

    fun authorize(url: HttpUrl, userConfirmed: Boolean) {
        if (url.scheme != "http") {
            allowedHost.set(null)
            return
        }
        if (!userConfirmed) throw MusicError.UnsafeConnection("局域网 HTTP 尚未获得用户授权")
        allowedHost.set(url.host.lowercase())
    }

    fun clear() = allowedHost.set(null)

    fun permits(host: String): Boolean = allowedHost.get() == host.lowercase()
}

/** A network interceptor runs again for redirect follow-ups, preventing HTTP DNS rebinding/escape. */
class PrivateHttpNetworkInterceptor(private val cleartextPolicy: CleartextHostPolicy) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (url.scheme == "http") {
            if (!cleartextPolicy.permits(url.host)) {
                throw IOException("已阻止未经授权的局域网 HTTP 主机")
            }
            val addresses = runCatching { InetAddress.getAllByName(url.host).toList() }.getOrDefault(emptyList())
            val policy = ConnectionPolicy()
            if (addresses.isEmpty() || addresses.any { address -> with(policy) { !address.isPrivateAddress() } }) {
                throw IOException("已阻止局域网 HTTP 跳转到非私网地址")
            }
        }
        return chain.proceed(chain.request())
    }
}
