package com.seasonyuu.fnmusic.core.airplay

import android.os.Build
import android.os.Bundle
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AirPlayProbeTest {
    @Test fun phonePinInputIsValidatedAndConsumedOnce() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ProbeUi.reset()
            assertFalse(ProbeUi.submit("0123"))
        }
        ProbeUi.event("pin_required")
        instrumentation.waitForIdleSync()
        instrumentation.runOnMainSync {
            assertFalse(ProbeUi.submit("123"))
            assertFalse(ProbeUi.submit("12a4"))
            assertTrue(ProbeUi.submit("0123"))
            assertFalse(ProbeUi.submit("9999"))
        }
        assertEquals("0123", ProbeUi.pollPin())
        assertEquals("", ProbeUi.pollPin())
        instrumentation.runOnMainSync { ProbeUi.reset() }
    }

    @Test fun rejectsInvalidEndpointWithoutOpeningSockets() {
        val result = NativeAirPlayProbe().run("not-an-ip", 7000, 1, "", "", callbacks())
        assertEquals("invalid_arguments", JSONObject(result).getString("reason"))
    }

    @Test fun pairingCredentialsAreEncryptedAndDeviceScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AirPlayCredentialStore(context)
        val id = "02464E000001"
        store.write(id, "test-only-pairing-credentials")
        assertEquals("test-only-pairing-credentials", store.read(id))
        assertEquals("", store.read("02464E000002"))
        val prefs = context.getSharedPreferences("airplay_pairings", 0)
        val ciphertext = prefs.getString(id, null)!!
        assertFalse(ciphertext.contains("test-only"))
        // Copying ciphertext to a different device must fail GCM AAD authentication.
        prefs.edit().putString("02464E000002", ciphertext).commit()
        assertEquals("", store.read("02464E000002"))
        prefs.edit().remove(id).remove("02464E000002").commit()
    }

    @Test fun discoversExplicitlySelectedMac() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Receiver discovery is opt-in", args.getString("airplay_discovery") == "true")
        val id = normalizeDeviceId(requireNotNull(args.getString("airplay_device_id")))
        require(id.isNotEmpty())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val found = CountDownLatch(1)
        var reason = "receiver_not_discovered"
        val discovery = AirPlayDiscovery(context)
        try {
            discovery.start({ devices -> if (devices.any { it.id == id }) found.countDown() }, { reason = it })
            val passed = found.await(15, TimeUnit.SECONDS)
            File(context.filesDir, "airplay-discovery-result.json").writeText(JSONObject()
                .put("status", if (passed) "passed" else "blocked")
                .put("reason", if (passed) "selected_receiver_discovered" else reason).toString())
            assertTrue("Selected receiver not discovered; this can be an emulator mDNS limitation", passed)
        } finally { discovery.close() }
    }

    @Test fun streamsToExplicitlySelectedMac() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Real receiver tests are opt-in", args.getString("airplay_live") == "true")
        val host = requireNotNull(args.getString("airplay_host"))
        val id = normalizeDeviceId(requireNotNull(args.getString("airplay_device_id")))
        require(id.isNotEmpty())
        val seconds = args.getString("airplay_seconds")?.toInt() ?: 90
        require(seconds in 90..600)
        val context = instrumentation.targetContext
        val reportFile = File(context.filesDir, "airplay-probe-result.json")
        val pinFile = File(context.filesDir, "airplay-probe-pin")
        val cancelFile = File(context.filesDir, "airplay-probe-cancel")
        reportFile.delete(); pinFile.delete(); cancelFile.delete()
        instrumentation.runOnMainSync { ProbeUi.reset() }
        val activity = instrumentation.startActivitySync(Intent(context, AirPlayProbeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val report = JSONObject().put("sdk", Build.VERSION.SDK_INT)
        val found = CountDownLatch(1)
        val discovery = AirPlayDiscovery(context)
        try {
            discovery.start({ devices ->
                if (devices.any { it.id == id }) found.countDown()
            }, { /* Mark discovery separately; direct endpoint does not prove mDNS. */ })
            report.put("discovery", if (found.await(12, TimeUnit.SECONDS)) "passed" else "blocked")
        } finally { discovery.close() }
        val store = AirPlayCredentialStore(context)
        val cb = object : NativeAirPlayProbe.Callbacks {
            override fun onEvent(event: String) {
                ProbeUi.event(event)
                instrumentation.sendStatus(0, Bundle().apply { putString("airplay_event", event) })
            }
            override fun pollPin(): String {
                val entered = ProbeUi.pollPin()
                if (entered.isNotEmpty()) return entered
                if (!pinFile.exists()) return ""
                return pinFile.readText().trim().also { pinFile.delete() }
            }
            override fun isCancelled() = ProbeUi.cancelled.get() || cancelFile.exists() || Thread.currentThread().isInterrupted
            override fun saveCredentials(credentials: String) = store.write(id, credentials)
        }
        try {
            val result = NativeAirPlayProbe().run(host, args.getString("airplay_port")?.toInt() ?: 7000,
                seconds, id, store.read(id), cb)
            report.put("transport", JSONObject(result))
            reportFile.writeText(report.toString(2))
            assertEquals("Transport probe failed; inspect airplay-probe-result.json", "passed", JSONObject(result).getString("status"))
        } finally {
            pinFile.delete(); cancelFile.delete()
            instrumentation.runOnMainSync { ProbeUi.clear(); activity.finish() }
        }
    }

    private fun callbacks() = object : NativeAirPlayProbe.Callbacks {
        override fun onEvent(event: String) = Unit
        override fun pollPin() = ""
        override fun isCancelled() = false
        override fun saveCredentials(credentials: String) = Unit
    }
}
