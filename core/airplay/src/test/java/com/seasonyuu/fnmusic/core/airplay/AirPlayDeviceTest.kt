package com.seasonyuu.fnmusic.core.airplay

import com.seasonyuu.fnmusic.core.model.AirPlayDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class AirPlayDeviceTest {
    @Test fun mergesProtocolsByIdentityAndPrefersAirplayEndpoint() {
        val raop = AirPlayDevice("02464E000001", "Speaker", "192.168.1.2", 5000, "", "", "_raop._tcp.")
        val airplay = raop.copy(port = 7000, serviceType = "_airplay._tcp.")
        assertEquals(listOf(airplay), discoverySnapshot(listOf(raop, airplay)))
        assertEquals(listOf(raop), discoverySnapshot(listOf(raop)))
    }

    @Test fun devicesSharingANameRemainDistinct() {
        val a = AirPlayDevice("02464E000001", "Mac", "192.168.1.2", 7000, "", "", "_airplay._tcp.")
        val b = a.copy(id = "02464E000002", host = "192.168.1.3")
        assertEquals(listOf(a, b), discoverySnapshot(listOf(a, b)))
    }

    @Test fun matchesRaopAndAirplayIdentifiers() {
        assertEquals(normalizeDeviceId("02:ab:cd:00:01:02"), normalizeDeviceId("02ABCD000102"))
        assertEquals("", normalizeDeviceId("receiver-name"))
        assertEquals("", normalizeDeviceId("02:ab:cd:00:01:XX"))
        assertEquals("", normalizeDeviceId("02ABCD00010200"))
    }
}
