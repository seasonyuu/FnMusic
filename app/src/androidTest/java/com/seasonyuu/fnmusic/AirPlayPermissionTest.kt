package com.seasonyuu.fnmusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Run only on a dedicated API 37 emulator with a fresh/revoked app permission. */
class AirPlayPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<AirPlayVerificationActivity>()
    @Test fun deniedThenGrantedThroughActualSystemDialog() {
        assumeTrue(Build.VERSION.SDK_INT >= 37 && InstrumentationRegistry.getArguments().getString("airplay_permissions") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val player = (context.applicationContext as FnMusicApplication).graph.player
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK))
        compose.onNodeWithTag("player-airplay").performClick()
        compose.waitForIdle()
        clickPermissionButton("permission_deny_button")
        compose.onNodeWithText("授予权限").assertExists()
        assertFalse(player.outputState.value.scanning)
        compose.onNodeWithText("授予权限").performClick()
        compose.waitForIdle()
        clickPermissionButton("permission_allow_button")
        val end = SystemClock.elapsedRealtime() + 10000
        while (!player.outputState.value.scanning && SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        assertEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK))
        assertTrue(player.outputState.value.scanning)
        compose.onNodeWithTag("airplay-local").performClick()
        compose.waitForIdle()
        assertFalse(player.outputState.value.scanning)
    }
    @Test fun revokedPermissionNeverStartsDiscoveryAfterRestart() {
        assumeTrue(Build.VERSION.SDK_INT >= 37 && InstrumentationRegistry.getArguments().getString("airplay_permissions") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val player = (context.applicationContext as FnMusicApplication).graph.player
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK))
        instrumentation.runOnMainSync { player.scanOutputs(true) }
        val end = SystemClock.elapsedRealtime() + 10000
        while (player.outputState.value.error == null && SystemClock.elapsedRealtime() < end) Thread.sleep(100)
        assertFalse(player.outputState.value.scanning)
        assertNotNull(player.outputState.value.error)
    }
    private fun clickPermissionButton(id: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        automation.serviceInfo = automation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }
        val end = SystemClock.elapsedRealtime() + 10000
        while (SystemClock.elapsedRealtime() < end) {
            val root = automation.rootInActiveWindow
            val labels = if (id == "permission_deny_button") listOf("Don’t allow", "Don't allow") else listOf("Allow")
            var node = root?.findAccessibilityNodeInfosByViewId("${root.packageName}:id/$id")?.firstOrNull()
                ?: labels.firstNotNullOfOrNull { label -> root?.findAccessibilityNodeInfosByText(label)?.firstOrNull { it.text?.toString() == label } }
            if (node != null) {
                while (node != null && !node.isClickable) node = node.parent
                assertTrue(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
                return
            }
            Thread.sleep(100)
        }
        automation.takeScreenshot()?.let { bitmap ->
            java.io.File(instrumentation.targetContext.filesDir, "permission-failure.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        fail("Expected Android permission button: $id")
    }
}
