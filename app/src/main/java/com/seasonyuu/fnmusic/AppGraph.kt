package com.seasonyuu.fnmusic

import android.content.Context
import androidx.room.Room
import com.seasonyuu.fnmusic.core.model.PlayableTrack
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.network.ConnectionPolicy
import com.seasonyuu.fnmusic.core.network.CredentialVault
import com.seasonyuu.fnmusic.core.network.NetworkRuntime
import com.seasonyuu.fnmusic.core.network.SessionCoordinator
import com.seasonyuu.fnmusic.core.player.Media3PlayerController
import com.seasonyuu.fnmusic.data.FnMusicDatabase
import com.seasonyuu.fnmusic.data.MusicCatalogRepository
import com.seasonyuu.fnmusic.data.MusicPlaybackEventReporter
import com.seasonyuu.fnmusic.data.MusicSearchRepository
import com.seasonyuu.fnmusic.data.OptimisticFavoriteRepository
import com.seasonyuu.fnmusic.data.SettingsStore

class AppGraph(context: Context) {
    val network = NetworkRuntime()
    private val vault = CredentialVault(context, network.json)
    val session = SessionCoordinator(network, vault, ConnectionPolicy())
    val catalog = MusicCatalogRepository(network.api)
    val search = MusicSearchRepository(network.api)
    val favorites = OptimisticFavoriteRepository(network.api)
    val events = MusicPlaybackEventReporter(network.api)
    val settings = SettingsStore(context)
    val catalogCache = CatalogCache(context, network.json)
    val database = Room.databaseBuilder(context, FnMusicDatabase::class.java, "fn_music.db").build()
    val player = Media3PlayerController(context)

    fun coverUrl(coverId: String?, size: Int): String? = coverId?.let { network.baseUrlProvider.coverUrl(it, size) }

    fun deviceId(): String = vault.deviceId()

    fun playable(track: Track): PlayableTrack = PlayableTrack(
        track = track,
        streamUrl = network.baseUrlProvider.streamUrl(track.id.value),
        coverUrl = coverUrl(track.coverId, 800),
    )
}
