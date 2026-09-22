package com.seasonyuu.fnmusic.core.player

import android.graphics.Bitmap
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import com.seasonyuu.fnmusic.core.airplay.AirPlayNowPlaying
import com.seasonyuu.fnmusic.core.airplay.AirPlaySession
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

/** Polls on the player's main dispatcher. Loading and encoding never block the audio worker. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun CoroutineScope.publishAirPlayNowPlaying(
    player: Player, loader: BitmapLoader, connection: () -> AirPlaySession?,
) = launch {
    var previousConnection: AirPlaySession? = null
    var previousKey: Any? = null
    var cover = byteArrayOf()
    var coverJob: Job? = null
    var artworkVersion = 0
    var lastState: Any? = null
    var lastPosition = 0L
    var lastPublished = 0L
    try {
        while (isActive) {
            val target = connection()?.takeIf { it.ready }
            val metadata = player.mediaMetadata
            val key = listOf(player.currentMediaItem?.mediaId, metadata.title, metadata.artist,
                metadata.albumTitle, metadata.artworkUri, metadata.artworkData?.contentHashCode())
            if (target !== previousConnection || key != previousKey) {
                previousConnection = target; previousKey = key
                coverJob?.cancel(); cover = byteArrayOf(); artworkVersion++; lastState = null
                if (target != null) {
                    val future = runCatching { loader.loadBitmapFromMetadata(metadata) }.getOrNull()
                    if (future != null) coverJob = launch {
                        try {
                            while (!future.isDone) delay(50)
                            val bitmap = future.get()
                            val encoded = withContext(Dispatchers.Default) {
                                val scale = minOf(1.0, 512.0 / maxOf(bitmap.width, bitmap.height))
                                val small = Bitmap.createScaledBitmap(bitmap,
                                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                                    (bitmap.height * scale).toInt().coerceAtLeast(1), true)
                                ByteArrayOutputStream().use { out ->
                                    small.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                    out.toByteArray().takeIf { it.size <= 512 * 1024 } ?: byteArrayOf()
                                }
                            }
                            ensureActive()
                            cover = encoded; artworkVersion++
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { /* Missing cover must not interrupt music. */ }
                    }
                }
            }
            if (target != null) {
                val position = player.currentPosition.coerceAtLeast(0)
                val duration = player.duration.coerceAtLeast(0)
                val state = listOf(key, player.isPlaying, duration, artworkVersion)
                val now = android.os.SystemClock.elapsedRealtime()
                if (state != lastState || kotlin.math.abs(position - lastPosition) > 2000 ||
                    now - lastPublished >= 5000) {
                    target.nowPlaying(AirPlayNowPlaying(metadata.title?.toString().orEmpty(),
                        metadata.artist?.toString().orEmpty(), metadata.albumTitle?.toString().orEmpty(),
                        position, duration, player.isPlaying, cover))
                    lastState = state; lastPublished = now
                }
                lastPosition = position
            }
            delay(250)
        }
    } finally { coverJob?.cancel() }
}
