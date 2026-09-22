package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.AppRelease
import com.seasonyuu.fnmusic.core.model.PROJECT_URL
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

internal data class ReleaseVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int =
        compareValuesBy(this, other, ReleaseVersion::major, ReleaseVersion::minor, ReleaseVersion::patch)

    companion object {
        private val release = Regex("^v?(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})\\.(0|[1-9][0-9]{0,2})$")
        private val development = Regex("^(.+)-dev\\.[0-9]+\\+[0-9a-f]+$")
        fun parse(value: String, allowDevelopment: Boolean = false): ReleaseVersion? {
            val base = if (allowDevelopment) development.matchEntire(value)?.groupValues?.get(1) ?: value else value
            val groups = release.matchEntire(base)?.groupValues ?: return null
            return ReleaseVersion(groups[1].toInt(), groups[2].toInt(), groups[3].toInt())
        }
    }
}

// Intentionally independent of the authenticated NAS client, its cookies and interceptors.
internal class AppUpdateRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build(),
    private val endpoint: String = "https://api.github.com/repos/seasonyuu/FnMusic/releases/latest",
) {
    suspend fun check(currentVersion: String): AppRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(endpoint)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "FnMusic-Update-Check")
            .build()
        client.newCall(request).execute().use { response ->
            when (response.code) {
                404 -> throw IOException("暂未找到正式发布，请查看发布列表")
                403, 429 -> throw IOException("GitHub 暂时限制了请求，请稍后重试")
            }
            if (!response.isSuccessful) throw IOException("检查更新失败（HTTP ${response.code}），请重试")
            val body = response.body?.string() ?: throw IOException("更新信息为空，请重试")
            parseRelease(body, currentVersion)
        }
    }
}

internal fun parseRelease(body: String, currentVersion: String): AppRelease {
    val json = Json.parseToJsonElement(body).jsonObject
    require(json["draft"]?.jsonPrimitive?.booleanOrNull == false &&
        json["prerelease"]?.jsonPrimitive?.booleanOrNull == false) { "不是正式发布" }
    fun string(key: String) = json[key]?.jsonPrimitive?.content ?: error("缺少 $key")
    val tag = string("tag_name")
    require(tag.startsWith("v")) { "无法识别的版本标签" }
    val remote = requireNotNull(ReleaseVersion.parse(tag)) { "无法识别的版本标签" }
    val current = requireNotNull(ReleaseVersion.parse(currentVersion, allowDevelopment = true)) { "无法识别当前版本" }
    val publishedAt = string("published_at")
    java.time.Instant.parse(publishedAt)
    // Construct the URL from a validated tag; never navigate to a server-supplied arbitrary URL.
    return AppRelease(tag.removePrefix("v"), publishedAt,
        json["body"]?.jsonPrimitive?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content.orEmpty(),
        "$PROJECT_URL/releases/tag/$tag", remote > current)
}
