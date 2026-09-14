package com.seasonyuu.fnmusic.core.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.IOException

class MusicTranscoding(private val network: NetworkRuntime) {
    private val mutex = Mutex()
    private val sessions = mutableSetOf<String>()
    suspend fun reset() = mutex.withLock { sessions.clear() }

    suspend fun prepare(guid: String) = mutex.withLock {
        if (guid in sessions) return@withLock
        val result = network.accountMutationApi.startTranscode(buildJsonObject {
            put("guid", guid)
            putJsonObject("output") { put("codec", "opus"); put("bitrate", 128); put("channel", 2) }
        }).requireData()
        if (result.string("status") !in setOf("success", "ready")) {
            throw IOException("服务器无法提供标准音质（${result.string("errno").ifBlank { result.string("status") }}），请在音质偏好中选择原始音质。")
        }
        delay(50)
        sessions += guid
    }
    suspend fun maintain(currentGuid: String?, seconds: Long) = mutex.withLock {
        val obsolete = sessions.filter { it != currentGuid }
        obsolete.forEach { guid ->
            // Remove only after a successful quit so a temporary error can be retried.
            network.api.quitTranscode(buildJsonObject { put("guid", guid) }).requireSuccess()
            sessions -= guid
        }
        if (currentGuid in sessions) network.api.heartbeatTranscode(buildJsonObject {
            put("guid", currentGuid!!); put("timestamp", seconds)
        }).requireSuccess()
    }
}
