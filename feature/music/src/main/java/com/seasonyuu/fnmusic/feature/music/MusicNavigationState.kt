package com.seasonyuu.fnmusic.feature.music

import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import com.seasonyuu.fnmusic.core.model.Album
import com.seasonyuu.fnmusic.core.model.Artist
import com.seasonyuu.fnmusic.core.model.Playlist
import com.seasonyuu.fnmusic.core.model.Track
import com.seasonyuu.fnmusic.core.model.TrackId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

internal enum class MusicPage { Root, Tracks, Recent, Albums, Artists, Playlists, Favorites, LiquidGlass, Password, Appearance, DisplayMode, ThemeColor, Cache, LyricsSettings, OnlineLyricsSettings, Quality, AdminLibraries, AdminUsers, AdminServer }

/** Resource identity travels with data so an outgoing page cannot render another page's response. */
data class DetailRequestKey(val type: String, val id: String)

internal sealed interface LibraryDetail {
    data class AlbumPage(val album: Album) : LibraryDetail
    data class ArtistPage(val artist: Artist) : LibraryDetail
    data class PlaylistPage(val playlist: Playlist) : LibraryDetail
    data class PlaylistEditorPage(val playlist: Playlist?, val initialTrackId: TrackId? = null) : LibraryDetail
    data class TrackPage(val track: Track) : LibraryDetail
}

internal val LibraryDetail.requestKey: DetailRequestKey?
    get() = when (this) {
        is LibraryDetail.AlbumPage -> DetailRequestKey("album", album.id.value)
        is LibraryDetail.ArtistPage -> DetailRequestKey("artist", artist.id.value)
        is LibraryDetail.PlaylistPage -> DetailRequestKey("playlist", playlist.id.value)
        is LibraryDetail.TrackPage -> DetailRequestKey("track", track.id.value)
        is LibraryDetail.PlaylistEditorPage -> null
    }

internal data class MusicPageEntry(
    val destination: MusicDestination,
    val page: MusicPage = MusicPage.Root,
    val detail: LibraryDetail? = null,
    val id: String = UUID.randomUUID().toString(),
    val depth: Int = 0,
)

internal class MusicNavigationState {
    var destination by mutableStateOf(MusicDestination.Home)
        private set
    private var stacks by mutableStateOf(MusicDestination.entries.associateWith { listOf(MusicPageEntry(it)) })
    // Derived UI appearance stays in memory and is recomputed by pages after process restore.
    private val appearances = mutableStateMapOf<String, MusicPageAppearance>()
    internal val entryIds: Set<String> get() = stacks.values.flatten().mapTo(mutableSetOf()) { it.id }

    internal fun appearanceFor(entry: MusicPageEntry): MusicPageAppearance =
        appearances[entry.id] ?: MusicPageAppearance()

    internal fun reportAppearance(entry: MusicPageEntry, appearance: MusicPageAppearance) {
        if (entry.id in entryIds) appearances[entry.id] = appearance
    }

    // Retain outgoing entries until the host finishes drawing their transition.
    internal fun retainAppearances(visibleEntryIds: Set<String>) {
        appearances.keys.retainAll(entryIds + visibleEntryIds)
    }

    val current: MusicPageEntry get() = stacks.getValue(destination).last()
    val canPop: Boolean get() = stacks.getValue(destination).size > 1
    val previous: MusicPageEntry? get() = stacks.getValue(destination).dropLast(1).lastOrNull()

    fun select(target: MusicDestination) { destination = target }
    fun push(detail: LibraryDetail? = null, page: MusicPage = MusicPage.Root) {
        stacks = stacks + (destination to (stacks.getValue(destination) + MusicPageEntry(destination, page, detail, depth = stacks.getValue(destination).size)))
    }
    fun pop(): MusicPageEntry? {
        if (!canPop) return null
        val removed = current
        stacks = stacks + (destination to stacks.getValue(destination).dropLast(1))
        return removed
    }
    fun resetCurrent(): List<MusicPageEntry> {
        val previous = stacks.getValue(destination)
        stacks = stacks + (destination to previous.take(1))
        return previous.drop(1)
    }

    companion object {
        val Saver = Saver<MusicNavigationState, Bundle>(
            save = { nav -> Bundle().apply {
                putInt("version", 2)
                putString("destination", nav.destination.name)
                nav.stacks.forEach { (tab, entries) ->
                    putParcelableArrayList(tab.name, ArrayList(entries.map { it.toBundle() }))
                }
            } },
            restore = { bundle -> restoreNavigation(bundle) },
        )

        internal fun restoreNavigation(bundle: Bundle): MusicNavigationState = runCatching {
            require(bundle.getInt("version") == 2)
            MusicNavigationState().apply {
                destination = MusicDestination.valueOf(requireNotNull(bundle.getString("destination")))
                stacks = MusicDestination.entries.associateWith { tab ->
                    @Suppress("DEPRECATION")
                    requireNotNull(bundle.getParcelableArrayList<Bundle>(tab.name)).mapIndexed { depth, saved -> saved.toEntry(tab).copy(depth = depth) }.flatMapIndexed { index, entry ->
                        if (entry.page == MusicPage.LiquidGlass && index > 0 &&
                            savedPagesNeedAppearance(bundle, tab, index)) {
                            listOf(MusicPageEntry(tab, MusicPage.Appearance), entry)
                        } else listOf(entry)
                    }.filterNot { it.page == MusicPage.ThemeColor || it.page == MusicPage.DisplayMode }.mapIndexed { depth, entry -> entry.copy(depth = depth) }.also { entries ->
                        require(entries.isNotEmpty() && entries.first().page == MusicPage.Root && entries.first().detail == null)
                    }
                }
            }
        }.getOrElse { MusicNavigationState() }
    }
}

private fun MusicPageEntry.toBundle() = Bundle().apply {
    putString("id", id)
    putString("page", page.name)
    val type: String
    val payload: String?
    when (val page = detail) {
        is LibraryDetail.AlbumPage -> { type = "album"; payload = Json.encodeToString(page.album) }
        is LibraryDetail.ArtistPage -> { type = "artist"; payload = Json.encodeToString(page.artist) }
        is LibraryDetail.PlaylistPage -> { type = "playlist"; payload = Json.encodeToString(page.playlist) }
        is LibraryDetail.TrackPage -> { type = "track"; payload = Json.encodeToString(page.track) }
        is LibraryDetail.PlaylistEditorPage -> {
            type = "editor"; payload = page.playlist?.let { Json.encodeToString(it) }
            putString("initialTrack", page.initialTrackId?.value)
        }
        null -> { type = "root"; payload = null }
    }
    putString("type", type)
    putString("payload", payload)
}

private fun Bundle.toEntry(tab: MusicDestination): MusicPageEntry {
    val payload = getString("payload")
    val detail = when (getString("type")) {
        "album" -> LibraryDetail.AlbumPage(Json.decodeFromString(requireNotNull(payload)))
        "artist" -> LibraryDetail.ArtistPage(Json.decodeFromString(requireNotNull(payload)))
        "playlist" -> LibraryDetail.PlaylistPage(Json.decodeFromString(requireNotNull(payload)))
        "track" -> LibraryDetail.TrackPage(Json.decodeFromString(requireNotNull(payload)))
        "editor" -> LibraryDetail.PlaylistEditorPage(payload?.let { Json.decodeFromString<Playlist>(it) }, getString("initialTrack")?.let(::TrackId))
        else -> null
    }
    return MusicPageEntry(tab, MusicPage.valueOf(requireNotNull(getString("page"))), detail, requireNotNull(getString("id")))
}

@Suppress("DEPRECATION")
private fun savedPagesNeedAppearance(bundle: Bundle, tab: MusicDestination, index: Int): Boolean =
    bundle.getParcelableArrayList<Bundle>(tab.name)?.getOrNull(index - 1)?.getString("page") != MusicPage.Appearance.name
