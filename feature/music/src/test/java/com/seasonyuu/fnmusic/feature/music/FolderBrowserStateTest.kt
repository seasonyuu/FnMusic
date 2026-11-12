package com.seasonyuu.fnmusic.feature.music

import com.seasonyuu.fnmusic.core.model.*
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class FolderBrowserStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val api = Stub()
    private var changes = 0
    private val state = FolderBrowserState(api, scope, { changes++ })
    @After fun cleanup() { state.dispose(); scope.cancel() }

    @Test fun lateDirectoryResponseCannotOverwriteCurrentPathOrSort() = runBlocking {
        val slow = CompletableDeferred<MusicDirectoryContent>()
        api.read = { path, sort -> if (path == "/music/slow") withContext(NonCancellable) { slow.await() } else sample(path, sort) }
        state.start()
        state.navigate("/music/slow")
        state.navigate("/music/fast")
        state.changeSort(FolderSort.ModifiedDescending)
        slow.complete(sample("/music/slow"))
        yield()
        assertEquals("/music/fast", state.content!!.folder.path)
        assertEquals(FolderSort.ModifiedDescending, state.content!!.sort)
        assertFalse(state.loading)
    }

    @Test fun selectionClearsOnNavigationAndUsesDifferentFolderAndTrackIdentities() {
        state.start(); state.navigate("/music")
        state.toggle(state.content!!.folders.single())
        state.toggle(state.content!!.tracks.single())
        assertEquals(2, state.selection.size)
        state.toggle(state.content!!.tracks.single())
        assertEquals(1, state.selection.size)
        state.selectAll(); assertEquals(2, state.selection.size)
        state.selectAll(); assertTrue(state.selection.isEmpty)
        state.navigate("/music/album")
        assertFalse(state.selectionMode)
        assertTrue(state.selection.isEmpty)
        state.up(); assertEquals("/music", state.path)
        state.up(); assertNull(state.path)
        assertFalse(state.up())
    }

    @Test fun errorsAndUnavailableRootsCannotBeMistakenForEmptyDirectories() {
        for (status in listOf(1, 2, 3, 7)) {
            api.rootValues = listOf(MusicFolder("root", path = "/music", accessStatus = status))
            state.refresh(); state.navigate("/music")
            assertNotNull(state.error)
            assertNull(state.content)
            assertFalse(state.usable)
        }
        api.rootValues = listOf(MusicFolder("root", path = "/music"))
        api.read = { _, _ -> throw IOException() }
        state.refresh(); assertNotNull(state.error)
        api.read = { p, sort -> sample(p, sort).copy(folders = emptyList(), tracks = emptyList()) }
        state.refresh()
        assertNull(state.error)
        assertTrue(state.usable)
        assertEquals(0, state.content!!.rowCount)
    }

    @Test fun mutationFailureRetainsSelectionAndNeverRetriesDestructiveRequest() {
        state.start(); state.navigate("/music"); state.selectAll()
        val selected = state.selection
        api.deleteFailure = IOException()
        state.delete(selected, true)
        assertEquals(1, api.deletes)
        assertEquals(selected, state.selection)
        assertTrue(state.selectionMode)
        assertEquals(0, changes)
        assertNotNull(state.message)
        assertFalse(state.busy)
        api.deleteFailure = null
        state.delete(selected, false)
        assertEquals(2, api.deletes)
        assertEquals(1, changes)
        assertTrue(state.selection.isEmpty)
        assertFalse(state.selectionMode)
    }

    @Test fun refreshInvalidatesTreeAndLateTreeResponsesAreDiscarded() = runBlocking {
        val old = CompletableDeferred<MusicDirectoryContent>()
        state.start()
        api.read = { p, sort -> if (p == "/music") withContext(NonCancellable) { old.await() } else sample(p, sort) }
        state.toggleTree("/music")
        assertTrue("/music" in state.treeLoading)
        api.read = { p, sort -> sample(p, sort).copy(folders = emptyList()) }
        state.refresh()
        old.complete(sample("/music")); yield()
        assertTrue(state.treeContents.getValue("/music").folders.isEmpty())
        assertTrue(state.treeLoading.isEmpty())
    }

    @Test fun accountChangeOrDisposalDropsResponsesAndSavedPaths() = runBlocking {
        state.start(); state.navigate("/music")
        val saved = state.save()
        val pending = CompletableDeferred<MusicDirectoryContent>()
        api.read = { _, _ -> withContext(NonCancellable) { pending.await() } }
        state.navigate("/music/slow")
        api.accountKey = "other-account"
        pending.complete(sample("/music/slow")); yield()
        assertNull(state.content)
        val other = FolderBrowserState(api, scope)
        other.restore(saved)
        assertNull(other.path)
        other.dispose()
    }

    @Test fun restoresBreadcrumbsSortTreeAndIndependentScrollPositions() {
        state.start(); state.navigate("/music/artist/album")
        state.changeSort(FolderSort.AddedDescending)
        if ("/music" !in state.expandedPaths) state.toggleTree("/music")
        state.treeCollapsed = true
        state.scrollPositions["/music"] = 15 to 32
        state.scrollPositions["/music/artist"] = 4 to 12
        val restored = FolderBrowserState(api, scope)
        restored.restore(state.save()); restored.start()
        assertEquals(listOf("/music", "/music/artist", "/music/artist/album"), restored.breadcrumbs.map { it.path })
        assertEquals(FolderSort.AddedDescending, restored.sort)
        assertTrue(restored.treeCollapsed)
        assertTrue("/music" in restored.expandedPaths)
        assertEquals(15 to 32, restored.scrollPositions["/music"])
        restored.up(); assertEquals("/music/artist", restored.path)
        assertEquals(4 to 12, restored.scrollPositions["/music/artist"])
        restored.dispose()
    }

    @Test fun siblingPathPrefixesAreNotInsideAnAuthorizedRoot() {
        assertFalse(folderContains("/music", "/music-private/song"))
        assertTrue(folderContains("/music", "/music/ album "))
        state.start(); state.navigate("/music-private")
        assertNull(state.path)
    }

    private class Stub : FolderBrowserActions {
        override var accountKey = "account"
        var rootValues = listOf(MusicFolder("root", "Music", "/music"))
        var read: suspend (String, FolderSort) -> MusicDirectoryContent = { p, s -> sample(p, s) }
        var deleteFailure: Exception? = null
        var deletes = 0
        override suspend fun roots() = rootValues
        override suspend fun content(path: String, sort: FolderSort) = read(path, sort)
        override suspend fun playableTracks(selection: FolderSelection) = emptyList<Track>()
        override suspend fun play(selection: FolderSelection, mode: FolderPlaybackMode) = FolderPlaybackResult(0, false)
        override suspend fun playlists() = emptyList<Playlist>()
        override suspend fun createPlaylist(name: String) = Playlist(PlaylistId("new"), name)
        override suspend fun addToPlaylist(selection: FolderSelection, playlistId: PlaylistId) = Unit
        override suspend fun delete(selection: FolderSelection, permanently: Boolean) { deletes++; deleteFailure?.let { throw it } }
        override suspend fun recover(selection: FolderSelection) = Unit
    }

    companion object {
        private fun sample(path: String, sort: FolderSort = FolderSort.TitleAscending) = MusicDirectoryContent(
            MusicDirectoryNode(path, path.substringAfterLast('/'), 15), listOf(MusicDirectoryNode("$path/album", "album", 14)),
            listOf(Track(TrackId("song"), "song")), sort,
        )
    }
}
