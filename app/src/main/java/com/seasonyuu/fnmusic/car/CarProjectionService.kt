package com.seasonyuu.fnmusic.car

import androidx.annotation.Keep
import com.seasonyuu.fnmusic.BuildConfig
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Instantiated by Shizuku in its shell process; never registered as an Android service. */
@Keep
class CarProjectionService : ICarProjectionService.Stub() {
    override fun destroy() = exitProcess(0)

    // Only our own launcher is exposed. Neither arbitrary commands nor components cross IPC.
    override fun launch(displayId: Int): String? {
        if (displayId <= 0) return "请选择外接屏幕"
        val reader = Executors.newSingleThreadExecutor()
        var process: Process? = null
        return try {
            val running = ProcessBuilder(projectionCommand(BuildConfig.APPLICATION_ID, displayId))
                .redirectErrorStream(true).start()
            process = running
            val output = reader.submit<String> {
                running.inputStream.bufferedReader().use { it.readText() }
            }
            if (!running.waitFor(20, TimeUnit.SECONDS)) {
                running.destroyForcibly()
                "车机启动超时，请确认仍已连接后重试"
            } else {
                projectionFailure(running.exitValue(), output.get(2, TimeUnit.SECONDS))
            }
        } catch (_: Exception) {
            "无法启动投屏，请检查 Shizuku 和车机连接后重试"
        } finally {
            process?.destroy()
            reader.shutdownNow()
        }
    }
}
