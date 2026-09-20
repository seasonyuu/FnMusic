package com.seasonyuu.fnmusic.data

import com.seasonyuu.fnmusic.core.model.AppearancePreference
import androidx.datastore.preferences.core.stringPreferencesKey
import com.seasonyuu.fnmusic.core.model.StreamingQuality
import com.seasonyuu.fnmusic.core.model.StreamingQualityPreference
import com.seasonyuu.fnmusic.core.model.PlaybackCachePreference
import androidx.datastore.preferences.core.intPreferencesKey
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.seasonyuu.fnmusic.core.model.LiquidGlassPreference
import androidx.datastore.preferences.core.floatPreferencesKey
import com.seasonyuu.fnmusic.core.model.LiquidGlassBlur
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsStore internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile("settings")
    })

    val onlineLyricsPreference: Flow<com.seasonyuu.fnmusic.core.model.OnlineLyricsPreference> = dataStore.data.map { values ->
        val sources = values[stringPreferencesKey("online_lyrics_sources")]?.split(',')?.mapNotNull { name ->
            com.seasonyuu.fnmusic.core.model.OnlineLyricsSource.entries.firstOrNull { it.name == name }
        }?.toSet()?.takeIf { it.isNotEmpty() } ?: com.seasonyuu.fnmusic.core.model.OnlineLyricsSource.entries.toSet()
        com.seasonyuu.fnmusic.core.model.OnlineLyricsPreference(values[booleanPreferencesKey("online_lyrics_enabled")] ?: true, sources)
    }

    suspend fun setOnlineLyricsPreference(value: com.seasonyuu.fnmusic.core.model.OnlineLyricsPreference) {
        require(value.sources.isNotEmpty()) { "请至少选择一个来源" }
        dataStore.edit {
            it[booleanPreferencesKey("online_lyrics_enabled")] = value.enabled
            it[stringPreferencesKey("online_lyrics_sources")] = value.sources.sortedBy { source -> source.ordinal }.joinToString(",") { source -> source.name }
        }
    }

    val amllEnabled: Flow<Boolean> = dataStore.data.map { it[booleanPreferencesKey("amll_enabled")] ?: true }

    suspend fun setAmllEnabled(enabled: Boolean) {
        dataStore.edit { it[booleanPreferencesKey("amll_enabled")] = enabled }
    }

    val lyricsSource: Flow<com.seasonyuu.fnmusic.core.model.LyricsFetchSource> = dataStore.data.map { values ->
        com.seasonyuu.fnmusic.core.model.LyricsFetchSource.entries.firstOrNull {
            it.name == values[stringPreferencesKey("lyrics_source")]
        } ?: com.seasonyuu.fnmusic.core.model.LyricsFetchSource.Bikonoo
    }

    suspend fun setLyricsSource(source: com.seasonyuu.fnmusic.core.model.LyricsFetchSource) {
        dataStore.edit { it[stringPreferencesKey("lyrics_source")] = source.name }
    }

    val themeColor: Flow<com.seasonyuu.fnmusic.core.model.ThemeColorPreference> = dataStore.data.map {
        com.seasonyuu.fnmusic.core.model.ThemeColorPreference.fromId(it[stringPreferencesKey("theme_color")])
    }
    suspend fun setThemeColor(value: com.seasonyuu.fnmusic.core.model.ThemeColorPreference) {
        dataStore.edit { it[stringPreferencesKey("theme_color")] = value.id }
    }

    val appearance: Flow<AppearancePreference> = dataStore.data.map { values ->
        AppearancePreference.entries.firstOrNull { it.name == values[APPEARANCE] } ?: AppearancePreference.Dark
    }
    suspend fun setAppearance(value: AppearancePreference) { dataStore.edit { it[APPEARANCE] = value.name } }

    val liquidGlass: Flow<LiquidGlassPreference> = dataStore.data.map { values ->
        LiquidGlassPreference(
            multiplier = values[LIQUID_GLASS_BLUR] ?: LiquidGlassBlur.Default,
            enabled = values[LIQUID_GLASS_ENABLED] ?: true,
        ).normalized()
    }

    suspend fun setLiquidGlass(value: LiquidGlassPreference) {
        val normalized = value.normalized()
        dataStore.edit {
            it[LIQUID_GLASS_BLUR] = normalized.multiplier
            it[LIQUID_GLASS_ENABLED] = normalized.enabled
        }
    }

    val liquidGlassBlur: Flow<Float> = dataStore.data.map { values ->
        LiquidGlassBlur.normalize(values[LIQUID_GLASS_BLUR] ?: LiquidGlassBlur.Default)
    }

    suspend fun setLiquidGlassBlur(value: Float) {
        dataStore.edit { it[LIQUID_GLASS_BLUR] = LiquidGlassBlur.normalize(value) }
    }

    val streamingQuality: Flow<StreamingQualityPreference> = dataStore.data.map { values ->
        fun quality(key: Preferences.Key<String>) = StreamingQuality.entries.firstOrNull { it.name == values[key] } ?: StreamingQuality.Original
        StreamingQualityPreference(quality(QUALITY_WIFI), quality(QUALITY_MOBILE))
    }
    suspend fun setStreamingQuality(value: StreamingQualityPreference) {
        dataStore.edit { it[QUALITY_WIFI] = value.wifi.name; it[QUALITY_MOBILE] = value.mobile.name }
    }

    val playbackCache: Flow<PlaybackCachePreference> = dataStore.data.map { values ->
        PlaybackCachePreference(values[CACHE_ENABLED] ?: true,
            values[CACHE_BYTES]?.takeIf(ALLOWED_CACHE_BYTES::contains) ?: DEFAULT_CACHE_BYTES,
            values[CACHE_TRACKS]?.takeIf { it in ALLOWED_CACHE_TRACKS } ?: 0)
    }
    suspend fun setPlaybackCache(value: PlaybackCachePreference) {
        require(value.bytes in ALLOWED_CACHE_BYTES && value.tracks in ALLOWED_CACHE_TRACKS)
        dataStore.edit {
            it[CACHE_ENABLED] = value.enabled; it[CACHE_BYTES] = value.bytes; it[CACHE_TRACKS] = value.tracks
        }
    }

    val cacheBytes: Flow<Long> = dataStore.data.map { values ->
        values[CACHE_BYTES]?.takeIf(ALLOWED_CACHE_BYTES::contains) ?: DEFAULT_CACHE_BYTES
    }

    suspend fun currentCacheBytes(): Long = cacheBytes.first()

    suspend fun setCacheBytes(value: Long) {
        require(value in ALLOWED_CACHE_BYTES)
        dataStore.edit { it[CACHE_BYTES] = value }
    }

    companion object {
        val ALLOWED_CACHE_TRACKS = setOf(0, 100, 500, 1000)
        private val CACHE_ENABLED = booleanPreferencesKey("media_cache_enabled")
        private val CACHE_TRACKS = intPreferencesKey("media_cache_tracks")
        val ALLOWED_CACHE_BYTES = setOf(128L, 512L, 1_024L, 2_048L).map { it * 1024L * 1024L }.toSet()
        const val DEFAULT_CACHE_BYTES = 512L * 1024L * 1024L
        private val QUALITY_WIFI = stringPreferencesKey("streaming_quality_wifi")
        private val QUALITY_MOBILE = stringPreferencesKey("streaming_quality_mobile")
        private val APPEARANCE = stringPreferencesKey("appearance")
        private val LIQUID_GLASS_ENABLED = booleanPreferencesKey("liquid_glass_enabled")
        private val LIQUID_GLASS_BLUR = floatPreferencesKey("liquid_glass_blur")
        private val CACHE_BYTES = longPreferencesKey("media_cache_bytes")
    }
}
