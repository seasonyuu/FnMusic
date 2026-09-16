package com.seasonyuu.fnmusic.core.designsystem

import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.*
import java.io.File
import java.util.Collections
import org.junit.Rule
import org.junit.Test

/** Reporting only: emulated GPU timing is not a real-device frame-rate guarantee. */
class LiquidMenuPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun reportOriginalAndLiquidFrameDistributions() {
        var liquid by mutableStateOf(false)
        var expanded by mutableStateOf(false)
        compose.setContent {
            FnMusicTheme {
                LiquidMenuHost {
                    val backdrop = rememberLayerBackdrop()
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop))
                    Box(Modifier.padding(40.dp)) {
                        if (liquid)
                            LiquidMenu(
                                expanded,
                                { expanded = false },
                                backdrop,
                                List(4) { LiquidMenuItem("$it", "Option $it") },
                                { expanded = false },
                                onExpandedChange = { expanded = it },
                                transition = LiquidMenuTransition.Attached,
                                trigger = {
                                    LiquidButton(it, backdrop, Modifier.height(48.dp).testTag("trigger").then(surfaceModifier()),
                                        foregroundModifier = foregroundModifier) { Text("Open") }
                                },
                            )
                        else {
                            Button({ expanded = true }, Modifier.testTag("trigger")) {
                                Text("Open")
                            }
                            DropdownMenu(expanded, { expanded = false }) {
                                repeat(4) { i ->
                                    DropdownMenuItem(
                                        { Text("Option $i") },
                                        { expanded = false },
                                        Modifier.testTag("original-$i"),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        val frames = Collections.synchronizedList(mutableListOf<Double>())
        val listener =
            Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
                frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6)
            }
        val window = compose.activity.window
        val results = mutableListOf<String>()
        for (mode in listOf(false, true)) {
            compose.runOnIdle { liquid = mode }
            fun cycle() {
                compose.onNodeWithTag("trigger").performClick()
                compose
                    .onNodeWithTag(if (mode) "liquid-menu-item-0" else "original-0")
                    .performClick()
                compose.waitForIdle()
            }
            repeat(3) { cycle() }
            frames.clear()
            compose.runOnIdle {
                window.addOnFrameMetricsAvailableListener(listener, Handler(Looper.getMainLooper()))
            }
            repeat(20) { cycle() }
            compose.runOnIdle { window.removeOnFrameMetricsAvailableListener(listener) }
            val samples = synchronized(frames) { frames.toList().sorted() }
            val refresh = compose.activity.window.decorView.display.refreshRate
            val budget = 1000.0 / refresh
            fun percentile(p: Double) =
                if (samples.isEmpty()) "null"
                else samples[((samples.size - 1) * p).toInt()].toString()
            results +=
                "\"${if(mode) "liquid" else "original"}\":{\"frames\":${samples.size},\"p50_ms\":${percentile(.5)},\"p95_ms\":${percentile(.95)},\"frame_budget_ms\":$budget,\"over_budget_ratio\":${if(samples.isEmpty()) "null" else samples.count{it>budget}.toDouble()/samples.size}}"
        }
        val dir = File(compose.activity.getExternalFilesDir(null), "liquid-menu").apply { mkdirs() }
        File(dir, "performance.json").writeText("{${results.joinToString(",")}}")
    }
}
