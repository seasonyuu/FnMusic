package com.seasonyuu.fnmusic.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LyricsSourceOrderStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun legacyOrderAndSelectionSurviveRecreation() = runBlocking {
        val file = folder.newFolder().resolve("settings.preferences_pb")
        suspend fun open(block: suspend (SettingsStore) -> Unit) {
            val job = SupervisorJob()
            val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
            try { block(SettingsStore(dataStore)) } finally { job.cancelAndJoin() }
        }
        val preference = OnlineLyricsPreference(false, setOf(OnlineLyricsSource.QQ), listOf(OnlineLyricsSource.Kugou, OnlineLyricsSource.QQ, OnlineLyricsSource.Netease))
        open {
            assertEquals(OnlineLyricsSource.entries, it.onlineLyricsPreference.first().order)
            it.setOnlineLyricsPreference(preference)
        }
        open { assertEquals(preference, it.onlineLyricsPreference.first()) }
    }

    @Test fun malformedOrderRetainsEveryKnownSourceExactlyOnce() = runBlocking {
        val job = SupervisorJob()
        val file = folder.newFolder().resolve("settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        try {
            dataStore.edit {
                it[stringPreferencesKey("online_lyrics_order")] = "QQ,unknown,QQ"
                it[stringPreferencesKey("online_lyrics_sources")] = "Kugou"
            }
            val result = SettingsStore(dataStore).onlineLyricsPreference.first()
            assertEquals(listOf(OnlineLyricsSource.QQ, OnlineLyricsSource.Netease, OnlineLyricsSource.Kugou), result.order)
            assertEquals(setOf(OnlineLyricsSource.Kugou), result.sources)
        } finally { job.cancelAndJoin() }
    }
}
