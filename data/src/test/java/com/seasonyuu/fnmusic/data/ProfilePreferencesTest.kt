package com.seasonyuu.fnmusic.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProfilePreferencesTest {
    @Test fun unknownThemeColorFallsBackWithoutChangingOtherSettings() = runBlocking {
        val job = SupervisorJob()
        val file = folder.newFolder().resolve("unknown.preferences_pb")
        val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            data.updateData { androidx.datastore.preferences.core.preferencesOf(
                androidx.datastore.preferences.core.stringPreferencesKey("theme_color") to "future-color",
                androidx.datastore.preferences.core.stringPreferencesKey("appearance") to "Light",
            ) }
            val settings = SettingsStore(data)
            assertEquals(ThemeColorPreference.Default, settings.themeColor.first())
            assertEquals(AppearancePreference.Light, settings.appearance.first())
            ThemeColorPreference.entries.forEach {
                settings.setThemeColor(it)
                assertEquals(it, settings.themeColor.first())
            }
        } finally { job.cancelAndJoin() }
    }
    @get:Rule val folder = TemporaryFolder()
    @Test fun preferencesMigrateAndSurviveRecreation() = runBlocking {
        val file = folder.newFolder().resolve("settings.preferences_pb")
        suspend fun store(block: suspend (SettingsStore) -> Unit) {
            val job = SupervisorJob()
            try { block(SettingsStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })) }
            finally { job.cancelAndJoin() }
        }
        store {
            assertEquals(AppearancePreference.Dark, it.appearance.first())
            assertEquals(ThemeColorPreference.Default, it.themeColor.first())
            it.setThemeColor(ThemeColorPreference.Green)
            it.setLiquidGlass(LiquidGlassPreference(1.5f, false))
            it.setCacheBytes(128L * 1024 * 1024)
            assertEquals(PlaybackCachePreference(bytes = 128L * 1024 * 1024), it.playbackCache.first())
            it.setAppearance(AppearancePreference.System)
            it.setStreamingQuality(StreamingQualityPreference(StreamingQuality.Original, StreamingQuality.Standard))
            it.setPlaybackCache(PlaybackCachePreference(false, 1024L * 1024 * 1024, 100))
        }
        store {
            assertEquals(AppearancePreference.System, it.appearance.first())
            assertEquals(ThemeColorPreference.Green, it.themeColor.first())
            assertEquals(LiquidGlassPreference(1.5f, false), it.liquidGlass.first())
            assertEquals(StreamingQualityPreference(StreamingQuality.Original, StreamingQuality.Standard), it.streamingQuality.first())
            assertEquals(PlaybackCachePreference(false, 1024L * 1024 * 1024, 100), it.playbackCache.first())
        }
    }
}
