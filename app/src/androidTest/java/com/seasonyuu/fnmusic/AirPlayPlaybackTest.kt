package com.seasonyuu.fnmusic

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AirPlayPlaybackTest {
    @Test fun realServicePreservesQueueAndControlsRemoteOutput() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("airplay_player") == "true")
        val target = requireNotNull(args.getString("airplay_device_id"))
        val seconds = (args.getString("airplay_seconds")?.toInt() ?: 90).also { require(it in 90..600) }
        val context = instrumentation.targetContext
        val graph = (context.applicationContext as FnMusicApplication).graph
        val player = graph.player
        val useNas = args.getString("airplay_nas") == "true"
        val systemControls = args.getString("airplay_system_controls") == "true"
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            java.io.FileInputStream(it.fileDescriptor).bufferedReader().readText()
        }
        val previousVolume = player.outputState.value.volume
        val report = JSONObject().put("status", "failed").put("manual_listening", "pending_manual_confirmation")
        fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
        fun event(stage: String) {
            report.put("stage", stage)
            instrumentation.sendStatus(0, Bundle().apply { putString("airplay_event", stage) })
        }
        fun awaitCondition(timeout: Long = 15000, condition: () -> Boolean) {
            val end = SystemClock.elapsedRealtime() + timeout
            while (!condition() && SystemClock.elapsedRealtime() < end) {
                if (player.outputState.value.error != null) fail(player.outputState.value.error)
                Thread.sleep(100)
            }
            assertTrue("Condition failed at ${report.optString("stage")}", condition())
        }
        val activity = instrumentation.startActivitySync(Intent(context, AirPlayVerificationActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            val nasTracks = if (useNas) {
                event("nas_login")
                kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(30000) { graph.session.reconnect() } }
                if (graph.session.state.value !is SessionState.Ready) {
                    report.put("status", "blocked").put("reason", "nas_login_required")
                    return
                }
                try {
                    kotlinx.coroutines.runBlocking { kotlinx.coroutines.withTimeout(30000) {
                        graph.catalog.firstTracks(size = 2).map(graph::playable)
                    } }
                } catch (_: Exception) {
                    report.put("status", "blocked").put("reason", "nas_catalog_unavailable")
                    return
                }.also {
                    if (it.size < 2) {
                        report.put("status", "blocked").put("reason", "two_nas_tracks_required")
                        return
                    }
                }
            } else null
            event("discovery")
            main { player.scanOutputs(true) }
            awaitCondition { player.outputState.value.devices.any { it.id == target } }
            main { player.selectOutput(target) }
            event("pairing")
            awaitCondition(180000) { (player.outputState.value.output as? PlaybackOutput.AirPlay)?.id == target }
            main { player.scanOutputs(false) }
            report.put("discovery", "passed").put("authenticated", true)
                .put("source", if (useNas) "nas" else "generated_pcm")
                .put("test_receiver_volume", if (useNas) 20 else 100)
            val tracks = nasTracks ?: listOf(440, 660).map { frequency ->
                val file = File(context.cacheDir, "airplay-verification-$frequency.wav")
                awaitCondition { file.length() == 44L + 44100L * 330 * 4 }
                PlayableTrack(Track(TrackId("airplay-verification-$frequency"), "测试音 $frequency Hz", durationSeconds = 330.0), file.toURI().toString())
            }
            // Generated PCM is limited to amplitude 1200, below the audible prototype (1800).
            // Remove receiver attenuation only for this diagnostic fixture.
            main { player.setOutputVolume(if (useNas) 20f else 100f); player.play(tracks) }
            event("streaming")
            awaitCondition { player.state.value.isPlaying && player.state.value.positionMs > 2000 }
            awaitCondition { player.outputState.value.hasNonzeroAudio && player.outputState.value.sentFrames > 44100 }
            val queue = player.state.value.queue.map { it.queueEntryId }
            if (args.getString("airplay_manual_ui") == "true") {
                val done = File(context.filesDir, "airplay-manual-ui-done")
                done.delete()
                val receiverUi = args.getString("airplay_receiver_ui") == "true"
                event(if (receiverUi) "manual_mac_now_playing_and_controls" else "manual_notification_and_lock_screen")
                val until = SystemClock.elapsedRealtime() + 180000
                var paused = false
                var resumed = false
                var changedTrack = false
                val initialIndex = player.state.value.currentIndex
                while (!done.exists() && SystemClock.elapsedRealtime() < until) {
                    Thread.sleep(200)
                    if (!player.state.value.isPlaying) paused = true
                    if (paused && player.state.value.isPlaying) resumed = true
                    if (player.state.value.currentIndex != initialIndex) changedTrack = true
                    assertNull(player.outputState.value.error)
                    assertEquals(queue, player.state.value.queue.map { it.queueEntryId })
                }
                done.delete()
                report.put("status", "pending_manual_confirmation").put("automatic_status", "passed")
                    .put("manual_ui_pause_observed", paused).put("manual_ui_resume_observed", resumed)
                    .put("manual_ui_track_change_observed", changedTrack)
                    .put(if (receiverUi) "receiver_visible_controls" else "lock_screen_visible_controls", "pending_manual_confirmation")
                return
            }
            val started = SystemClock.elapsedRealtime()
            while (SystemClock.elapsedRealtime() - started < seconds * 1000L) {
                Thread.sleep(500)
                assertNull(player.outputState.value.error)
                assertTrue("Unexpected buffering or disconnect", player.state.value.isPlaying)
            }
            report.put("continuous_playback_seconds", seconds)
                .put("audio_frames_submitted", player.outputState.value.sentFrames)
            if (systemControls) {
                event("notification_actions")
                val notifications = context.getSystemService(android.app.NotificationManager::class.java)
                fun action(vararg titles: String): android.app.Notification.Action? = notifications.activeNotifications
                    .filter { it.notification.category == android.app.Notification.CATEGORY_TRANSPORT }
                    .flatMap { it.notification.actions?.toList().orEmpty() }
                    .firstOrNull { candidate -> titles.any { candidate.title.toString().equals(it, ignoreCase = true) } }
                awaitCondition { action("Pause", "暂停") != null }
                requireNotNull(action("Pause", "暂停")).actionIntent.send()
                awaitCondition { !player.state.value.isPlaying }
                awaitCondition { action("Play", "播放") != null }
                requireNotNull(action("Play", "播放")).actionIntent.send()
                awaitCondition { player.state.value.isPlaying }
                report.put("notification_pending_intents", "passed")
                event("screen_off_media_keys")
                val power = context.getSystemService(android.os.PowerManager::class.java)
                shell("input keyevent 223")
                try {
                    awaitCondition { !power.isInteractive }
                    shell("cmd media_session dispatch pause")
                    awaitCondition { !player.state.value.isPlaying }
                    shell("cmd media_session dispatch play")
                    awaitCondition { player.state.value.isPlaying }
                    shell("cmd media_session dispatch next")
                    awaitCondition { player.state.value.currentIndex == 1 && player.state.value.isPlaying }
                    assertEquals(queue, player.state.value.queue.map { it.queueEntryId })
                    shell("cmd media_session dispatch previous")
                    awaitCondition { player.state.value.currentIndex == 0 && player.state.value.isPlaying }
                    report.put("screen_off_media_keys", "passed")
                    report.put("lock_screen_visible_controls", "pending_manual_confirmation")
                } finally { shell("input keyevent 224") }
            }
            event("pause")
            main { player.pause() }
            Thread.sleep(2200)
            val paused = player.state.value.positionMs
            Thread.sleep(2000)
            assertFalse(player.state.value.isPlaying)
            assertTrue(kotlin.math.abs(player.state.value.positionMs - paused) < 500)
            event("resume")
            main { player.resume() }
            awaitCondition { player.state.value.isPlaying && player.state.value.positionMs > paused + 1000 }
            event("seek")
            main { player.seekTo(60000) }
            awaitCondition { player.state.value.positionMs in 59000..68000 && player.state.value.isPlaying }
            event("next_track")
            main { player.skipNext() }
            awaitCondition { player.state.value.currentIndex == 1 && player.state.value.isPlaying && player.state.value.positionMs > 1500 }
            assertEquals(queue, player.state.value.queue.map { it.queueEntryId })
            event("volume")
            main { player.setOutputVolume(if (useNas) 10f else 60f) }
            awaitCondition { player.outputState.value.volume == if (useNas) 10f else 60f }
            Thread.sleep(3000)
            main { player.setOutputVolume(if (useNas) 20f else 100f) }
            Thread.sleep(3000)
            val beforeLocal = player.state.value.positionMs
            event("local")
            main { player.useLocalOutput() }
            awaitCondition { player.outputState.value.output == PlaybackOutput.Local && player.state.value.isPlaying }
            assertEquals(queue, player.state.value.queue.map { it.queueEntryId })
            assertTrue(kotlin.math.abs(player.state.value.positionMs - beforeLocal) < 5000)
            Thread.sleep(3000)
            report.put("status", "passed").put("controls", "passed")
        } finally {
            main { player.pause(); player.setOutputVolume(previousVolume); player.useLocalOutput(); player.clear(); player.scanOutputs(false); activity.finish() }
            File(context.filesDir, "airplay-player-result.json").writeText(report.toString(2))
        }
    }
}
