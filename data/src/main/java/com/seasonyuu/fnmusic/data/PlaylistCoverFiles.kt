package com.seasonyuu.fnmusic.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.seasonyuu.fnmusic.core.network.MusicApi
import com.seasonyuu.fnmusic.core.network.requireData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/** Only an app-private, non-backed-up draft copy is retained, never a gallery permission. */
class PlaylistCoverFiles(context: Context, private val api: MusicApi) {
    private val appContext = context.applicationContext
    private val resolver = context.applicationContext.contentResolver
    private val directory = File(context.noBackupFilesDir, "playlist-cover-drafts")

    suspend fun prepare(uri: String): String = withContext(Dispatchers.IO) {
        val source = Uri.parse(uri)
        require(source.scheme == "content") { "请选择相册中的图片" }
        val bytes = resolver.openInputStream(source)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= MAX_BYTES) { "图片不能超过 5 MiB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("无法读取照片，请重新选择")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "图片已损坏或无法读取" }
        val extension = when (bounds.outMimeType) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> error("仅支持 JPG、PNG 或 WEBP 图片")
        }
        check(directory.isDirectory || directory.mkdirs()) { "无法创建封面草稿" }
        val file = File.createTempFile("cover-", ".$extension", directory)
        try { file.writeBytes(bytes); file.absolutePath }
        catch (e: Exception) { file.delete(); throw e }
    }

    suspend fun upload(path: String): String = withContext(Dispatchers.IO) {
        val file = ownedFile(path)
        require(file.isFile && file.length() in 1..MAX_BYTES.toLong()) { "封面草稿不可用，请重新选择照片" }
        val mime = when (file.extension) {
            "jpg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"
            else -> error("不支持的图片格式")
        }
        val part = MultipartBody.Part.createFormData("file", "cover.${file.extension}", file.asRequestBody(mime.toMediaType()))
        api.uploadPlaylistCover(part).requireData().coverId.also {
            require(it.isNotBlank()) { "上传响应缺少 coverId" }
        }
    }

    fun discard(path: String) { runCatching { ownedFile(path).delete() } }

    suspend fun uploadDefault(coverId: String): String = withContext(Dispatchers.IO) {
        val bytes = DefaultPlaylistCover.bytes(appContext, coverId)
        val part = MultipartBody.Part.createFormData("file", "cover.png", bytes.toRequestBody("image/png".toMediaType()))
        api.uploadPlaylistCover(part).requireData().coverId.also {
            require(it.isNotBlank()) { "上传响应缺少 coverId" }
        }
    }

    private fun ownedFile(path: String): File = File(path).canonicalFile.also {
        require(it.parentFile == directory.canonicalFile && it.name.startsWith("cover-")) { "无效封面草稿" }
    }

    private companion object { const val MAX_BYTES = 5 * 1024 * 1024 }
}
