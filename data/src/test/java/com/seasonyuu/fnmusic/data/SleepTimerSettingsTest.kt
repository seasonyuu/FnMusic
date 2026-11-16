package com.seasonyuu.fnmusic.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SleepTimerSettingsTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun remembersDurationAcrossStoreRecreation() = runBlocking {
        val file = folder.newFolder().resolve("settings.preferences_pb")
        suspend fun open(block: suspend (SettingsStore) -> Unit) {
            val job = SupervisorJob()
            val data = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
            try { block(SettingsStore(data)) } finally { job.cancelAndJoin() }
        }
        open {
            assertEquals(30, it.sleepTimerMinutes.first())
            it.setSleepTimerMinutes(1440)
        }
        open { assertEquals(1440, it.sleepTimerMinutes.first()) }
    }
}
