package com.seasonyuu.fnmusic.core.player

import com.seasonyuu.fnmusic.core.model.SleepTimerController
import com.seasonyuu.fnmusic.core.model.SleepTimerState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel

/** Owned by the application; commands and ticks run on the main dispatcher. */
class DefaultSleepTimerController(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val readMinutes: suspend () -> Int,
    private val saveMinutes: suspend (Int) -> Unit,
    private val pause: () -> Unit,
) : SleepTimerController {
    private val mutableState = MutableStateFlow(SleepTimerState())
    override val state = mutableState.asStateFlow()
    private var timer: Job? = null
    private var generation = 0L
    private val saves = Channel<Int>(Channel.CONFLATED)

    init {
        scope.launch {
            try {
                val minutes = readMinutes().takeIf { it in 1..1440 } ?: 30
                mutableState.value = SleepTimerState(durationMinutes = minutes, initialized = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = SleepTimerState(initialized = true, error = "读取上次时长失败，已使用 30 分钟")
            }
            for (minutes in saves) {
                try {
                    saveMinutes(minutes)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    mutableState.value = mutableState.value.copy(error = "时长保存失败，当前定时仍然有效")
                }
            }
        }
    }

    override fun enable() = start(state.value.durationMinutes)

    override fun start(minutes: Int) {
        require(minutes in 1..1440)
        if (!state.value.initialized) return
        timer?.cancel()
        val token = ++generation
        val deadline = now() + minutes * 60_000L
        mutableState.value = SleepTimerState(minutes, enabled = true, remainingSeconds = minutes * 60L, initialized = true)
        saves.trySend(minutes)
        timer = scope.launch {
            while (token == generation) {
                val remaining = (deadline - now()).coerceAtLeast(0)
                if (remaining == 0L) {
                    mutableState.value = state.value.copy(enabled = false, remainingSeconds = 0)
                    pause()
                    break
                }
                mutableState.value = state.value.copy(remainingSeconds = (remaining + 999) / 1000)
                delay(minOf(remaining, 1000L))
            }
        }
    }

    override fun cancel() {
        generation++
        timer?.cancel()
        timer = null
        mutableState.value = state.value.copy(enabled = false, remainingSeconds = 0)
    }
}
