package com.seasonyuu.fnmusic.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.seasonyuu.fnmusic.core.model.*
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** noBackupFilesDir excludes these device-only preferences from backup and transfer. */
class PlaylistOrderStore internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(PreferenceDataStoreFactory.create {
        File(context.applicationContext.noBackupFilesDir, "playlist-orders.preferences_pb")
    })
    private val json = Json { ignoreUnknownKeys = true }
    private fun key(account: String, id: PlaylistId) = stringPreferencesKey(
        MessageDigest.getInstance("SHA-256").digest(json.encodeToString(listOf(account, id.value)).toByteArray())
            .joinToString("") { "%02x".format(it) },
    )
    suspend fun read(account: String, id: PlaylistId): PlaylistOrder =
        store.data.first()[key(account, id)]?.let { json.decodeFromString<PlaylistOrder>(it) } ?: PlaylistOrder()

    suspend fun write(account: String, id: PlaylistId, value: PlaylistOrder) {
        store.edit { it[key(account, id)] = json.encodeToString(value) }
    }

    suspend fun delete(account: String, id: PlaylistId) { store.edit { it.remove(key(account, id)) } }
}
