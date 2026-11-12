package com.seasonyuu.fnmusic

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ProfileFolderBrowserTest {
    @Test fun playableExpansionDeduplicatesFiltersAndCapsBeforeQueueMutation() = runBlocking {
        val repository = Stub()
        repository.tracks = listOf(Track(TrackId("removed"), "removed", isAdminDeleted = true)) +
            (0..1_002).flatMap { listOf(Track(TrackId("$it"), "$it"), Track(TrackId("$it"), "duplicate")) }
        var played = emptyList<Track>()
        var mode: FolderPlaybackMode? = null
        val api = browser(repository, play = { tracks, action -> played = tracks; mode = action })
        val selection = FolderSelection(listOf("/music/artist"), listOf(TrackId("0")))
        val result = api.play(selection, FolderPlaybackMode.Next)
        assertEquals(selection, repository.expanded)
        assertEquals(1_000, result.count)
        assertTrue(result.limited)
        assertEquals((0..999).map { "$it" }, played.map { it.id.value })
        assertEquals(FolderPlaybackMode.Next, mode)
        repository.tracks = emptyList()
        played = emptyList(); mode = null
        assertEquals(FolderPlaybackResult(0, false), api.play(selection, FolderPlaybackMode.Append))
        assertNull(mode)
    }

    @Test fun ordinaryUsersCannotDeleteOrRecoverAndDisabledServersCannotBrowse() = runBlocking {
        val repository = Stub()
        var enabled = true
        val api = browser(repository, enabled = { enabled }, manage = { false })
        val target = FolderSelection(trackIds = listOf(TrackId("t")))
        assertTrue(runCatching { api.delete(target, true) }.isFailure)
        assertTrue(runCatching { api.recover(target) }.isFailure)
        assertEquals(0, repository.deletes)
        assertEquals(0, repository.recovers)
        enabled = false
        assertTrue(runCatching { api.roots() }.isFailure)
        assertEquals(0, repository.rootReads)
    }

    @Test fun changedAccountCannotApplyOldPlayableResponseToNewQueue() = runBlocking {
        val repository = Stub()
        val pending = CompletableDeferred<List<Track>>()
        repository.expand = { pending.await() }
        var owner = "first"
        var queueWrites = 0
        val api = browser(repository, account = { owner }, play = { _, _ -> queueWrites++ })
        val operation = async { runCatching { api.play(FolderSelection(listOf("/music")), FolderPlaybackMode.Play) } }
        yield()
        owner = "second"
        pending.complete(listOf(Track(TrackId("old-song"), "old")))
        assertTrue(operation.await().isFailure)
        assertEquals(0, queueWrites)
    }

    @Test fun onlySuccessfulMutationsInvalidateCatalogAndPlaylistMembership() = runBlocking {
        val repository = Stub()
        val refreshed = mutableListOf<PlaylistId?>()
        val api = browser(repository, changed = { refreshed += it })
        val target = FolderSelection(listOf("/music"))
        api.addToPlaylist(target, PlaylistId("playlist"))
        api.delete(target, false)
        api.recover(target)
        assertEquals(listOf(PlaylistId("playlist"), null, null), refreshed)
        repository.failDelete = true
        assertTrue(runCatching { api.delete(target, true) }.isFailure)
        assertEquals(3, refreshed.size)
        assertEquals(2, repository.deletes)
    }

    private fun browser(repository: Stub, account: () -> String = { "account" }, enabled: () -> Boolean = { true },
        manage: () -> Boolean = { true }, play: (List<Track>, FolderPlaybackMode) -> Unit = { _, _ -> },
        changed: suspend (PlaylistId?) -> Unit = {}) = ProfileFolderBrowser(
        account, enabled, manage, repository, play, { emptyList() }, { Playlist(PlaylistId("new"), it) }, changed, {},
    )

    private class Stub : FolderBrowser {
        var tracks = emptyList<Track>()
        var expanded: FolderSelection? = null
        var expand: (suspend () -> List<Track>)? = null
        var rootReads = 0
        var deletes = 0
        var recovers = 0
        var failDelete = false
        override suspend fun roots(): List<MusicFolder> { rootReads++; return emptyList() }
        override suspend fun content(path: String, sort: FolderSort) = MusicDirectoryContent(MusicDirectoryNode(path, path), emptyList(), emptyList(), sort)
        override suspend fun playableTracks(selection: FolderSelection): List<Track> { expanded = selection; return expand?.invoke() ?: tracks }
        override suspend fun addToPlaylist(selection: FolderSelection, playlistId: PlaylistId) = Unit
        override suspend fun delete(selection: FolderSelection, permanently: Boolean) { deletes++; if (failDelete) throw java.io.IOException() }
        override suspend fun recover(selection: FolderSelection) { recovers++ }
    }
}
