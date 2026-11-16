package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.*
import org.junit.Assert.*
import org.junit.Test

class SleepTimerInputTest {
    @Test fun validatesWheelBoundsAndCombinesHoursAndMinutes() {
        assertNull(sleepTimerMinutes(0, 0))
        assertNull(sleepTimerMinutes(-1, 30))
        assertNull(sleepTimerMinutes(1, 60))
        assertNull(sleepTimerMinutes(25, 0))
        assertNull(sleepTimerMinutes(24, 1))
        assertEquals(1, sleepTimerMinutes(0, 1))
        assertEquals(90, sleepTimerMinutes(1, 30))
        assertEquals(1439, sleepTimerMinutes(23, 59))
        assertEquals(1440, sleepTimerMinutes(24, 0))
    }
    @Test fun formatsTotalMinutesAndTwoDigitSeconds() {
        assertEquals("0:00", formatSleepTimer(0))
        assertEquals("0:09", formatSleepTimer(9))
        assertEquals("29:59", formatSleepTimer(1799))
        assertEquals("90:00", formatSleepTimer(5400))
        assertEquals("1440:00", formatSleepTimer(86400))
    }
}
