package com.seasonyuu.fnmusic.core.player

import android.content.Context
import android.os.Build
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.model.LocalAudioDeviceKind
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LocalAudioRouterPlatformTest {
    @Test fun emulatorAudioTrackConfirmsActualSpeakerAfterPlaybackAndRecreation() {
        assumeTrue("Silent platform check is emulator-only", Build.HARDWARE in listOf("ranchu","goldfish"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val file=File(context.cacheDir,"local-route-silence.wav")
        val pcmSize=44100*4
        val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray());putInt(36+pcmSize);put("WAVEfmt ".toByteArray());putInt(16)
            putShort(1);putShort(2);putInt(44100);putInt(176400);putShort(4);putShort(16)
            put("data".toByteArray());putInt(pcmSize)
        }
        file.outputStream().use {it.write(header.array());it.write(ByteArray(pcmSize))}
        lateinit var player:ExoPlayer
        lateinit var router:AndroidLocalAudioRouter
        var speakerId=-1
        val builtTracks=java.util.concurrent.atomic.AtomicInteger()
        var playerCreated=false
        var routerCreated=false
        try {
            instrumentation.runOnMainSync {
                val renderers=object:DefaultRenderersFactory(context) {
                    override fun buildAudioSink(context:Context,enableFloatOutput:Boolean,enableAudioTrackPlaybackParams:Boolean):AudioSink =
                        AirPlayAudioSink(DefaultAudioSink.Builder(context).setAudioTrackProvider { config,attributes,session ->
                            DefaultAudioSink.AudioTrackProvider.DEFAULT.getAudioTrack(config,attributes,session).also {
                                builtTracks.incrementAndGet();router.attach(it)
                            }
                        }.build(),invalidateLocalTrack={router.invalidateTrack()}) {null}
                }
                player=ExoPlayer.Builder(context,renderers).build();playerCreated=true
                router=AndroidLocalAudioRouter(context,player::setPreferredAudioDevice,player::pause);routerCreated=true
                speakerId=router.state.devices.first {it.kind==LocalAudioDeviceKind.Speaker}.id
                assertTrue(router.request(speakerId));assertNull(router.state.currentId);assertTrue(router.state.pending)
                player.volume=0f;player.repeatMode=Player.REPEAT_MODE_ALL
                player.setMediaItem(MediaItem.fromUri(file.toURI().toString()));player.prepare();player.play()
            }
            fun awaitRoute() {
                var confirmed=false
                val deadline=System.currentTimeMillis()+10000
                while(!confirmed && System.currentTimeMillis()<deadline) {
                    instrumentation.runOnMainSync {
                        router.tick(player.isPlaying)
                        confirmed=router.state.currentId==speakerId && !router.state.pending
                    }
                    if(!confirmed) Thread.sleep(50)
                }
                assertTrue("Actual AudioTrack route must confirm the speaker",confirmed)
            }
            awaitRoute()
            instrumentation.runOnMainSync {
                player.pause();assertTrue(router.request(speakerId));router.tick(false)
                assertTrue(router.state.pending);assertTrue(router.state.awaitingPlayback)
                player.stop();player.prepare();player.play()
            }
            awaitRoute()
            instrumentation.runOnMainSync {assertTrue(builtTracks.get()>=2)}
        } finally {
            instrumentation.runOnMainSync {
                if(routerCreated) router.close()
                if(playerCreated) player.release()
            }
            file.delete()
        }
    }
}
