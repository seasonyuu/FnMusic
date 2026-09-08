package com.seasonyuu.fnmusic.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LiquidGlassStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun defaultAndSavedValueSurviveStoreRecreation() = runBlocking {
        val file = folder.newFolder().resolve("settings.preferences_pb")
        suspend fun openStore(block: suspend (SettingsStore) -> Unit) {
            val job = SupervisorJob()
            val dataStore = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
            try { block(SettingsStore(dataStore)) } finally { job.cancelAndJoin() }
        }
        openStore { store ->
            assertEquals(1f, store.liquidGlassBlur.first(), 0f)
            store.setLiquidGlassBlur(1.75f)
        }
        openStore { store ->
            assertEquals(1.75f, store.liquidGlassBlur.first(), 0f)
            store.setLiquidGlassBlur(1f)
        }
        openStore { assertEquals(1f, it.liquidGlassBlur.first(), 0f) }
    }
}
