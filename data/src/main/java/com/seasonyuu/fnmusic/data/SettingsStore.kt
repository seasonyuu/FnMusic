package com.seasonyuu.fnmusic.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsStore(context: Context) {
    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.applicationContext.preferencesDataStoreFile("settings")
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
        private val CACHE_BYTES = longPreferencesKey("media_cache_bytes")
    }
}
