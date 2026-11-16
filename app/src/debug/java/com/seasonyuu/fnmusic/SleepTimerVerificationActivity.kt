package com.seasonyuu.fnmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.feature.music.MusicShell
import com.seasonyuu.fnmusic.feature.music.MusicUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Debug-only, offline acceptance entry. Never connects to a NAS or selects a remote receiver. */
class SleepTimerVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as FnMusicApplication).graph
        val player = graph.player
        setContent {
            var dark by remember { mutableStateOf(true) }
            var showingPlayer by remember { mutableStateOf(false) }
            var requestPlayer by remember { mutableStateOf(false) }
            var tracks by remember { mutableStateOf<List<PlayableTrack>>(emptyList()) }
            val state by player.state.collectAsState()
            LaunchedEffect(Unit) {
                tracks = withContext(Dispatchers.IO) {
                    val file = File(cacheDir, "sleep-timer-verification.wav")
                    val samples = 8_000 * 180
                    file.outputStream().buffered().use { output ->
                        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                        header.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
                            .putInt(16).putShort(1).putShort(1).putInt(8_000).putInt(16_000)
                            .putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
                        output.write(header.array())
                        // Quiet tone, generated locally; no downloads or microphone access.
                        val second = ByteBuffer.allocate(16_000).order(ByteOrder.LITTLE_ENDIAN)
                        repeat(8_000) { second.putShort((300 * kotlin.math.sin(it * 2 * Math.PI * 440 / 8_000)).toInt().toShort()) }
                        repeat(180) { output.write(second.array()) }
                    }
                    listOf("A", "B").map { suffix ->
                        val cover = File(cacheDir, "sleep-timer-cover-$suffix.png")
                        val bitmap = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.parseColor(if (suffix == "A") "#326D91" else "#8C493A"))
                        cover.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                        bitmap.recycle()
                        PlayableTrack(Track(TrackId("sleep-timer-fixture-$suffix"), "本地测试音 $suffix",
                            coverId = suffix, durationSeconds = 180.0), file.toURI().toString(), cover.toURI().toString())
                    }
                }
            }
            FnMusicTheme(darkTheme = dark) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { dark = !dark }) { Text(if (dark) "切换浅色" else "切换深色") }
                        TextButton(enabled = tracks.isNotEmpty(), onClick = {
                            player.useLocalOutput()
                            player.play(tracks)
                            requestPlayer = true
                            showingPlayer = true
                        }) { Text("播放本地音频") }
                    }
                    if (showingPlayer) MusicShell(
                        state = MusicUiState(), playerState = state, sleepTimer = graph.sleepTimer,
                        pagedTracks = { flowOf(PagingData.empty()) }, pagedAlbums = { flowOf(PagingData.empty()) },
                        pagedArtists = flowOf(PagingData.empty()), pagedArtistTracks = { flowOf(PagingData.empty()) },
                        pagedArtistAlbums = { flowOf(PagingData.empty()) }, pagedFavorites = flowOf(PagingData.empty()),
                        coverUrl = { id, _ -> id?.let { File(cacheDir, "sleep-timer-cover-$it.png").toURI().toString() } }, onRefresh = {}, onSearch = {}, onSearchType = {}, onRoam = {},
                        onPlayAllTracks = {}, onPlayAllFavorites = {}, onLoadAlbum = {}, onLoadArtist = {},
                        onPlayAllArtistTracks = {}, onLoadPlaylist = {}, onCreatePlaylist = { _, _, _, _ -> false },
                        onUpdatePlaylist = { _, _, _ -> }, onDeletePlaylist = {}, onAddTrackToPlaylist = { _, _ -> },
                        onRemoveTracksFromPlaylist = { _, _ -> }, onPurgeInvalidPlaylistTracks = {}, onLoadTrackMetadata = {},
                        onPlay = { _, _ -> }, onPlayNext = {}, onAddToQueue = {}, onToggleFavorite = {},
                        onTogglePlayback = { if (state.isPlaying) player.pause() else player.resume() },
                        onSeek = player::seekTo, onPrevious = player::skipPrevious, onNext = player::skipNext,
                        onSkipToQueueItem = player::skipTo, onSkipToHistoryItem = {}, onClearPlaybackHistory = {},
                        onMoveQueueItem = { _, _ -> }, onRemoveFromQueue = {}, onToggleShuffle = {}, onCycleRepeatMode = {},
                        onCacheSizeChange = {}, onLogout = {}, openPlayerRequested = requestPlayer,
                        onPlayerOpenRequestConsumed = { requestPlayer = false },
                    ) else Text("本地定时关闭验收，不需要 NAS 登录。", Modifier.padding(16.dp))
                }
            }
        }
    }
}
