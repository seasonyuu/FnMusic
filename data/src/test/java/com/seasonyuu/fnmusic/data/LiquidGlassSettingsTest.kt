package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.LiquidGlassBlur
import com.seasonyuu.fnmusic.core.model.LiquidGlassPreference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test

class LiquidGlassSettingsTest {
    @Test fun sliderPreservesDefaultAtMidpointAndMaterialRatios() {
        assertEquals(0.1f, LiquidGlassBlur.fromSlider(0f), 0f)
        assertEquals(1f, LiquidGlassBlur.fromSlider(0.5f), 0f)
        assertEquals(2f, LiquidGlassBlur.fromSlider(1f), 0f)
        assertEquals(0.55f, LiquidGlassBlur.fromSlider(0.25f), 0.00001f)
        assertEquals(1.5f, LiquidGlassBlur.fromSlider(0.75f), 0f)
        for (i in 0..100) {
            val position = i / 100f
            assertEquals(position, LiquidGlassBlur.toSlider(LiquidGlassBlur.fromSlider(position)), 0.00001f)
        }
        assertEquals(1f, LiquidGlassBlur.normalize(Float.NaN), 0f)
        assertEquals(0.1f, LiquidGlassBlur.normalize(-1f), 0f)
        assertEquals(2f, LiquidGlassBlur.normalize(3f), 0f)
    }

    @Test fun pendingInitialReadCannotOverwritePreview() = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)
        val initial = CompletableDeferred<LiquidGlassPreference>()
        try {
            val settings = LiquidGlassSettings(scope, { initial.await() }, {})
            settings.preview(1.8f)
            initial.complete(LiquidGlassPreference(0.5f))
            yield()
            assertEquals(1.8f, settings.state.value.multiplier, 0f)
        } finally { job.cancelAndJoin() }
    }

    @Test fun savesAreOrderedAndNeverRollBackNewerPreview() = runBlocking {
        withTimeout(5_000) {
            val job = SupervisorJob()
            val scope = CoroutineScope(coroutineContext + job)
            val started = Channel<Float>(Channel.UNLIMITED)
            val release = Channel<Unit>()
            val persisted = mutableListOf<Float>()
            try {
                val settings = LiquidGlassSettings(scope, { LiquidGlassPreference() }) {
                    started.send(it.multiplier)
                    release.receive()
                    persisted += it.multiplier
                }
                yield()
                settings.preview(0.25f)
                settings.save()
                assertEquals(0.25f, started.receive(), 0f)
                settings.preview(2f)
                settings.save()
                settings.preview(1.5f)
                release.send(Unit)
                assertEquals(2f, started.receive(), 0f)
                assertEquals(1.5f, settings.state.value.multiplier, 0f)
                release.send(Unit)
                yield()
                assertEquals(listOf(0.25f, 2f), persisted)
                assertEquals(1.5f, settings.state.value.multiplier, 0f)
            } finally { job.cancelAndJoin() }
        }
    }

    @Test fun failurePreservesPreviewAndRetryClearsError() = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)
        var fail = true
        try {
            val settings = LiquidGlassSettings(scope, { LiquidGlassPreference() }) { if (fail) error("disk full") }
            yield()
            settings.preview(2f)
            settings.save()
            yield()
            assertNotNull(settings.state.value.error)
            assertEquals(2f, settings.state.value.multiplier, 0f)
            fail = false
            settings.save()
            yield()
            assertNull(settings.state.value.error)
            settings.preview(LiquidGlassBlur.Default)
            settings.save()
            yield()
            assertEquals(1f, settings.state.value.multiplier, 0f)
        } finally { job.cancelAndJoin() }
    }

    @Test fun switchPreservesIntensityAndInitialReadCannotUndoIt() = runBlocking {
        val job = SupervisorJob()
        val initial = CompletableDeferred<LiquidGlassPreference>()
        val writes = mutableListOf<LiquidGlassPreference>()
        try {
            val settings = LiquidGlassSettings(CoroutineScope(coroutineContext + job), { initial.await() }, { writes += it })
            settings.preview(1.75f)
            settings.setEnabled(false)
            initial.complete(LiquidGlassPreference(0.5f, true))
            yield()
            assertEquals(LiquidGlassPreference(1.75f, false), settings.state.value.preference)
            settings.setEnabled(true)
            yield()
            assertEquals(listOf(LiquidGlassPreference(1.75f, false), LiquidGlassPreference(1.75f, true)), writes)
        } finally { job.cancelAndJoin() }
    }

    @Test fun switchAndSliderSavesRemainOrderedAfterFailureAndRetry() = runBlocking {
        val job = SupervisorJob()
        var fail = true
        val writes = mutableListOf<LiquidGlassPreference>()
        try {
            val settings = LiquidGlassSettings(CoroutineScope(coroutineContext + job), { LiquidGlassPreference(1.8f) }) {
                if (fail) error("disk full")
                writes += it
            }
            yield()
            settings.setEnabled(false)
            yield()
            assertNotNull(settings.state.value.error)
            assertEquals(LiquidGlassPreference(1.8f, false), settings.state.value.preference)
            fail = false
            settings.save()
            settings.preview(1f)
            settings.save()
            settings.setEnabled(true)
            yield()
            assertEquals(listOf(LiquidGlassPreference(1.8f, false), LiquidGlassPreference(1f, false), LiquidGlassPreference(1f, true)), writes)
            assertNull(settings.state.value.error)
        } finally { job.cancelAndJoin() }
    }

}
