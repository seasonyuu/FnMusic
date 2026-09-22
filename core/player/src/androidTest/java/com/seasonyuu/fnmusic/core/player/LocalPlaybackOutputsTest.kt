package com.seasonyuu.fnmusic.core.player

import android.os.Bundle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.SessionResult
import androidx.test.platform.app.InstrumentationRegistry
import com.seasonyuu.fnmusic.core.airplay.AirPlaySession
import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class LocalPlaybackOutputsTest {
    private class Router : LocalAudioRouter {
        val tracker = LocalRouteTracker().apply { devices(listOf(LocalAudioDevice(1,"Speaker",LocalAudioDeviceKind.Speaker),LocalAudioDevice(2,"Headphones",LocalAudioDeviceKind.Bluetooth))) }
        override val state get() = tracker.state
        var active = true
        var closed = false
        override fun request(id: Int?): Boolean {
            if(id != null && state.devices.none { it.id == id }) { tracker.fail("Unavailable");return false }
            tracker.request(id);return true
        }
        override fun activate(active: Boolean) { this.active=active;if(!active) tracker.deactivate() }
        override fun tick(playing: Boolean) {}
        override fun close() { closed=true }
    }
    private class Remote(override val device: AirPlayDevice,val event:(String)->Unit) : AirPlaySession {
        override var ready=true
        override val hasNonzeroAudio=false
        var closed=false
        override fun connect() {}
        override fun submitPin(pin:String) {}
        override fun volume(value:Float) {}
        override fun sentFrames()=0L
        override fun play(value:Boolean) {}
        override fun offer(samples:ShortArray)=true
        override fun flush() {}
        override fun positionFrames()=0L
        override fun pending()=false
        override fun close() {closed=true;ready=false}
    }
    @Test fun stateRoundTripPreservesActualAndPendingDevices() {
        val local=LocalAudioState(listOf(LocalAudioDevice(5,"USB",LocalAudioDeviceKind.Usb)),
            currentId=5,requestedId=5,pending=true,awaitingPlayback=true,followsSystem=false,error="test")
        val state=OutputState(local=local)
        assertEquals(state,OutputCommands.decode(OutputCommands.encode(state)))
        assertEquals(LocalAudioState(),OutputCommands.decode(Bundle.EMPTY).local)
    }
    @Test fun localRequestsNeedNoNetworkPermissionAndPreserveQueuePositionAndIntent() = onMain {
        val router=Router();var playing=false;var prepares=0
        val player=player({playing},{playing=it},{prepares++})
        val outputs=PlaybackOutputs(context(),{player},hasNetworkPermission={false},localRouter={router})
        assertEquals(SessionResult.RESULT_SUCCESS,outputs.command(local(2)).resultCode)
        assertNull(outputs.state.local.currentId);assertTrue(outputs.state.local.pending)
        assertFalse(playing);assertEquals(0,prepares)
        router.tracker.observe(2,true,100)
        assertEquals(2,OutputCommands.decode(outputs.command(Bundle().apply {putString("operation","get")}).extras).local.currentId)
        val rejected=outputs.command(local(99));assertNotEquals(SessionResult.RESULT_SUCCESS,rejected.resultCode)
        assertEquals(2,outputs.state.local.currentId);assertNotNull(OutputCommands.decode(rejected.extras).local.error)
        assertEquals(SessionResult.RESULT_SUCCESS,outputs.command(Bundle().apply {putString("operation","local")}).resultCode)
        assertTrue(outputs.state.local.followsSystem)
        outputs.close();assertTrue(router.closed)
    }
    @Test fun airPlayHandoffAndStaleCallbacksRespectLocalSelection() = onMain {
        val router=Router();var playing=true;var prepares=0
        val player=player({playing},{playing=it},{prepares++})
        val sessions=mutableListOf<Remote>()
        val outputs=PlaybackOutputs(context(),{player},hasNetworkPermission={true},localRouter={router}) { d,event -> Remote(d,event).also(sessions::add) }
        val mac=AirPlayDevice("001122334455","Mac","192.0.2.1",7000,"Mac","","_airplay._tcp")
        outputs.discovered(listOf(mac))
        fun selectMac()=outputs.command(Bundle().apply {putString("operation","select");putString("id",mac.id)})
        outputs.command(local(2));selectMac();sessions.last().event("connected")
        assertFalse(router.active);assertFalse(router.state.pending)
        val connected=sessions.last()
        assertNotEquals(SessionResult.RESULT_SUCCESS,outputs.command(local(99)).resultCode)
        assertSame(connected,outputs.connection);assertFalse(connected.closed);assertTrue(playing)
        val before=prepares
        outputs.command(local(1))
        assertNull(outputs.connection);assertTrue(connected.closed);assertTrue(router.active)
        assertTrue(playing);assertEquals(before+1,prepares);assertEquals(PlaybackOutput.Local,outputs.state.output)
        assertTrue(outputs.state.local.pending);assertNull(outputs.state.local.currentId)
        connected.event("remote:paus");assertTrue(playing)
        selectMac();val pending=sessions.last();outputs.command(local(2))
        assertTrue(pending.closed);pending.event("connected")
        assertNull(outputs.connection);assertEquals(PlaybackOutput.Local,outputs.state.output)
        outputs.close()
    }
    private fun player(playing:()->Boolean,set:(Boolean)->Unit,prepared:()->Unit):ExoPlayer =
        Proxy.newProxyInstance(ExoPlayer::class.java.classLoader,arrayOf(ExoPlayer::class.java)) { _,method,args ->
            when(method.name) {
                "getPlayWhenReady","isPlaying" -> playing()
                "setPlayWhenReady" -> {set(args[0] as Boolean);null}
                "getCurrentPosition" -> 12345L
                "seekTo" -> {assertEquals(12345L,args[0]);null}
                "prepare" -> {prepared();null}
                "stop" -> null
                "pause" -> {set(false);null}
                "setMediaItems","clearMediaItems","removeMediaItems" -> error("Queue must remain intact")
                else -> null
            }
        } as ExoPlayer
    private fun local(id:Int)=Bundle().apply {putString("operation","local-device");putInt("deviceId",id)}
    private fun context()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun onMain(block:()->Unit)=InstrumentationRegistry.getInstrumentation().runOnMainSync(block)
}
