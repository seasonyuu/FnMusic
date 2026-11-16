package com.seasonyuu.fnmusic.core.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSleepTimerControllerTest {
    @Test fun waitsForSettingsThenPausesExactlyOnceAndKeepsDuration() = runTest {
        var pauses = 0
        val saved = mutableListOf<Int>()
        val timer = DefaultSleepTimerController(backgroundScope, { testScheduler.currentTime },
            { delay(100); 45 }, { saved += it }, { pauses++ })
        timer.enable()
        assertFalse(timer.state.value.enabled)
        advanceTimeBy(100); runCurrent()
        assertEquals(45, timer.state.value.durationMinutes)
        timer.start(1); runCurrent()
        advanceTimeBy(59_001); runCurrent()
        assertTrue(timer.state.value.enabled)
        assertEquals(1L, timer.state.value.remainingSeconds)
        advanceTimeBy(999); runCurrent()
        assertEquals(1, pauses)
        assertFalse(timer.state.value.enabled)
        assertEquals(1, timer.state.value.durationMinutes)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, pauses)
        assertEquals(listOf(1), saved)
    }

    @Test fun replacementAndCancelCannotPauseLaterAndEnableUsesLastDuration() = runTest {
        var pauses = 0
        val timer = DefaultSleepTimerController(backgroundScope, { testScheduler.currentTime }, { 30 }, {}, { pauses++ })
        runCurrent()
        timer.start(1)
        advanceTimeBy(30_000)
        timer.start(1)
        advanceTimeBy(30_000); runCurrent()
        assertEquals(0, pauses)
        assertEquals(30L, timer.state.value.remainingSeconds)
        timer.cancel()
        advanceTimeBy(120_000); runCurrent()
        assertEquals(0, pauses)
        timer.enable()
        assertEquals(60L, timer.state.value.remainingSeconds)
        advanceTimeBy(60_000); runCurrent()
        assertEquals(1, pauses)
    }

    @Test fun elapsedClockJumpExpiresWithoutAccumulatingMissedTicks() = runTest {
        var now = 0L
        var pauses = 0
        val timer = DefaultSleepTimerController(backgroundScope, { now }, { 90 }, {}, { pauses++ })
        runCurrent()
        timer.enable(); runCurrent()
        now = 90 * 60_000L + 20_000
        advanceTimeBy(1000); runCurrent()
        assertFalse(timer.state.value.enabled)
        assertEquals(1, pauses)
    }

    @Test fun persistenceFailureDoesNotDisableTimerAndNewInstanceStartsOff() = runTest {
        val timer = DefaultSleepTimerController(backgroundScope, { testScheduler.currentTime }, { 60 },
            { error("disk") }, {})
        runCurrent(); timer.enable(); runCurrent()
        assertTrue(timer.state.value.enabled)
        assertNotNull(timer.state.value.error)
        val recreated = DefaultSleepTimerController(backgroundScope, { testScheduler.currentTime }, { 60 }, {}, {})
        runCurrent()
        assertEquals(60, recreated.state.value.durationMinutes)
        assertFalse(recreated.state.value.enabled)
    }
}
