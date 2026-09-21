package com.seasonyuu.fnmusic.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Bundled Web artwork. Local template identifiers are not server cover IDs. */
object DefaultPlaylistCover {
    private val previews = mutableMapOf<String, Bitmap>()

    /** Four shared previews; callers must not recycle them. */
    fun preview(context: Context, id: String): Bitmap = synchronized(previews) {
        previews.getOrPut(id) {
            context.assets.open(assetPath(id)).use { input ->
                requireNotNull(BitmapFactory.decodeStream(input)) { "默认封面资源不可用" }
            }
        }
    }

    /** Upload the original PNG, without re-encoding or rescaling it. */
    fun bytes(context: Context, id: String): ByteArray =
        context.assets.open(assetPath(id)).use { it.readBytes() }

    private fun assetPath(id: String): String {
        val index = (1..4).firstOrNull { id == "playlist_default_$it" } ?: error("未知默认封面")
        return "playlist-covers/$index.png"
    }
}
