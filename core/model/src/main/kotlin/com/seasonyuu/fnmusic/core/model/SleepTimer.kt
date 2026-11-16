package com.seasonyuu.fnmusic.core.model

import kotlinx.coroutines.flow.StateFlow

data class SleepTimerState(
    val durationMinutes: Int = 30,
    val enabled: Boolean = false,
    val remainingSeconds: Long = 0,
    val initialized: Boolean = false,
    val error: String? = null,
)

interface SleepTimerController {
    val state: StateFlow<SleepTimerState>
    fun enable()
    fun start(minutes: Int)
    fun cancel()
}

fun sleepTimerMinutes(hours: Int, minutes: Int): Int? =
    if (hours in 0..24 && minutes in 0..59) (hours * 60 + minutes).takeIf { it in 1..1440 }
    else null

fun formatSleepTimer(seconds: Long): String =
    seconds.coerceAtLeast(0).let { "${it / 60}:${(it % 60).toString().padStart(2, '0')}" }
