package com.seasonyuu.fnmusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.seasonyuu.fnmusic.core.designsystem.FnMusicTheme
import com.seasonyuu.fnmusic.core.model.*
import com.seasonyuu.fnmusic.feature.music.AirPlayOutputEntry
import com.seasonyuu.fnmusic.feature.music.LocalPlaybackOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

/** Debug APK only: generated files exercise the actual service, queue, renderer and output UI. */
class AirPlayVerificationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val player = (application as FnMusicApplication).graph.player
        setContent {
            FnMusicTheme {
                CompositionLocalProvider(LocalPlaybackOutput provides player) {
                    val state by player.state.collectAsState()
                    val output by player.outputState.collectAsState()
                    var tracks by remember { mutableStateOf<List<PlayableTrack>>(emptyList()) }
                    LaunchedEffect(Unit) { tracks = withContext(Dispatchers.IO) {
                        listOf(440, 660).map { frequency ->
                            val file = File(cacheDir, "airplay-verification-$frequency.wav")
                            writeTone(file, frequency)
                            PlayableTrack(Track(TrackId("airplay-verification-$frequency"), "测试音 $frequency Hz", durationSeconds = 330.0), file.toURI().toString())
                        }
                    } }
                    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("AirPlay 播放器验收", style = MaterialTheme.typography.headlineSmall)
                        Text("使用真实播放器和低振幅测试音，无需 NAS 登录。开始测试会将远端音量设为 100%，以免测试音被额外衰减。")
                        AirPlayOutputEntry(state.current)
                        Text(state.current?.track?.title ?: "尚未开始")
                        Text("${state.playbackStatus} · ${state.positionMs / 1000} 秒 · 队列 ${state.queue.size} 首")
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        output.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = { player.setOutputVolume(100f); player.play(tracks) }, enabled = tracks.isNotEmpty()) { Text("开始测试队列") }
                        Row {
                            Button(onClick = { player.pause() }) { Text("暂停") }
                            Button(onClick = { player.resume() }) { Text("继续") }
                            Button(onClick = { player.skipNext() }) { Text("切歌") }
                        }
                        Slider(value = state.positionMs.toFloat().coerceIn(0f, 330000f), onValueChange = { player.seekTo(it.toLong()) }, valueRange = 0f..330000f)
                        Text("远端音量 ${output.volume.toInt()}%")
                        Slider(value = output.volume, onValueChange = player::setOutputVolume, valueRange = 0f..100f)
                        Button(onClick = { player.useLocalOutput() }) { Text("切回本机") }
                        Button(onClick = { player.pause(); player.setOutputVolume(20f); player.useLocalOutput(); player.clear() }) { Text("停止并清理测试队列") }
                    }
                }
            }
        }
    }
    private fun writeTone(file: File, frequency: Int) {
        val frames = 44100 * 330
        if (file.length() == 44L + frames * 4) return
        file.outputStream().buffered().use { output ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray()).putInt(36 + frames * 4).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(2).putInt(44100).putInt(176400).putShort(4).putShort(16)
                .put("data".toByteArray()).putInt(frames * 4)
            output.write(header.array())
            val second = ByteBuffer.allocate(44100 * 4).order(ByteOrder.LITTLE_ENDIAN)
            repeat(44100) { i ->
                val sample = (1200 * sin(2 * Math.PI * frequency * i / 44100)).toInt().toShort()
                second.putShort(sample).putShort(sample)
            }
            repeat(330) { output.write(second.array()) }
        }
    }
}
