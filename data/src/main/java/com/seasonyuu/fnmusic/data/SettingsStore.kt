package com.seasonyuu.fnmusic.data

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

    val cacheBytes: Flow<Long> = dataStore.data.map { values ->
        values[CACHE_BYTES]?.takeIf(ALLOWED_CACHE_BYTES::contains) ?: DEFAULT_CACHE_BYTES
    }

    suspend fun currentCacheBytes(): Long = cacheBytes.first()

    suspend fun setCacheBytes(value: Long) {
        require(value in ALLOWED_CACHE_BYTES)
        dataStore.edit { it[CACHE_BYTES] = value }
    }

    companion object {
        val ALLOWED_CACHE_BYTES = setOf(128L, 512L, 1_024L, 2_048L).map { it * 1024L * 1024L }.toSet()
        const val DEFAULT_CACHE_BYTES = 512L * 1024L * 1024L
        private val LIQUID_GLASS_ENABLED = booleanPreferencesKey("liquid_glass_enabled")
        private val LIQUID_GLASS_BLUR = floatPreferencesKey("liquid_glass_blur")
        private val CACHE_BYTES = longPreferencesKey("media_cache_bytes")
    }
}
