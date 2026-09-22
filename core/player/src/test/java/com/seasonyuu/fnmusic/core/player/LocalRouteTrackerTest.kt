package com.seasonyuu.fnmusic.core.player

import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Test

class LocalRouteTrackerTest {
    private val speaker = LocalAudioDevice(1,"Speaker",LocalAudioDeviceKind.Speaker)
    private val headphones = LocalAudioDevice(2,"Headphones",LocalAudioDeviceKind.Bluetooth)
    private fun tracker() = LocalRouteTracker(1000).apply { devices(listOf(speaker,headphones)) }
    @Test fun requestDoesNotMasqueradeAsActualOutput() {
        val t=tracker(); t.observe(1,true,0);t.request(2)
        assertEquals(1,t.state.currentId);assertTrue(t.state.pending)
        t.observe(1,true,100);assertTrue(t.state.pending)
        t.observe(2,true,200);assertFalse(t.state.pending);assertEquals(2,t.state.currentId)
    }
    @Test fun pausesDoNotExpireOrConfirmRequests() {
        val t=tracker();t.request(2)
        assertFalse(t.observe(2,false,0));assertTrue(t.state.awaitingPlayback);assertNull(t.state.currentId)
        assertFalse(t.observe(null,false,100000));assertTrue(t.state.pending)
        t.observe(1,true,100001);t.observe(null,false,100010)
        assertFalse(t.observe(1,true,200000));assertTrue(t.state.pending)
        assertTrue(t.observe(1,true,201001));assertFalse(t.state.pending);assertNotNull(t.state.error)
    }
    @Test fun unsupportedOrMissingRouteCannotBeSelected() {
        val t=tracker()
        assertThrows(IllegalArgumentException::class.java) { t.request(99) }
        t.request(2);assertTrue(t.devices(listOf(speaker)))
        assertFalse(t.state.pending);assertTrue(t.state.followsSystem);assertNotNull(t.state.error)
        assertFalse(t.observe(2,true,0));assertNull(t.state.currentId)
    }
    @Test fun replacingRequestIgnoresConfirmationForEarlierRequest() {
        val t=tracker();t.request(2);t.request(1);t.observe(2,true,0)
        assertTrue(t.state.pending);assertEquals(2,t.state.currentId)
        t.observe(1,true,100);assertFalse(t.state.pending)
    }
    @Test fun systemChangesAndDeviceRemovalUpdateActualOutput() {
        val t=tracker();t.request(null);t.observe(2,true,0)
        assertFalse(t.state.pending);assertTrue(t.state.followsSystem)
        assertTrue(t.devices(listOf(speaker)));assertNull(t.state.currentId)
        t.observe(1,true,1);assertEquals(1,t.state.currentId)
    }
    @Test fun losingCurrentDeviceCancelsPendingPreferenceConsistently() {
        val t = tracker()
        t.observe(2, true, 0)
        t.request(1)
        assertTrue(t.devices(listOf(speaker)))
        assertNull(t.state.currentId)
        assertNull(t.state.requestedId)
        assertTrue(t.state.followsSystem)
        assertFalse(t.state.pending)
        assertNotNull(t.state.error)
    }
    @Test fun airPlayDeactivationClearsPendingAndStaleDevice() {
        val t=tracker();t.observe(1,true,0);t.request(2);t.deactivate()
        assertFalse(t.state.pending);assertNull(t.state.currentId)
        t.request(2);assertTrue(t.state.pending);t.observe(2,true,100);assertEquals(2,t.state.currentId)
    }
    @Test fun duplicateIdsDoNotDuplicateRows() {
        val t=tracker();t.devices(listOf(speaker,speaker,headphones));assertEquals(2,t.state.devices.size)
    }
    @Test fun onlyMediaOutputTypesAreListed() {
        assertEquals(LocalAudioDeviceKind.Bluetooth,localDeviceKind(8)) // A2DP
        assertEquals(LocalAudioDeviceKind.Wired,localDeviceKind(4)) // wired headphones
        assertEquals(LocalAudioDeviceKind.Usb,localDeviceKind(22)) // USB headset
        assertNull(localDeviceKind(7)) // Bluetooth SCO, for calls
        assertNull(localDeviceKind(1)) // earpiece
        assertNull(localDeviceKind(15)) // microphone
        assertNull(localDeviceKind(25)) // remote submix
    }
}
