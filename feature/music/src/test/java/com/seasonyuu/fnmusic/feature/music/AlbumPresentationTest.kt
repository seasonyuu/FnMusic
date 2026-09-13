package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlbumPresentationTest {
    private fun track(duration: Double = 0.0, number: Int? = null) =
        Track(TrackId("test"), "Track", durationSeconds = duration, trackNo = number)

    @Test fun missingOrInvalidTrackNumberFallsBackToPosition() {
        assertEquals("3", albumTrackNumber(track(number = null), 2))
        assertEquals("3", albumTrackNumber(track(number = 0), 2))
        assertEquals("1", albumTrackNumber(track(number = 1), 12))
    }

    @Test fun incompleteDurationIsNotPresentedAsAlbumTotal() {
        assertNull(albumDurationLabel(emptyList()))
        assertNull(albumDurationLabel(listOf(track(120.0), track())))
        assertNull(albumDurationLabel(listOf(track(Double.NaN))))
        assertNull(albumDurationLabel(listOf(track(Double.POSITIVE_INFINITY))))
        assertEquals("48 分钟", albumDurationLabel(listOf(track(1440.0), track(1440.0))))
        assertEquals("30 秒", albumDurationLabel(listOf(track(30.0))))
    }
}
