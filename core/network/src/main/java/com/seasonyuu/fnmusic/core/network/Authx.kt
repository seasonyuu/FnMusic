package com.seasonyuu.fnmusic.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale

internal const val AUTHX_PREFIX = "NDzZTVxnRKP8Z0jXg1VAMonaG8akvh"
internal const val AUTHX_API_KEY = "6D5602D4-A342-4799-A0F0-BB795E7167D0"

fun ByteArray.hexDigest(algorithm: String): String =
    MessageDigest.getInstance(algorithm).digest(this).joinToString("") { "%02x".format(Locale.US, it) }

fun String.md5(): String = toByteArray(StandardCharsets.UTF_8).hexDigest("MD5")
fun String.sha256(): String = toByteArray(StandardCharsets.UTF_8).hexDigest("SHA-256")

class AuthxSigner(
    private val nonceProvider: () -> String = { SecureRandom().nextInt(900_000).plus(100_000).toString() },
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    fun sign(method: String, encodedPath: String, queryPairs: List<Pair<String, String>>, body: String?): String {
        val dataHash = if (method.equals("GET", ignoreCase = true)) {
            val values = linkedMapOf<String, String>()
            queryPairs.forEach { (key, value) ->
                if (value != "undefined" && value != "null") values[key] = value
            }
            val encoded = values.toSortedMap().entries.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
            URLDecoder.decode(encoded, StandardCharsets.UTF_8.name()).md5()
        } else {
            (body ?: "").md5()
        }
        val nonce = nonceProvider()
        val timestamp = timeProvider().toString()
        val signingText = listOf(AUTHX_PREFIX, encodedPath, nonce, timestamp, dataHash, AUTHX_API_KEY).joinToString("_")
        return "nonce=$nonce&timestamp=$timestamp&sign=${signingText.md5()}"
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
}

class AuthxInterceptor(private val signer: AuthxSigner = AuthxSigner()) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val bodyText = request.body?.let { body ->
            Buffer().use { buffer ->
                body.writeTo(buffer)
                buffer.readUtf8()
            }
        }
        val queryPairs = buildList {
            for (index in 0 until request.url.querySize) {
                add(request.url.queryParameterName(index) to (request.url.queryParameterValue(index) ?: ""))
            }
        }
        val authx = signer.sign(request.method, request.url.encodedPath, queryPairs, bodyText)
        return chain.proceed(request.newBuilder().header("authx", authx).build())
    }
}
